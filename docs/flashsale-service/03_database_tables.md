# Flash Sale Service — Database Tables

> Cập nhật: 2026-05-03

---

## FS_SESSIONS
Session Flash Sale (theo khoảng thời gian)

| Cột | Kiểu | Ghi chú |
|-----|------|--------|
| `id` | BIGSERIAL | Primary Key |
| `name` | VARCHAR | Tên flash sale session |
| `start_time` | TIMESTAMP | Thời điểm bắt đầu |
| `end_time` | TIMESTAMP | Thời điểm kết thúc |
| `status` | VARCHAR | UPCOMING \| ACTIVE \| ENDED |
| `deleted_at` | TIMESTAMP | Soft delete |
| `created_at` | TIMESTAMP | Thời điểm tạo |
| `updated_at` | TIMESTAMP | Cập nhật cuối |

---

## FS_ITEMS
Sản phẩm tham gia Flash Sale

| Cột | Kiểu | Ghi chú |
|-----|------|--------|
| `id` | BIGSERIAL | Primary Key |
| `session_id` | BIGINT | FK → FS_SESSIONS.id |
| `sku_code` | VARCHAR | FK → MG_PRODUCT_VARIANTS.sku_code |
| `flash_price` | DECIMAL | Giá flash sale |
| `flash_stock` | INT | Tồn kho flash sale |
| `limit_per_user` | INT | Giới hạn mỗi user |
| `sold_qty` | INT | Số lượng đã bán |
| `status` | VARCHAR | PENDING \| APPROVED \| REJECTED \| CANCELLED |
| `version` | INT | Optimistic Locking |
| `created_at` | TIMESTAMP | Thời điểm tạo |
| `updated_at` | TIMESTAMP | Cập nhật cuối |

---

## FS_REMINDERS
Nhắc nhở Flash Sale (dành cho Buyer có trust_score ≥ 30)

| Cột | Kiểu | Ghi chú |
|-----|------|--------|
| `id` | BIGSERIAL | Primary Key |
| `customer_id` | BIGINT | FK → CUSTOMERS.id |
| `session_id` | BIGINT | FK → FS_SESSIONS.id |
| `created_at` | TIMESTAMP | Thời điểm tạo |
