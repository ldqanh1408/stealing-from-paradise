# Product Service — Database Tables

> Stack: PostgreSQL · MongoDB · Elasticsearch
> Cập nhật: 2026-05-06

---

## IMAGES (PostgreSQL)

Lưu trữ metadata ảnh sản phẩm và avatar trên MinIO

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | UUID | Primary Key, gen_random_uuid() |
| `bucket` | VARCHAR | Bucket trên MinIO |
| `object_key` | VARCHAR | Object key trên MinIO |
| `content_type` | VARCHAR | MIME type |
| `file_size` | BIGINT | Kích thước file (bytes) |
| `url` | TEXT | Public URL (pre-signed hoặc CDN) |
| `uploaded_by` | BIGINT | FK → USERS.id |
| `created_at` | TIMESTAMP | Thời điểm tạo |

---

## MG_CATEGORIES (MongoDB)

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

## MG_PRODUCTS (MongoDB)

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

## MG_PRODUCT_IMAGES (PostgreSQL)

Liên kết hình ảnh sản phẩm

| Cột | Kiểu | Ghi chí |
|-----|------|--------|
| `id` | BIGSERIAL | Primary Key |
| `product_id` | VARCHAR | FK → MG_PRODUCTS.id |
| `image_id` | UUID | FK → IMAGES.id |
| `is_main` | BOOLEAN | Ảnh chính (default: false) |
| `sort_order` | INT | Thứ tự sắp xếp (default: 0) |

---

## MG_PRODUCT_VARIANTS (MongoDB)

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

## MG_INVENTORIES (MongoDB)

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

## MG_CARTS (MongoDB)

Giỏ hàng (1:1 với CUSTOMERS)

| Cột | Kiểu | Ghi chí |
|-----|------|--------|
| `id` | VARCHAR | PK, Mongo ObjectId |
| `customer_id` | BIGINT | FK → CUSTOMERS.id, UNIQUE |
| `total_items` | INT | Tổng số món hàng |
| `created_at` | TIMESTAMP | Thời điểm tạo |
| `updated_at` | TIMESTAMP | Thời điểm cập nhật |

---

## MG_CART_ITEMS (MongoDB)

Chi tiết giỏ hàng

| Cột | Kiểu | Ghi chí |
|-----|------|--------|
| `id` | VARCHAR | PK, Mongo ObjectId |
| `cart_id` | VARCHAR | FK → MG_CARTS.id |
| `variant_id` | VARCHAR | FK → MG_PRODUCT_VARIANTS.id |
| `sku_code` | VARCHAR | Mã SKU |
| `fs_item_id` | BIGINT | FK → FS_ITEMS.id, nullable |
| `price_snapshot` | DECIMAL | Giá tại thời điểm thêm vào |
| `is_selected` | BOOLEAN | Chọn để checkout (mặc định TRUE) |
| `quantity` | INT | Số lượng |
| `added_at` | TIMESTAMP | Thời điểm thêm |
