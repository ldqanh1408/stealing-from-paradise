# Data Retention, Cleanup Cronjobs & Policy

**Phiên bản:** **v5.0** - Mỗi service tự quản lý jobs của mình
**Áp dụng cho:** Marketplace Microservices (Java 25 / Spring Boot 4.0.4)
**Cập nhật:** 2026-05-01

> **Thay đổi so với v4.0:** Mỗi service tự chạy jobs của mình thay vì worker-service trung tâm. ShedLock được cấu hình trong mỗi service. Jobs được gán cho service sở hữu primary data.

---

## 1. NGUYÊN TẮC CHUNG

| Nguyên Tắc | Nội Dung |
|---------|---------|
| **Service Ownership** | Mỗi job chạy trong service sở hữu primary data. Không có worker-service trung tâm. |
| **Distributed Lock** | Mỗi job dùng ShedLock (PostgreSQL provider) để đảm bảo chỉ 1 node chạy tại 1 thời điểm. ShedLock table nằm trong DB của service đó. |
| **Soft Delete First** | Mỗi dữ liệu cần xóa đều được đánh dấu trước (soft delete), sau grace period mới hard delete. |
| **Audit Trail** | Các bảng tài chính (`TRANSACTIONS`, `REFUNDS`) **không bao giờ bị hard delete**. |
| **Idempotent** | Mỗi cleanup job phải idempotent — chạy 2 lần kết quả như chạy 1 lần. |
| **Off-peak Execution** | Tất cả job nặng chạy ngoài giờ cao điểm: **02:00 — 05:00 UTC+7**. |
| **Batch Size** | Mỗi lần xử lý tối đa 500—1000 bản ghi để tránh lock table. Dùng `LIMIT` + loop nếu cần. |
| **Cross-store Consistency** | Khi xóa record trên PostgreSQL/MongoDB, phải đồng bộ xóa dữ liệu liên quan trên Redis và MinIO trong cùng một luồng. |

---

## 2. BẢNG TÓM TẮT THEO SERVICE

```
┌─────────────────────┬──────────────────────────────────────────────────────────────┬─────────┐
│ Service              │ Jobs                                                            │ DB      │
├─────────────────────┼──────────────────────────────────────────────────────────────┼─────────┤
│ identity-service     │ JOB-17 (auto lock/unlock accounts)                             │ Postgres │
│ (:8081)            │                                                                  │         │
├─────────────────────┼──────────────────────────────────────────────────────────────┼─────────┤
│ flashsale-service   │ JOB-01 (session lifecycle)                                     │ Postgres│
│ (:8085)            │ JOB-02 (reminder dispatcher)                                    │         │
│                     │ JOB-08 (flash sale data cleanup)                               │         │
│                     │ JOB-21 (stock reconciliation)                                  │         │
├─────────────────────┼──────────────────────────────────────────────────────────────┼─────────┤
│ product-service      │ JOB-07 (stale cart cleanup)                                    │ MongoDB │
│ (:8090)            │ JOB-10 (soft-deleted products hard cleanup)                     │         │
│                     │ JOB-16 (rejected products soft-delete)                          │         │
├─────────────────────┼──────────────────────────────────────────────────────────────┼─────────┤
│ order-service       │ JOB-13 (stale PENDING orders auto-cancel)                      │ Postgres │
│ (:8083)            │ JOB-22 (auto-delivered stale SHIPPING)                        │         │
├─────────────────────┼──────────────────────────────────────────────────────────────┼─────────┤
│ payment-service     │ JOB-04 (outbox event publisher)                               │ Postgres │
│ (:8082)            │ JOB-05 (outbox events cleanup)                                  │         │
│                     │ JOB-06 (failed events cleanup)                                  │         │
│                     │ JOB-12 (shedlock stale cleanup)                                │         │
│                     │ JOB-15 (stripe onboarding URL nullification)                    │         │
├─────────────────────┼──────────────────────────────────────────────────────────────┼─────────┤
│ notification-service │ JOB-09 (notification TTL cleanup)                               │ MongoDB │
│ (:8092)            │ (dùng MongoDB TTL Index — không cần cron)                       │         │
└─────────────────────┴──────────────────────────────────────────────────────────────┴─────────┘
```

