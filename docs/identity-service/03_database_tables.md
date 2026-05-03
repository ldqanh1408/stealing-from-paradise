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

---

## LOYALTY_ACCOUNTS
Tài khoản điểm (1:1 với CUSTOMERS)

| Cột | Kiểu | Ghi chú |
|-----|------|--------|
| `id` | BIGSERIAL | Primary Key |
| `customer_id` | BIGINT | FK → CUSTOMERS.id, UNIQUE |
| `total_earned_points` | INT | Tổng điểm tích lũy |
| `available_points` | INT | Điểm còn có thể sử dụng |
| `used_points` | INT | Tổng điểm đã dùng |
| `expired_points` | INT | Tổng điểm đã hết hạn |
| `version` | INT | Optimistic Locking |
| `created_at` | TIMESTAMP | Thời điểm tạo |
| `updated_at` | TIMESTAMP | Cập nhật cuối |

---

## POINT_TRANSACTIONS
Giao dịch điểm (tích/sử dụng/hết hạn/hoàn)

| Cột | Kiểu | Ghi chú |
|-----|------|--------|
| `id` | BIGSERIAL | Primary Key |
| `customer_id` | BIGINT | FK → CUSTOMERS.id |
| `order_id` | BIGINT | FK → ORDERS.id |
| `order_code` | VARCHAR | Mã đơn hàng hiển thị |
| `delta` | INT | Số điểm thay đổi: dương/cộng, âm/trừ |
| `remaining_delta` | INT | Số điểm còn lại (giảm khi dùng) |
| `type` | VARCHAR | EARNED \| USED \| EXPIRED \| REFUNDED |
| `status` | VARCHAR | PENDING \| CONFIRMED |
| `balance_after` | INT | available_points sau giao dịch |
| `note` | VARCHAR | Ghi chú |
| `expires_at` | TIMESTAMP | Thời điểm hết hạn |
| `created_at` | TIMESTAMP | Thời gian giao dịch |

---

## TRUST_SCORE_EVENTS_CONFIG
Cấu hình các sự kiện ảnh hưởng trust score

| Cột | Kiểu | Ghi chú |
|-----|------|--------|
| `id` | BIGSERIAL | Primary Key |
| `event_code` | VARCHAR | Unique, mã sự kiện (VD: PRODUCT_REJECTED_FIRST) |
| `delta` | INT | Dương = cộng, âm = trừ |
| `description` | TEXT | Mô tả sự kiện |
| `is_active` | BOOLEAN | Cờ bật/tắt (default TRUE) |
| `updated_at` | TIMESTAMP | Cập nhật cuối (Admin chỉnh) |

---

## TRUST_SCORE_LOGS
Lịch sử thay đổi trust score

| Cột | Kiểu | Ghi chú |
|-----|------|--------|
| `id` | BIGSERIAL | Primary Key |
| `user_id` | BIGINT | FK → USERS.id |
| `delta` | INT | Mức thay đổi (+/-) |
| `event_code` | VARCHAR | FK → TRUST_SCORE_EVENTS_CONFIG.event_code |
| `reason` | VARCHAR | Lý do override hoặc Admin note |
| `changed_by` | VARCHAR | ADMIN \| SYSTEM |
| `created_at` | TIMESTAMP | Thời gian thay đổi |

---

## USER_BAN_HISTORY
Lịch sử khóa/mở khóa tài khoản

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

## APPEALS
Khiếu nại trust score

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
