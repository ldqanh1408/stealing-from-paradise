# Data Retention, Cleanup Cronjobs & Policy

**Phiên bản:** **v4.0** • **Áp dụng cho:** Marketplace Microservices (Java 25 / Spring Boot 4.0.4)

Tất cả cronjob chạy trong **Worker Service** — Quartz Scheduler + ShedLock (PostgreSQL provider)

---

## 1. NGUYÊN TẮC CHUNG

| Nguyên Tắc | Nội Dung |
|---------|---------|
| **Soft Delete First** | Mỗi dữ liệu cần xóa đều được đánh dấu trước (soft delete), sau một khoảng thời gian grace period mới hard delete. |
| **Distributed Lock** | Mỗi cronjob dùng ShedLock để đảm bảo chỉ 1 node chạy 1 job tại 1 thời điểm trong môi trường multi-instance. |
| **Audit Trail** | Các bảng tài chính (`TRANSACTIONS`, `REFUNDS`, `POINT_TRANSACTIONS`) **không bao giờ bị hard delete**. Chỉ archive nếu cần. |
| **Idempotent** | Mỗi cleanup job phải idempotent — chạy 2 lần kết quả như chạy 1 lần. |
| **Off-peak Execution** | Tất cả job nặng chạy ngoài giờ cao điểm: **02:00 — 05:00 UTC+7**. |
| **Batch Size** | Mỗi lần xử lý tối đa 500—1000 bản ghi để trình lock table. Dùng `LIMIT` + loop nếu cần. |
| **Cross-store Consistency** | Khi xóa record trên PostgreSQL/MongoDB, phải đồng bộ xóa dữ liệu liên quan trên Redis (cache/stock keys) và MinIO (media assets) trong cùng một luồng xử lý. |

---

## 2. DANH SÁCH CRONJOB

---

### JOB-01 — Flash Sale Session Lifecycle Manager

| Thuộc Tính | Giá Trị |
|----------|--------|
| **Mô Tả** | Tự động chuyển trạng thái `FS_SESSIONS`: UPCOMING → ACTIVE khi đến giờ, ACTIVE → ENDED khi hết giờ. |
| **Cron** | `0 * * * * *` (mỗi 1 phút) |
| **ShedLock name** | `flash-sale-session-lifecycle` |
| **Lock duration** | 55 giây |
| **Bảng tác động** | `FS_SESSIONS`, `FS_ITEMS` |
| **Cache tác động** | Redis — xóa stock keys khi session ENDED |

**Logic:**

```sql
-- Activate sessions
UPDATE FS_SESSIONS SET status = 'ACTIVE', updated_at = NOW()
WHERE status = 'UPCOMING' AND start_time <= NOW();

-- End sessions
UPDATE FS_SESSIONS SET status = 'ENDED', updated_at = NOW()
WHERE status = 'ACTIVE' AND end_time <= NOW();

-- Cancel pending items của session đã ENDED
UPDATE FS_ITEMS SET status = 'CANCELLED'
WHERE session_id IN (SELECT id FROM FS_SESSIONS WHERE status = 'ENDED')
  AND status = 'PENDING';
```

**Redis Cleanup:**

```
DEL fs:stock:{sessionId}:*
DEL fs:user_limit:{sessionId}:*
```

**Side effects:** Phát Kafka event `flash_sale.session_started` và `flash_sale.session_ended`.

---

### JOB-02 — Flash Sale Reminder Dispatcher

| Thuộc Tính | Giá Trị |
|----------|--------|
| **Mô Tả** | Gửi thông báo nhắc nhở cho Buyer đã đăng ký trước khi Flash Sale session bắt đầu 15 phút. |
| **Cron** | `0 * * * * *` (mỗi 1 phút) |
| **ShedLock name** | `flash-sale-reminder-dispatcher` |
| **Lock duration** | 55 giây |
| **Bảng tác động** | `FS_REMINDERS`, `FS_SESSIONS` |

```sql
SELECT r.user_id, r.session_id
FROM FS_REMINDERS r
JOIN FS_SESSIONS s ON r.session_id = s.id
WHERE s.status = 'UPCOMING'
  AND s.start_time BETWEEN NOW() AND NOW() + INTERVAL '15 minutes';
```

---

### JOB-03 — Loyalty Points Expiry

| Thuộc Tính | Giá Trị |
|----------|--------|
| **Mô Tả** | Tìm các điểm thưởng ở hết hạn, trừ ra khỏi `available_points` và tạo transaction ghi nhận. |
| **Cron** | `0 0 2 * * *` (02:00 hàng ngày) |
| **ShedLock name** | `loyalty-points-expiry` |
| **Lock duration** | 10 phút |
| **Bảng tác động** | `POINT_TRANSACTIONS`, `LOYALTY_ACCOUNTS` |