---

## 3. DANH SÁCH CHI TIẾT THEO SERVICE

---

### 🏠 identity-service (`:8081`) — Trust Score & Account Management

---

#### JOB-17 — Auto-Lock/Unlock Accounts by Trust Score

| Thuộc Tính | Giá Trị |
|------------|---------|
| **Mô Tả** | (1) Tự động khóa tài khoản khi `trust_score < 10`. (2) Tự động mở khóa tạm thời khi đến `locked_until`. |
| **Service** | `identity-service` |
| **Cron** | `0 0/15 * * * *` (mỗi 15 phút) |
| **ShedLock** | `auto-lock-by-trust-score` — lock duration 14 phút |
| **Bảng tác động** | `USERS` |

```sql
-- Bước 1: Khóa tài khoản khi trust_score < 10
UPDATE USERS
SET status      = 'LOCKED',
    lock_reason = 'Trust score quá thấp (< 10). Liên hệ support để khiếu nại.',
    updated_at  = NOW()
WHERE trust_score < 10
  AND status = 'ACTIVE'
LIMIT 100;

-- Với mỗi user vừa bị khóa:
-- Phát Kafka event account.auto_locked

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

-- Với mỗi user vừa được mở khóa:
-- Phát Kafka event account.unlocked → Notification Service thông báo User
-- Identity Service thêm JTI vào Redis blocklist (xem Redis Token Blocklist bên dưới)
```

---

### ⚡ flashsale-service (`:8085`) — Flash Sale

---

#### JOB-01 — Flash Sale Session Lifecycle Manager

| Thuộc Tính | Giá Trị |
|------------|---------|
| **Mô Tả** | Tự động chuyển trạng thái `FS_SESSIONS`: UPCOMING → ACTIVE khi đến giờ, ACTIVE → ENDED khi hết giờ. |
| **Service** | `flashsale-service` |
| **Cron** | `0 * * * * *` (mỗi 1 phút) |
| **ShedLock** | `flash-sale-session-lifecycle` — lock duration 55 giây |
| **Bảng tác động** | `FS_SESSIONS`, `FS_ITEMS` |
| **Cache tác động** | Redis — seed/xóa stock keys |

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

```redis
-- Khi session ACTIVE: seed Redis stock
HSET fs:stock:{sessionId}:{itemId} {flash_stock}

-- Khi session ENDED: cleanup Redis
DEL fs:stock:{sessionId}:*
DEL fs:user_limit:{sessionId}:*
```

**Side effects:** Phát Kafka event `flash_sale.session_started` và `flash_sale.session_ended`.

---

#### JOB-02 — Flash Sale Reminder Dispatcher

| Thuộc Tính | Giá Trị |
|------------|---------|
| **Mô Tả** | Gửi thông báo nhắc nhở cho Buyer đã đăng ký trước khi Flash Sale bắt đầu 15 phút. |
| **Service** | `flashsale-service` |
| **Cron** | `0 * * * * *` (mỗi 1 phút) |
| **ShedLock** | `flash-sale-reminder-dispatcher` — lock duration 55 giây |
| **Bảng tác động** | `FS_REMINDERS`, `FS_SESSIONS` |

```sql
SELECT r.user_id, r.session_id
FROM FS_REMINDERS r
JOIN FS_SESSIONS s ON r.session_id = s.id
WHERE s.status = 'UPCOMING'
  AND s.start_time BETWEEN NOW() AND NOW() + INTERVAL '15 minutes';
```

> Mỗi Buyer nhận tối đa 1 notification mỗi session (tránh spam).

---

#### JOB-08 — Flash Sale Data Cleanup

