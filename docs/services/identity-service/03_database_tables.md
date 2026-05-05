# Identity Service — Database Tables

> Stack: PostgreSQL  
> Quản lý bởi: Flyway migrations (`V1__init_users_roles_addresses.sql` → `V7__drop_removed_user_columns.sql`)  
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
| `role` | VARCHAR | BUYER \| SELLER \| ADMIN, mặc định BUYER (V4) |
| `version` | INT | Optimistic locking, mặc định 0 |
| `created_at` | TIMESTAMP | Thời điểm tạo |
| `updated_at` | TIMESTAMP | Cập nhật cuối |

**Ràng buộc:** `username`, `email`, `phone` đều là UNIQUE

> **Các cột đã drop (V7):** `avatar_url`, `trust_score`, `locked_until`, `lock_reason`, `appeal_count`, `product_posting_suspended`, `last_cancellation_penalty_at`, `last_warning_at`, `last_posting_suspension_at`, `reward_10_orders_accumulated`

---

## ROLES
Vai trò người dùng (multi-role support)

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | BIGSERIAL | Primary Key |
| `user_id` | BIGINT | FK → USERS.id, ON DELETE CASCADE |
| `role_name` | VARCHAR | BUYER \| SELLER \| ADMIN |
| `created_at` | TIMESTAMP | Thời điểm tạo |
| `updated_at` | TIMESTAMP | Cập nhật cuối |

**Index:** `idx_roles_user_id` ON roles(user_id)

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

**Index:** `idx_addresses_user_id` ON addresses(user_id)

---

## ADMINS
Hồ sơ Admin (1:1 với USERS)

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | BIGSERIAL | Primary Key |
| `user_id` | BIGINT | FK → USERS.id, UNIQUE |
| `created_at` | TIMESTAMP | Thời điểm tạo |
| `updated_at` | TIMESTAMP | Cập nhật cuối