**Policy:** Điểm `EARNED` hết hạn sau **365 ngày** kể từ `expires_at`. JOB-03 chỉ expire `remaining_delta` (không phải `delta` gốc) để trình expire-over khi user đã dùng một phần.

```sql
SELECT id, user_id, remaining_delta
FROM POINT_TRANSACTIONS
WHERE type = 'EARNED'
  AND status = 'CONFIRMED'
  AND expires_at <= NOW()
  AND remaining_delta > 0
LIMIT 500;

-- Với mỗi bản ghi:
-- 1. INSERT POINT_TRANSACTIONS: type='EXPIRED', delta = -remaining_delta
-- 2. UPDATE LOYALTY_ACCOUNTS: available_points -= remaining_delta, expired_points += remaining_delta
-- 3. UPDATE POINT_TRANSACTIONS SET remaining_delta = 0 WHERE id = ?
-- 4. Dùng Optimistic Locking (version) để trình race condition
```

---

### JOB-04 — Outbox Event Publisher

| Thuộc Tính | Giá Trị |
|----------|--------|
| **Mô Tả** | Đọc các event `PENDING` trong `OUTBOX_EVENTS`, publish lên Kafka. |
| **Cron** | `0/10 * * * * *` (mỗi 10 giây) |
| **ShedLock name** | `outbox-event-publisher-{serviceName}` |
| **Lock duration** | 9 giây |
| **Bảng tác động** | `OUTBOX_EVENTS` |

```sql
SELECT id, topic, payload FROM OUTBOX_EVENTS
WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT 100;
-- Thành công: status = 'PROCESSED', processed_at = NOW()
-- Thất bại:   retry_count++; >= 5 → status = 'FAILED', insert FAILED_EVENTS
```

---

### JOB-05 — Outbox Events Cleanup

| Thuộc Tính | Giá Trị |
|----------|--------|
| **Cron** | `0 0 3 * * *` (03:00 hàng ngày) |
| **ShedLock name** | `outbox-cleanup` |
| **Lock duration** | 5 phút |

```sql
DELETE FROM OUTBOX_EVENTS WHERE status = 'PROCESSED' AND processed_at < NOW() - INTERVAL '7 days' LIMIT 1000;
DELETE FROM OUTBOX_EVENTS WHERE status = 'FAILED'    AND created_at  < NOW() - INTERVAL '3 days'  LIMIT 1000;
```

---

### JOB-06 — Failed Events Cleanup

| Thuộc Tính | Giá Trị |
|----------|--------|
| **Cron** | `0 0 3 30 * ?` (03:00 ngày 30 hàng tháng) |
| **ShedLock name** | `failed-events-cleanup` |
| **Lock duration** | 10 phút |

```sql
DELETE FROM FAILED_EVENTS WHERE status = 'RESOLVED' AND updated_at < NOW() - INTERVAL '30 days' LIMIT 500;
DELETE FROM FAILED_EVENTS WHERE status = 'DEAD'     AND updated_at < NOW() - INTERVAL '90 days' LIMIT 500;
```

---

### JOB-07 — Stale Cart Cleanup

| Thuộc Tính | Giá Trị |
|----------|--------|
| **Cron** | `0 0 4 * * *` (04:00 hàng ngày) |
| **ShedLock name** | `stale-cart-cleanup` |
| **Lock duration** | 10 phút |
| **Bảng tác động** | `MG_CARTS`, `MG_CART_ITEMS` (MongoDB) |

```javascript
// Bước 1: Xóa flash sale cart items thuộc session đã ENDED
db.mg_cart_items.deleteMany({ fs_item_id: { $in: endedFsItemIds } });

// Bước 2: Xóa cart inactive > 90 ngày
db.mg_carts.find({ updated_at: { $lt: new Date(Date.now() - 90*24*60*60*1000) } })
  .forEach(cart => {
    db.mg_cart_items.deleteMany({ cart_id: cart._id });
    db.mg_carts.deleteOne({ _id: cart._id });
  });
```

---

### JOB-08 — Flash Sale Data Cleanup

| Thuộc Tính | Giá Trị |
|----------|--------|
| **Cron** | `0 0 2 * * *` (02:00 hàng ngày) |
| **ShedLock name** | `flash-sale-cleanup` |
| **Lock duration** | 5 phút |
| **Bảng tác động** | `FS_REMINDERS`, `FS_SESSIONS`, `FS_ITEMS` |

| Bảng | Điều Kiện | Retention | Hành Động |
|------|----------|----------|---------|
| `FS_REMINDERS` | Session ENDED | 0 ngày | Hard delete |
| `FS_ITEMS` CANCELLED/REJECTED | Session ENDED | 30 ngày | Hard delete |
| `FS_ITEMS` APPROVED | Session ENDED | 180 ngày | Hard delete |
| `FS_SESSIONS` | Status ENDED | 365 ngày | Hard delete |