| Thuộc Tính | Giá Trị |
|------------|---------|
| **Mô Tả** | Xóa dữ liệu Flash Sale theo retention policy. |
| **Service** | `flashsale-service` |
| **Cron** | `0 0 2 * * *` (02:00 hàng ngày) |
| **ShedLock** | `flash-sale-cleanup` — lock duration 5 phút |
| **Bảng tác động** | `FS_REMINDERS`, `FS_SESSIONS`, `FS_ITEMS` |

| Bảng | Điều Kiện | Retention | Hành Động |
|------|------------|-----------|----------|
| `FS_REMINDERS` | Session ENDED | 0 ngày | Hard delete |
| `FS_ITEMS` CANCELLED/REJECTED | Session ENDED | 30 ngày | Hard delete |
| `FS_ITEMS` APPROVED | Session ENDED | 180 ngày | Hard delete |
| `FS_SESSIONS` | Status ENDED | 365 ngày | Hard delete |

---

#### JOB-21 — Flash Sale Stock Reconciliation

| Thuộc Tính | Giá Trị |
|------------|---------|
| **Mô Tả** | Chống thất thoát tồn kho Redis khi Pod crash sau khi DECR nhưng trước khi tạo Order. So sánh tồn kho Redis với số lượng đơn hàng thực tế trong DB, tự động INCR bổ lại nếu lệch. |
| **Service** | `flashsale-service` |
| **Cron** | `0 */5 * * * *` (mỗi 5 phút, chỉ chạy khi có session ACTIVE) |
| **ShedLock** | `flash-sale-stock-reconciliation` — lock duration 4 phút 30 giây |
| **Bảng tác động** | `FS_ITEMS`, `ORDER_ITEMS`, Redis `fs:stock:*` |
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

> **Lý do:** Khi Pod xử lý `POST /flash-sale/buy` crash sau `DECR fs:stock` nhưng trước khi commit Order vào DB, compensation trong memory sẽ mất theo. Job này là lớp an toàn cuối cùng chống "ghost stock" bị kẹt trong Redis.

---

### 📦 product-service (`:8090`) — Products & Carts

---

#### JOB-07 — Stale Cart Cleanup

| Thuộc Tính | Giá Trị |
|------------|---------|
| **Mô Tả** | Xóa cart không hoạt động và flash sale cart items của session đã kết thúc. |
| **Service** | `product-service` |
| **Cron** | `0 0 4 * * *` (04:00 hàng ngày) |
| **ShedLock** | `stale-cart-cleanup` — lock duration 10 phút |
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

#### JOB-10 — Soft-Deleted Products Hard Cleanup

| Thuộc Tính | Giá Trị |
|------------|---------|
| **Mô Tả** | Xóa vĩnh viễn sản phẩm đã soft-delete và không còn đơn hàng liên quan. |
| **Service** | `product-service` |
| **Cron** | `0 0 3 * * 0` (03:00 Chủ nhật) |
| **ShedLock** | `soft-deleted-products-cleanup` — lock duration 30 phút |
| **Bảng tác động** | `MG_PRODUCTS`, `MG_PRODUCT_VARIANTS`, `MG_INVENTORIES`, `ES_PRODUCTS_INDEX`, MinIO |

> **Điều kiện:** `deleted_at` có hơn 30 ngày và `stock_locked = 0`.

**Luồng:** Xóa MongoDB → gửi Search Service xóa ES → xóa MinIO `products-media`.

---

#### JOB-16 — Rejected Products Soft-Delete

| Thuộc Tính | Giá Trị |
|------------|---------|
| **Mô Tả** | Soft-delete sản phẩm `REJECTED` không được Seller re-submit sau 90 ngày. JOB-10 sẽ hard-delete 30 ngày sau. |
| **Service** | `product-service` |
| **Cron** | `0 0 3 * * 0` (03:00 Chủ nhật — cùng ngày JOB-10) |
| **ShedLock** | `rejected-products-cleanup` — lock duration 15 phút |

