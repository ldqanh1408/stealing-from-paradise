# Payment Service — Database Tables

> Stack: PostgreSQL · Axon
> Cập nhật: 2026-05-05

---

## SELLER_STRIPE_ACCOUNTS
KYC Stripe cho Seller

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | BIGSERIAL | Primary Key |
| `seller_id` | BIGINT | FK → SELLERS.id, UNIQUE |
| `stripe_account_id` | VARCHAR | acct_xxx — Stripe Express Account ID |
| `account_status` | VARCHAR | PENDING \| ACTIVE \| RESTRICTED \| SUSPENDED |
| `charges_enabled` | BOOLEAN | Stripe cho phép nhận thanh toán |
| `payouts_enabled` | BOOLEAN | Stripe cho phép rút tiền |
| `details_submitted` | BOOLEAN | KYC đã hoàn tất |
| `onboarding_url` | TEXT | Account Link URL (nullify sau 24h) |
| `express_dashboard_url` | TEXT | Dashboard URL |
| `onboarding_url_expires_at` | TIMESTAMP | Thời điểm URL hết hạn |
| `created_at` | TIMESTAMP | Thời điểm tạo |
| `updated_at` | TIMESTAMP | Cập nhật cuối |

---

## TRANSACTIONS
Giao dịch thanh toán (dùng Stripe)

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | BIGSERIAL | Primary Key |
| `parent_order_id` | BIGINT | FK → PARENT_ORDERS.id |
| `amount` | DECIMAL | Số tiền giao dịch |
| `trans_ref` | VARCHAR | PaymentIntent ID (pi_xxx) |
| `stripe_transfer_id` | VARCHAR | Transfer ID tr_xxx (chỉ lưu transfer đầu tiên) |
| `application_fee_amount` | DECIMAL | Phí sàn |
| `stripe_connect_mode` | VARCHAR | DESTINATION \| TRANSFER \| NONE |
| `status` | VARCHAR | SUCCESS \| FAILED \| REFUNDED \| PARTIALLY_REFUNDED |
| `raw_response` | JSONB | Nguyên bản payload từ cổng thanh toán |
| `pay_at` | TIMESTAMP | Thời gian tiền về |
| `created_at` | TIMESTAMP | Thời điểm tạo |
| `updated_at` | TIMESTAMP | Cập nhật cuối |

---

## SELLER_TRANSFERS
Transfer tiền cho Seller sau khi DELIVERED

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | BIGSERIAL | Primary Key |
| `order_id` | BIGINT | FK → ORDERS.id |
| `seller_id` | BIGINT | FK → SELLERS.id |
| `transfer_amount` | DECIMAL | Số tiền transfer (sau trừ phí) |
| `stripe_transfer_id` | VARCHAR | Stripe Transfer ID (dùng cho Reversal) |
| `status` | VARCHAR | PENDING \| SUCCESS \| FAILED \| REVERSED |
| `created_at` | TIMESTAMP | Thời điểm tạo |
| `updated_at` | TIMESTAMP | Cập nhật cuối |

---

## REFUNDS
Phiếu hoàn tiền

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | BIGSERIAL | Primary Key |
| `transaction_id` | BIGINT | FK → TRANSACTIONS.id |
| `order_id` | BIGINT | FK → ORDERS.id |
| `group_ref` | UUID | Nhóm nhiều refund trong một request |
| `type` | VARCHAR | FULL \| PARTIAL |
| `initiated_by` | VARCHAR | BUYER \| SELLER \| SYSTEM |
| `refund_reason_type` | VARCHAR | BUYER_REQUEST \| RETURN_TO_SENDER \| ADMIN_OVERRIDE |
| `amount` | DECIMAL | Số tiền hoàn lại |
| `reason` | VARCHAR | Lý do hoàn |
| `status` | VARCHAR | PENDING \| SUCCESS \| FAILED \| REJECTED |
| `evidence_images` | JSONB | Mảng ảnh bằng chứng (MinIO) |
| `reject_reason` | VARCHAR | Lý do từ chối |
| `admin_note` | TEXT | Ghi chú admin |
| `reviewed_by` | BIGINT | FK → ADMINS.id |
| `reviewed_at` | TIMESTAMP | Thời điểm duyệt/từ chối |
| `refund_ref` | VARCHAR | Stripe refund ID (re_xxx) |
| `raw_response` | JSONB | Payload từ Stripe |
| `created_at` | TIMESTAMP | Thời điểm tạo |
| `updated_at` | TIMESTAMP | Cập nhật cuối |

---

## REFUND_ITEMS
Chi tiết hoàn tiền (từng sản phẩm)

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | BIGSERIAL | Primary Key |
| `refund_id` | BIGINT | FK → REFUNDS.id |
| `item_id` | BIGINT | FK → ORDER_ITEMS.id |
| `quantity` | INT | Số lượng hoàn |
| `refund_amount` | DECIMAL | Số tiền hoàn cho item |
| `item_reason` | VARCHAR | Lý do hoàn riêng |
| `status` | VARCHAR | PENDING \| SUCCESS \| FAILED |
| `return_tracking_number` | VARCHAR | Mã vận đơn hoàn hàng |
| `returned_at` | TIMESTAMP | Thời điểm Seller xác nhận nhận lại |

---

## Infrastructure Tables

> Các bảng dưới đây thuộc Infrastructure & Messaging domain, được quản lý chính bởi **worker-service**.
> Payment service maintain local copy phục vụ outbox pattern riêng.

### OUTBOX_EVENTS
Event Outbox Pattern (cho eventual consistency)

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | BIGSERIAL | Primary Key |
| `topic` | VARCHAR | Tên topic/event |
| `payload` | JSONB | Nội dung event |
| `status` | VARCHAR | PENDING \| PROCESSED \| FAILED |
| `retry_count` | INT | Số lần retry |
| `processed_at` | TIMESTAMP | Thời điểm xử lý |
| `created_at` | TIMESTAMP | Thời điểm tạo |
| `updated_at` | TIMESTAMP | Cập nhật cuối |

---

### FAILED_EVENTS
Lưu trữ event/task lỗi để xử lý thủ công

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | BIGSERIAL | Primary Key |
| `topic_or_task` | VARCHAR | Tên topic hoặc task |
| `payload` | JSONB | Payload bị lỗi |
| `error_reason` | TEXT | Lý do lỗi |
| `retry_count` | INT | Số lần retry |
| `status` | VARCHAR | PENDING \| DEAD \| RESOLVED \| MANUAL_INTERVENTION |
| `created_at` | TIMESTAMP | Thời điểm tạo |
| `updated_at` | TIMESTAMP | Cập nhật cuối |

---

### SHEDLOCK
Distributed Lock cho scheduled jobs (ShedLock)

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `name` | VARCHAR | Primary Key, tên lock |
| `lock_until` | TIMESTAMP | Thời điểm hết lock |
| `locked_at` | TIMESTAMP | Thời điểm bắt đầu lock |
| `locked_by` | VARCHAR | Node/thread đang giữ lock |