---

### JOB-09 — Notification Cleanup (MongoDB TTL)

Không dùng Quartz — dùng MongoDB TTL Index.

```javascript
db.mg_notifications.createIndex(
  { "created_at": 1 },
  { expireAfterSeconds: 90 * 24 * 60 * 60 }  // 90 ngày
);
```

---

### JOB-10 — Soft-Deleted Products Hard Cleanup

| Thuộc Tính | Giá Trị |
|----------|--------|
| **Cron** | `0 0 3 * * 0` (03:00 Chủ nhật) |
| **ShedLock name** | `soft-deleted-products-cleanup` |
| **Lock duration** | 30 phút |
| **Bảng tác động** | `MG_PRODUCTS`, `MG_PRODUCT_VARIANTS`, `MG_INVENTORIES`, `ES_PRODUCTS_INDEX`, MinIO |

**Điều kiện:** `deleted_at` có hơn 30 ngày và `stock_locked = 0`.

**Luồng:** Xóa MongoDB → gửi Search Service xóa ES → xóa MinIO `products-media`.

---

### JOB-11 — Trust Score Log Cleanup

| Thuộc Tính | Giá Trị |
|----------|--------|
| **Cron** | `0 0 4 1 * *` (04:00 ngày 1 hàng tháng) |
| **ShedLock name** | `trust-score-log-cleanup` |
| **Lock duration** | 5 phút |

```sql
DELETE FROM TRUST_SCORE_LOGS WHERE created_at < NOW() - INTERVAL '2 years' LIMIT 1000;
```

---

### JOB-12 — ShedLock Stale Entry Cleanup

| Thuộc Tính | Giá Trị |
|----------|--------|
| **Cron** | `0 0 5 * * *` (05:00 hàng ngày) |
| **ShedLock name** | *(không dùng ShedLock cho job này)* |

```sql
DELETE FROM SHEDLOCK WHERE lock_until < NOW() - INTERVAL '1 hour';
```

---

### JOB-13 — Stale PENDING Orders Auto-Cancel *(v2.0)*

| Thuộc Tính | Giá Trị |
|----------|--------|
| **Cron** | `0 0/5 * * * *` (mỗi 5 phút) |
| **ShedLock name** | `stale-pending-orders-cancel` |
| **Lock duration** | 4 phút 30 giây |
| **Bảng tác động** | `ORDERS`, `PARENT_ORDERS`, `TRANSACTIONS`, `POINT_TRANSACTIONS`, `MG_INVENTORIES` |

| Loại Đơn | Timeout | Hành Động |
|---------|--------|---------|
| Đơn thường | 30 phút | Auto-cancel |
| Đơn Flash Sale | 10 phút | Auto-cancel |

**Side effects:** Phát Kafka `order.auto_cancelled`, void PENDING POINT_TRANSACTIONS, giải phóng `stock_locked`.

---

### JOB-14 — Orphaned PENDING Loyalty Points Cleanup *(v2.0)*

| Thuộc Tính | Giá Trị |
|----------|--------|
| **Cron** | `0 0 3 * * *` (03:00 hàng ngày) |
| **ShedLock name** | `orphaned-pending-points-cleanup` |
| **Lock duration** | 10 phút |

Xử lý `POINT_TRANSACTIONS` PENDING mà đơn hàng gốc đã ở trạng thái cuối (CANCELLED/REFUNDED) — trường hợp JOB-13 bị sót.

---

### JOB-15 — Seller Stripe Onboarding URL Nullification *(v2.0)*

| Thuộc Tính | Giá Trị |
|----------|--------|
| **Cron** | `0 0 2 * * *` (02:00 hàng ngày) |
| **ShedLock name** | `stripe-onboarding-url-cleanup` |
| **Lock duration** | 2 phút |

```sql
UPDATE SELLER_STRIPE_ACCOUNTS
SET onboarding_url = NULL, updated_at = NOW()
WHERE onboarding_url IS NOT NULL
  AND account_status IN ('PENDING', 'RESTRICTED')
  AND updated_at < NOW() - INTERVAL '24 hours';
```

---

### JOB-16 — Rejected Products Cleanup *(v2.0)*

| Thuộc Tính | Giá Trị |
|----------|--------|
| **Cron** | `0 0 3 * * 0` (03:00 Chủ nhật — cùng ngày JOB-10) |
| **ShedLock name** | `rejected-products-cleanup` |
| **Lock duration** | 15 phút |

Soft-delete sản phẩm `REJECTED` không được Seller re-submit sau **90 ngày**. JOB-10 sẽ hard-delete 30 ngày sau.

---

### JOB-17 — Auto-Lock/Unlock Accounts by Trust Score *(v3.0)*

