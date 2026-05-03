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

## USER_BAN_HISTORY
Lịch sử khóa/mở khóa tài khoản (do Admin thực hiện)

| Cột | Kiểu | Ghi chú |
|-----|------|--------|
| `id` | BIGSERIAL | Primary Key |
| `user_id` | BIGINT | FK → USERS.id |
| `action` | VARCHAR | LOCKED \| UNLOCKED |
| `reason` | TEXT | Lý do khóa/mở khóa |
| `performed_by` | VARCHAR | ADMIN \| SYSTEM |
| `admin_id` | BIGINT | FK → ADMINS.id, NULL nếu SYSTEM |
| `locked_until` | TIMESTAMP | NULL = vĩnh viễn; có giá trị = khóa tạm thời |
| `created_at` | TIMESTAMP | Thời điểm thực hiện |

---

## TRUST_SCORE_EVENTS_CONFIG
Cấu hình các sự kiện ảnh hưởng trust score (Admin quản lý)

| Cột | Kiểu | Ghi chú |
|-----|------|--------|
| `id` | BIGSERIAL | Primary Key |
| `event_code` | VARCHAR | Unique, mã sự kiện (VD: PRODUCT_REJECTED_FIRST) |
| `delta` | INT | Dương = cộng, âm = trừ |
| `description` | TEXT | Mô tả sự kiện |
| `is_active` | BOOLEAN | Cờ bật/tắt (default TRUE) |
| `updated_at` | TIMESTAMP | Cập nhật cuối (Admin chỉnh) |

---

## APPEALS
Khiếu nại trust score (Admin xử lý)

| Cột | Kiểu | Ghi chú |
|-----|------|--------|
| `id` | BIGSERIAL | Primary Key |
| `user_id` | BIGINT | FK → USERS.id |
| `trust_score_log_id` | BIGINT | FK → TRUST_SCORE_LOGS.id |
| `reason` | TEXT | Lý do khiếu nại của User |
| `evidence_urls` | JSONB | Mảng URL bằng chứng (MinIO) |
| `status` | VARCHAR | PENDING \| APPROVED \| REJECTED |
| `reviewed_by` | BIGINT | FK → ADMINS.id, nullable |
| `admin_note` | TEXT | Ghi chú Admin |
| `reviewed_at` | TIMESTAMP | Thời điểm xử lý |
| `created_at` | TIMESTAMP | Thời điểm nộp |
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
