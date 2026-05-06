# E-Commerce Database Schema

**Ngày cập nhật:** 2026-05-06

---

## Mục lục

1. [Media & Images](#1-media--images)
2. [Users & Identity](#2-users--identity)
3. [Catalog - Categories & Products](#5-catalog---categories--products)
4. [Cart](#6-cart)
5. [Flash Sales](#7-flash-sales)
6. [Orders](#8-orders)
7. [Payments & Transfers](#9-payments--transfers)
8. [Notifications](#10-notifications)
9. [Infrastructure & Messaging](#11-infrastructure--messaging)
10. [Search Index](#12-search-index)
11. [AI Chat Support](#13-ai-chat-support)

---

## 1. Media & Images

Lưu trữ tại **MinIO** (object storage), bucket `products-media`. Mỗi sản phẩm/variant có thể có nhiều ảnh. Ảnh được upload qua presigned URL và xóa khi sản phẩm bị xóa.

| Collection | Key | Kiểu | Ghi chú |
| ---------- | --- | ---- | ------- |
| `product_images` | `products/{seller_id}/{product_id}/{uuid}-{type}.{ext}` | MinIO Object | Ảnh gốc, full-size |
| `product_images` | `products/{seller_id}/{product_id}/{uuid}-{type}_thumb.{ext}` | MinIO Object | Thumbnail, resize tự động |
| `product_images` | `products/{seller_id}/{product_id}/{uuid}-{type}_small.{ext}` | MinIO Object | Ảnh nhỏ cho danh sách |

**Entity tham chiếu**:
- `products.images[]` — Mảng URL ảnh trong MongoDB document
- `variants.image_url` — URL ảnh đại diện cho từng variant

## 2. Users & Identity

### USERS

Bảng người dùng cơ bản (dùng chung cho Buyer, Seller, Admin)

| Cột          | Kiểu      | Ghi chú                 |
| ------------ | --------- | ----------------------- |
| `id`         | BIGSERIAL | Primary Key, ID tự tăng |
| `username`   | VARCHAR   | Unique, tên đăng nhập   |
| `email`      | VARCHAR   | Unique, email xác thực  |
| `phone`      | VARCHAR   | Unique, số điện thoại   |
| `password`   | VARCHAR   | Mật khẩu Bcrypt         |
| `full_name`  | VARCHAR   | Tên hiển thị            |
| `status`     | VARCHAR   | ACTIVE | LOCKED         |
| `role`       | VARCHAR   | BUYER | SELLER | ADMIN, mặc định BUYER |
| `version`    | INT       | Optimistic locking, mặc định 0 |
| `created_at` | TIMESTAMP | Thời điểm tạo           |
| `updated_at` | TIMESTAMP | Cập nhật cuối           |

**Ràng buộc:** `username`, `email`, `phone` đều là UNIQUE

**Index:** `idx_users_role` ON users(role)

---

### ROLES

Vai trò người dùng (multi-role support từ V1)

| Cột          | Kiểu      | Ghi chú                               |
| ------------ | --------- | ------------------------------------- |
| `id`         | BIGSERIAL | Primary Key                           |
| `user_id`    | BIGINT    | FK → USERS.id, ON DELETE CASCADE      |
| `role_name`  | VARCHAR   | BUYER | SELLER | ADMIN              |
| `created_at` | TIMESTAMP | Thời điểm tạo                         |
| `updated_at` | TIMESTAMP | Cập nhật cuối                         |

**Index:** `idx_roles_user_id` ON roles(user_id)

---

### CUSTOMERS

Hồ sơ Buyer (1:1 với USERS)

| Cột          | Kiểu      | Ghi chú               |
| ------------ | --------- | --------------------- |
| `id`         | BIGSERIAL | Primary Key           |
| `user_id`    | BIGINT    | FK → USERS.id, UNIQUE |
| `created_at` | TIMESTAMP | Thời điểm tạo         |
| `updated_at` | TIMESTAMP | Cập nhật cuối         |

---

### SELLERS

Hồ sơ Seller (1:1 với USERS, bắt buộc KYC Stripe)

| Cột          | Kiểu      | Ghi chú               |
| ------------ | --------- | --------------------- |
| `id`         | BIGSERIAL | Primary Key           |
| `user_id`    | BIGINT    | FK → USERS.id, UNIQUE |
| `created_at` | TIMESTAMP | Thời điểm tạo         |
| `updated_at` | TIMESTAMP | Cập nhật cuối         |

---

### ADMINS

Hồ sơ Admin (1:1 với USERS)

| Cột          | Kiểu      | Ghi chú               |
| ------------ | --------- | --------------------- |
| `id`         | BIGSERIAL | Primary Key           |
| `user_id`    | BIGINT    | FK → USERS.id, UNIQUE |
| `created_at` | TIMESTAMP | Thời điểm tạo         |
| `updated_at` | TIMESTAMP | Cập nhật cuối         |

---

### ADDRESSES

Địa chỉ giao hàng (dùng chung cho Buyer và Seller)

| Cột            | Kiểu      | Ghi chú                       |
| -------------- | --------- | ----------------------------- |
| `id`           | BIGSERIAL | Primary Key                   |
| `user_id`      | BIGINT    | FK → USERS.id, chủ sở hữu     |
| `province_id`  | INT       | Mã Tỉnh/Thành phố             |
| `district_id`  | INT       | Mã Quận/Huyện                 |
| `full_address` | TEXT      | Địa chỉ chi tiết              |
| `is_default`   | BOOLEAN   | Cờ mặc định cho Fast Checkout |
| `created_at`   | TIMESTAMP | Thời điểm tạo                 |
| `updated_at`   | TIMESTAMP | Cập nhật cuối                 |

---

## 3. Catalog - Categories & Products

### MG_CATEGORIES (MongoDB)

Danh mục sản phẩm (hỗ trợ phân cấp)

| Cột          | Kiểu      | Ghi chú                             |
| ------------ | --------- | ----------------------------------- |
| `id`         | VARCHAR   | PK, Mongo ObjectId                  |
| `name`       | VARCHAR   | Tên danh mục                        |
| `slug`       | VARCHAR   | Unique, slug thân thiện             |
| `parent_id`  | VARCHAR   | FK → MG_CATEGORIES.id, danh mục cha |
| `level`      | INT       | Cấp danh mục                        |
| `created_at` | TIMESTAMP | Thời điểm tạo                       |
| `updated_at` | TIMESTAMP | Cập nhật cuối                       |

---

### MG_PRODUCTS (MongoDB)

Sản phẩm (do Seller đăng)

| Cột             | Kiểu      | Ghi chú                       |
| --------------- | --------- | ----------------------------- |
| `id`            | VARCHAR   | PK, Mongo ObjectId            |
| `seller_id`     | BIGINT    | FK → SELLERS.id               |
| `category_id`   | VARCHAR   | FK → MG_CATEGORIES.id         |
| `name`          | VARCHAR   | Tên sản phẩm                  |
| `description`   | TEXT      | Mô tả sản phẩm                |
| `attributes`    | JSONB     | Thuộc tính động               |
| `is_flash`      | BOOLEAN   | Cờ tham gia Flash Sale        |
| `status`        | VARCHAR   | PENDING | APPROVED | REJECTED |
| `reject_reason` | VARCHAR   | Lý do từ chối                 |
| `deleted_at`    | TIMESTAMP | Soft delete                   |
| `created_at`    | TIMESTAMP | Thời điểm tạo                 |
| `updated_at`    | TIMESTAMP | Cập nhật cuối                 |

---

### MG_PRODUCT_IMAGES

Liên kết hình ảnh sản phẩm

| Cột          | Kiểu      | Ghi chú                     |
| ------------ | --------- | --------------------------- |
| `id`         | BIGSERIAL | Primary Key                 |
| `product_id` | VARCHAR   | FK → MG_PRODUCTS.id         |
| `image_id`   | UUID      | FK → IMAGES.id              |
| `is_main`    | BOOLEAN   | Ảnh chính (default: false)  |
| `sort_order` | INT       | Thứ tự sắp xếp (default: 0) |

---

### MG_PRODUCT_VARIANTS (MongoDB)

Phân loại sản phẩm (size, màu, etc.)

| Cột          | Kiểu      | Ghi chú                     |
| ------------ | --------- | --------------------------- |
| `id`         | VARCHAR   | PK, Mongo ObjectId          |
| `product_id` | VARCHAR   | FK → MG_PRODUCTS.id         |
| `image_id`   | UUID      | FK → IMAGES.id, ảnh variant |
| `sku_code`   | VARCHAR   | Unique, mã SKU              |
| `tier_name`  | VARCHAR   | Tên phân loại               |
| `price`      | DECIMAL   | Giá bán                     |
| `created_at` | TIMESTAMP | Thời điểm tạo               |
| `updated_at` | TIMESTAMP | Cập nhật cuối               |

---

### MG_INVENTORIES (MongoDB)

Quản lý tồn kho theo SKU

| Cột                    | Kiểu      | Ghi chú                                   |
| ---------------------- | --------- | ----------------------------------------- |
| `id`                   | VARCHAR   | PK, Mongo ObjectId                        |
| `sku_code`             | VARCHAR   | Unique, FK → MG_PRODUCT_VARIANTS.sku_code |
| `product_id`           | VARCHAR   | FK → MG_PRODUCTS.id                       |
| `stock_total`          | INT       | Tổng tồn kho                              |
| `stock_locked`         | INT       | Số lượng đang giữ chỗ (PENDING/PAID)      |
| `stock_available`      | INT       | Số lượng còn có thể bán                   |
| `stock_flash_reserved` | INT       | Số lượng khóa cho Flash Sale đã duyệt     |
| `updated_at`           | TIMESTAMP | Cập nhật cuối                             |

---

## 4. Cart

### MG_CARTS (MongoDB)

Giỏ hàng (1:1 với CUSTOMERS)

| Cột           | Kiểu      | Ghi chú                   |
| ------------- | --------- | ------------------------- |
| `id`          | VARCHAR   | PK, Mongo ObjectId        |
| `customer_id` | BIGINT    | FK → CUSTOMERS.id, UNIQUE |
| `total_items` | INT       | Tổng số món hàng          |
| `created_at`  | TIMESTAMP | Thời điểm tạo             |
| `updated_at`  | TIMESTAMP | Thời điểm cập nhật        |

---

### MG_CART_ITEMS (MongoDB)

Chi tiết giỏ hàng

| Cột              | Kiểu      | Ghi chú                     |
| ---------------- | --------- | --------------------------- |
| `id`             | VARCHAR   | PK, Mongo ObjectId          |
| `cart_id`        | VARCHAR   | FK → MG_CARTS.id            |
| `variant_id`     | VARCHAR   | FK → MG_PRODUCT_VARIANTS.id |
| `sku_code`       | VARCHAR   | Mã SKU                      |
| `fs_item_id`     | BIGINT    | FK → FS_ITEMS.id, nullable  |
| `price_snapshot` | DECIMAL   | Giá tại thời điểm thêm vào  |
| `is_selected`    | BOOLEAN   | Chọn để checkout (mặc định TRUE) |
| `quantity`       | INT       | Số lượng                    |
| `added_at`       | TIMESTAMP | Thời điểm thêm              |

---

## 5. Flash Sales

### FS_SESSIONS

Session Flash Sale (theo khoảng thời gian)

| Cột          | Kiểu      | Ghi chú                   |
| ------------ | --------- | ------------------------- |
| `id`         | BIGSERIAL | Primary Key               |
| `name`       | VARCHAR   | Tên flash sale session    |
| `start_time` | TIMESTAMP | Thời điểm bắt đầu         |
| `end_time`   | TIMESTAMP | Thời điểm kết thúc        |
| `status`     | VARCHAR   | UPCOMING | ACTIVE | ENDED |
| `deleted_at` | TIMESTAMP | Soft delete               |
| `created_at` | TIMESTAMP | Thời điểm tạo             |
| `updated_at` | TIMESTAMP | Cập nhật cuối             |

---

### FS_ITEMS

Sản phẩm tham gia Flash Sale

| Cột              | Kiểu      | Ghi chú                                   |
| ---------------- | --------- | ----------------------------------------- |
| `id`             | BIGSERIAL | Primary Key                               |
| `session_id`     | BIGINT    | FK → FS_SESSIONS.id                       |
| `sku_code`       | VARCHAR   | FK → MG_PRODUCT_VARIANTS.sku_code         |
| `flash_price`    | DECIMAL   | Giá flash sale                            |
| `flash_stock`    | INT       | Tồn kho flash sale                        |
| `limit_per_user` | INT       | Giới hạn mỗi user                         |
| `sold_qty`       | INT       | Số lượng đã bán                           |
| `status`         | VARCHAR   | PENDING | APPROVED | REJECTED | CANCELLED |
| `version`        | INT       | Optimistic Locking                        |
| `created_at`     | TIMESTAMP | Thời điểm tạo                             |
| `updated_at`     | TIMESTAMP | Cập nhật cuối                             |

---

### FS_REMINDERS

Nhắc nhở Flash Sale

| Cột           | Kiểu      | Ghi chú             |
| ------------- | --------- | ------------------- |
| `id`          | BIGSERIAL | Primary Key         |
| `customer_id` | BIGINT    | FK → CUSTOMERS.id   |
| `session_id`  | BIGINT    | FK → FS_SESSIONS.id |
| `created_at`  | TIMESTAMP | Thời điểm tạo       |

---

## 6. Orders

> **Giải thích `order_code`:** `order_code` là mã hiển thị đơn hàng (public display code) dành cho khách hàng và người bán, có dạng dễ đọc như `DH20260506-1A2B3C`. Mã này khác với trường `id` (BIGSERIAL) chỉ dùng nội bộ. `order_code` đảm bảo tính duy nhất và thân thiện khi tra cứu, xuất hiện trên giao diện, email và tin nhắn.

### PARENT_ORDERS

Đơn hàng tổng (nhóm các đơn từ Seller khác nhau)

| Cột           | Kiểu      | Ghi chú                    |
| ------------- | --------- | -------------------------- |
| `id`          | BIGSERIAL | Primary Key                              |
| `customer_id` | BIGINT    | FK → CUSTOMERS.id                        |
| `total_amt`   | DECIMAL   | Tổng tiền trước khuyến mãi               |
| `final_amt`   | DECIMAL   | Số tiền thực thu                         |
| `status`      | VARCHAR   | PENDING_PAYMENT | PAID | CANCELLED      |
| `created_at`  | TIMESTAMP | Thời điểm tạo                            |
| `updated_at`  | TIMESTAMP | Cập nhật cuối                            |

---

### ORDERS

Đơn hàng chi tiết (từng Seller)

| Cột                 | Kiểu      | Ghi chú                                                                                      |
| ------------------- | --------- | -------------------------------------------------------------------------------------------- |
| `id`                | BIGSERIAL | Primary Key                                                                                  |
| `parent_order_id`   | BIGINT    | FK → PARENT_ORDERS.id                                                                        |
| `seller_id`         | BIGINT    | FK → SELLERS.id                                                                              |
| `order_code`        | VARCHAR   | Unique, mã hiển thị đơn hàng (xem giải thích phía trên)                                      |
| `customer_id`       | BIGINT    | FK → CUSTOMERS.id                                                                            |
| `total_amt`         | DECIMAL   | Tổng tiền trước khuyến mãi                                                                   |
| `final_amt`         | DECIMAL   | Số tiền thanh toán cuối                                                                      |
| `status`            | VARCHAR   | PENDING | PAID | SHIPPING | DELIVERED | RETURNED | REFUNDED | PARTIALLY_REFUNDED | CANCELLED |
| `cancelled_by`      | VARCHAR   | BUYER | SELLER | SYSTEM                                                                      |
| `cancel_reason`     | TEXT      | Lý do hủy                                                                                    |
| `shipping_address`  | JSONB     | Snapshot địa chỉ giao hàng                                                                   |
| `shipping_deadline` | TIMESTAMP | Hạn cập nhật mã vận đơn (created_at + 3 ngày)                                                |
| `paid_at`           | TIMESTAMP | Thời điểm thanh toán thành công                                                              |
| `shipped_at`        | TIMESTAMP | Thời điểm seller xác nhận đã giao hàng (tất cả item có tracking)                             |
| `delivered_at`      | TIMESTAMP | Thời điểm giao hàng thành công đến tay khách                                                 |
| `created_at`        | TIMESTAMP | Thời điểm tạo                                                                                |
| `updated_at`        | TIMESTAMP | Cập nhật cuối                                                                                |

> **Lưu ý:** Đã dời cột `tracking_number` xuống bảng `ORDER_ITEMS` để hỗ trợ giao hàng từng phần (mỗi item/món hàng có thể có mã vận đơn riêng). `shipped_at` được cập nhật khi tất cả item đã có tracking, `delivered_at` khi toàn bộ đơn hoàn tất.

---

### ORDER_ITEMS

Chi tiết sản phẩm trong đơn hàng

| Cột                 | Kiểu      | Ghi chú                     |
| ------------------- | --------- | --------------------------- |
| `id`                | BIGSERIAL | Primary Key                 |
| `order_id`          | BIGINT    | FK → ORDERS.id              |
| `sku_code`          | VARCHAR   | Mã SKU tại thời điểm mua    |
| `variant_id`        | VARCHAR   | FK → MG_PRODUCT_VARIANTS.id |
| `name_snapshot`     | VARCHAR   | Tên sản phẩm snapshot       |
| `image_snapshot`    | VARCHAR   | Ảnh snapshot                |
| `price_snapshot`    | DECIMAL   | Đơn giá snapshot            |
| `quantity`          | INT       | Số lượng mua                |
| `fs_item_id`        | BIGINT    | FK → FS_ITEMS.id, nullable  |
| `tracking_number`   | VARCHAR   | Mã vận đơn cho món hàng     |
| `carrier`           | VARCHAR   | Đơn vị vận chuyển (VD: GHTK, Viettel Post) |
| `created_at`        | TIMESTAMP | Thời điểm tạo               |

---

## 7. Payments & Transfers

### SELLER_STRIPE_ACCOUNTS

KYC Stripe cho Seller

| Cột                         | Kiểu      | Ghi chú                                   |
| --------------------------- | --------- | ----------------------------------------- |
| `id`                        | BIGSERIAL | Primary Key                               |
| `seller_id`                 | BIGINT    | FK → SELLERS.id, UNIQUE                   |
| `stripe_account_id`         | VARCHAR   | acct_xxx — Stripe Express Account ID      |
| `account_status`            | VARCHAR   | PENDING | ACTIVE | RESTRICTED | SUSPENDED |
| `charges_enabled`           | BOOLEAN   | Stripe cho phép nhận thanh toán           |
| `payouts_enabled`           | BOOLEAN   | Stripe cho phép rút tiền                  |
| `details_submitted`         | BOOLEAN   | KYC đã hoàn tất                           |
| `onboarding_url`            | TEXT      | Account Link URL (nullify sau 24h)        |
| `express_dashboard_url`     | TEXT      | Dashboard URL                             |
| `onboarding_url_expires_at` | TIMESTAMP | Thời điểm URL hết hạn                     |
| `created_at`                | TIMESTAMP | Thời điểm tạo                             |
| `updated_at`                | TIMESTAMP | Cập nhật cuối                             |

---

### TRANSACTIONS

Giao dịch thanh toán (dùng Stripe hoặc VNPAY)

| Cột                      | Kiểu      | Ghi chú                                          |
| ------------------------ | --------- | ------------------------------------------------ |
| `id`                     | BIGSERIAL | Primary Key                                      |
| `parent_order_id`        | BIGINT    | FK → PARENT_ORDERS.id                            |
| `amount`                 | DECIMAL   | Số tiền giao dịch                                |
| `trans_ref`              | VARCHAR   | PaymentIntent ID (pi_xxx)                        |
| `stripe_transfer_id`     | VARCHAR   | Transfer ID tr_xxx (chỉ lưu transfer đầu tiên)   |
| `application_fee_amount` | DECIMAL   | Phí sàn                                          |
| `stripe_connect_mode`    | VARCHAR   | DESTINATION | TRANSFER | NONE                    |
| `status`                 | VARCHAR   | SUCCESS | FAILED | REFUNDED | PARTIALLY_REFUNDED |
| `raw_response`           | JSONB     | Nguyên bản payload từ cổng thanh toán            |
| `pay_at`                 | TIMESTAMP | Thời gian tiền về                                |
| `created_at`             | TIMESTAMP | Thời điểm tạo                                    |
| `updated_at`             | TIMESTAMP | Cập nhật cuối                                    |

---

### SELLER_TRANSFERS

Transfer tiền cho Seller sau khi DELIVERED (delayed payout flow: platform giữ tiền → chờ hết hạn hoàn hàng → trừ phí sàn → chuyển khoản seller)

| Cột                      | Kiểu       | Ghi chú                                               |
| ------------------------ | ---------- | ----------------------------------------------------- |
| `id`                     | BIGSERIAL  | Primary Key                                           |
| `order_id`               | BIGINT     | FK → ORDERS.id (sub-order)                            |
| `seller_id`              | BIGINT     | FK → SELLERS.id                                       |
| `transaction_id`         | BIGINT     | FK → TRANSACTIONS.id, liên kết trực tiếp giao dịch gốc|
| `transfer_amount`        | DECIMAL    | Số tiền transfer (gross, chưa trừ phí sàn)            |
| `stripe_transfer_id`     | VARCHAR    | Stripe Transfer ID (dùng cho Reversal)                |
| `delivered_at`           | TIMESTAMP  | Thời điểm xác nhận giao hàng                          |
| `payout_eligible_at`     | TIMESTAMP  | Thời điểm có thể chuyển tiền (delivered + 7 ngày)     |
| `platform_commission_amt`| DECIMAL    | Phí sàn khấu trừ (5% của transfer_amount)             |
| `payout_at`              | TIMESTAMP  | Thời gian thực hiện payout                            |
| `payout_retry_count`     | INTEGER    | Số lần thử lại payout (default 0)                     |
| `status`                 | VARCHAR    | PENDING | AWAITING_DELIVERY | RETURN_WINDOW | READY_FOR_PAYOUT | PAID_OUT | FAILED | SKIPPED | REFUNDED | REVERSED | PARTIALLY_REVERSED |
| `created_at`             | TIMESTAMP  | Thời điểm tạo                                         |
| `updated_at`             | TIMESTAMP  | Cập nhật cuối                                         |

> **Status flow:** PENDING → AWAITING_DELIVERY (payment success) → RETURN_WINDOW (order delivered) → READY_FOR_PAYOUT (cron claim) → PAID_OUT (Stripe Transfer created). Refund trong return window → REFUNDED (không cần reversal vì tiền chưa rời platform). Refund sau payout → REVERSED (có Stripe Transfer reversal).

> **UNIQUE (order_id):** Mỗi order chỉ được transfer một lần — ràng buộc này ngăn duplicate transfer do job chạy lại hoặc retry không idempotent, tránh trả tiền gấp đôi cho seller.

> **transaction_id:** Cho phép truy xuất trực tiếp giao dịch gốc mà không cần join qua ORDERS rồi PARENT_ORDERS. Trường này bắt buộc phải có để hỗ trợ reversal và đối soát nhanh.

---

### REFUNDS

Phiếu hoàn tiền

| Cột                  | Kiểu      | Ghi chú                                           |
| -------------------- | --------- | ------------------------------------------------- |
| `id`                 | BIGSERIAL | Primary Key                                       |
| `transaction_id`     | BIGINT    | FK → TRANSACTIONS.id                              |
| `order_id`           | BIGINT    | FK → ORDERS.id                                    |
| `group_ref`          | UUID      | Nhóm nhiều refund trong một request               |
| `type`               | VARCHAR   | FULL | PARTIAL                                    |
| `initiated_by`       | VARCHAR   | BUYER | SELLER | SYSTEM                           |
| `refund_reason_type` | VARCHAR   | BUYER_REQUEST | RETURN_TO_SENDER | ADMIN_OVERRIDE |
| `amount`             | DECIMAL   | Số tiền hoàn lại                                  |
| `reason`             | VARCHAR   | Lý do hoàn                                        |
| `status`             | VARCHAR   | PENDING | SUCCESS | FAILED | REJECTED             |
| `evidence_images`    | JSONB     | Mảng ảnh bằng chứng (MinIO)                       |
| `reject_reason`      | VARCHAR   | Lý do từ chối                                     |
| `admin_note`         | TEXT      | Ghi chú admin                                     |
| `reviewed_by`        | BIGINT    | FK → ADMINS.id                                    |
| `reviewed_at`        | TIMESTAMP | Thời điểm duyệt/từ chối                           |
| `refund_ref`         | VARCHAR   | Stripe refund ID (re_xxx)                         |
| `raw_response`       | JSONB     | Payload từ Stripe                                 |
| `created_at`         | TIMESTAMP | Thời điểm tạo                                     |
| `updated_at`         | TIMESTAMP | Cập nhật cuối                                     |

---

### REFUND_ITEMS

Chi tiết hoàn tiền (từng sản phẩm)

| Cột                      | Kiểu      | Ghi chú                            |
| ------------------------ | --------- | ---------------------------------- |
| `id`                     | BIGSERIAL | Primary Key                        |
| `refund_id`              | BIGINT    | FK → REFUNDS.id                    |
| `item_id`                | BIGINT    | FK → ORDER_ITEMS.id                |
| `quantity`               | INT       | Số lượng hoàn                      |
| `refund_amount`          | DECIMAL   | Số tiền hoàn cho item              |
| `item_reason`            | VARCHAR   | Lý do hoàn riêng                   |
| `status`                 | VARCHAR   | PENDING | SUCCESS | FAILED         |
| `return_tracking_number` | VARCHAR   | Mã vận đơn hoàn hàng               |
| `carrier`                | VARCHAR   | Đơn vị vận chuyển trả hàng         |
| `returned_at`            | TIMESTAMP | Thời điểm Seller xác nhận nhận lại |

---

## 8. Notifications

### MG_NOTIFICATIONS (MongoDB)

Thông báo cho Buyer, Seller, Admin

| Cột          | Kiểu      | Ghi chú                           |
| ------------ | --------- | --------------------------------- |
| `id`         | VARCHAR   | PK, Mongo ObjectId                |
| `user_id`    | BIGINT    | FK → USERS.id                     |
| `title`      | VARCHAR   | Tiêu đề thông báo                 |
| `body`       | TEXT      | Nội dung thông báo                |
| `type`       | VARCHAR   | Loại thông báo                    |
| `metadata`   | JSONB     | Dữ liệu bổ sung                   |
| `is_read`    | BOOLEAN   | Đã đọc hay chưa                   |
| `created_at` | TIMESTAMP | Thời điểm tạo (TTL Index 90 ngày) |

---

## 9. Infrastructure & Messaging

### OUTBOX_EVENTS

Event Outbox Pattern (cho eventual consistency)

| Cột            | Kiểu      | Ghi chú                      |
| -------------- | --------- | ---------------------------- |
| `id`           | BIGSERIAL | Primary Key                  |
| `topic`        | VARCHAR   | Tên topic/event              |
| `payload`      | JSONB     | Nội dung event               |
| `status`       | VARCHAR   | PENDING | PROCESSED | FAILED |
| `retry_count`  | INT       | Số lần retry                 |
| `processed_at` | TIMESTAMP | Thời điểm xử lý              |
| `created_at`   | TIMESTAMP | Thời điểm tạo                |
| `updated_at`   | TIMESTAMP | Cập nhật cuối                |

---

### FAILED_EVENTS

Lưu trữ event/task lỗi để xử lý thủ công

| Cột             | Kiểu      | Ghi chú                                         |
| --------------- | --------- | ----------------------------------------------- |
| `id`            | BIGSERIAL | Primary Key                                     |
| `topic_or_task` | VARCHAR   | Tên topic hoặc task                             |
| `payload`       | JSONB     | Payload bị lỗi                                  |
| `error_reason`  | TEXT      | Lý do lỗi                                       |
| `retry_count`   | INT       | Số lần retry                                    |
| `status`        | VARCHAR   | PENDING | DEAD | RESOLVED | MANUAL_INTERVENTION |
| `created_at`    | TIMESTAMP | Thời điểm tạo                                   |
| `updated_at`    | TIMESTAMP | Cập nhật cuối                                   |

---

### SHEDLOCK

Distributed Lock cho scheduled jobs (ShedLock)

| Cột          | Kiểu      | Ghi chú                   |
| ------------ | --------- | ------------------------- |
| `name`       | VARCHAR   | Primary Key, tên lock     |
| `lock_until` | TIMESTAMP | Thời điểm hết lock        |
| `locked_at`  | TIMESTAMP | Thời điểm bắt đầu lock    |
| `locked_by`  | VARCHAR   | Node/thread đang giữ lock |

---

## 10. Search Index

### ES_PRODUCTS_INDEX (Elasticsearch)

Đánh chỉ mục sản phẩm cho tìm kiếm

| Cột               | Kiểu      | Ghi chú                      |
| ----------------- | --------- | ---------------------------- |
| `id`              | VARCHAR   | Mongo ObjectId — keyword     |
| `name`            | TEXT      | Tên sản phẩm — text          |
| `description`     | TEXT      | Mô tả — text                 |
| `seller_id`       | BIGINT    | ID người bán — long          |
| `seller_name`     | VARCHAR   | Tên shop — text              |
| `category_id`     | VARCHAR   | ID danh mục — keyword        |
| `category_name`   | VARCHAR   | Tên danh mục — keyword       |
| `price_min`       | DECIMAL   | Giá nhỏ nhất — double        |
| `price_max`       | DECIMAL   | Giá lớn nhất — double        |
| `stock_available` | INT       | Tồn kho khả dụng — integer   |
| `is_flash`        | BOOLEAN   | Có đang Flash Sale — boolean |
| `status`          | VARCHAR   | Trạng thái — keyword         |
| `images`          | JSONB     | Danh sách ảnh — keyword[]    |
| `attributes`      | JSONB     | Thuộc tính động — nested     |
| `tags`            | JSONB     | Tag tìm kiếm — keyword[]     |
| `created_at`      | TIMESTAMP | Thời điểm tạo — date         |
| `updated_at`      | TIMESTAMP | Cập nhật cuối — date         |

---

## 11. AI Chat Support

### CHAT_SESSIONS

Vòng đời một cuộc trò chuyện AI

| Cột               | Kiểu        | Ghi chú                               |
| ----------------- | ----------- | ------------------------------------- |
| `id`              | UUID        | Primary Key, gen_random_uuid()        |
| `user_id`         | BIGINT      | FK → USERS.id, chủ sở hữu session     |
| `status`          | ENUM        | ACTIVE | CLOSED | EXPIRED             |
| `context_summary` | TEXT        | Tóm tắt nén khi history > 50 messages |
| `created_at`      | TIMESTAMPTZ | Thời điểm tạo                         |
| `updated_at`      | TIMESTAMPTZ | Cập nhật cuối                         |
| `closed_at`       | TIMESTAMPTZ | Thời điểm đóng                        |

---

### CHAT_MESSAGES

Lịch sử hội thoại đầy đủ (gồm TOOL_CALL và TOOL_RESULT)

| Cột           | Kiểu         | Ghi chú                                                          |
| ------------- | ------------ | ---------------------------------------------------------------- |
| `id`          | UUID         | Primary Key, gen_random_uuid()                                   |
| `session_id`  | UUID         | FK → CHAT_SESSIONS.id                                            |
| `role`        | ENUM         | USER | ASSISTANT | TOOL_CALL | TOOL_RESULT                       |
| `content`     | TEXT         | Nội dung (JSON string với TOOL_CALL/TOOL_RESULT)                 |
| `tool_name`   | VARCHAR(100) | Chỉ có giá trị khi role = TOOL_CALL/TOOL_RESULT                  |
| `sequence_no` | INT          | Thứ tự tuyệt đối trong session, UNIQUE (session_id, sequence_no) |
| `tokens_used` | INT          | Chỉ có với ASSISTANT messages                                    |
| `created_at`  | TIMESTAMPTZ  | Thời điểm tạo                                                    |

---

### PENDING_CONFIRMATIONS

Human-in-the-loop cho action Mức 3

| Cột           | Kiểu        | Ghi chú                                                 |
| ------------- | ----------- | ------------------------------------------------------- |
| `id`          | UUID        | Primary Key, chính là confirm token                     |
| `session_id`  | UUID        | FK → CHAT_SESSIONS.id                                   |
| `message_id`  | UUID        | FK → CHAT_MESSAGES.id                                   |
| `user_id`     | BIGINT      | FK → USERS.id, NOT NULL                                 |
| `action_type` | ENUM        | CANCEL_ORDER | UPDATE_PROFILE | DELETE_ACCOUNT | CUSTOM |
| `payload`     | JSONB       | Dữ liệu để thực thi sau khi confirmed                   |
| `status`      | ENUM        | PENDING | CONFIRMED | REJECTED | EXPIRED                |
| `expires_at`  | TIMESTAMPTZ | now + 5 phút                                            |
| `created_at`  | TIMESTAMPTZ | Thời điểm tạo                                           |
| `resolved_at` | TIMESTAMPTZ | Thời điểm xử lý                                         |

---

### TOOL_CALL_LOGS

Audit trail bất biến (chỉ INSERT, không UPDATE/DELETE) — Partition by month

| Cột            | Kiểu         | Ghi chú                              |
| -------------- | ------------ | ------------------------------------ |
| `id`           | UUID         | Primary Key, gen_random_uuid()       |
| `session_id`   | UUID         | FK → CHAT_SESSIONS.id                |
| `message_id`   | UUID         | FK → CHAT_MESSAGES.id                |
| `user_id`      | BIGINT       | FK → USERS.id, NOT NULL              |
| `tool_name`    | VARCHAR(100) | Tên tool được gọi                    |
| `input_params` | JSONB        | Tham số đầu vào                      |
| `output`       | JSONB        | Kết quả trả về                       |
| `status`       | ENUM         | SUCCESS | FAILED | BLOCKED | TIMEOUT |
| `duration_ms`  | INT          | Thời gian xử lý                      |
| `risk_level`   | SMALLINT     | 1 / 2 / 3                            |
| `created_at`   | TIMESTAMPTZ  | Thời điểm tạo                        |

---

### OUTBOX_EVENTS (AI Chat)

Event Outbox Pattern cho Kafka fallback khi publish thất bại

| Cột             | Kiểu         | Ghi chú                              |
| --------------- | ------------ | ------------------------------------ |
| `id`            | UUID         | Primary Key, gen_random_uuid()       |
| `event_type`    | VARCHAR(100) | Loại event                           |
| `payload`       | JSONB        | Nội dung event                       |
| `status`        | ENUM         | PENDING | PROCESSING | DONE | FAILED |
| `retry_count`   | SMALLINT     | Số lần retry, mặc định 0             |
| `error_message` | TEXT         | Lỗi nếu có                           |
| `created_at`    | TIMESTAMPTZ  | Thời điểm tạo                        |
| `processed_at`  | TIMESTAMPTZ  | Thời điểm xử lý                      |

> Partial index: `WHERE status = 'PENDING'` để query nhanh các event chưa xử lý.

---

### Redis Keys (AI Chat)

| Key                   | TTL     | Mục đích                                  |
| --------------------- | ------- | ----------------------------------------- |
| `rate:{userId}`       | 60s     | Rate limit counter (20 req/phút)          |
| `tool:rate:{userId}`  | 60s     | Rate limit riêng cho Tool calls (10/phút) |
| `ctx:{sessionId}`     | 30 phút | Cache 20 messages gần nhất                |
| `pending:{confirmId}` | 5 phút  | Fast lookup khi user bấm confirm          |
| `buf:{sessionId}`     | 10 phút | Buffer 20 SP từ PageIndex cho "Xem thêm"  |
| `tool:cache:{hash}`   | 60s     | Cache kết quả Tool đọc (Mức 1)            |

---

## Nhóm Bảng (Table Groups)

1. **identity** - Người dùng & hồ sơ
2. **catalog** - Sản phẩm & danh mục
3. **orders** - Đơn hàng
4. **payments** - Thanh toán & transfers
5. **flash_sale** - Flash Sale
6. **cart** - Giỏ hàng
7. **notifications** - Thông báo
8. **infrastructure** - Messaging & locks
9. **search** - Elasticsearch index
10. **ai_chat** - AI Chat Support

---

## Ghi Chú Quan Trọng

- **Optimistic Locking**: Sử dụng `version` column để tránh update conflict
- **Outbox Pattern**: OUTBOX_EVENTS + FAILED_EVENTS cho eventual consistency
- **ShedLock**: Distributed lock cho scheduled jobs
- **MongoDB**: Cart, Categories, Products, Variants, Inventories, Notifications
- **PostgreSQL**: Users, Orders, Payments, AI Chat
- **Elasticsearch**: Full-text search cho sản phẩm
- **MinIO**: Lưu trữ ảnh (URLs trong IMAGES table)

---

*Tài liệu này được cập nhật ngày 2026-05-06*