| Thuộc Tính | Giá Trị |
|----------|--------|
| **Mô Tả** | (1) Tự động khóa tài khoản khi `trust_score < 10`. (2) Tự động mở khóa tạm thời khi đến `locked_until`. |
| **Cron** | `0 0/15 * * * *` (mỗi 15 phút) |
| **ShedLock name** | `auto-lock-by-trust-score` |
| **Lock duration** | 14 phút |
| **Bảng tác động** | `USERS`, `TRUST_SCORE_LOGS`, `USER_BAN_HISTORY` |

```sql
-- Bước 1: Khóa tài khoản khi trust_score < 10
UPDATE USERS
SET status      = 'LOCKED',
    lock_reason = 'Trust score quá thấp (< 10). Liên hệ support để khiếu nại.',
    updated_at  = NOW()
WHERE trust_score < 10
  AND status = 'ACTIVE'
LIMIT 100;

-- Ghi USER_BAN_HISTORY cho mỗi user vừa bị khóa
INSERT INTO USER_BAN_HISTORY (user_id, action, reason, performed_by, locked_until)
VALUES (?, 'LOCKED', 'Trust score < 10 — auto-locked by JOB-17', 'SYSTEM', NULL);

-- Bước 2: Tự mở khóa tạm thời khi hết thời hạn
UPDATE USERS
SET status       = 'ACTIVE',
    locked_until = NULL,
    lock_reason  = NULL,
    updated_at   = NOW()
WHERE status = 'LOCKED'
  AND locked_until IS NOT NULL
  AND locked_until <= NOW()
LIMIT 100;

-- Ghi USER_BAN_HISTORY cho mỗi user vừa được mở khóa
INSERT INTO USER_BAN_HISTORY (user_id, action, reason, performed_by)
VALUES (?, 'UNLOCKED', 'locked_until reached — auto-unlocked by JOB-17', 'SYSTEM');
```

**Side effects:** Phát Kafka event `account.auto_locked` → Notification Service thông báo cho user. Khi lock: Identity Service thêm JTI vào Redis blocklist (xem Section 5.3).

**[GAP-PATCH]** Nhánh Auto-Unlock **bắt buộc** phát thêm Kafka event `account.unlocked` (payload: `{userId, reason: "locked_until_expired"}`) ngay sau khi `UPDATE USERS SET status = 'ACTIVE'` thành công → Notification Service nhận event này để gửi Email/Push Notification thông báo cho User biết tài khoản đã được mở khóa. Thiếu event này khiến User bị mở khóa ngầm trong DB nhưng không nhận được thông báo.

---

### JOB-18 — Buyer Excessive Cancellation Detector *(v3.0)*

| Thuộc Tính | Giá Trị |
|----------|--------|
| **Mô Tả** | Phát hiện Buyer hủy đơn quá nhiều (> 5 lần/30 ngày) và tự động trừ điểm trust score. |
| **Cron** | `0 0 3 * * *` (03:00 hàng ngày) |
| **ShedLock name** | `buyer-cancellation-detector` |
| **Lock duration** | 10 phút |
| **Bảng tác động** | `ORDERS`, `USERS`, `TRUST_SCORE_LOGS` |

```sql
-- [GAP-PATCH] Bắt buộc thêm điều kiện AND o.cancelled_by = 'BUYER'
-- để trình trừ điểm Buyer oan khi Seller/SYSTEM là người hủy đơn.
-- Tìm buyer có > 5 đơn bị cancel trong 30 ngày gần nhất,
-- chưa bị phạt trong rolling window này (last_cancellation_penalty_at)
SELECT o.user_id, COUNT(*) AS cancel_count
FROM ORDERS o
JOIN USERS u ON o.user_id = u.id
WHERE o.status = 'CANCELLED'
  AND o.cancelled_by = 'BUYER'
  AND o.updated_at >= NOW() - INTERVAL '30 days'
  AND o.is_flash_sale = false
  AND (u.last_cancellation_penalty_at IS NULL
       OR u.last_cancellation_penalty_at < NOW() - INTERVAL '30 days')
GROUP BY o.user_id
HAVING COUNT(*) > 5;

-- Với mỗi user_id tìm được:
-- 1. Lấy delta từ TRUST_SCORE_EVENTS_CONFIG WHERE event_code = 'EXCESSIVE_CANCELLATION' AND is_active = TRUE
-- 2. UPDATE USERS SET trust_score = GREATEST(trust_score + delta, 0), last_cancellation_penalty_at = NOW()
-- 3. INSERT TRUST_SCORE_LOGS (user_id, delta, event_code, changed_by='SYSTEM')
-- 4. Phát Kafka event trust_score.warning nếu trust_score sau < 30
```

**Idempotency:** `last_cancellation_penalty_at` đảm bảo mỗi user chỉ bị phạt một lần mỗi rolling 30 ngày.

---

### JOB-19 — Seller Good Behavior Reward *(v3.0)*

