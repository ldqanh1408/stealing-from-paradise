# E-Commerce Database Schema

**Ngày cập nhật:** 2026-04-29

---

## Mục lục

1. [Media & Images](#1-media--images)
2. [Users & Identity](#2-users--identity)
3. [Trust & Moderation](#3-trust--moderation)
4. [Loyalty & Points](#4-loyalty--points)
5. [Catalog - Categories & Products](#5-catalog---categories--products)
6. [Cart](#6-cart)
7. [Flash Sales](#7-flash-sales)
8. [Orders](#8-orders)
9. [Payments & Transfers](#9-payments--transfers)
10. [Reviews](#10-reviews)
11. [Notifications](#11-notifications)
12. [Infrastructure & Messaging](#12-infrastructure--messaging)
13. [Search Index](#13-search-index)

---

## 1. Media & Images

### IMAGES
Lưu trữ tập trung cho ảnh sản phẩm, review, bằng chứng hoàn tiền

| Cột | Kiểu | Ghi chú |
|-----|------|--------|
| `id` | UUID | Primary Key |
| `url` | TEXT | Liên kết MinIO |
| `file_name` | VARCHAR | Tên file |
| `file_size` | INT | Kích thước (bytes) |
| `created_at` | TIMESTAMP | Thời điểm tạo |

---

## 2. Users & Identity

### USERS
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

### CUSTOMERS
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

### SELLERS
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

### ADMINS
Hồ sơ Admin (1:1 với USERS)

| Cột | Kiểu | Ghi chú |
|-----|------|--------|
| `id` | BIGSERIAL | Primary Key |
| `user_id` | BIGINT | FK → USERS.id, UNIQUE |
| `created_at` | TIMESTAMP | Thời điểm tạo |
| `updated_at` | TIMESTAMP | Cập nhật cuối |

---

### ADDRESSES
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

## 3. Trust & Moderation

### TRUST_SCORE_EVENTS_CONFIG
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

### TRUST_SCORE_LOGS
Lịch sử thay đổi trust score

| Cột | Kiểu | Ghi chí |
|-----|------|--------|
| `id` | BIGSERIAL | Primary Key |
| `user_id` | BIGINT | FK → USERS.id |
| `delta` | INT | Mức thay đổi (+/-) |
| `event_code` | VARCHAR | FK → TRUST_SCORE_EVENTS_CONFIG.event_code |
| `reason` | VARCHAR | Lý do override hoặc Admin note |
| `changed_by` | VARCHAR | ADMIN \| SYSTEM |
| `created_at` | TIMESTAMP | Thời gian thay đổi |

---

### USER_BAN_HISTORY
Lịch sử khóa/mở khóa tài khoản

| Cột | Kiểu | Ghi chí |
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

### APPEALS
Khiếu nại trust score

| Cột | Kiểu | Ghi chí |
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

## 4. Loyalty & Points

### LOYALTY_ACCOUNTS
Tài khoản điểm (1:1 với CUSTOMERS)

| Cột | Kiểu | Ghi chí |
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

### POINT_TRANSACTIONS
Giao dịch điểm (tích/sử dụng/hết hạn/hoàn)

| Cột | Kiểu | Ghi chí |
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

## 5. Catalog - Categories & Products

### MG_CATEGORIES (MongoDB)
Danh mục sản phẩm (hỗ trợ phân cấp)

| Cột | Kiểu | Ghi chí |
|-----|------|--------|
| `id` | VARCHAR | PK, Mongo ObjectId |
| `name` | VARCHAR | Tên danh mục |
| `slug` | VARCHAR | Unique, slug thân thiện |
| `parent_id` | VARCHAR | FK → MG_CATEGORIES.id, danh mục cha |
| `level` | INT | Cấp danh mục |
| `created_at` | TIMESTAMP | Thời điểm tạo |
| `updated_at` | TIMESTAMP | Cập nhật cuối |

---

### MG_PRODUCTS (MongoDB)
Sản phẩm (do Seller đăng)

| Cột | Kiểu | Ghi chí |
|-----|------|--------|
| `id` | VARCHAR | PK, Mongo ObjectId |
| `seller_id` | BIGINT | FK → SELLERS.id |
| `category_id` | VARCHAR | FK → MG_CATEGORIES.id |
| `name` | VARCHAR | Tên sản phẩm |
| `description` | TEXT | Mô tả sản phẩm |
| `attributes` | JSONB | Thuộc tính động |
| `is_flash` | BOOLEAN | Cờ tham gia Flash Sale |
| `status` | VARCHAR | PENDING \| APPROVED \| REJECTED |
| `reject_reason` | VARCHAR | Lý do từ chối |
| `deleted_at` | TIMESTAMP | Soft delete |
| `created_at` | TIMESTAMP | Thời điểm tạo |
| `updated_at` | TIMESTAMP | Cập nhật cuối |

---

### MG_PRODUCT_IMAGES
Liên kết hình ảnh sản phẩm

| Cột | Kiểu | Ghi chí |
|-----|------|--------|
| `id` | BIGSERIAL | Primary Key |
| `product_id` | VARCHAR | FK → MG_PRODUCTS.id |
| `image_id` | UUID | FK → IMAGES.id |
| `is_main` | BOOLEAN | Ảnh chính (default: false) |
| `sort_order` | INT | Thứ tự sắp xếp (default: 0) |

---

### MG_PRODUCT_VARIANTS (MongoDB)
Phân loại sản phẩm (size, màu, etc.)

| Cột | Kiểu | Ghi chí |
|-----|------|--------|
| `id` | VARCHAR | PK, Mongo ObjectId |
| `product_id` | VARCHAR | FK → MG_PRODUCTS.id |
| `image_id` | UUID | FK → IMAGES.id, ảnh variant |
| `sku_code` | VARCHAR | Unique, mã SKU |
| `tier_name` | VARCHAR | Tên phân loại |
| `price` | DECIMAL | Giá bán |
| `created_at` | TIMESTAMP | Thời điểm tạo |
| `updated_at` | TIMESTAMP | Cập nhật cuối |

---

### MG_INVENTORIES (MongoDB)
Quản lý tồn kho theo SKU

| Cột | Kiểu | Ghi chí |
|-----|------|--------|
| `id` | VARCHAR | PK, Mongo ObjectId |
| `sku_code` | VARCHAR | Unique, FK → MG_PRODUCT_VARIANTS.sku_code |
| `product_id` | VARCHAR | FK → MG_PRODUCTS.id |
| `stock_total` | INT | Tổng tồn kho |
| `stock_locked` | INT | Số lượng đang giữ chỗ (PENDING/PAID) |
| `stock_available` | INT | Số lượng còn có thể bán |
| `stock_flash_reserved` | INT | Số lượng khóa cho Flash Sale đã duyệt |
| `updated_at` | TIMESTAMP | Cập nhật cuối |

---

## 6. Cart

### MG_CARTS (MongoDB)
Giỏ hàng (1:1 với CUSTOMERS)

| Cột | Kiểu | Ghi chí |
|-----|------|--------|
| `id` | VARCHAR | PK, Mongo ObjectId |
| `customer_id` | BIGINT | FK → CUSTOMERS.id, UNIQUE |
| `total_items` | INT | Tổng số món hàng |
| `created_at` | TIMESTAMP | Thời điểm tạo |
| `updated_at` | TIMESTAMP | Thời điểm cập nhật |

---

### MG_CART_ITEMS (MongoDB)
Chi tiết giỏ hàng

| Cột | Kiểu | Ghi chí |
|-----|------|--------|
| `id` | VARCHAR | PK, Mongo ObjectId |
| `cart_id` | VARCHAR | FK → MG_CARTS.id |
| `variant_id` | VARCHAR | FK → MG_PRODUCT_VARIANTS.id |
| `sku_code` | VARCHAR | Mã SKU |
| `fs_item_id` | BIGINT | FK → FS_ITEMS.id, nullable |
| `price_snapshot` | DECIMAL | Giá tại thời điểm thêm vào |
| `quantity` | INT | Số lượng |
| `added_at` | TIMESTAMP | Thời điểm thêm |

---

## 7. Flash Sales

### FS_SESSIONS
Session Flash Sale (theo khoảng thời gian)

| Cột | Kiểu | Ghi chí |
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

### FS_ITEMS
Sản phẩm tham gia Flash Sale

| Cột | Kiểu | Ghi chí |
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

### FS_REMINDERS
Nhắc nhở Flash Sale (dành cho Buyer có trust_score ≥ 30)

| Cột | Kiểu | Ghi chí |
|-----|------|--------|
| `id` | BIGSERIAL | Primary Key |
| `customer_id` | BIGINT | FK → CUSTOMERS.id |
| `session_id` | BIGINT | FK → FS_SESSIONS.id |
| `created_at` | TIMESTAMP | Thời điểm tạo |

---

## 8. Orders

### PARENT_ORDERS
Đơn hàng tổng (nhóm các đơn từ Seller khác nhau)

| Cột | Kiểu | Ghi chí |
|-----|------|--------|
| `id` | BIGSERIAL | Primary Key |
| `customer_id` | BIGINT | FK → CUSTOMERS.id |
| `total_amt` | DECIMAL | Tổng tiền trước khuyến mãi |
| `loyalty_discount` | DECIMAL | Số tiền giảm từ điểm |
| `final_amt` | DECIMAL | Số tiền thực thu |
| `created_at` | TIMESTAMP | Thời điểm tạo |
| `updated_at` | TIMESTAMP | Cập nhật cuối |

---

### ORDERS
Đơn hàng chi tiết (từng Seller)

| Cột | Kiểu | Ghi chí |
|-----|------|--------|
| `id` | BIGSERIAL | Primary Key |
| `parent_order_id` | BIGINT | FK → PARENT_ORDERS.id |
| `seller_id` | BIGINT | FK → SELLERS.id |
| `order_code` | VARCHAR | Unique, mã hiển thị |
| `customer_id` | BIGINT | FK → CUSTOMERS.id |
| `total_amt` | DECIMAL | Tổng tiền trước khuyến mãi |
| `final_amt` | DECIMAL | Số tiền thanh toán cuối |
| `status` | VARCHAR | PENDING \| PAID \| SHIPPING \| DELIVERED \| RETURNED \| REFUNDED \| PARTIALLY_REFUNDED \| CANCELLED |
| `cancelled_by` | VARCHAR | BUYER \| SELLER \| SYSTEM |
| `cancel_reason` | TEXT | Lý do hủy |
| `is_flash_sale` | BOOLEAN | Từ Flash Sale hay không |
| `shipping_address` | JSONB | Snapshot địa chỉ giao hàng |
| `tracking_number` | VARCHAR | Mã vận đơn |
| `shipping_deadline` | TIMESTAMP | Hạn cập nhật mã vận đơn (created_at + 3 ngày) |
| `version` | INT | Optimistic Locking |
| `created_at` | TIMESTAMP | Thời điểm tạo |
| `updated_at` | TIMESTAMP | Cập nhật cuối |

---

### ORDER_ITEMS
Chi tiết sản phẩm trong đơn hàng

| Cột | Kiểu | Ghi chí |
|-----|------|--------|
| `id` | BIGSERIAL | Primary Key |
| `order_id` | BIGINT | FK → ORDERS.id |
| `sku_code` | VARCHAR | Mã SKU tại thời điểm mua |
| `variant_id` | VARCHAR | FK → MG_PRODUCT_VARIANTS.id |
| `name_snapshot` | VARCHAR | Tên sản phẩm snapshot |
| `image_snapshot` | VARCHAR | Ảnh snapshot |
| `price_snapshot` | DECIMAL | Đơn giá snapshot |
| `quantity` | INT | Số lượng mua |
| `refunded_quantity` | INT | Số lượng đã hoàn |
| `fs_item_id` | BIGINT | FK → FS_ITEMS.id, nullable |
| `created_at` | TIMESTAMP | Thời điểm tạo |

---

## 9. Payments & Transfers

### SELLER_STRIPE_ACCOUNTS
KYC Stripe cho Seller

| Cột | Kiểu | Ghi chí |
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

### TRANSACTIONS
Giao dịch thanh toán (dùng Stripe hoặc VNPAY)

| Cột | Kiểu | Ghi chí |
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

### SELLER_TRANSFERS
Transfer tiền cho Seller sau khi DELIVERED

| Cột | Kiểu | Ghi chí |
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

### REFUNDS
Phiếu hoàn tiền

| Cột | Kiểu | Ghi chí |
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

### REFUND_ITEMS
Chi tiết hoàn tiền (từng sản phẩm)

| Cột | Kiểu | Ghi chí |
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

---

## 10. Reviews

### REVIEWS
Đánh giá sản phẩm

| Cột | Kiểu | Ghi chí |
|-----|------|--------|
| `id` | UUID | Primary Key |
| `product_id` | VARCHAR | FK → MG_PRODUCTS.id |
| `variant_id` | VARCHAR | FK → MG_PRODUCT_VARIANTS.id |
| `customer_id` | BIGINT | FK → CUSTOMERS.id |
| `order_item_id` | BIGINT | FK → ORDER_ITEMS.id, UNIQUE |
| `rating` | SMALLINT | Điểm đánh giá |
| `title` | VARCHAR | Tiêu đề đánh giá |
| `content` | TEXT | Nội dung đánh giá |
| `status` | VARCHAR | Trạng thái hiển thị |
| `created_at` | TIMESTAMP | Thời điểm tạo |
| `updated_at` | TIMESTAMP | Cập nhật cuối |

---

### REVIEW_MEDIA
Hình ảnh/Video trong đánh giá

| Cột | Kiểu | Ghi chí |
|-----|------|--------|
| `id` | UUID | Primary Key |
| `review_id` | UUID | FK → REVIEWS.id |
| `image_id` | UUID | FK → IMAGES.id |
| `media_type` | VARCHAR | image \| video |
| `created_at` | TIMESTAMP | Thời điểm tạo |

---

### REVIEW_SUMMARY
Tóm tắt đánh giá theo sản phẩm

| Cột | Kiểu | Ghi chí |
|-----|------|--------|
| `id` | UUID | Primary Key |
| `product_id` | VARCHAR | FK → MG_PRODUCTS.id, UNIQUE |
| `avg_rating` | DECIMAL | Điểm trung bình |
| `total_count` | INT | Tổng số đánh giá |
| `updated_at` | TIMESTAMP | Cập nhật cuối |

---

## 11. Notifications

### MG_NOTIFICATIONS (MongoDB)
Thông báo cho Buyer, Seller, Admin

| Cột | Kiểu | Ghi chí |
|-----|------|--------|
| `id` | VARCHAR | PK, Mongo ObjectId |
| `user_id` | BIGINT | FK → USERS.id |
| `title` | VARCHAR | Tiêu đề thông báo |
| `body` | TEXT | Nội dung thông báo |
| `type` | VARCHAR | Loại thông báo |
| `metadata` | JSONB | Dữ liệu bổ sung |
| `is_read` | BOOLEAN | Đã đọc hay chưa |
| `created_at` | TIMESTAMP | Thời điểm tạo (TTL Index 90 ngày) |

---

## 12. Infrastructure & Messaging

### OUTBOX_EVENTS
Event Outbox Pattern (cho eventual consistency)

| Cột | Kiểu | Ghi chí |
|-----|------|--------|
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

| Cột | Kiểu | Ghi chí |
|-----|------|--------|
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

| Cột | Kiểu | Ghi chí |
|-----|------|--------|
| `name` | VARCHAR | Primary Key, tên lock |
| `lock_until` | TIMESTAMP | Thời điểm hết lock |
| `locked_at` | TIMESTAMP | Thời điểm bắt đầu lock |
| `locked_by` | VARCHAR | Node/thread đang giữ lock |

---

## 13. Search Index

### ES_PRODUCTS_INDEX (Elasticsearch)
Đánh chỉ mục sản phẩm cho tìm kiếm

| Cột | Kiểu | Ghi chí |
|-----|------|--------|
| `id` | VARCHAR | Mongo ObjectId — keyword |
| `name` | TEXT | Tên sản phẩm — text |
| `description` | TEXT | Mô tả — text |
| `seller_id` | BIGINT | ID người bán — long |
| `seller_name` | VARCHAR | Tên shop — text |
| `category_id` | VARCHAR | ID danh mục — keyword |
| `category_name` | VARCHAR | Tên danh mục — keyword |
| `price_min` | DECIMAL | Giá nhỏ nhất — double |
| `price_max` | DECIMAL | Giá lớn nhất — double |
| `stock_available` | INT | Tồn kho khả dụng — integer |
| `is_flash` | BOOLEAN | Có đang Flash Sale — boolean |
| `status` | VARCHAR | Trạng thái — keyword |
| `images` | JSONB | Danh sách ảnh — keyword[] |
| `attributes` | JSONB | Thuộc tính động — nested |
| `tags` | JSONB | Tag tìm kiếm — keyword[] |
| `created_at` | TIMESTAMP | Thời điểm tạo — date |
| `updated_at` | TIMESTAMP | Cập nhật cuối — date |

---

## Các Tính Năng Chính

### 1. Trust & Moderation System
- **Trust Score**: Điểm tín nhiệm 0-100 cho cả Buyer và Seller
- **Events Config**: Cấu hình sự kiện ảnh hưởng điểm (Admin quản lý)
- **Appeal Mechanism**: User có thể khiếu nại (tối đa 3 lần/năm)
- **Ban History**: Theo dõi lịch sử khóa tài khoản

### 2. Loyalty & Points System
- **Loyalty Accounts**: Tài khoản điểm 1:1 với Customer
- **Point Transactions**: Lịch sử tích/sử dụng/hết hạn/hoàn điểm
- **Status Tracking**: PENDING/CONFIRMED để đảm bảo consistency
- **Expiration Management**: Tự động hết hạn theo ngày

### 3. Flash Sales
- **Sessions**: Khoảng thời gian Flash Sale
- **Items**: Sản phẩm tham gia với giá & tồn kho riêng
- **Reminders**: Buyer có thể nhắc nhở (yêu cầu trust_score ≥ 30)
- **Stock Management**: Tách biệt tồn kho Flash vs Regular

### 4. Orders & Fulfillment
- **Parent Orders**: Nhóm đơn hàng từ nhiều Seller
- **Sub Orders**: Từng đơn từ Seller (xử lý riêng)
- **Shipping Deadline**: Tracking JOB-13b phát hiện giao hàng trễ
- **Soft Cancellation**: Lưu vết ai hủy (BUYER/SELLER/SYSTEM)

### 5. Payments & Refunds
- **Stripe Integration**: KYC Seller + Express Account
- **Optimistic Locking**: Tránh race condition
- **Refund Workflow**: FULL/PARTIAL với RTS (Return to Sender)
- **Seller Transfers**: Tự động transfer sau DELIVERED

### 6. Catalog & Search
- **MongoDB**: Products/Categories/Variants/Inventories
- **Elasticsearch**: Đánh chỉ mục full-text search
- **Dynamic Attributes**: Hỗ trợ thuộc tính linh hoạt
- **Soft Delete**: Sản phẩm xóa không mất dữ liệu

### 7. Reviews & Ratings
- **1:1 với ORDER_ITEMS**: Mỗi item chỉ có 1 review
- **Media Support**: Ảnh/video bằng chứng
- **Review Summary**: Aggregated rating per product
- **Status Control**: Admin duyệt trước khi hiển thị

---

## Ràng Buộc & Relationships

### Primary Keys
- **PostgreSQL Tables**: BIGSERIAL (PostgreSQL) hoặc UUID
- **MongoDB Collections**: ObjectId (VARCHAR)

### Foreign Keys
- Relationships được định nghĩa rõ ràng (ref: > hoặc ref: -)
- UNIQUE constraints cho 1:1 relationships

### Optimistic Locking
- **version** column: Tăng mỗi update để tránh race condition
- Dùng cho: USERS, LOYALTY_ACCOUNTS, ORDERS, FS_ITEMS, TRANSACTIONS

### Soft Deletes
- **deleted_at**: TIMESTAMP NULL để keep audit trail
- Tables: MG_PRODUCTS, FS_SESSIONS, USERS (implicit via status)

---

## Kiểu Dữ Liệu

| Kiểu | Mô tả |
|------|-------|
| BIGSERIAL | 64-bit auto-increment integer |
| VARCHAR | Chuỗi ký tự biến độ dài |
| TEXT | Chuỗi dài (mô tả, lý do, etc.) |
| INT | 32-bit integer |
| DECIMAL | Số thập phân (tiền, điểm) |
| BOOLEAN | TRUE/FALSE |
| TIMESTAMP | Thời gian (YYYY-MM-DD HH:MM:SS) |
| UUID | Unique identifier (128-bit) |
| JSONB | JSON binary (PostgreSQL) |

---

## Nhóm Bảng (Table Groups)

1. **identity** - Người dùng & hồ sơ
2. **catalog** - Sản phẩm & danh mục
3. **orders** - Đơn hàng
4. **payments** - Thanh toán & transfers
5. **loyalty** - Điểm & thưởng
6. **flash_sale** - Flash Sale
7. **cart** - Giỏ hàng
8. **reviews** - Đánh giá
9. **moderation** - Trust & kiểm duyệt
10. **notifications** - Thông báo
11. **infrastructure** - Messaging & locks
12. **search** - Elasticsearch index

---

## Ghi Chú Quan Trọng

- **Optimistic Locking**: Sử dụng `version` column để tránh update conflict
- **Outbox Pattern**: OUTBOX_EVENTS + FAILED_EVENTS cho eventual consistency
- **ShedLock**: Distributed lock cho scheduled jobs
- **MongoDB**: Cart, Categories, Products, Variants, Inventories, Notifications
- **PostgreSQL**: Users, Orders, Payments, Loyalty, Trust, Reviews (metadata)
- **Elasticsearch**: Full-text search cho sản phẩm
- **MinIO**: Lưu trữ ảnh (URLs trong IMAGES table)

---

*Tài liệu này được sinh ra từ DBML schema ngày 2026-04-29*