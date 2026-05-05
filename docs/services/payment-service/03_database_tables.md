# Payment Service — Database Tables

> Stack: PostgreSQL · Axon  
> Quản lý bởi: Flyway migrations (`V1__init_transactions_refunds.sql` → `V8__add_payout_fields_to_seller_transfers.sql`)  
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
Transfer tiền cho Seller — delayed payout flow (platform giữ tiền → chờ hết hạn hoàn hàng → trừ phí sàn → chuyển khoản seller)

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | BIGSERIAL | Primary Key |
| `order_id` | BIGINT | FK → ORDERS.id |
| `seller_id` | BIGINT | FK → SELLERS.id |
| `transfer_amount` | DECIMAL | Số tiền transfer (gross, chưa trừ phí sàn) |
| `stripe_transfer_id` | VARCHAR | Stripe Transfer ID (dùng cho Reversal) |
| `delivered_at` | TIMESTAMP | Thời điểm xác nhận giao hàng (V8) |
| `payout_eligible_at` | TIMESTAMP | Thời điểm có thể chuyển tiền = delivered + 7 ngày (V8) |
| `platform_commission_amt` | DECIMAL | Phí sàn khấu trừ, 5% transfer_amount (V8) |
| `payout_at` | TIMESTAMP | Thời gian thực hiện payout (V8) |
| `payout_retry_count` | INTEGER | Số lần thử lại payout, mặc định 0 (V8) |
| `status` | VARCHAR | PENDING \| AWAITING_DELIVERY \| RETURN_WINDOW \| READY_FOR_PAYOUT \| PAID_OUT \| FAILED \| SKIPPED \| REFUNDED \| REVERSED \| PARTIALLY_REVERSED |
| `created_at` | TIMESTAMP | Thời điểm tạo |
| `updated_at` | TIMESTAMP | Cập nhật cuối |

**Status flow:** PENDING → AWAITING_DELIVERY (payment success) → RETURN_WINDOW (order delivered) → READY_FOR_PAYOUT (cron claim) → PAID_OUT (Stripe Transfer). Refund trong return window → REFUNDED (không cần reversal). Refund sau payout → REVERSED (có Stripe Transfer reversal).

**Index:** `idx_seller_transfers_order_id` ON seller_transfers(order_id); `idx_st_payout_eligible` ON seller_transfers(status, payout_eligible_at) (V8)

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
| `return_evidence_images` | JSONB | Mảng ảnh bằng chứng hoàn hàng (MinIO) |
| `returned_at` | TIMESTAMP | Thời điểm Seller xác nhận nhận lại |

---

## Axon Saga Tables

> Các bảng infrastructure do Axon Framework quản lý, được tạo bởi `V2__add_axon_saga_tables.sql`.

### TOKEN_ENTRY
Lưu vết tiến độ của Axon TrackingEventProcessor

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `processor_name` | VARCHAR(255) | NOT NULL |
| `segment` | INTEGER | NOT NULL |
| `owner` | VARCHAR(255) | Node đang giữ segment |
| `timestamp` | VARCHAR(255) | NOT NULL |
| `token` | BYTEA | Serialized token |
| `token_type` | VARCHAR(255) | |

**PK:** (processor_name, segment)

### SAGA_ENTRY
Lưu trạng thái Saga

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `saga_id` | VARCHAR(255) | Primary Key |
| `revision` | VARCHAR(255) | |
| `saga_type` | VARCHAR(255) | |
| `serialized_saga` | BYTEA | Serialized Saga state |

### ASSOCIATION_VALUE_ENTRY
Ánh xạ giữa Saga và các định danh (orderId, paymentId, …)

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | BIGSERIAL | Primary Key |
| `association_key` | VARCHAR(255) | NOT NULL |
| `association_value` | VARCHAR(255) | |
| `saga_id` | VARCHAR(255) | NOT NULL |
| `saga_type` | VARCHAR(255) | |

**Indexes:** `idx_saga_association` ON (association_key, association_value); `idx_saga_id_type` ON (saga_id, saga_type)

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