| Thuộc Tính | Giá Trị |
|----------|--------|
| **Mô Tả** | Tự động cộng điểm trust score cho Seller không có refund trong 30 ngày liên tiếp. |
| **Cron** | `0 0 4 1 * *` (04:00 ngày 1 hàng tháng) |
| **ShedLock name** | `seller-good-behavior-reward` |
| **Lock duration** | 10 phút |
| **Bảng tác động** | `USERS`, `TRUST_SCORE_LOGS`, `REFUNDS`, `ORDERS` |

```sql
-- Tìm seller có đơn DELIVERED trong 30 ngày nhưng không có refund PENDING/SUCCESS
SELECT DISTINCT o.seller_id
FROM ORDERS o
WHERE o.status = 'DELIVERED'
  AND o.updated_at >= NOW() - INTERVAL '30 days'
  AND o.seller_id NOT IN (
    SELECT DISTINCT o2.seller_id
    FROM REFUNDS r
    JOIN ORDERS o2 ON r.order_id = o2.id
    WHERE r.status IN ('PENDING', 'SUCCESS')
      AND r.created_at >= NOW() - INTERVAL '30 days'
  )
  AND o.seller_id IN (
    SELECT user_id FROM ROLES WHERE role_name = 'SELLER'
  );

-- Với mỗi seller_id tìm được:
-- 1. Lấy delta từ TRUST_SCORE_EVENTS_CONFIG WHERE event_code = 'SELLER_NO_REFUND_30D' AND is_active = TRUE
-- 2. UPDATE USERS SET trust_score = LEAST(trust_score + delta, 100), updated_at = NOW()
-- 3. INSERT TRUST_SCORE_LOGS (user_id, delta, event_code='SELLER_NO_REFUND_30D', changed_by='SYSTEM')
```

---

### JOB-20 — Annual Appeal Count Reset *(v3.0)*

| Thuộc Tính | Giá Trị |
|----------|--------|
| **Mô Tả** | Reset `USERS.appeal_count = 0` đầu năm mới, cho phép user appeal lại trong năm tiếp theo. |
| **Cron** | `0 0 0 1 1 *` (00:00 ngày 1 tháng 1 hàng năm) |
| **ShedLock name** | `annual-appeal-count-reset` |
| **Lock duration** | 5 phút |
| **Bảng tác động** | `USERS` |

```sql
UPDATE USERS
SET appeal_count = 0,
    updated_at   = NOW()
WHERE appeal_count > 0
LIMIT 5000;
```

---

### JOB-21 — Flash Sale Stock Reconciliation *(GAP-PATCH)*

| Thuộc Tính | Giá Trị |
|----------|--------|
| **Mô Tả** | Chống thất thoát tồn kho Redis khi Pod crash sau khi DECR nhưng trước khi tạo Order. So sánh tồn kho Redis `fs:stock:{fsItemId}` với số lượng đơn hàng thực tế trong DB, tự động INCR bổ lại nếu lệch. |
| **Cron** | `0 */5 * * * *` (mỗi 5 phút, chỉ chạy trong giờ Flash Sale đang ACTIVE) |
| **ShedLock name** | `flash-sale-stock-reconciliation` |
| **Lock duration** | 4 phút 30 giây |
| **Bảng tác động** | `FS_ITEMS`, `ORDER_ITEMS`, Redis keys `fs:stock:*` |
| **Điều kiện kích hoạt** | Chỉ xử lý các `FS_SESSIONS.status = 'ACTIVE'` |

```sql
-- Bước 1: Lấy tất cả FS_ITEMS thuộc session đang ACTIVE
SELECT fi.id AS fs_item_id, fi.flash_stock
FROM FS_ITEMS fi
JOIN FS_SESSIONS fs ON fi.session_id = fs.id
WHERE fs.status = 'ACTIVE' AND fi.status = 'APPROVED';

-- Bước 2: Với mỗi fs_item_id, đếm số lượng đã tạo đơn thành công trong DB
SELECT SUM(oi.quantity) AS confirmed_qty
FROM ORDER_ITEMS oi
JOIN ORDERS o ON oi.order_id = o.id
WHERE oi.fs_item_id = ?
  AND o.status NOT IN ('CANCELLED');

-- Bước 3 (Java): So sánh Redis và DB
-- redis_stock = GET fs:stock:{fsItemId}
-- expected_stock = flash_stock - confirmed_qty
-- Nếu redis_stock < expected_stock (lệch do crash):
--   INCR fs:stock:{fsItemId} BY (expected_stock - redis_stock)
--   Log vào FAILED_EVENTS với topic='flash_sale.stock_reconciled' để audit
```

**Lý do:** Khi Pod xử lý `POST /flash-sale/buy` crash sau `DECR fs:stock` nhưng trước khi commit Order vào DB, mechanism compensation chạy trong memory sẽ mất theo. Job này là lớp an toàn cuối cùng chống "ghost stock" bị kết trong Redis.