---

### 🛒 order-service (`:8083`) — Orders

---

#### JOB-13 — Stale PENDING Orders Auto-Cancel

| Thuộc Tính | Giá Trị |
|------------|---------|
| **Mô Tả** | Tự động hủy đơn quá hạn thanh toán. Đơn thường > 30 phút, đơn Flash Sale > 10 phút. |
| **Service** | `order-service` |
| **Cron** | `0 0/5 * * * *` (mỗi 5 phút) |
| **ShedLock** | `stale-pending-orders-cancel` — lock duration 4 phút 30 giây |
| **Bảng tác động** | `ORDERS`, `PARENT_ORDERS`, `TRANSACTIONS`, `MG_INVENTORIES` |

| Loại Đơn | Timeout | Hành Động |
|----------|---------|-----------|
| Đơn thường | 30 phút | Auto-cancel |
| Đơn Flash Sale | 10 phút | Auto-cancel |

**Side effects:**
- Phát Kafka `order.auto_cancelled`
- Giải phóng `stock_locked` trong `MG_INVENTORIES` (product-service nhận Kafka)

---

#### JOB-22 — Auto-Delivered Stale SHIPPING Orders

| Thuộc Tính | Giá Trị |
|------------|---------|
| **Mô Tả** | Tự động chuyển các đơn hàng tắc nghẽn ở trạng thái SHIPPING quá 7 ngày sang DELIVERED. Vấn đề: Buyer quên bấm "nhận hàng" → đơn kẹt ở SHIPPING vĩnh viễn. |
| **Service** | `order-service` |
| **Cron** | `0 0 2 * * *` (02:00 hàng ngày) |
| **ShedLock** | `auto-delivered-stale-shipping` — lock duration 55 phút |
| **Bảng tác động** | `ORDERS`, `OUTBOX_EVENTS` |
| **Điều kiện** | `ORDERS.status = 'SHIPPING'` VÀ `ORDERS.updated_at < NOW() - INTERVAL '7 days'` |
| **Batch size** | 200 bản ghi/lần, có Sleep 100ms giữa các batch |

> ⚠️ **NGOẠI LỆ:** Đơn có `REFUNDS.refund_reason_type = 'RETURN_TO_SENDER'` KHÔNG bị ảnh hưởng (đơn RTS đã có refund pending, không cần auto-delivered).

```sql
-- Bước 1: Lấy danh sách đơn cần auto-delivered
-- NGOẠI TRỪ đơn có refund RTS đang pending/approved
SELECT o.id, o.buyer_id, o.seller_id, o.final_amt, o.is_flash_sale
FROM ORDERS o
WHERE o.status = 'SHIPPING'
  AND o.updated_at < NOW() - INTERVAL '7 days'
  AND NOT EXISTS (
    SELECT 1 FROM REFUNDS r
    WHERE r.order_id = o.id
      AND r.refund_reason_type = 'RETURN_TO_SENDER'
      AND r.status IN ('PENDING', 'SUCCESS')
  )
LIMIT 200;

-- Bước 2 (per batch, trong transaction):
UPDATE ORDERS
SET status = 'DELIVERED', updated_at = NOW()
WHERE id = ANY(?)
  AND status = 'SHIPPING'; -- Optimistic guard chống race condition

-- Bước 3: INSERT vào OUTBOX_EVENTS để Kafka Producer phát order.delivered
INSERT INTO OUTBOX_EVENTS (topic, payload, status, created_at)
VALUES ('order.delivered', ?, 'PENDING', NOW());
-- Payload: {orderId, buyerId, sellerId, orderAmount, autoDelivered: true}
```

**Side effects:** Phát Kafka `order.delivered` với flag `autoDelivered: true`:
- Identity Service: cộng điểm Trust Score cho Seller
- Payment Service: Stripe Transfer cho Seller
- Notification Service: thông báo Buyer và Seller

