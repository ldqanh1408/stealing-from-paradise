# Product Service — API Reference

> Base path: `/api/v1` → Gateway routes to `product-service:8082`
>
> Databases: MongoDB (products, categories, variants, inventories, carts) + PostgreSQL (images)

---

## Products

### GET /products/{productId}
**Chi tiết sản phẩm**

**Quyền truy cập**: Public

**Path Params:** `productId` — Mongo ObjectId

**Response 200:**
```json
{
  "success": true,
  "data": {
    "id": "64f3a...",
    "name": "Áo thun nam cotton",
    "description": "Áo thun cao cấp...",
    "seller_id": 10,
    "seller_name": "Shop Thời Trang",
    "category_id": "64f3b...",
    "category_name": "Áo thun",
    "status": "APPROVED",
    "images": [
      {
        "image_id": "uuid-abc",
        "url": "https://cdn.marketplace.vn/products/...",
        "is_main": true,
        "sort_order": 0
      }
    ],
    "variants": [
      {
        "id": "64f3c...",
        "sku_code": "SP001-S",
        "tier_name": "Màu Đen / Size S",
        "price": 250000,
        "stock_available": 100
      }
    ],
    "min_price": 250000,
    "max_price": 300000,
    "created_at": "2025-11-01T08:00:00Z"
  }
}
```

---

### POST /products
**Tạo sản phẩm mới (Seller)**

**Quyền truy cập**: JWT Required (SELLER)

**Request Body:**
```json
{
  "name": "string",              // (Required)
  "description": "string",       // (Required)
  "category_id": "string",       // Mongo ObjectId (Required)
  "images": ["string"],          // Array of image_id từ MinIO upload trước
  "attributes": {                // Tuỳ danh mục
    "brand": "Nike",
    "material": "Cotton 100%"
  }
}
```

**Response 201:** Product created (status = PENDING, chờ admin duyệt)

---

### PUT /products/{productId}
**Cập nhật sản phẩm (Seller)**

**Quyền truy cập**: JWT Required (SELLER)

**Request Body** (all optional):
```json
{
  "name": "string",
  "description": "string",
  "category_id": "string",
  "images": ["string"],
  "attributes": {}
}
```

**Response 200:** Product updated

---

### DELETE /products/{productId}
**Xóa mềm sản phẩm (Seller)**

**Quyền truy cập**: JWT Required (SELLER)

**Response 200:** Soft deleted (`deleted_at` set)

---

### GET /products
**Danh sách sản phẩm (Public)**

**Quyền truy cập**: Public

**Query Params:**

| Param | Type | Mô tả |
|-------|------|-------|
| category | string | Filter theo category_id |
| search | string | Tìm kiếm theo tên |
| page | integer | Default 0 |
| size | integer | Default 20 |

---

### GET /products/{productId}/presigned-url
**Pre-signed URL upload ảnh sản phẩm**

**Quyền truy cập**: JWT Required (SELLER)

**Query Params:**

| Param | Type | Required | Mô tả |
|-------|------|----------|-------|
| file_name | string | ✓ | Tên file gốc |
| content_type | string | ✓ | image/jpeg | image/png | image/webp |

**Response 200:**
```json
{
  "presigned_url": "https://minio.internal/...",
  "object_url": "https://cdn.marketplace.vn/products/...",
  "expires_in": 900
}
```

---

### GET /sellers/me/products
**Danh sách sản phẩm của Seller**

**Quyền truy cập**: JWT Required (SELLER)

**Query Params:** page, size

---

## Variants (Seller)

### GET /seller/products/{productId}/variants
**Danh sách variants của sản phẩm**

**Quyền truy cập**: JWT Required (SELLER)

---

### POST /seller/products/{productId}/variants
**Thêm variant mới**

**Quyền truy cập**: JWT Required (SELLER)

**Request Body:**
```json
{
  "sku_code": "string",        // Unique (Required)
  "tier_name": "string",       // VD: "Màu Đen / Size S" (Required)
  "price": "decimal",          // (Required)
  "stock": "integer"           // Số lượng tồn ban đầu (Required)
}
```

**Response 201:** Variant created

---

### PUT /seller/variants/{variantId}
**Cập nhật variant**

**Quyền truy cập**: JWT Required (SELLER)

**Request Body** (all optional):
```json
{
  "tier_name": "string",
  "price": "decimal"
}
```

**Response 200:** Variant updated

---

### DELETE /seller/variants/{variantId}
**Xóa variant**

**Quyền truy cập**: JWT Required (SELLER)

**Response 200:** Variant deleted

---

## Product Lifecycle (Seller)

### POST /seller/products/{productId}/submit
**Gửi sản phẩm chờ duyệt**

**Quyền truy cập**: JWT Required (SELLER)

**Response 200:** Product status → PENDING_REVIEW

---

### POST /seller/products/{productId}/publish
**Mở bán sản phẩm**

**Quyền truy cập**: JWT Required (SELLER)

**Response 200:** Product status → APPROVED

---

### POST /seller/products/{productId}/unpublish
**Tạm ẩn sản phẩm**

**Quyền truy cập**: JWT Required (SELLER)

**Response 200:** Product status → DRAFT

---

## Inventory

### GET /inventory/{skuCode}
**Thông tin tồn kho**

**Quyền truy cập**: JWT Required

**Response 200:**
```json
{
  "success": true,
  "data": {
    "sku_code": "SP001-S",
    "stock_available": 100,
    "stock_locked": 5,
    "last_updated": "2025-11-01T08:00:00Z"
  }
}
```

