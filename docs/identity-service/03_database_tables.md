# Identity Service — Database Tables

> Cập nhật: 2026-05-03

---

## USERS
Bảng người dùng cơ bản (dùng chung cho Buyer, Seller, Admin)

| Cột | Kiểu | Ghi chú |
|-----|------|--------|
| `id` | BIGSERIAL | Primary Key, ID tự tăng |
| `username` | VARCHAR | Unique, tên đăng nhập |
| `email` | VARCHAR | Unique, email xác thực |
| `phone` | VARCHAR | Unique, số điện thoại |
| `password` | VARCHAR | Mật khẩu Bcrypt |
| `full_name` | VARCHAR | Tên hiển thị |
| `status` | VARCHAR | ACTIVE \| LOCKED |
| `locked_until` | TIMESTAMP | NULL = khóa vĩnh viễn; có giá trị = tự mở sau |
| `lock_reason` | VARCHAR | Lý do khóa |
| `version` | INT | Optimistic Locking |
| `created_at` | TIMESTAMP | Thời điểm tạo |
| `updated_at` | TIMESTAMP | Cập nhật cuối |

**Ràng buộc:** `username`, `email`, `phone` đều là UNIQUE

---

## CUSTOMERS
Hồ sơ Buyer (1:1 với USERS)

| Cột | Kiểu | Ghi chú |
|-----|------|--------|
| `id` | BIGSERIAL | Primary Key |
| `user_id` | BIGINT | FK → USERS.id, UNIQUE |
| `trust_score` | INT | 0-100, mặc định 80 |
| `appeal_count` | INT | Số lần appeal/năm (max 3) |
| `last_warning_at` | TIMESTAMP | Mốc warning gần nhất (debounce 24h) |
| `last_cancellation_penalty_at` | TIMESTAMP | Mốc trừ điểm hủy đơn gần nhất |
| `reward_10_orders_accumulated` | INT | Tổng điểm từ sự kiện EVERY_10_ORDERS (không reset, cap +20) |
| `created_at` | TIMESTAMP | Thời điểm tạo |
| `updated_at` | TIMESTAMP | Cập nhật cuối |

---

## SELLERS
Hồ sơ Seller (1:1 với USERS, bắt buộc KYC Stripe)

| Cột | Kiểu | Ghi chú |
|-----|------|--------|
| `id` | BIGSERIAL | Primary Key |
| `user_id` | BIGINT | FK → USERS.id, UNIQUE |
| `trust_score` | INT | 0-100, mặc định 80 |
| `appeal_count` | INT | Số lần appeal/năm (max 3) |
| `last_warning_at` | TIMESTAMP | Mốc warning gần nhất (debounce 24h) |
| `product_posting_suspended` | BOOLEAN | TRUE = tạm dừng đăng sản phẩm |
| `last_posting_suspension_at` | TIMESTAMP | Mốc cấm đăng bài gần nhất |
| `created_at` | TIMESTAMP | Thời điểm tạo |
| `updated_at` | TIMESTAMP | Cập nhật cuối |

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

## ADDRESSES
Địa chỉ giao hàng (dùng chung cho Buyer và Seller)

| Cột | Kiểu | Ghi chú |
|-----|------|--------|
| `id` | BIGSERIAL | Primary Key |
| `user_id` | BIGINT | FK → USERS.id, chủ sở hữu |
| `province_id` | INT | Mã Tỉnh/Thành phố |
| `district_id` | INT | Mã Quận/Huyện |
| `full_address` | TEXT | Địa chỉ chi tiết |
| `is_default` | BOOLEAN | Cờ mặc định cho Fast Checkout |
| `created_at` | TIMESTAMP | Thời điểm tạo |
| `updated_at` | TIMESTAMP | Cập nhật cuối |
