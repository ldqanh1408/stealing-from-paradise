# Admin Service — Database Tables

> Cập nhật: 2026-05-03

---

## ADMINS
Hồ sơ Admin (1:1 với USERS)

| Cột | Kiểu | Ghi chú |
|-----|------|--------|
| `id` | BIGSERIAL | Primary Key |
| `user_id` | BIGINT | FK → USERS.id, UNIQUE |
| `created_at` | TIMESTAMP | Thời điểm tạo |
| `updated_at` | TIMESTAMP | Cập nhật cuối |

---

## REFUNDS
Admin có thể review và điều chỉnh refund

| Cột | Kiểu | Ghi chú |
|-----|------|--------|
| `id` | BIGSERIAL | Primary Key |
| `transaction_id` | BIGINT | FK → TRANSACTIONS.id |
| `order_id` | BIGINT | FK → ORDERS.id |
| `type` | VARCHAR | FULL \| PARTIAL |
| `initiated_by` | VARCHAR | BUYER \| SELLER \| SYSTEM |
| `amount` | DECIMAL | Số tiền hoàn lại |
| `reason` | VARCHAR | Lý do hoàn |
| `status` | VARCHAR | PENDING \| SUCCESS \| FAILED \| REJECTED |
| `evidence_images` | JSONB | Mảng ảnh bằng chứng (MinIO) |
| `reject_reason` | VARCHAR | Lý do từ chối |
| `admin_note` | TEXT | Ghi chú admin |
| `adjust_amount` | DECIMAL | Số tiền admin điều chỉnh |
| `reviewed_by` | BIGINT | FK → ADMINS.id |
| `reviewed_at` | TIMESTAMP | Thời điểm duyệt/từ chối |
| `created_at` | TIMESTAMP | Thời điểm tạo |
| `updated_at` | TIMESTAMP | Cập nhật cuối |

> Refund liên quan đến Admin — xem thêm Payment Service để biết đầy đủ các cột.