---

### JOB-22 — Auto-Delivered Stale SHIPPING Orders *(GAP-PATCH R1)*

| Thuộc Tính | Giá Trị |
|----------|--------|
| **Mô Tả** | [GAP-PATCH] Tự động chuyển các đơn hàng tắc nghen ở trạng thái SHIPPING quá 7 ngày sang DELIVERED và phát Kafka event `order.delivered`. Vấn đề: Nếu Buyer nhận hàng nhưng không bấm nút "nhận hàng", đơn kết vĩnh viễn ở SHIPPING — Seller không nhận được điểm Trust Score thưởng, điểm Loyalty của Buyer kết ở PENDING mãi mãi, tiền đối sót có thể bị treo. |
| **Cron** | `0 0 2 * * *` (02:00 AM hàng ngày) |
| **ShedLock name** | `auto-delivered-stale-shipping` |
| **Lock duration** | 55 phút |
| **Bảng tác động** | `ORDERS`, `OUTBOX_EVENTS` |
| **Điều kiện kích hoạt** | `ORDERS.status = 'SHIPPING'` VÀ `ORDERS.updated_at < NOW() - INTERVAL '7 days'` |
| **Batch size** | 200 bản ghi/lần, có Sleep 100ms giữa các batch |

```sql
-- Bước 1: Lấy danh sách đơn cần auto-delivered
SELECT id, buyer_id, seller_id, final_amt, is_flash_sale
FROM ORDERS
WHERE status = 'SHIPPING'
  AND updated_at < NOW() - INTERVAL '7 days'
LIMIT 200;

-- Bước 2 (per batch, trong transaction):
UPDATE ORDERS
SET status = 'DELIVERED', updated_at = NOW()
WHERE id = ANY(?)
  AND status = 'SHIPPING'; -- Optimistic guard chống race condition

-- Bước 3: INSERT vào OUTBOX_EVENTS để Kafka Producer phát order.delivered
-- Payload: {orderId, buyerId, sellerId, orderAmount, autoDelivered: true}
-- Consumer chain: Identity Service (trust score reward), Loyalty Service (confirm điểm PENDING)
INSERT INTO OUTBOX_EVENTS (topic, payload, status, created_at)
VALUES ('order.delivered', ?, 'PENDING', NOW());
```

**Side effects:** Phát Kafka `order.delivered` với flag `autoDelivered: true` → Identity Service cộng điểm Trust Score cho Seller (ORDER_DELIVERED_SELLER), Loyalty Service chuyển điểm PENDING → CONFIRMED, Notification Service thông báo cho Buyer và Seller.

**Phân biệt với JOB-13:** JOB-13 auto-cancel đơn PENDING chưa thanh toán sau 30 phút. JOB-22 auto-deliver đơn đã SHIPPING (đã thanh toán) sau 7 ngày Buyer không xác nhận.

---

## 3. POLICY TỔNG HỢP THEO BẢNG

### PostgreSQL

| Bảng | Retention | Hard Delete? | Job | Ghi Chú |
|------|-----------|---------|-----|--------|
| `USERS` | Vĩnh viễn | Không | JOB-17 (lock/unlock) | Không xóa; khóa bởi JOB-17 hoặc Admin |
| `ROLES` | Vĩnh viễn | Không | — | Gắn chặt với USERS |
| `ADDRESSES` | Đơn khi user tự xóa | Có | — | `DELETE /users/me/addresses/{id}` |
| `TRUST_SCORE_LOGS` | 2 năm | Có | JOB-11 | Admin + System ghi log; event_code FK tới config |
| `TRUST_SCORE_EVENTS_CONFIG` | Vĩnh viễn | Không | — | Cấu hình delta động; soft-disable qua `is_active = FALSE` |
| `USER_BAN_HISTORY` | 5 năm | Không | — | Tài liệu pháp lý; ghi mỗi lần lock/unlock |
| `ORDERS` | Vĩnh viễn | Không | JOB-22 (auto-delivered) | Tài chính; không bao giờ xóa |
| `PARENT_ORDERS` | Vĩnh viễn | Không | — | Tài chính |
| `ORDER_ITEMS` | Vĩnh viễn | Không | — | Tài chính |
| `TRANSACTIONS` | Vĩnh viễn | Không | — | Tài chính; không bao giờ xóa |
| `REFUNDS` | Vĩnh viễn | Không | — | Tài chính; audit trail |
| `REFUND_ITEMS` | Vĩnh viễn | Không | — | Tài chính; lưu return evidence |
| `POINT_TRANSACTIONS` | Vĩnh viễn | Không | JOB-03/14 (expire/void) | Tài chính; không xóa |
| `LOYALTY_ACCOUNTS` | Vĩnh viễn | Không | — | Không xóa; soft-lock khi user LOCKED |
| `OUTBOX_EVENTS` | 7 ngày (PROCESSED) / 3 ngày (FAILED) | Có | JOB-04/05 | Event sourcing |
| `FAILED_EVENTS` | 30 ngày (RESOLVED) / 90 ngày (DEAD) | Có | JOB-06 | DLQ; audit trail |
| `SHEDLOCK` | 1 giờ qua NOW() | Có | JOB-12 | Distributed lock; stale cleanup |
| `FS_SESSIONS` | 365 ngày khi ENDED | Có | JOB-08 | Archive trước khi xóa |
| `FS_ITEMS` | 30 ngày (REJECTED) / 180 ngày (APPROVED) | Có | JOB-08 | Session-scoped |
| `FS_REMINDERS` | 0 ngày khi session ENDED | Có | JOB-01/08 | Hard delete ngay |
| `MG_PRODUCTS` | 30 ngày (soft-deleted) | Có | JOB-10 | Soft delete → grace period → hard delete |
| `MG_PRODUCT_VARIANTS` | Kèm theo MG_PRODUCTS | Có | JOB-10 | Cascade delete |
| `MG_INVENTORIES` | Kèm theo MG_PRODUCTS | Có | JOB-10 | Cascade delete |
| `SELLER_STRIPE_ACCOUNTS` | Vĩnh viễn | Không | JOB-15 (onboarding_url) | Không xóa Stripe account info |

