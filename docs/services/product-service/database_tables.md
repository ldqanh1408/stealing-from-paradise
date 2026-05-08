# Product Service — Database Design

> **Stack:** PostgreSQL (images only) + MongoDB (catalog, cart) + Elasticsearch + Redis
> **Cập nhật:** 2026-05-06
> **Ghi chú:** Tài liệu này mô tả thiết kế database thực tế của product-service. Khác với thiết kế ban đầu (PostgreSQL hoàn toàn), kiến trúc hiện tại dùng **MongoDB** cho catalog và cart để đạt scalability cao, và **PostgreSQL** chỉ cho bảng `IMAGES` (media bridge).

---

## Tổng Quan

Product Service quản lý toàn bộ vòng đời của sản phẩm: từ lúc seller tạo sản phẩm, qua kiểm duyệt, đến khi hiển thị cho khách mua. Service này cũng tích hợp giỏ hàng (cart) để tránh phức tạp khi seller thay đổi giá variant ảnh hưởng đến dữ liệu hiển thị cho khách.

### Stack

| Database | Technology | Tables/Collections |
|----------|-----------|-------------------|
| PostgreSQL | JDBC | `IMAGES`, `MG_PRODUCT_IMAGES` |
| MongoDB | MongoDB Driver | `MG_CATEGORIES`, `MG_PRODUCTS`, `MG_PRODUCT_VARIANTS`, `MG_INVENTORIES`, `MG_CARTS`, `MG_CART_ITEMS` |
| Elasticsearch | — | `ES_PRODUCTS_INDEX` (được đồng bộ từ MongoDB) |
| Redis | — | Stock cache, checkout preview tokens |

### Sơ đồ quan hệ

```
CATEGORY (MongoDB — cây đa cấp, tự tham chiếu)
    └── PRODUCT (MongoDB — nhiều product thuộc 1 category)
            ├── PRODUCT_VARIANT (MongoDB — nhiều biến thể của 1 product)
            │     └── INVENTORY (MongoDB — 1:1 với variant qua sku_code)
            └── PRODUCT_IMAGE (PostgreSQL — ảnh sản phẩm)

CART (MongoDB — 1:1 với CUSTOMERS)
    └── CART_ITEM (MongoDB — FK sang variant, lưu price_snapshot)
```

---

## Chi tiết từng bảng/collection

### 1. IMAGES (PostgreSQL)

Lưu trữ metadata ảnh sản phẩm trên MinIO. Ảnh gốc được upload qua pre-signed URL.

```sql
CREATE TABLE images (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bucket      VARCHAR(255) NOT NULL,
    object_key  VARCHAR(512) NOT NULL,
    content_type VARCHAR(100),
    file_size   BIGINT,
    url         TEXT,
    uploaded_by BIGINT REFERENCES users(id),
    created_at  TIMESTAMP DEFAULT NOW()
);
```

### 2. MG_PRODUCT_IMAGES (PostgreSQL)

Liên kết ảnh với sản phẩm (many-to-many)

```sql
CREATE TABLE mg_product_images (
    id          BIGSERIAL PRIMARY KEY,
    product_id  VARCHAR(50) NOT NULL,  -- MongoDB ObjectId
    image_id    UUID NOT NULL REFERENCES images(id),
    is_main     BOOLEAN DEFAULT FALSE,
    sort_order  INT DEFAULT 0
);
```

### 3. MG_CATEGORIES (MongoDB)

Danh mục sản phẩm theo cây đa cấp. Một category có thể có nhiều category con thông qua `parent_id` tự tham chiếu.

```json
{
  "_id": "ObjectId",
  "name": "Áo thun",
  "slug": "ao-thun",
  "parent_id": "ObjectId | null",
  "level": 0,
  "created_at": "ISODate",
  "updated_at": "ISODate"
}
```

| Trường | Vai trò | Ghi chú |
|--------|---------|----------|
| `slug` | URL-friendly name, dùng cho SEO | Ví dụ: `ao-thun-nam` |
| `parent_id` | FK tự tham chiếu | null = root category |

---

### 4. MG_PRODUCTS (MongoDB)

Đại diện cho một sản phẩm ở cấp độ tổng quan. Một product có thể có nhiều biến thể (variant). Khách hàng nhìn thấy Product trước, sau đó mới đi vào chọn variant cụ thể.