**Phân biệt với JOB-13:** JOB-13 auto-cancel đơn PENDING chưa thanh toán. JOB-22 auto-deliver đơn đã SHIPPING (đã thanh toán) sau 7 ngày.

---

### 💳 payment-service (`:8082`) — Payments & Outbox

---

#### JOB-04 — Outbox Event Publisher

| Thuộc Tính | Giá Trị |
|------------|---------|
| **Mô Tả** | Đọc các event `PENDING` trong `OUTBOX_EVENTS`, publish lên Kafka. |
| **Service** | `payment-service` |
| **Cron** | `0/10 * * * * *` (mỗi 10 giây) |
| **ShedLock** | `outbox-event-publisher` — lock duration 9 giây |
| **Bảng tác động** | `OUTBOX_EVENTS` |

```sql
SELECT id, topic, payload FROM OUTBOX_EVENTS
WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT 100;
-- Thành công: status = 'PROCESSED', processed_at = NOW()
-- Thất bại:   retry_count++; >= 5 → status = 'FAILED', insert FAILED_EVENTS
```

---

#### JOB-05 — Outbox Events Cleanup

| Thuộc Tính | Giá Trị |
|------------|---------|
| **Service** | `payment-service` |
| **Cron** | `0 0 3 * * *` (03:00 hàng ngày) |
| **ShedLock** | `outbox-cleanup` — lock duration 5 phút |

```sql
DELETE FROM OUTBOX_EVENTS WHERE status = 'PROCESSED' AND processed_at < NOW() - INTERVAL '7 days' LIMIT 1000;
DELETE FROM OUTBOX_EVENTS WHERE status = 'FAILED'    AND created_at  < NOW() - INTERVAL '3 days'  LIMIT 1000;
```

---

#### JOB-06 — Failed Events Cleanup

| Thuộc Tính | Giá Trị |
|------------|---------|
| **Service** | `payment-service` |
| **Cron** | `0 0 3 30 * ?` (03:00 ngày 30 hàng tháng) |
| **ShedLock** | `failed-events-cleanup` — lock duration 10 phút |

```sql
DELETE FROM FAILED_EVENTS WHERE status = 'RESOLVED' AND updated_at < NOW() - INTERVAL '30 days' LIMIT 500;
DELETE FROM FAILED_EVENTS WHERE status = 'DEAD'     AND updated_at < NOW() - INTERVAL '90 days' LIMIT 500;
```

---

#### JOB-12 — ShedLock Stale Entry Cleanup

| Thuộc Tính | Giá Trị |
|------------|---------|
| **Mô Tả** | Dọn ShedLock entries bị treo do máy crash. Chạy không dùng ShedLock (self-cleanup). |
| **Service** | `payment-service` (hoặc bất kỳ service nào có ShedLock) |
| **Cron** | `0 0 5 * * *` (05:00 hàng ngày) |
| **ShedLock** | *(Không dùng ShedLock cho job này)* |

```sql
DELETE FROM SHEDLOCK WHERE lock_until < NOW() - INTERVAL '1 hour';
```

---

#### JOB-15 — Seller Stripe Onboarding URL Nullification

| Thuộc Tính | Giá Trị |
|------------|---------|
| **Mô Tả** | Vô hiệu hóa Stripe onboarding URL hết hạn 24 giờ. |
| **Service** | `payment-service` |
| **Cron** | `0 0 2 * * *` (02:00 hàng ngày) |
| **ShedLock** | `stripe-onboarding-url-cleanup` — lock duration 2 phút |

```sql
UPDATE SELLER_STRIPE_ACCOUNTS
SET onboarding_url = NULL, updated_at = NOW()
WHERE onboarding_url IS NOT NULL
  AND account_status IN ('PENDING', 'RESTRICTED')
  AND updated_at < NOW() - INTERVAL '24 hours';
```

---

### 🔔 notification-service (`:8092`) — Notifications

---

