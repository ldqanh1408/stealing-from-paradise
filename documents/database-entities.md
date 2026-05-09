# E-Commerce Database Schema (Cập nhật 2026-05-09)

## Mục lục
1. [Media & Images](#1-media--images)
2. [Users & Identity](#2-users--identity)
3. [Catalog – Categories & Products](#3-catalog--categories--products)
4. [Cart](#4-cart)
5. [Flash Sales](#5-flash-sales)
6. [Orders](#6-orders)
7. [Payments & Transfers](#7-payments--transfers)
8. [Notifications](#8-notifications)
9. [Infrastructure & Messaging](#9-infrastructure--messaging)
10. [Search Index](#10-search-index)
11. [AI Chat Support](#11-ai-chat-support)

---

## 1. Media & Images

Lưu trữ tại **MinIO** (object storage), bucket `products-media`.

| Collection | Key | Ghi chú |
|------------|-----|---------|
| `product_images` | `products/{seller_id}/{product_id}/{uuid}-{type}.{ext}` | Ảnh gốc |
| `product_images` | `products/{seller_id}/{product_id}/{uuid}-{type}_thumb.{ext}` | Thumbnail |
| `product_images` | `products/{seller_id}/{product_id}/{uuid}-{type}_small.{ext}` | Ảnh danh sách |

---

## 2. Users & Identity

### USERS

| Cột          | Kiểu      | Ghi chú |
|--------------|-----------|---------|
| `id`         | BIGSERIAL | Primary Key |
| `username`   | VARCHAR   | Unique |
| `email`      | VARCHAR   | Unique |
| `phone`      | VARCHAR   | Unique |
| `password`   | VARCHAR   | Bcrypt |
| `full_name`  | VARCHAR   | |
| `status`     | VARCHAR   | ACTIVE / LOCKED |
| `role`       | VARCHAR   | BUYER / SELLER / ADMIN (mặc định BUYER) |
| `version`    | INT       | Optimistic locking (0) |
| `created_at` | TIMESTAMP | |
| `updated_at` | TIMESTAMP | |

**Index:** `idx_users_role` ON users(role)

### ROLES

| Cột          | Kiểu      | Ghi chú |
|--------------|-----------|---------|
| `id`         | BIGSERIAL | PK |
| `user_id`    | BIGINT    | FK → USERS.id, ON DELETE CASCADE |
| `role_name`  | VARCHAR   | BUYER / SELLER / ADMIN |
| `created_at` | TIMESTAMP | |
| `updated_at` | TIMESTAMP | |

### CUSTOMERS

| Cột          | Kiểu      | Ghi chú |
|--------------|-----------|---------|
| `id`         | BIGSERIAL | PK |
| `user_id`    | BIGINT    | FK → USERS.id, UNIQUE |
| `created_at` | TIMESTAMP | |
| `updated_at` | TIMESTAMP | |

### SELLERS

| Cột          | Kiểu      | Ghi chú |
|--------------|-----------|---------|
| `id`         | BIGSERIAL | PK |
| `user_id`    | BIGINT    | FK → USERS.id, UNIQUE |
| `created_at` | TIMESTAMP | |
| `updated_at` | TIMESTAMP | |

### ADMINS

| Cột          | Kiểu      | Ghi chú |
|--------------|-----------|---------|
| `id`         | BIGSERIAL | PK |
| `user_id`    | BIGINT    | FK → USERS.id, UNIQUE |
| `created_at` | TIMESTAMP | |
| `updated_at` | TIMESTAMP | |

### ADDRESSES

| Cột            | Kiểu      | Ghi chú |
|---------------|-----------|---------|
| `id`           | BIGSERIAL | PK |
| `user_id`      | BIGINT    | FK → USERS.id |
| `province_id`  | INT       | Mã tỉnh/thành |
| `district_id`  | INT       | Mã quận/huyện |
| `full_address` | TEXT      | |
| `is_default`   | BOOLEAN   | |
| `created_at`   | TIMESTAMP | |
| `updated_at`   | TIMESTAMP | |

---

## 3. Catalog – Categories & Products

Tất cả bảng catalog chuyển sang **PostgreSQL** (không dùng MongoDB). Các bảng cũ `MG_*` được thay thế hoàn toàn.

### CATEGORY

Danh mục đa cấp – tự tham chiếu.

| Cột          | Kiểu      | Ghi chú |
|--------------|-----------|---------|
| `id`         | UUID      | PK (gen_random_uuid()) |
| `parent_id`  | UUID      | FK → category.id, NULL = root |
| `name`       | VARCHAR(255) | NOT NULL |
| `slug`       | VARCHAR(255) | UNIQUE |
| `description`| TEXT      | |
| `image_url`  | TEXT      | |
| `sort_order` | INT       | DEFAULT 0 |
| `is_active`  | BOOLEAN   | DEFAULT TRUE |
| `created_at` | TIMESTAMP | |
| `updated_at` | TIMESTAMP | |

**Index:** idx_category_parent, idx_category_slug

### PRODUCT

| Cột          | Kiểu      | Ghi chú |
|--------------|-----------|---------|
| `id`         | UUID      | PK |
| `category_id`| UUID      | FK → category.id |
| `seller_id`  | UUID      | (không FK cứng) |
| `name`       | VARCHAR(500) | |
| `slug`       | VARCHAR(500) | UNIQUE |
| `description`| TEXT      | Rich text / HTML |
| `attributes` | JSONB     | Thuộc tính dạng key-value |
| `status`     | VARCHAR(50) | active / out_of_stock / inactive |
| `created_at` | TIMESTAMP | |
| `updated_at` | TIMESTAMP | |

**Index:** idx_product_category, idx_product_seller, idx_product_status, idx_product_slug, GIN on attributes

### PRODUCT_VARIANT

| Cột                 | Kiểu          | Ghi chú |
|---------------------|---------------|---------|
| `id`                | UUID          | PK |
| `product_id`        | UUID          | FK → product.id ON DELETE CASCADE |
| `variant_code`      | VARCHAR(100)  | UNIQUE (mã nội bộ seller) |
| `variant_name`      | VARCHAR(255)  | Tên nhóm biến thể |
| `variant_attributes`| JSONB         | e.g. {"color":"Đen","size":"M"} |
| `price`             | DECIMAL(18,2) | NOT NULL |
| `original_price`    | DECIMAL(18,2) | Giá gốc (gạch chéo) |
| `stock_quantity`    | INT           | DEFAULT 0 |
| `status`            | VARCHAR(50)   | active / out_of_stock / inactive |
| `version`           | INT           | Optimistic lock (DEFAULT 1) |
| `image_url`         | TEXT          | Ảnh nhanh cho variant |
| `created_at`        | TIMESTAMP     | |
| `updated_at`        | TIMESTAMP     | |

**Index:** idx_variant_product, idx_variant_status, idx_variant_price, GIN on variant_attributes

### PRODUCT_IMAGE

| Cột         | Kiểu  | Ghi chú |
|-------------|-------|---------|
| `id`        | UUID  | PK |
| `product_id`| UUID  | FK → product.id ON DELETE CASCADE |
| `variant_id`| UUID  | FK → product_variant.id ON DELETE SET NULL (NULL = ảnh chung) |
| `url`       | TEXT  | URL MinIO |
| `sort_order`| INT   | DEFAULT 0 (nhỏ nhất là ảnh chính) |
| `created_at`| TIMESTAMP | |

**Index:** idx_product_image_product, idx_product_image_variant

### STOCK_RESERVATION

| Cột         | Kiểu          | Ghi chú |
|-------------|---------------|---------|
| `id`        | UUID          | PK |
| `variant_id`| UUID          | FK → product_variant.id |
| `session_id`| VARCHAR(100)  | Checkout session ID |
| `quantity`  | INT           | |
| `status`    | VARCHAR(50)   | pending / confirmed / released |
| `expires_at`| TIMESTAMP     | NOW() + 15 phút |
| `created_at`| TIMESTAMP     | |
| `updated_at`| TIMESTAMP     | |

**Index:** idx_reservation_variant, idx_reservation_session, idx_reservation_status, idx_reservation_expires

---

## 4. Cart

### CART

| Cột          | Kiểu      | Ghi chú |
|--------------|-----------|---------|
| `id`         | UUID      | PK |
| `customer_id`| UUID      | UNIQUE (1 khách – 1 cart) |
| `status`     | VARCHAR(50)| active |
| `created_at` | TIMESTAMP | |
| `updated_at` | TIMESTAMP | |

### CART_ITEM

| Cột                        | Kiểu          | Ghi chú |
|----------------------------|---------------|---------|
| `id`                       | UUID          | PK |
| `cart_id`                  | UUID          | FK → cart.id ON DELETE CASCADE |
| `variant_id`               | UUID          | FK → product_variant.id |
| `quantity`                 | INT           | DEFAULT 1 |
| `price_snapshot`           | DECIMAL(18,2) | Giá lúc thêm vào |
| `variant_name_snapshot`    | VARCHAR(500)  | Tên variant snapshot |
| `variant_image_snapshot`   | TEXT          | Ảnh variant snapshot |
| `created_at`               | TIMESTAMP     | |
| `updated_at`               | TIMESTAMP     | |

**UNIQUE(cart_id, variant_id)**

**Index:** idx_cart_item_cart, idx_cart_item_variant

---

## 5. Flash Sales

### FS_SESSIONS

| Cột                      | Kiểu      | Ghi chú |
|--------------------------|-----------|---------|
| `id`                     | BIGSERIAL | PK |
| `name`                   | VARCHAR(255) | |
| `start_time`             | TIMESTAMP | |
| `end_time`               | TIMESTAMP | |
| `registration_deadline`  | TIMESTAMP | Tự tính = start_time - 15 phút |
| `discount_percentage`    | DECIMAL(5,2) | % giảm chung cho session |
| `status`                 | VARCHAR(20) | UPCOMING / ACTIVE / ENDED |
| `deleted_at`             | TIMESTAMP | Soft delete |
| `created_at`             | TIMESTAMP | |
| `updated_at`             | TIMESTAMP | |

**Constraint:** chk_status, chk_time, chk_registration_deadline, chk_discount

**Index:** idx_fs_sessions_status, idx_fs_sessions_time, idx_fs_sessions_registration_deadline

### FS_ITEMS

| Cột               | Kiểu          | Ghi chú |
|-------------------|---------------|---------|
| `id`              | BIGSERIAL     | PK |
| `session_id`      | BIGINT        | FK → fs_sessions.id |
| `product_id`      | UUID          | Product tham gia (không phải SKU) |
| `discount_applied`| DECIMAL(5,2)  | % giảm (tự động lấy từ session nếu không có) |
| `seller_id`       | UUID          | |
| `registered_at`   | TIMESTAMP     | |
| `created_at`      | TIMESTAMP     | |
| `updated_at`      | TIMESTAMP     | |

**UNIQUE(session_id, product_id)**

**Index:** idx_fs_items_session, idx_fs_items_product, idx_fs_items_seller

> **Ghi chú quan trọng:**
> - Giá flash sale được tính **dynamic** khi buyer mua: `flash_price = sku.price * (1 - discount_applied/100)`.
> - Không còn trường `flash_price`, `flash_stock`, `status` phức tạp – đăng ký là tự động duyệt.
> - Tồn kho vẫn dùng `product_variant.stock_quantity` và cơ chế reservation khi checkout.

### FS_REMINDERS (giữ nguyên)

| Cột          | Kiểu      | Ghi chú |
|--------------|-----------|---------|
| `id`         | BIGSERIAL | PK |
| `customer_id`| BIGINT    | FK → customers.id |
| `session_id` | BIGINT    | FK → fs_sessions.id |
| `created_at` | TIMESTAMP | |

---

## 6. Orders

> Các bảng ORDER giữ nguyên cấu trúc chính, tham chiếu đến `product_variant.id` (UUID) thay vì MongoDB ObjectId. Trường `order_code` là mã hiển thị dễ đọc.

### PARENT_ORDERS

| Cột              | Kiểu          | Ghi chú |
|------------------|---------------|---------|
| `id`             | BIGSERIAL     | PK |
| `customer_id`    | BIGINT        | FK → customers.id |
| `session_id`     | VARCHAR(100)  | FK → stock_reservation.session_id, UNIQUE |
| `total_amt`      | DECIMAL(18,2) | |
| `final_amt`      | DECIMAL(18,2) | |
| `status`         | VARCHAR(50)   | PENDING_PAYMENT / PAID / CANCELLED |
| `created_at`     | TIMESTAMP     | |
| `updated_at`     | TIMESTAMP     | |

**Index:** idx_parent_orders_customer, idx_parent_orders_session, idx_parent_orders_status

### ORDERS

| Cột                 | Kiểu      |
|---------------------|-----------|
| `id`                | BIGSERIAL |
| `parent_order_id`   | BIGINT    |
| `seller_id`         | BIGINT    |
| `order_code`        | VARCHAR   |
| `customer_id`       | BIGINT    |
| `total_amt`         | DECIMAL   |
| `final_amt`         | DECIMAL   |
| `net_payout_amount` | DECIMAL   |
| `status`            | VARCHAR   |
| `cancelled_by`      | VARCHAR   |
| `cancel_reason`     | TEXT      |
| `shipping_address`  | JSONB     |
| `shipping_deadline` | TIMESTAMP |
| `tracking_number`   | VARCHAR   | |
| `carrier`           | VARCHAR   | |
| `paid_at`           | TIMESTAMP |
| `return_window_end` | TIMESTAMP |
| `shipped_at`        | TIMESTAMP |
| `delivered_at`      | TIMESTAMP |
| `created_at`        | TIMESTAMP |
| `updated_at`        | TIMESTAMP |

### ORDER_ITEMS

| Cột                 | Kiểu      | Ghi chú |
|---------------------|-----------|---------|
| `id`                | BIGSERIAL | PK |
| `order_id`          | BIGINT    | FK → orders.id |
| `sku_code`          | VARCHAR   | Mã SKU snapshot |
| `variant_id`        | UUID      | FK → product_variant.id |
| `name_snapshot`     | VARCHAR   | |
| `image_snapshot`    | VARCHAR   | |
| `price_snapshot`    | DECIMAL   | |
| `quantity`          | INT       | |
| `fs_item_id`        | BIGINT    | FK → fs_items.id (nullable) |
| `created_at`        | TIMESTAMP | |

---

## 7. Payments & Transfers

(giữ nguyên hoàn toàn, không thay đổi so với thiết kế cũ)

### SELLER_STRIPE_ACCOUNTS

| Cột                         | Kiểu      |
|-----------------------------|-----------|
| `id`                        | BIGSERIAL |
| `seller_id`                 | BIGINT    |
| `stripe_account_id`         | VARCHAR   |
| `account_status`            | VARCHAR   |
| `charges_enabled`           | BOOLEAN   |
| `payouts_enabled`           | BOOLEAN   |
| `details_submitted`         | BOOLEAN   |
| `onboarding_url`            | TEXT      |
| `express_dashboard_url`     | TEXT      |
| `onboarding_url_expires_at` | TIMESTAMP |
| `created_at`                | TIMESTAMP |
| `updated_at`                | TIMESTAMP |

### TRANSACTIONS

| Cột                      | Kiểu      |
|--------------------------|-----------|
| `id`                     | BIGSERIAL |
| `parent_order_id`        | BIGINT    |
| `amount`                 | DECIMAL   |
| `trans_ref`              | VARCHAR   |
| `stripe_transfer_id`     | VARCHAR   |
| `application_fee_amount` | DECIMAL   |
| `stripe_connect_mode`    | VARCHAR   |
| `status`                 | VARCHAR   |
| `raw_response`           | JSONB     |
| `pay_at`                 | TIMESTAMP |
| `created_at`             | TIMESTAMP |
| `updated_at`             | TIMESTAMP |

### SELLER_TRANSFERS

| Cột                     | Kiểu      |
|-------------------------|-----------|
| `id`                    | BIGSERIAL |
| `order_id`              | BIGINT    |
| `seller_id`             | BIGINT    |
| `transaction_id`        | BIGINT    |
| `transfer_amount`       | DECIMAL   |
| `refunded_amount`       | DECIMAL   |
| `stripe_transfer_id`    | VARCHAR   |
| `delivered_at`          | TIMESTAMP |
| `net_payout_amount`     | DECIMAL   |
| `payout_eligible_at`    | TIMESTAMP |
| `platform_commission_amt`| DECIMAL  |
| `payout_at`             | TIMESTAMP |
| `payout_retry_count`    | INTEGER   |
| `status`                | VARCHAR   |
| `created_at`            | TIMESTAMP |
| `updated_at`            | TIMESTAMP |

### REFUNDS

| Cột              | Kiểu      |
|------------------|-----------|
| `id`             | BIGSERIAL |
| `transaction_id` | BIGINT    |
| `order_id`       | BIGINT    |
| `group_ref`      | UUID      |
| `type`           | VARCHAR   |
| `amount`         | DECIMAL   |
| `status`         | VARCHAR   |
| `reason`         | VARCHAR   |
| `refund_ref`     | VARCHAR   |
| `raw_response`   | JSONB     |
| `created_at`     | TIMESTAMP |
| `updated_at`     | TIMESTAMP |

### REFUND_ITEMS

| Cột                      | Kiểu      |
|--------------------------|-----------|
| `id`                     | BIGSERIAL |
| `refund_id`              | BIGINT    |
| `item_id`                | BIGINT    |
| `refund_amount`          | DECIMAL   |
| `reason`                 | VARCHAR   |
| `status`                 | VARCHAR   |
| `evidence_images`        | JSONB     |
| `reject_reason`          | VARCHAR   |
| `reviewed_at`            | TIMESTAMP |
| `return_tracking_number` | VARCHAR   |
| `carrier`                | VARCHAR   |
| `returned_at`            | TIMESTAMP |

---

## 8. Notifications

### MG_NOTIFICATIONS (vẫn dùng MongoDB)

| Cột          | Kiểu      |
|--------------|-----------|
| `id`         | VARCHAR   |
| `user_id`    | BIGINT    |
| `title`      | VARCHAR   |
| `body`       | TEXT      |
| `type`       | VARCHAR   |
| `metadata`   | JSONB     |
| `is_read`    | BOOLEAN   |
| `created_at` | TIMESTAMP |

---

## 9. Infrastructure & Messaging

Giữ nguyên các bảng OUTBOX_EVENTS, FAILED_EVENTS (nếu có), SHEDLOCK (distributed lock) như thiết kế cũ, không thay đổi.

---

## 10. Search Index (Elasticsearch)

**Index name:** `skus`  
Kiến trúc **SKU-first** với field collapsing theo `product_id`.

### Mapping tóm tắt

| Field               | Type    |
|---------------------|---------|
| `sku_id`            | keyword |
| `product_id`        | keyword |
| `seller_id`         | keyword |
| `product_name`      | text (phân tích tiếng Việt) |
| `product_slug`      | keyword |
| `product_description`| text |
| `product_attributes`| object (dynamic) |
| `category_id`       | keyword |
| `category_path`     | keyword |
| `variant_name`      | keyword |
| `variant_attributes`| object (dynamic) |
| `sku_code`          | keyword |
| `price`             | double |
| `original_price`    | double |
| `has_discount`      | boolean |
| `discount_pct`      | integer |
| `flash_session_id`  | keyword |
| `stock_status`      | keyword (in_stock / out_of_stock) |
| `product_status`    | keyword |
| `sku_status`        | keyword |
| `is_active`         | boolean |
| `thumbnail_url`     | keyword (index: false) |
| `sku_image_url`     | keyword (index: false) |
| `seller_name`       | text |
| `product_created_at`| date |
| `sku_updated_at`    | date |

**Nguyên tắc:** Mỗi variant (SKU) là một document. Khi hiển thị listing, dùng field collapse theo `product_id` và inner_hits để lấy SKU đại diện (giá thấp nhất, còn hàng). Đồng bộ dữ liệu qua event từ Product Service (partial update).

---

## 11. AI Chat Support

(giữ nguyên toàn bộ bảng chat_sessions, chat_messages, pending_confirmations, tool_call_logs, outbox_events_ai như thiết kế cũ, không thay đổi)

---

## Nhóm bảng (Table Groups)

| Nhóm           | Bảng chính (mới)                               |
|----------------|------------------------------------------------|
| **identity**   | users, roles, customers, sellers, admins, addresses |
| **catalog**    | category, product, product_variant, product_image, stock_reservation |
| **cart**       | cart, cart_item                                |
| **flash_sale** | fs_sessions, fs_items, fs_reminders            |
| **orders**     | parent_orders, orders, order_items             |
| **payments**   | seller_stripe_accounts, transactions, seller_transfers, refunds, refund_items |
| **notifications** | mg_notifications (MongoDB)                  |
| **search**     | Elasticsearch index `skus`                     |
| **ai_chat**    | chat_sessions, chat_messages, pending_confirmations, tool_call_logs, outbox_events_ai |

---

*Cập nhật ngày: 2026-05-09*