---

### PUT /inventory/{skuCode}/restock
**Nhập thêm hàng**

**Quyền truy cập**: JWT Required (SELLER)

**Request Body:**
```json
{
  "quantity": "integer",      // Số lượng nhập thêm (> 0) (Required)
  "note": "string"            // Ghi chú nhập hàng
}
```

**Response 200:**
```json
{
  "success": true,
  "data": {
    "sku_code": "SP001-S",
    "stock_available": 150,
    "stock_locked": 5
  },
  "message": "Nhập thêm hàng thành công"
}
```

---

### POST /seller/inventory/adjust
**Điều chỉnh tồn kho**

**Quyền truy cập**: JWT Required (SELLER)

**Request Body:**
```json
{
  "sku_code": "string",        // (Required)
  "adjust_type": "string",     // ADD | REMOVE | SET (Required)
  "quantity": "integer",       // Số lượng (Required)
  "reason": "string"           // Lý do điều chỉnh
}
```

**Response 200:**
```json
{
  "success": true,
  "data": {
    "sku_code": "SP001-S",
    "stock_available": 90,
    "stock_locked": 5
  },
  "message": "Điều chỉnh tồn kho thành công"
}
```

---

### GET /seller/inventory/{skuCode}/logs
**Lịch sử biến động tồn kho (Seller)**

**Quyền truy cập**: JWT Required (SELLER)

**Query Params:**

| Param | Type | Mô tả |
|-------|------|-------|
| from_date | date | ISO 8601 |
| to_date | date | ISO 8601 |
| page | integer | Default 0 |
| size | integer | Default 20 |

**Response 200:**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "sku_code": "SP001-S",
        "change": 50,
        "type": "RESTOCK",
        "note": "Nhập thêm hàng đợt 2",
        "created_at": "2025-11-01T08:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "total_elements": 1,
    "total_pages": 1,
    "last": true
  }
}
```

---

## Categories

### GET /categories
**Danh sách danh mục**

**Quyền truy cập**: Public

**Response 200:**
```json
{
  "success": true,
  "data": [
    {
      "category_id": "64f3b...",
      "name": "Thời Trang Nam",
      "slug": "thoi-trang-nam",
      "parent_id": null,
      "level": 0,
      "children": [
        {
          "category_id": "64f3b...",
          "name": "Áo thun",
          "slug": "ao-thun",
          "parent_id": "...",
          "level": 1
        }
      ]
    }
  ]
}
```

---

### POST /admin/categories
**Tạo danh mục (Admin)**

**Quyền truy cập**: JWT Required (ADMIN)

**Request Body:**
```json
{
  "name": "string",            // (Required)
  "slug": "string",            // (Required, unique)
  "parent_id": "string"        // ObjectId của cha, null = root
}
```

**Response 201:** Category created

---

### PUT /admin/categories/{categoryId}
**Cập nhật danh mục**

**Quyền truy cập**: JWT Required (ADMIN)

**Request Body** (all optional):
```json
{
  "name": "string",
  "slug": "string"
}
```

---

### DELETE /admin/categories/{categoryId}
**Xóa danh mục**

**Quyền truy cập**: JWT Required (ADMIN)

**Errors:** 400 (category has children/products)

---

## Cart

### GET /cart
**Lấy giỏ hàng**

**Quyền truy cập**: JWT Required

**Response 200:**
```json
{
  "success": true,
  "data": {
    "cart_id": "64f3d...",
    "total_items": 3,
    "groups": [
      {
        "seller_id": 10,
        "seller_name": "Shop ABC",
        "items": [
          {
            "item_id": "64f3e...",
            "sku_code": "SP001-S",
            "product_name": "Áo thun nam",
            "image": "https://cdn.marketplace.vn/...",
            "price": 250000,
            "quantity": 2
          }
        ]
      }
    ]
  }
}
```

---

### POST /cart/items
**Thêm vào giỏ hàng**

**Quyền truy cập**: JWT Required

**Request Body:**
```json
{
  "sku_code": "string",     // (Required)
  "quantity": "integer"     // 1-99 (Required)
}
```

**Response 201:** Item added

---

### PUT /cart/items/{itemId}
**Cập nhật số lượng**

**Quyền truy cập**: JWT Required

**Request Body:**
```json
{
  "quantity": "integer"    // 1-99 (Required)
}
```

---

### DELETE /cart/items/{itemId}
**Xóa item khỏi giỏ**

**Quyền truy cập**: JWT Required

---

### DELETE /cart
**Xóa toàn bộ giỏ hàng**

**Quyền truy cập**: JWT Required

---

## Admin — Products

### GET /admin/products/pending
**Sản phẩm chờ duyệt**

**Quyền truy cập**: JWT Required (ADMIN)

**Query Params:**

| Param | Type | Mô tả |
|-------|------|-------|
| category_id | string | Filter |
| seller_id | long | Filter |
| page | integer | Default 0 |
| size | integer | Default 20 |

---

### POST /admin/products/{productId}/approve
**Duyệt sản phẩm**

**Quyền truy cập**: JWT Required (ADMIN)

**Request Body:**
```json
{
  "note": "string"    // Ghi chú duyệt
}
```

**Response 200:** Product approved

---

### POST /admin/products/{productId}/reject
**Từ chối sản phẩm**

**Quyền truy cập**: JWT Required (ADMIN)

**Request Body:**
```json
{
  "reason": "string",    // (Required)
  "note": "string"
}
```

**Response 200:** Product rejected