#### JOB-09 — Notification Cleanup (MongoDB TTL Index)

| Thuộc Tính | Giá Trị |
|------------|---------|
| **Mô Tả** | Tự động xóa thông báo cũ hơn 90 ngày bằng MongoDB TTL Index. Không cần cron job. |
| **Service** | `notification-service` |
| **Cơ chế** | MongoDB TTL Index — tự động delete |

```javascript
// Tạo TTL index (chạy 1 lần lúc khởi tạo service)
db.mg_notifications.createIndex(
  { "created_at": 1 },
  { expireAfterSeconds: 90 * 24 * 60 * 60 }  // 90 ngày
);
```

---

## 4. POLICY TỔNG HỢP THEO BẢNG

### PostgreSQL

| Bảng | Retention | Hard Delete? | Job | Service |
|------|-----------|---------|-----|---------|
| `USERS` | Vĩnh viễn | Không | JOB-17 (lock/unlock) | identity-service |
| `ROLES` | Vĩnh viễn | Không | — | — |
| `ADDRESSES` | Khi user tự xóa | Có | — | identity-service |
| `ORDERS` | Vĩnh viễn | Không | JOB-22 (auto-delivered) | order-service |
| `PARENT_ORDERS` | Vĩnh viễn | Không | — | order-service |
| `ORDER_ITEMS` | Vĩnh viễn | Không | — | order-service |
| `TRANSACTIONS` | Vĩnh viễn | Không | — | payment-service |
| `REFUNDS` | Vĩnh viễn | Không | — | payment-service |
| `REFUND_ITEMS` | Vĩnh viễn | Không | — | payment-service |
| `OUTBOX_EVENTS` | 7 ngày (PROCESSED) / 3 ngày (FAILED) | Có | JOB-04/05 | payment-service |
| `FAILED_EVENTS` | 30 ngày (RESOLVED) / 90 ngày (DEAD) | Có | JOB-06 | payment-service |
| `SHEDLOCK` | 1 giờ qua NOW() | Có | JOB-12 | payment-service |
| `FS_SESSIONS` | 365 ngày khi ENDED | Có | JOB-08 | flashsale-service |
| `FS_ITEMS` | 30/180 ngày | Có | JOB-08 | flashsale-service |
| `FS_REMINDERS` | 0 ngày khi session ENDED | Có | JOB-08 | flashsale-service |
| `SELLER_STRIPE_ACCOUNTS` | Vĩnh viễn | Không | JOB-15 (onboarding_url) | payment-service |

### MongoDB

| Bảng | Retention | Hard Delete? | Job | Service |
|------|-----------|---------|-----|---------|
| `mg_carts` | 90 ngày inactive | Có | JOB-07 | product-service |
| `mg_cart_items` | Kèm theo cart | Có | JOB-07 | product-service |
| `mg_notifications` | 90 ngày | Có | JOB-09 (TTL Index) | notification-service |
| `mg_products` | Kèm theo PostgreSQL | Có | JOB-10/16 | product-service |

### Elasticsearch

| Index | Retention | Cleanup Job | Service |
|-------|-----------|---------|---------|
| `es_products_*` | Kèm theo MG_PRODUCTS | JOB-10 | product-service |
| `es_orders_*` | Vĩnh viễn | — | — |

---

## 5. EXTERNAL STORAGE & CACHE POLICY

### 5.1 Redis

#### Flash Sale Stock Keys (flashsale-service)

- **Key pattern:** `fs:stock:{sessionId}:{itemId}`
- **Value:** số lượng còn lại
- **TTL:** Redis auto-expire 24h fallback
- **Cleanup:** JOB-01 (side effect), JOB-21 (reconciliation)

#### Redis Token Blocklist (identity-service)

- **Key pattern:** `revoked_token:{jti}`
- **Value:** `1`
- **TTL:** Thời gian còn lại của token gốc (max 900 giây)
- **Use case:** JWT revocation khi account bị LOCKED (JOB-17)