```json
{
  "_id": "ObjectId",
  "seller_id": 42,
  "category_id": "ObjectId",
  "name": "Áo thun nam form regular",
  "description": "<p>Mô tả rich text...</p>",
  "attributes": { "material": "100% Cotton", "origin": "Việt Nam" },
  "is_flash": false,
  "status": "APPROVED",
  "reject_reason": null,
  "deleted_at": null,
  "created_at": "ISODate",
  "updated_at": "ISODate"
}
```

#### Trường `status`

| Giá trị | Ý nghĩa |
|---------|----------|
| `PENDING` | Chờ Admin duyệt |
| `APPROVED` | Đã duyệt, hiển thị trong tìm kiếm |
| `REJECTED` | Bị từ chối, có lý do |

#### Trường `attributes`

```json
// Thời trang
{ "material": "100% Cotton", "origin": "Việt Nam", "style": "Casual" }
// Điện thoại
{ "ram": "8GB", "storage": "256GB", "screen_size": "6.7 inch" }
```

---

### 5. MG_PRODUCT_VARIANTS (MongoDB)

Biến thể cụ thể của product. Mỗi variant có giá, tồn kho và bộ thuộc tính biến thể riêng. Đây là đơn vị thực sự được thêm vào giỏ hàng và mua.

```json
{
  "_id": "ObjectId",
  "product_id": "ObjectId",
  "image_id": "UUID (FK → images.id)",
  "sku_code": "SKU-001",
  "tier_name": "Màu sắc, Size",
  "price": 150000,
  "created_at": "ISODate",
  "updated_at": "ISODate"
}
```

> **Lưu ý:** `stock_quantity` được lưu trong collection `MG_INVENTORIES` (1:1 qua `sku_code`), không phải trong variant. Điều này cho phép MongoDB schema đơn giản hơn và inventory có thể được update độc lập với variant metadata.

---

### 6. MG_INVENTORIES (MongoDB)

Quản lý tồn kho theo SKU — 1:1 với MG_PRODUCT_VARIANTS qua `sku_code`.

```json
{
  "_id": "ObjectId",
  "sku_code": "SKU-001",
  "product_id": "ObjectId",
  "stock_total": 100,
  "stock_locked": 5,
  "stock_available": 95,
  "stock_flash_reserved": 20,
  "updated_at": "ISODate"
}
```

| Trường | Ý nghĩa |
|--------|----------|
| `stock_total` | Tổng tồn kho ban đầu |
| `stock_locked` | Đang giữ chỗ (đơn PENDING/PAID chưa hoàn tất) |
| `stock_available` | `stock_total - stock_locked - stock_flash_reserved` |
| `stock_flash_reserved` | Đã khóa cho Flash Sale đã duyệt |

---

### 7. MG_CARTS (MongoDB)

Giỏ hàng của khách — 1:1 với CUSTOMERS.

```json
{
  "_id": "ObjectId",
  "customer_id": 42,
  "total_items": 3,
  "created_at": "ISODate",
  "updated_at": "ISODate"
}
```

---

### 8. MG_CART_ITEMS (MongoDB)

Từng sản phẩm trong giỏ hàng. Lưu snapshot giá tại thời điểm thêm vào.

```json
{
  "_id": "ObjectId",
  "cart_id": "ObjectId",
  "variant_id": "ObjectId",
  "sku_code": "SKU-001",
  "fs_item_id": 5,
  "price_snapshot": 120000,
  "is_selected": true,
  "quantity": 2,
  "added_at": "ISODate"
}
```

| Trường | Vai trò | Ghi chú |
|--------|---------|----------|
| `price_snapshot` | Giá variant tại lúc thêm vào giỏ | So sánh với `MG_INVENTORIES` price để phát hiện thay đổi |
| `is_selected` | Chọn để checkout | Mặc định TRUE |

---

## Lưu ý về Object Storage (MinIO)

Tất cả file binary (ảnh sản phẩm) đều lưu trên MinIO hoặc S3-compatible storage. Database chỉ lưu URL trỏ đến file.

```
Flow upload ảnh sản phẩm (seller):
  Seller upload → API Gateway → MinIO
                              → trả về presigned URL
                              → Product Service lưu metadata vào images (PostgreSQL)
```

MinIO bucket: `products-media`
Object key pattern: `products/{seller_id}/{product_id}/{uuid}-{type}.{ext}`
