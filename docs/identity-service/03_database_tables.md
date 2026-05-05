# Identity Service — Database Tables

> Stack: PostgreSQL
> Cập nhật: 2026-05-05

---

## USERS
Bảng người dùng cơ bản (dùng chung cho Buyer, Seller, Admin)

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | BIGSERIAL | Primary Key, ID tự tăng |
| `username` | VARCHAR | Unique, tên đăng nhập |
| `email` | VARCHAR | Unique, email xác thực |
| `phone` | VARCHAR | Unique, số điện thoại |
| `password` | VARCHAR | Mật khẩu Bcrypt |
| `full_name` | VARCHAR | Tên hiển thị |
| `status` | VARCHAR | ACTIVE \| LOCKED |
| `created_at` | TIMESTAMP | Thời điểm tạo |
| `updated_at` | TIMESTAMP | Cập nhật cuối |

**Ràng buộc:** `username`, `email`, `phone` đều là UNIQUE

---

## CUSTOMERS
Hồ sơ Buyer (1:1 với USERS)

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | BIGSERIAL | Primary Key |
| `user_id` | BIGINT | FK → USERS.id, UNIQUE |
| `created_at` | TIMESTAMP | Thời điểm tạo |
| `updated_at` | TIMESTAMP | Cập nhật cuối |

---

## SELLERS
Hồ sơ Seller (1:1 với USERS, bắt buộc KYC Stripe)

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | BIGSERIAL | Primary Key |
| `user_id` | BIGINT | FK → USERS.id, UNIQUE |
| `created_at` | TIMESTAMP | Thời điểm tạo |
| `updated_at` | TIMESTAMP | Cập nhật cuối |

---

## ADMINS
Hồ sơ Admin (1:1 với USERS)

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | BIGSERIAL | Primary Key |
| `user_id` | BIGINT | FK → USERS.id, UNIQUE |
| `created_at` | TIMESTAMP | Thời điểm tạo |
| `updated_at` | TIMESTAMP | Cập nhật cuối |

---

## ADDRESSES
Địa chỉ giao hàng (dùng chung cho Buyer và Seller)

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | BIGSERIAL | Primary Key |
| `user_id` | BIGINT | FK → USERS.id, chủ sở hữu |
| `province_id` | INT | Mã Tỉnh/Thành phố |
| `district_id` | INT | Mã Quận/Huyện |
| `full_address` | TEXT | Địa chỉ chi tiết |
| `is_default` | BOOLEAN | Cờ mặc định cho Fast Checkout |
| `created_at` | TIMESTAMP | Thời điểm tạo |
| `updated_at` | TIMESTAMP | Cập nhật cuối |
