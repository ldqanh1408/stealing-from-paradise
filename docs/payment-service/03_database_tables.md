# Payment Service — Database Tables

> Cập nhật: 2026-05-03

---

## SELLER_STRIPE_ACCOUNTS
KYC Stripe cho Seller

| Cột | Kiểu | Ghi chú |
|-----|------|--------|
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
Giao dịch thanh toán (dùng Stripe hoặc VNPAY)

| Cột | Kiểu | Ghi chú |
|-----|------|--------|
| `id` | BIGSERIAL | Primary Key |
| `parent_order_id` | BIGINT | FK → PARENT_ORDERS.id |
| `amount` | DECIMAL | Số tiền giao dịch |
| `method` | VARCHAR | STRIPE \| VNPAY |
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
|-----|------|--------|
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
|-----|------|--------|
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
| `adjust_amount` | DECIMAL | Số tiền admin điều chỉnh |
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
|-----|------|--------|
| `id` | BIGSERIAL | Primary Key |
| `refund_id` | BIGINT | FK → REFUNDS.id |
| `item_id` | BIGINT | FK → ORDER_ITEMS.id |
| `quantity` | INT | Số lượng hoàn |
| `refund_amount` | DECIMAL | Số tiền hoàn cho item |
| `item_reason` | VARCHAR | Lý do hoàn riêng |
| `status` | VARCHAR | PENDING \| SUCCESS \| FAILED |
| `return_tracking_number` | VARCHAR | Mã vận đơn hoàn hàng |
| `return_evidence_images` | JSONB | Mảng ảnh gói hàng hoàn (MinIO) |
| `returned_at` | TIMESTAMP | Thời điểm Seller xác nhận nhận lại |
