# Chính Sách Hệ Thống — v3 RTS

**Phiên bản:** **v3 RTS** • **Áp dụng cho:** Marketplace Microservices (Java 25 / Spring Boot 4.0.4)

Tài liệu này định nghĩa các quy tắc nghiệp vụ và điều khiển hành vi tài khoản, điểm tin nhiệm, tham gia flash sale, seller, và data retention, đồng bộ với API Spec v5.3 RTS.

**Tài liệu liên quan:** [05_OPERATIONS.md](05_OPERATIONS.md) (v5.0 — Distributed per Service)

**Thay đổi so với v1.0:** Tích hợp toàn bộ ở xuất từ Mục 7 vào chính sách chính thức. Bổ sung schema `TRUST_SCORE_EVENTS_CONFIG`, `USER_BAN_HISTORY`, cột mới trong `USERS` và `POINT_TRANSACTIONS`. Chuyển các JOB-17/18/19/20 từ trạng thái "ở xuất" sang chính thức.

---

## Mục Lục

1. [Trust Score — Điểm Tin Nhiệm](#1-trust-score--điểm-tin-nhiệm)
2. [Vòng Đời Tài Khoản (Account Lifecycle)](#2-vòng-đời-tài-khoản-account-lifecycle)
3. [Chính Sách Tham Gia Flash Sale](#3-chính-sách-tham-gia-flash-sale)
4. [Chính Sách Seller](#4-chính-sách-seller)
5. [Chính Sách Hoàn Tiền (Refund)](#5-chính-sách-hoàn-tiền-refund)
6. [Chính Sách Điểm Tích Lũy (Loyalty Points)](#6-chính-sách-điểm-tích-lũy-loyalty-points)
7. [Schema Bổ Sung (v3 RTS)](#7-schema-bổ-sung-v3-rts)

---

## 1. Trust Score — Điểm Tin Nhiệm

### 1.1 Khái Niệm

`USERS.trust_score` là điểm số nguyên (0–100) phản ánh mức độ tin cậy của một tài khoản. Mỗi thay đổi đều được ghi log vào `TRUST_SCORE_LOGS` với trường `changed_by = 'ADMIN'` hoặc `'SYSTEM'` và `event_code` tham chiếu đến `TRUST_SCORE_EVENTS_CONFIG`.

**Delta của mỗi sự kiện được cấu hình động** trong bảng `TRUST_SCORE_EVENTS_CONFIG` — không hardcode trong logic SYSTEM. Xem Mục 7.1 để biết chi tiết.

### 1.2 Thang Điểm & Mục Ý Nghĩa

> **[UNIFIED v3 RTS]** Bảng tier dưới đây là nguồn chính thức duy nhất, đồng bộ với trường `trust_tier` trả về từ API `GET /users/me` và toàn bộ logic giới hạn tính năng trong hệ thống.

| Tier (API `trust_tier`) | Khoảng Điểm | Mô Tả | Quyền Hạn |
|---------|-----------|--------|---------|
| **ELITE** | 100 | Elite | Toàn quyền, mức ưu tiên cao nhất |
| **DIAMOND** | 90 — 99 | Diamond | Toàn quyền, mức ưu tiên cao |
| **PLATINUM** | 80 — 89 | Platinum | Toàn quyền bình thường |
| **GOLD** | 60 — 79 | Gold | Toàn quyền bình thường |
| **SILVER** | 40 — 59 | Silver | Flash Sale max 3 items, PENDING products ≤ 10 |
| **BRONZE** | 0 — 39 | Bronze | Flash Sale max 1 item (nếu score ≥ 30), PENDING products ≤ 3 |

> **Mức đó định khi tạo tài khoản:** `trust_score = 80` (PLATINUM — đúng theo API Spec).

**Ngưỡng hành động tự động** (dựa trên giá trị tuyệt đối, độc lập với tên tier):

- `score < 10` → JOB-17 tự động khóa tài khoản (`USERS.status = 'LOCKED'`)
- `score < 30` → Không được tham gia Flash Sale (chặn tại API)
- `score < 30` → Kafka event `trust_score.warning` khi giảm xuống dưới ngưỡng này
- Seller bị trừ điểm 3 lần / 30 ngày → `product_posting_suspended = TRUE`

### 1.3 Sự Kiện Trừ Điểm

| Sự Kiện | `event_code` | `changed_by` | Ghi Chú |
|--------|---------|---------|---------|
| Seller: sản phẩm bị Admin REJECT lần đầu | `PRODUCT_REJECTED_FIRST` | `SYSTEM` | Mỗi lần reject một sản phẩm |
| Seller: sản phẩm bị REJECT lần 2+ (cộng lỗi do) | `PRODUCT_REJECTED_REPEAT` | `SYSTEM` | Tái phạm cộng vi phạm |
| Buyer/Seller: Refund bị từ chối và bằng chứng giả | `REFUND_FRAUD_EVIDENCE` | `SYSTEM` | Khi Admin set `REFUNDS.status = 'REJECTED'` và ghi nhận gian lận |
| Buyer: hủy đơn quá nhiều (> 5 lần/30 ngày) | `EXCESSIVE_CANCELLATION` | `SYSTEM` | Tính trên rolling 30 ngày — xử lý bởi JOB-18 |
| Buyer: không nhận hàng không có cơ sở | `UNDELIVERED_CLAIM` | `SYSTEM` | Sau khi Admin xác minh |
| Seller: giao hàng chậm trễ quá 3 lần/tháng | `SELLER_LATE_DELIVERY` | `SYSTEM` | Tracking tự động qua `ORDERS.tracking_number` |
| Tài khoản bị report spam/gian lận nhiều lần | `SPAM_FRAUD_REPORT` | `ADMIN` | Sau điều tra — Admin nhập lý do vào `TRUST_SCORE_LOGS.reason` |
| Admin điều chỉnh thủ công (vi phạm chính sách) | *(Admin tự chọn)* | `ADMIN` | `TRUST_SCORE_LOGS.reason` bắt buộc |
| **[GAP-PATCH]** Seller tự hủy đơn hàng ở thanh toán | `SELLER_CANCELLATION` | `SYSTEM` | Kích hoạt ngay lập tức khi `POST /orders/{id}/cancel` được gửi bởi role `SELLER`. Order Service set `cancelled_by = 'SELLER'` và produce Kafka event tới Identity Service trừ điểm. Mức đó định **-5 điểm/lần** (cấu hình trong `TRUST_SCORE_EVENTS_CONFIG`). |

### 1.4 Sự Kiện Cộng Điểm

| Sự Kiện | `event_code` | `changed_by` | Ghi Chú |
|--------|---------|---------|---------|
| Hoàn thành đơn hàng đầu tiên (Buyer) | `FIRST_ORDER_COMPLETED` | `SYSTEM` | Một lần duy nhất |
| Mỗi 10 đơn hàng hoàn thành thành công | `EVERY_10_ORDERS` | `SYSTEM` | Tối đa +20 từng lần sự kiện này |
| Seller: sản phẩm được duyệt thành công | `PRODUCT_APPROVED` | `SYSTEM` | Mỗi sản phẩm APPROVED |
| Seller: không có refund trong 30 ngày liên tiếp | `SELLER_NO_REFUND_30D` | `SYSTEM` | JOB-19 chạy ngày 1 hàng tháng |
| Hoàn thành xác minh danh tính (phone + email) | `IDENTITY_VERIFIED` | `SYSTEM` | Một lần duy nhất |
| Admin phục hồi sau xử phạt sai | *(Admin tự chọn)* | `ADMIN` | Ghi lý do bắt buộc |

> **Giới hạn:** `trust_score` không vượt quá **100** và không thấp hơn **0**.

### 1.5 Ngưỡng Kích Hoạt Hành động Tự động

| Điều Kiện | Hành Động Tự Động | Cơ Chế |
|----------|----------|--------|
| `trust_score` giảm xuống < **30** | Gửi cảnh báo cho user qua Notification Service | `SYSTEM` phát Kafka event `trust_score.warning` |
| `trust_score` giảm xuống < **10** | Khóa tài khoản tự động: `USERS.status = 'LOCKED'` | JOB-17 (chạy mỗi 15 phút) |
| Seller bị trừ điểm **3 lần trong 30 ngày** | Tạm dừng quyền đăng sản phẩm mới | SYSTEM set `USERS.product_posting_suspended = TRUE` |
| Buyer hủy đơn **> 5 lần trong 30 ngày** | Trừ điểm (`EXCESSIVE_CANCELLATION`) và gửi cảnh báo | JOB-18 (chạy 03:00 hàng ngày) |

**Trình double-penalize:** Khi JOB-18 trừ điểm buyer, cập nhật `USERS.last_cancellation_penalty_at = NOW()`. Lần chạy tiếp theo chỉ xét các đơn hủy **sau** thời điểm trừ điểm đó.

### 1.6 Quy Trình Appeal (Khiếu Nại)

1. User gửi khiếu nại qua API `POST /support/trust-score-appeal`.
2. Admin xem xét `TRUST_SCORE_LOGS` trong 7 ngày làm việc.
3. Nếu hệ thống xử phạt sai → Admin cộng điểm bổ qua `POST /admin/users/{userId}/trust-score`.
4. Mỗi tài khoản chỉ được appeal **tối đa 3 lần/năm**, theo dõi qua `USERS.appeal_count`.
5. JOB-20 reset `USERS.appeal_count = 0` vào lúc 00:00 ngày 1 tháng 1 hàng năm.

---

## 2. Vòng Đời Tài Khoản (Account Lifecycle)

### 2.1 Trạng Thái Tài Khoản

```
[ACTIVE] ←→ ←→ ←→ ←→ ←→ ←→ ←→ ←→ ←→ ←→ ←→ ←→ ←→ ←→ ←→ ←→ ←→ [LOCKED]
           Admin lock / JOB-17 (trust_score < 10)   ↓
                                                     ↓ Admin unlock /
                                                     ↓ JOB-17 (locked_until <= NOW())
                                                     ↓
                                                 [ACTIVE]
```

`USERS.status` chỉ có hai giá trị: `ACTIVE` và `LOCKED`. Mỗi thao tác khóa/mở đều được ghi vào `USER_BAN_HISTORY`.

### 2.2 Khóa Tạm Thời

`USERS.locked_until` hỗ trợ cơ chế khóa có thời hạn:

- `locked_until IS NULL` → khóa vĩnh viễn — đến khi Admin mở.
- `locked_until IS NOT NULL` → JOB-17 tự động mở khóa khi `locked_until <= NOW()`.

Khi Admin gọi `POST /admin/users/{userId}/lock`, request body có thể kèm `locked_until` (timestamp tùy chọn).

### 2.3 Hành Vi Khi Tài Khoản Bị LOCKED

| Hành Động | Bị Chặn? | Ghi Chú |
|----------|--------|--------|
| Đăng nhập | ✓ Bị Chặn | Identity Service trả về `403 ACCOUNT_LOCKED` kèm `lock_reason` |
| Đặt hàng mới | ✓ Bị Chặn | |
| Nhận đơn hàng đang xử lý | ✗ Không Chặn | Đơn PAID/SHIPPING vẫn tiếp tục |
| Nhận thanh toán Stripe (Seller) | ✗ Không Chặn | Stripe Transfer ở queue trước — vẫn xử lý |
| Rút điểm loyalty | ✓ Bị Chặn | `available_points` vẫn giữ nguyên, không mất |
| Truy cập refund đang mở | ✗ Không Chặn | Admin xử lý refund phía backend |
| Tham gia Flash Sale | ✓ Bị Chặn | |

> **JWT Revocation:** Khi Admin lock tài khoản qua `POST /admin/users/{userId}/lock`, Identity Service thêm tất cả JTI đang hoạt động của user vào Redis blocklist với TTL = thời gian còn lại của token (max 900 giây). Mọi request tiếp theo sẽ bị từ chối **ngay lập tức** mà không cần chờ token hết hạn.
>
> Key pattern: `revoked_token:{jti}` = `1`, TTL = thời gian còn lại của token.

### 2.4 Điều Kiện Mở Khóa (LOCKED → ACTIVE)

| Loại Khóa | Điều Kiện Mở Khóa |
|----------|---------|
| Khóa tự động (trust_score < 10) | Admin review → tăng trust_score ≥ 30, sau đó Admin gọi `POST /admin/users/{userId}/unlock` |
| Khóa thủ công (Admin) | Admin gọi `POST /admin/users/{userId}/unlock` sau khi xử lý vi phạm |
| Khóa tạm thời (`locked_until` có giá trị) | JOB-17 tự động set `status = 'ACTIVE'`, `locked_until = NULL` khi đến hạn |

Mỗi thao tác unlock đều ghi vào `USER_BAN_HISTORY` với `action = 'UNLOCKED'`.

### 2.5 Tài Khoản Không Bao Giờ Bị Xóa (Hard Delete)

Bảng `USERS` giữ vĩnh viễn. Dữ liệu tài chính liên quan (`ORDERS`, `TRANSACTIONS`, `REFUNDS`, `POINT_TRANSACTIONS`) đều phụ thuộc vào `user_id` và không bao giờ bị hard delete.

---

## 3. Chính Sách Tham Gia Flash Sale

### 3.1 Điều Kiện Tham Gia Của Buyer

| Tiêu Chí | Yêu Cầu Tối Thiểu | Ghi Chú |
|---------|---------|--------|
| `USERS.status` | `ACTIVE` | Tài khoản không bị khóa |
| `USERS.trust_score` | ≥ **30** | Dưới 30 → không được tham gia |
| Xác minh số điện thoại (`USERS.phone`) | Bắt buộc | `phone IS NOT NULL` |
| Xác minh email (`USERS.email`) | Bắt buộc | |
| Có role `BUYER` trong `ROLES` | Bắt buộc | |

### 3.2 Giới Hạn Mua Theo Trust Score (Buyer)

| Mức Trust Score | Giới Hạn Đơn Flash Sale / Session | Ghi Chú |
|---------|---------|--------|
| ≥ 60 (GOLD / PLATINUM / DIAMOND / ELITE) | Theo `FS_ITEMS.limit_per_user` | Không bị hạn chế thêm |
| 40 — 59 (SILVER) | min(`FS_ITEMS.limit_per_user`, 3) | Tối đa 3 item/loại/session |
| 30 — 39 (BRONZE, ở ngưỡng FS) | min(`FS_ITEMS.limit_per_user`, 1) | Tối đa 1 item/loại/session |
| < 30 (BRONZE, dưới ngưỡng) | **Không được mua** | Chặn tại API `/flash-sale/sessions/{id}/buy` |

> Giới hạn per-user được lưu trong Redis key `fs:user_limit:{sessionId}:{itemId}:{userId}` và kiểm tra tại Flash Sale Service (atomic counter — trình race condition).

### 3.3 Điều Kiện Tham Gia Của Seller (Đăng Ký FS_ITEMS)

| Tiêu Chí | Yêu Cầu | Ghi Chú |
|---------|---------|--------|
| `USERS.status` | `ACTIVE` | |
| `USERS.trust_score` | ≥ **40** (SILVER+) | Seller điểm thấp (BRONZE) không được đăng ký flash sale |
| `USERS.product_posting_suspended` | `FALSE` | Seller đang bị tạm dừng không được đăng mới |
| `SELLER_STRIPE_ACCOUNTS.account_status` | `ACTIVE` | KYC hoàn tất, Stripe cho phép nhận tiền |
| `SELLER_STRIPE_ACCOUNTS.charges_enabled` | `true` | |
| `SELLER_STRIPE_ACCOUNTS.payouts_enabled` | `true` | |
| Sản phẩm đăng ký | `MG_PRODUCTS.status = 'APPROVED'` | Không duyệt sản phẩm REJECTED/PENDING |
| Tồn kho (`MG_INVENTORIES.stock_available`) | > 0 tại thời điểm đăng ký | |

### 3.4 Quy Trình Duyệt FS_ITEMS

```
Seller submit FS_ITEM
        ↓
        ↓
[FS_ITEMS.status = PENDING]
        ↓
        ├─→ Admin duyệt → [APPROVED] → Vào session, seed Redis khi session ACTIVE
        ↓
        ├─→ Admin từ chối → [REJECTED] → Xóa sau 30 ngày (JOB-08)
        ↓
        └─→ Session kết thúc trước khi duyệt → [CANCELLED] → Xóa sau 30 ngày (JOB-08)
```

### 3.5 Hành Vi Khi Flash Sale Session ENDED

| Đối Tượng | Hành Động |
|---------|---------|
| Redis `fs:stock:*` keys | Xóa ngay (JOB-01 side effect) |
| Redis `fs:user_limit:*` keys | Xóa ngay (JOB-01 side effect) |
| `MG_CART_ITEMS` có `fs_item_id` | Xóa ngay (JOB-07) |
| `FS_REMINDERS` của session | Xóa ngay (JOB-08) |
| Đơn Flash Sale PENDING > 10 phút | Auto-cancel (JOB-13) |
| `FS_ITEMS` status CANCELLED/REJECTED | Xóa sau 30 ngày (JOB-08) |
| `FS_ITEMS` status APPROVED | Xóa sau 180 ngày (JOB-08, giữ cho báo cáo doanh thu) |
| `FS_SESSIONS` | Xóa sau 365 ngày (JOB-08) |

### 3.6 Timeout Đơn Hàng Flash Sale

| Loại Đơn | Timeout PENDING | Hành Động |
|---------|---------|--------|
| Đơn thường | 30 phút | Auto-cancel (JOB-13) |
| Đơn Flash Sale (`is_flash_sale = true`) | **10 phút** | Auto-cancel (JOB-13) — giải phóng flash stock khó kiếm |

---

## 4. Chính Sách Seller

### 4.1 Onboarding Stripe (KYC)

| Bước | Trạng Thái `SELLER_STRIPE_ACCOUNTS.account_status` | Mô Tả |
|---------|---------|--------|
| Bắt đầu KYC | `PENDING` | Seller gọi `POST /stripe/onboarding/start` |
| Đang hoàn thiện | `RESTRICTED` | Thiếu một số thông tin Stripe yêu cầu |
| Hoàn tất | `ACTIVE` | `details_submitted = true`, `charges_enabled = true`, `payouts_enabled = true` |
| Bị Stripe đình chỉ | `SUSPENDED` | Stripe webhook `account.updated` cập nhật tự động |

> `SELLER_STRIPE_ACCOUNTS.onboarding_url` bị nullify sau 24 giờ (JOB-15) do Stripe Account Links chỉ hợp lệ ~5 phút. Seller cần gọi `POST /stripe/onboarding/refresh-link` để lấy link mới.

### 4.2 Quyền Seller Theo Trạng Thái Stripe

| Stripe Status | Đăng Sản Phẩm | Đăng Ký Flash Sale | Nhận Thanh Toán | Rút Tiền |
|---------|---------|---------|--------|--------|
| `PENDING` | ✗ | ✗ | ✗ | ✗ |
| `RESTRICTED` | ✗ | ✗ | ✗ | ✗ |
| `ACTIVE` | ✓ | ✓ (nếu trust_score ≥ 50 và không bị suspend đăng sản phẩm) | ✓ | ✓ |
| `SUSPENDED` | ✗ | ✗ | ✗ | ✗ |

### 4.3 Quy Trình Duyệt Sản Phẩm

```
Seller submit sản phẩm
        ↓
        ↓
[MG_PRODUCTS.status = PENDING]
        ↓
        ├─→ Admin duyệt → [APPROVED] → Index vào Elasticsearch
        ↓   ↓
        ↓   └─→ trust_score += delta('PRODUCT_APPROVED') (SYSTEM)
        ↓
        └─→ Admin từ chối → [REJECTED]
                ↓
                ├─→ trust_score -= delta('PRODUCT_REJECTED_FIRST') (lần đầu) (SYSTEM)
                ↓   trust_score -= delta('PRODUCT_REJECTED_REPEAT') (tái phạm) (SYSTEM)
                ↓
                ├─→ Seller re-submit trong 90 ngày → [PENDING] lại
                ↓
                └─→ Không re-submit trong 90 ngày → JOB-16 soft-delete
                        ↓
                        └─→ JOB-10 hard-delete sau 30 ngày (+ xóa MinIO + ES)
```

### 4.4 Giới Hạn Sản Phẩm Seller Theo Trust Score

Giới hạn áp dụng cho số sản phẩm đang ở trạng thái **PENDING/DRAFT** tại cùng một thời điểm (kiểm tra tại `POST /products` và `POST /seller/products/{id}/submit`).

| Tier (Trust Score) | Giới Hạn Sản Phẩm PENDING Cùng Lúc | Ghi Chú |
|---------|---------|--------|
| PLATINUM / DIAMOND / ELITE (≥ 80) | Không giới hạn | |
| GOLD (60 — 79) | Tối đa 30 sản phẩm | |
| SILVER (40 — 59) | Tối đa 10 sản phẩm | |
| BRONZE (0 — 39) | Tối đa 3 sản phẩm | Áp dụng khi score ≥ 30; nếu < 30 bị chặn Flash Sale nhưng vẫn được đăng |

### 4.5 Tạm Dừng Seller

| Điều Kiện | Hành Động | Tự Động? |
|---------|---------|--------|
| Trust Score < 30 | Không được đăng sản phẩm mới, không tham gia Flash Sale | ✓ SYSTEM |
| Trust Score < 10 | Tài khoản LOCKED, mọi sản phẩm ẩn khỏi search | ✓ JOB-17 |
| Stripe `SUSPENDED` | Không nhận thanh toán mới, thông báo Seller | ✓ Webhook |
| 3 lần trừ điểm trong 30 ngày | `product_posting_suspended = TRUE` — Admin review trước khi reset | ✓ SYSTEM flag |

**Reset `product_posting_suspended`:** Admin gọi `POST /admin/users/{userId}/unlock-product-posting` sau khi review.

---

## 5. Chính Sách Hoàn Tiền (Refund)

### 5.1 Điều Kiện Mở Refund

| Điều Kiện | Yêu Cầu |
|---------|--------|
| `ORDERS.status` | `DELIVERED` (Buyer request) hoặc `SHIPPING` (chỉ dành cho luồng Return To Sender do Seller khởi tạo — xem 5.3b) |
| Thời gian từ khi giao hàng | ≥ **7 ngày** sau khi `ORDERS.status` chuyển sang `DELIVERED` |
| Buyer đã xác minh tài khoản | `phone IS NOT NULL` và `email IS NOT NULL` |
| Chưa có refund đang mở cho đơn hàng này | `REFUNDS` không có record `PENDING` hoặc `SUCCESS` cho `order_id` này |

### 5.2 Loại Refund

| Loại | `REFUNDS.type` | Mô Tả |
|---------|---------|--------|
| Hoàn toàn bộ | `FULL` | Hoàn tất cả `ORDER_ITEMS` |
| Hoàn một phần | `PARTIAL` | Hoàn một hoặc nhiều item có thể qua `REFUND_ITEMS` |

### 5.3 Quy Trình Xét Duyệt

```
Buyer gửi refund request
        ↓
        ↓
[REFUNDS.status = PENDING, initiated_by=BUYER]
        ↓  (Admin review REFUNDS.evidence_images từ MinIO — pre-signed URL TTL 15 phút)
        ↓
        ├─→ Admin duyệt → [SUCCESS]
        ↓   ↓
        ↓   ├─→ Stripe refund (re_xxx) → TRANSACTIONS cập nhật status
        ↓   ├─→ ORDERS.status = REFUNDED  PARTIALLY_REFUNDED
        ↓   └─→ trust_score Seller -= delta('SELLER_CAUSED_REFUND') nếu lỗi Seller [SYSTEM]
        ↓
        └─→ Admin từ chối → [REJECTED]
                ↓
                └─→ trust_score Buyer -= delta('REFUND_FRAUD_EVIDENCE') nếu bằng chứng giả [SYSTEM]
```

### 5.3b Quy Trình Return To Sender (RTS) — Hoàn Hàng Do Đơn Vị Vận Chuyển

```
Đơn vận chuyển bị hoàn hàng về Seller (gọi 3 lần Buyer không nghe / sai địa chỉ)
        ↓
        ↓
Seller gọi POST /orders/{id}/return-to-sender (kèm nh giao hàng, tracking hoàn)
        ↓
        ↓
[ORDERS.status = RETURNED]
[REFUNDS tạo tự động: type=FULL, initiated_by=SELLER, refund_reason_type=RETURN_TO_SENDER, status=PENDING]
[REFUND_ITEMS: return_evidence_images, returned_at ghi nhận]
[MG_INVENTORIES.$inc stock_available cho từng SKU — atomic]
        ↓
        → Kafka: order.returned → Refund Module
        ↓
[Refund Module tự động gọi Stripe Refund API — KHÔNG cần Admin duyệt]
        ↓
        ├─→ Stripe OK → REFUNDS.status = SUCCESS, refund_ref ghi nhận
        ↓                  → Kafka: refund.rts_completed → Notification Buyer + Seller
        ↓
        └─→ Stripe FAIL → REFUNDS.status = FAILED → vào FAILED_EVENTS DLQ → retry
```

**Nguyên Tắc RTS:**

- Chỉ Seller mới được gọi API RTS — không phải Buyer, không phải Admin
- Điều kiện: `ORDERS.status = SHIPPING` bắt buộc
- RTS Full Refund **không cần Admin duyệt** — Seller đã xác nhận hàng và lỗi căn cơ
- JOB-22 **phải loại trừ** đơn có `REFUNDS.refund_reason_type = 'RETURN_TO_SENDER'` khi quét SHIPPING > 7 ngày
- Nh bằng chứng RTS lưu vào `REFUND_ITEMS.return_evidence_images` (bucket: `refund-evidence`)

### 5.4 Nh Bằng Chứng (MinIO)

- Bucket: `refund-evidence`
- Key pattern: `refunds/{refund_id}/{filename}`
- **Không bao giờ xóa** — giữ vĩnh viễn (audit trail pháp lý)
- Admin truy cập qua pre-signed URL có TTL **15 phút**

### 5.5 Admin Điều Chỉnh Số Tiền

Admin có thể điều chỉnh `REFUNDS.adjust_amount` trước khi duyệt. Lý do ghi vào `REFUNDS.admin_note`. Mỗi điều chỉnh đều được audit qua `REFUNDS.reviewed_by` (FK → `USERS.id`) và `REFUNDS.reviewed_at`.

---

## 6. Chính Sách Điểm Tích Lũy (Loyalty Points)

### 6.1 Tích Điểm

| Sự Kiện | Điểm Tích Lũy | Thời Điểm Ghi Nhận | `POINT_TRANSACTIONS.type` |
|---------|---------|--------|--------|
| Đặt hàng thành công | Theo tỷ lệ quy đổi (VD: 1.000đ = 1 điểm) | Khi `ORDERS.status = PAID` | `EARNED` với `status = PENDING` |
| Xác nhận hoàn thành đơn | Điểm chuyển từ PENDING → CONFIRMED | Khi `ORDERS.status = DELIVERED` | Cập nhật `status = CONFIRMED` |

### 6.2 Sử Dụng Điểm

- Điểm trừ vào `PARENT_ORDERS.loyalty_discount` khi checkout.
- Ghi `POINT_TRANSACTIONS` với `type = 'USED'`, `delta` mới.
- Chỉ trừ từ `LOYALTY_ACCOUNTS.available_points` (không trừ điểm `PENDING`).
- Tỷ lệ quy đổi: 1 điểm = 1.000đ (cấu hình — không hardcode vào DB).
- Khi USED xảy ra, cập nhật `remaining_delta` của transaction EARNED tương ứng (theo FIFO).

### 6.3 Hủy Điểm Khi Đơn Bị Cancel/Refund

| Trường Hợp | Hành Động |
|---------|--------|
| Đơn bị cancel (JOB-13) | `POINT_TRANSACTIONS` PENDING bị void (status = CONFIRMED, delta giữ nguyên để audit, nhưng không cộng vào `available_points`) |
| Đơn bị REFUNDED (hoàn toàn) | Tạo `POINT_TRANSACTIONS` mới `type = REFUNDED`, delta bằng điểm được cộng |
| Điểm PENDING mở cũ | Void bởi JOB-14 (hàng ngày) |

### 6.4 Hết Hạn Điểm

- Điểm `EARNED` hết hạn sau **365 ngày** kể từ `POINT_TRANSACTIONS.expires_at`.
- JOB-03 chạy lúc 02:00 hàng ngày để expire và tạo `POINT_TRANSACTIONS` type `EXPIRED`.
- **JOB-03 chỉ expire `remaining_delta`** (không phải `delta` gốc) để trình expire-over khi user đã dùng một phần điểm.
- Optimistic Locking dùng `LOYALTY_ACCOUNTS.version` để trình race condition.

### 6.5 Bảo Vệ Điểm Khi Tài Khoản Bị LOCKED

- `LOYALTY_ACCOUNTS.available_points` **không bị xóa** khi tài khoản bị khóa.
- Khi tài khoản được mở khóa, điểm chưa hết hạn vẫn còn và có thể dùng được.
- Điểm ở hết hạn trong thời gian bị khóa vẫn bị expire bình thường bởi JOB-03.

---

## 7. Schema Bổ Sung (v3 RTS)

### 7.1 Bảng `TRUST_SCORE_EVENTS_CONFIG` — Cấu Hình Động Delta

Thay thế việc hardcode delta trong logic SYSTEM. Admin có thể điều chỉnh delta của từng sự kiện qua API `PUT /admin/trust-score-events-config/{eventCode}` mà không cần deploy lại.

```sql
CREATE TABLE TRUST_SCORE_EVENTS_CONFIG (
    id          BIGSERIAL PRIMARY KEY,
    event_code  VARCHAR UNIQUE NOT NULL,
    delta       INT NOT NULL,
    description TEXT,
    is_active   BOOLEAN DEFAULT TRUE,
    updated_at  TIMESTAMP DEFAULT NOW()
);

-- Seed data:
INSERT INTO TRUST_SCORE_EVENTS_CONFIG (event_code, delta, description) VALUES
  ('PRODUCT_REJECTED_FIRST',     -5,  'Sản phẩm bị từ chối lần đầu'),
  ('PRODUCT_REJECTED_REPEAT',    -10, 'Sản phẩm bị từ chối tái phạm'),
  ('PRODUCT_APPROVED',           +2,  'Sản phẩm được duyệt'),
  ('REFUND_FRAUD_EVIDENCE',      -15, 'Bằng chứng refund giả mạo'),
  ('EXCESSIVE_CANCELLATION',     -10, 'Hủy đơn quá nhiều'),
  ('SELLER_CAUSED_REFUND',       -5,  'Refund do lỗi của seller'),
  ('UNDELIVERED_CLAIM',          -10, 'Báo không nhận hàng không có cơ sở'),
  ('SELLER_LATE_DELIVERY',       -5,  'Giao hàng chậm trễ'),
  ('SPAM_FRAUD_REPORT',          -20, 'Report spam/gian lận được xác minh'),
  ('SELLER_NO_REFUND_30D',       +3,  'Seller không có refund trong 30 ngày'),
  ('FIRST_ORDER_COMPLETED',      +5,  'Hoàn thành đơn đầu tiên'),
  ('EVERY_10_ORDERS',            +2,  'Mỗi 10 đơn hoàn thành'),
  ('IDENTITY_VERIFIED',          +5,  'Xác minh phone + email'),
  ('SELLER_CANCELLATION',        -5,  'Seller hủy đơn ở thanh toán');
```

### 7.2 Bảng `USER_BAN_HISTORY` — Lịch Sử Khóa Tài Khoản

Mỗi thao tác khóa/mở tài khoản (Admin hoặc SYSTEM) đều được ghi vào bảng này.

```sql
CREATE TABLE USER_BAN_HISTORY (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES USERS(id),
    action       VARCHAR NOT NULL,     -- 'LOCKED' hoặc 'UNLOCKED'
    reason       TEXT,
    performed_by VARCHAR NOT NULL,     -- 'ADMIN' hoặc 'SYSTEM'
    admin_id     BIGINT REFERENCES USERS(id),
    locked_until TIMESTAMP,           -- NULL = vĩnh viễn
    created_at   TIMESTAMP DEFAULT NOW()
);
```

**Retention:** 5 năm. Không hard delete (tài liệu pháp lý).

### 7.3 Cột Bổ Sung `USERS`

```sql
ALTER TABLE USERS ADD COLUMN locked_until                 TIMESTAMP DEFAULT NULL;
ALTER TABLE USERS ADD COLUMN lock_reason                  VARCHAR   DEFAULT NULL;
ALTER TABLE USERS ADD COLUMN appeal_count                 INT       DEFAULT 0;
ALTER TABLE USERS ADD COLUMN product_posting_suspended    BOOLEAN   DEFAULT FALSE;
ALTER TABLE USERS ADD COLUMN last_cancellation_penalty_at TIMESTAMP DEFAULT NULL;
```

### 7.4 Cột Bổ Sung `POINT_TRANSACTIONS`

```sql
ALTER TABLE POINT_TRANSACTIONS ADD COLUMN remaining_delta INT;
```

**Mô Tả:**
- `delta`: giá trị gốc của giao dịch (không thay đổi).
- `remaining_delta`: phần còn lại chưa expire hoặc đã dùng. Ban đầu = `delta`, sau đó giảm dần khi user dùng điểm hoặc JOB-03 expire.

### 7.5 Cột Bổ Sung `TRUST_SCORE_LOGS`

```sql
ALTER TABLE TRUST_SCORE_LOGS ADD COLUMN changed_by   VARCHAR NOT NULL DEFAULT 'SYSTEM'; -- 'ADMIN' hoặc 'SYSTEM'
ALTER TABLE TRUST_SCORE_LOGS ADD COLUMN event_code   VARCHAR REFERENCES TRUST_SCORE_EVENTS_CONFIG(event_code);
ALTER TABLE TRUST_SCORE_LOGS ADD COLUMN reason       TEXT;
ALTER TABLE TRUST_SCORE_LOGS ADD COLUMN old_score    INT;
ALTER TABLE TRUST_SCORE_LOGS ADD COLUMN new_score    INT;
ALTER TABLE TRUST_SCORE_LOGS ADD COLUMN created_at   TIMESTAMP DEFAULT NOW();
```

---

**Tài liệu cập nhật: 2026-04-14**
**Phiên bản: 3.0 RTS Unified**