### MongoDB

| Bảng | Retention | Hard Delete? | Job | Ghi Chú |
|------|-----------|---------|-----|--------|
| `mg_carts` | 90 ngày inactive | Có | JOB-07 | Xóa giỏ hàng cũ |
| `mg_cart_items` | Kèm theo cart | Có | JOB-07 | Cascade delete |
| `mg_notifications` | 90 ngày | Có | JOB-09 (TTL Index) | TTL Index auto-expire |
| `mg_products` | Kèm theo PostgreSQL | Có | JOB-10 | Cross-store consistency |

### Elasticsearch

| Index | Retention | Cleanup Job | Ghi Chú |
|-------|-----------|---------|--------|
| `es_products_*` | Kèm theo MG_PRODUCTS | JOB-10 | Xóa khi product soft/hard delete |
| `es_orders_*` | Vĩnh viễn | — | Archive định kỳ nếu cần |

---

## 4. BẢNG TÓM TẮT CRONJOB

| Job | Cron | Tần Suất | Lock Duration | Off-peak? | Batch Size |
|-----|------|---------|---------|-----------|-----------|
| JOB-01 | `0 * * * * *` | 1 phút | 55s | Không | N/A (atomic) |
| JOB-02 | `0 * * * * *` | 1 phút | 55s | Không | N/A (atomic) |
| JOB-03 | `0 0 2 * * *` | 02:00 hng ngày | 10m | Có | 500 |
| JOB-04 | `0/10 * * * * *` | 10 giây | 9s | Không | 100 |
| JOB-05 | `0 0 3 * * *` | 03:00 hng ngày | 5m | Có | 1000 |
| JOB-06 | `0 0 3 30 * ?` | 03:00 ngày 30 | 10m | Có | 500 |
| JOB-07 | `0 0 4 * * *` | 04:00 hng ngày | 10m | Có | N/A (batch) |
| JOB-08 | `0 0 2 * * *` | 02:00 hng ngày | 5m | Có | N/A (batch) |
| JOB-09 | TTL Index | Tự động | — | — | — |
| JOB-10 | `0 0 3 * * 0` | 03:00 Chủ nhật | 30m | Có | 50 |
| JOB-11 | `0 0 4 1 * *` | 04:00 ngày 1 | 5m | Có | 1000 |
| JOB-12 | `0 0 5 * * *` | 05:00 hng ngày | — | Có | — |
| JOB-13 | `0 0/5 * * * *` | 5 phút | 4m30s | Không | 200 |
| JOB-14 | `0 0 3 * * *` | 03:00 hng ngày | 10m | Có | 500 |
| JOB-15 | `0 0 2 * * *` | 02:00 hng ngày | 2m | Có | N/A (batch) |
| JOB-16 | `0 0 3 * * 0` | 03:00 Chủ nhật | 15m | Có | N/A (batch) |
| JOB-17 | `0 0/15 * * * *` | 15 phút | 14m | Không | 100 |
| JOB-18 | `0 0 3 * * *` | 03:00 hng ngày | 10m | Có | 100 |
| JOB-19 | `0 0 4 1 * *` | 04:00 ngày 1 | 10m | Có | N/A (batch) |
| JOB-20 | `0 0 0 1 1 *` | 00:00 ngày 1/1 | 5m | Không | 5000 |
| JOB-21 | `0 */5 * * * *` | 5 phút (chỉ ACTIVE) | 4m30s | Không | N/A (atomic) |
| JOB-22 | `0 0 2 * * *` | 02:00 hng ngày | 55m | Có | 200 |