**Logic khi lock:**
1. Identity Service lấy tất cả JTI đang active của user
2. Với mỗi JTI: `SET revoked_token:{jti} = 1 EX {ttl}`
3. Mỗi request check key này trước khi validate JWT signature

### 5.2 MinIO

| Bucket | Data | Retention | Cleanup | Service |
|--------|------|----------|---------|---------|
| `products-media` | MG_PRODUCTS.images | Kèm theo product | JOB-10 | product-service |
| `user-avatars` | USERS.avatar_url | Vĩnh viễn (hoặc user xóa) | — | — |
| `refund-evidence` | REFUNDS.evidence_images | **Vĩnh viễn** | **KHÔNG bao giờ xóa** | — |

---

## 6. BẢNG TÓM TẮT NHANH

| Job | Service | Cron | Lock Duration | Off-peak |
|-----|---------|------|-------------|----------|
| JOB-01 | flashsale-service | 1 phút | 55s | Không |
| JOB-02 | flashsale-service | 1 phút | 55s | Không |
| JOB-04 | payment-service | 10 giây | 9s | Không |
| JOB-05 | payment-service | 03:00 hàng ngày | 5m | Có |
| JOB-06 | payment-service | 03:00 ngày 30 | 10m | Có |
| JOB-07 | product-service | 04:00 hàng ngày | 10m | Có |
| JOB-08 | flashsale-service | 02:00 hàng ngày | 5m | Có |
| JOB-09 | notification-service | TTL Index | — | — |
| JOB-10 | product-service | 03:00 Chủ nhật | 30m | Có |
| JOB-12 | payment-service | 05:00 hàng ngày | — | Có |
| JOB-13 | order-service | 5 phút | 4m30s | Không |
| JOB-15 | payment-service | 02:00 hàng ngày | 2m | Có |
| JOB-16 | product-service | 03:00 Chủ nhật | 15m | Có |
| JOB-17 | identity-service | 15 phút | 14m | Không |
| JOB-21 | flashsale-service | 5 phút (ACTIVE) | 4m30s | Không |
| JOB-22 | order-service | 02:00 hàng ngày | 55m | Có |

---

## 7. CHECKLIST TRIỂN KHAI

### Mỗi Service cần có

- [ ] ShedLock table (`shedlock`) được tạo trên PostgreSQL của service đó
- [ ] Spring `@Scheduled` + ShedLock annotation configured trong service
- [ ] Kafka topics (`order.delivered`, `order.auto_cancelled`, `account.auto_locked`, `account.unlocked`, `trust_score.warning`, `flash_sale.stock_reconciled`, etc.) được tạo
- [ ] Redis keys configured (nếu service dùng Redis)
- [ ] MongoDB TTL index created (notification-service)
- [ ] MinIO buckets created (product-service, payment-service)

### Alerting & Monitoring

- [ ] Job failure alert khi retry_count >= 3
- [ ] ShedLock timeout alert (> 30 phút)
- [ ] Outbox event backlog alert (PENDING > 1000)
- [ ] Failed events backlog alert (DEAD > 100)
- [ ] Redis memory usage alert
- [ ] Kafka lag monitor

### Testing

- [ ] Unit test JOB-17 lock/unlock logic
- [ ] Unit test JOB-21 stock reconciliation
- [ ] Integration test JOB-22 auto-delivery + Kafka event
- [ ] Chaos test: Pod crash during JOB-21 DECR + recovery
- [ ] Load test: 1000 concurrent orders during Flash Sale + JOB-21 reconciliation

### Security & Compliance

- [ ] REFUNDS.evidence_images bucket đặt private
- [ ] Pre-signed URL TTL enforce 15 phút max
- [ ] Audit log cho mỗi Admin action
- [ ] Retention policy compliance check

---

**Tài liệu cập nhật: 2026-04-22**
**Phiên bản: 5.0 — Distributed Jobs per Service**