---

## 5. EXTERNAL STORAGE & CACHE POLICY

### 5.1 Redis

#### Flash Sale Stock Keys (Flash Sale Service :8086)

- **Key pattern:** `fs:stock:{sessionId}:{itemId}`
- **Value:** số lượng còn lại (tương tự `FS_ITEMS.flash_stock - confirmed_qty`)
- **TTL:** Đến khi session ENDED, JOB-01 xóa ngay (Redis auto-expire 24h fallback)
- **Cleanup:** JOB-01 (side effect), JOB-21 (reconciliation)

#### Redis Pub/Sub (Notification Service :8088)

- **Channel pattern:** `notifications:{userId}`
- **Message:** JSON thông báo (socket.io/webhook)
- **Retention:** 0 (in-memory; không lưu)

#### 5.3 Redis Token Blocklist (Identity Service :8081) (v3.0)

- **Key pattern:** `revoked_token:{jti}`
- **Value:** `1` (flag)
- **TTL:** Thời gian còn lại của token gốc (max 900 giây)
- **Use case:** JWT revocation khi account bị LOCKED (JOB-17)
- **Cleanup:** Redis tự auto-expire sau TTL

**Logic khi lock account:**
1. Identity Service lấy tất cả JTI đang active của user từ token cache
2. Với mỗi JTI: SET `revoked_token:{jti}` = 1 EX {ttl}
3. Mỗi request sau đó check key này trước khi validate JWT signature

### 5.2 MinIO (Object Storage)

#### Nh sản phẩm ( MG_PRODUCTS.images )

- **Bucket:** `products-media`
- **Key pattern:** `products/{productId}/{filename}`
- **Retention:** Kèm theo MG_PRODUCTS; xóa khi JOB-10 hard-delete
- **Cleanup:** JOB-10 → gửi API delete qua S3-compatible endpoint

#### Avatar người dùng ( USERS.avatar_url )

- **Bucket:** `user-avatars`
- **Key pattern:** `avatars/{userId}/{filename}`
- **Retention:** Cho đến khi user xóa tài khoản (hiện không hard-delete) hoặc upload avatar mới (ghi đè)
- **Cleanup:** Khi user xóa avatar qua API, DELETE object ngay

#### Nh bằng chứng hoàn tiền ( REFUNDS.evidence_images )

- **Bucket:** `refund-evidence`
- **Key pattern:** `refunds/{refundId}/{filename}`
- **Retention:** **Vĩnh viễn** (audit trail pháp lý)
- **Cleanup:** **KHÔNG bao giờ xóa**
- **Truy cập:** Pre-signed URL TTL 15 phút (Admin review)

---

## 6. CHECKLIST TRIỂN KHAI

### Infrastructure & Setup

- [ ] ShedLock table (`shedlock`) được tạo trên PostgreSQL
- [ ] Quartz Scheduler configured trong Worker Service
- [ ] Kafka topics (`order.delivered`, `order.auto_cancelled`, `account.auto_locked`, `account.unlocked`, `trust_score.warning`, `flash_sale.stock_reconciled`, etc.) được tạo
- [ ] Redis cluster ready (high availability)
- [ ] MinIO buckets (`products-media`, `user-avatars`, `refund-evidence`) được tạo
- [ ] MongoDB TTL index cho `mg_notifications` được tạo
- [ ] Connection pool kích thước >= 20 cho multi-job concurrency

### Alerting & Monitoring

- [ ] Job failure alert (Slack/PagerDuty) khi retry_count >= 3
- [ ] ShedLock timeout alert (> 30 phút)
- [ ] Outbox event backlog alert (PENDING > 1000)
- [ ] Failed events backlog alert (DEAD > 100)
- [ ] Redis memory usage alert
- [ ] Kafka lag monitor

### Testing

- [ ] Unit test JOB-17 lock/unlock logic
- [ ] Unit test JOB-18 rolling window logic
- [ ] Unit test JOB-21 stock reconciliation
- [ ] Integration test JOB-22 auto-delivery + Kafka event
- [ ] Chaos test: Pod crash during JOB-21 DECR + recovery
- [ ] Load test: 1000 concurrent orders during Flash Sale + JOB-21 reconciliation

### Security & Compliance

- [ ] REFUNDS.evidence_images bucket đặt private (no public read)
- [ ] Pre-signed URL TTL enforce 15 phút max
- [ ] Audit log cho mỗi Admin action (lock/unlock, adjust refund, etc.)
- [ ] Retention policy compliance check vs legal team
- [ ] PII anonymization policy (nếu có GDPR requirement)

---

**Tài liệu cập nhật: 2026-04-14**
**Phiên bản: 4.0 RTS Unified**

