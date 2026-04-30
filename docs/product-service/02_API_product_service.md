# 📦 Product Service API

**Port**: `:8082`  
**Mô tả**: Sản phẩm, variant, danh mục, kho hàng, ảnh (MinIO), **giỏ hàng** (gộp từ Cart Service `:8083`)  
**Base URL**: `/api/v1`

---

## 📚 Mục Lục

1. [Product CRUD](#product-crud)
2. [Variant CRUD](#variant-crud)
3. [Product Lifecycle](#product-lifecycle)
4. [Inventory Management](#inventory-management)
5. [Inventory Query](#inventory-query)
6. [Category Management](#category-management)
7. [🛒 Cart Endpoints](#-cart-endpoints) *(gộp từ Cart Service)*

---

## Product CRUD

### POST /products
**Tạo sản phẩm mới (Seller)**

**Quyền truy cập**: JWT Required (SELLER)

**Request Body**:
```json
{
  "name": "Áo Thun Nike Air Nam",
  "description": "<p>Áo thun chất lượng cao, thoáng mát...</p>",
  "category_id": "507f1f77bcf86cd799439011",
  "attributes": {
    "brand": "Nike",
    "material": "100% Cotton",
    "size_chart": "S-M-L-XL-XXL"
  },
  "images": [
    "https://cdn.marketplace.vn/products-media/products/5/101/uuid-front.jpg",
    "https://cdn.marketplace.vn/products-media/products/5/101/uuid-back.jpg"
  ]
}
```

**Validation Rules**:
| Field | Type | Rules |
|-------|------|-------|
| name | string | 5–200 ký tự |
| description | string | Tối đa 10000 ký tự (HTML allowed) |
| category_id | string | Leaf category only |
| images | array | 1–10 URLs; JPEG/PNG/WebP |

**Response 201**:
```json
{
  "product_id": "507f1f77bcf86cd799439012",
  "seller_id": 5,
  "name": "Áo Thun Nike Air Nam",
  "category_id": "507f1f77bcf86cd799439011",
  "status": "DRAFT",
  "stock_available": 0,
  "created_at": "2026-04-15T10:00:00Z"
}
```

**Kafka Events**:
```json
{
  "topic": "product.created",
  "payload": {
    "product_id": "507f1f77bcf86cd799439012",
    "seller_id": 5,
    "name": "Áo Thun Nike Air Nam",
    "category_id": "507f1f77bcf86cd799439011",
    "status": "DRAFT",
    "timestamp": "2026-04-15T10:00:00Z"
  }
}
```

---

### GET /products/{productId}
**Chi tiết sản phẩm public**

**Quyền truy cập**: Public

**Response 200**: Thông tin chi tiết sản phẩm

---

### GET /sellers/me/products
**Sản phẩm của Seller**

**Quyền truy cập**: JWT Required (SELLER)

**Response 200**: Danh sách sản phẩm của seller với phân trang

---

### GET /products/{productId}/presigned-url
**URL upload ảnh sản phẩm (MinIO)**

**Quyền truy cập**: JWT Required (SELLER)

**Mô tả**:
- Trả về Pre-signed PUT URL để Seller upload ảnh sản phẩm
- Bucket: `products-media`
- Prefix: `products/{seller_id}/{product_id}/{uuid}.{ext}`
- TTL: 15 phút

**Query Params**:
| Param | Type | Required | Mô tả |
|-------|------|----------|-------|
| file_name | string | ✓ | Tên file gốc (vd: product-front.jpg) |
| content_type | string | ✓ | image/jpeg \| image/png \| image/webp |

**Response 200**:
```json
{
  "presigned_url": "https://minio.internal/products-media/products/5/101/uuid.jpg?X-Amz-Signature=...",
  "object_url": "https://cdn.marketplace.vn/products-media/products/5/101/uuid.jpg",
  "expires_in": 900
}
```

---

### DELETE /seller/products/{productId}
**Xóa sản phẩm**

**Quyền truy cập**: JWT Required (SELLER - owner)

**Kafka Events**:
```json
{
  "topic": "product.deleted",
  "payload": { "product_id": "507f1f77bcf86cd799439012", "seller_id": 5 }
}
```

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 409 | Sản phẩm có stock_locked > 0 (đang bị giữ bởi đơn hàng PENDING) |

---

## Variant CRUD

### GET /seller/products/{productId}/variants
**Danh sách variants của sản phẩm**

**Quyền truy cập**: JWT Required (SELLER - owner)

**Response 200**: Danh sách MG_PRODUCT_VARIANTS của product

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 404 | Không tìm thấy product hoặc không thuộc seller |

---

### POST /seller/products/{productId}/variants
**Tạo variant mới**

**Quyền truy cập**: JWT Required (SELLER - owner)

**Request Body**:
```json
{
  "sku_code": "NK-AIR-RED-XL",
  "tier_name": "Đỏ / XL",
  "price": 350000
}
```

**Validation Rules**:
| Field | Type | Rules |
|-------|------|-------|
| sku_code | string | Unique; 3–50 ký tự; alphanumeric + dash |
| tier_name | string | 1–100 ký tự |
| price | decimal | > 0; max 9,999,999,999 |

**Response 201**:
```json
{
  "variant_id": "507f1f77bcf86cd799439013",
  "sku_code": "NK-AIR-RED-XL",
  "tier_name": "Đỏ / XL",
  "price": 350000,
  "product_id": "507f1f77bcf86cd799439012",
  "created_at": "2026-04-15T10:00:00Z"
}
```

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 409 | sku_code đã tồn tại |

---

### PUT /seller/variants/{variantId}
**Cập nhật variant**

**Quyền truy cập**: JWT Required (SELLER - owner)

**Request Body** (optional):
```json
{
  "tier_name": "Xanh / XL",
  "price": 380000
}
```

**Response 200**: Cập nhật variant thành công

---

### DELETE /seller/variants/{variantId}
**Xóa variant**

**Quyền truy cập**: JWT Required (SELLER - owner)

**Response 200**: Xóa variant thành công

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 409 | Variant đang được tham chiếu bởi đơn hàng / inventory |

---

## Product Lifecycle

### POST /seller/products/{productId}/submit
**Gửi sản phẩm duyệt**

**Quyền truy cập**: JWT Required (SELLER - owner)

**Request Body**: `{}`

**Response 200**:
```json
{
  "product_id": "507f1f77bcf86cd799439012",
  "status": "PENDING",
  "message": "Sản phẩm đã được gửi duyệt"
}
```

**Kafka Events**:
```json
{
  "topic": "product.pending_review",
  "payload": {
    "product_id": "507f1f77bcf86cd799439012",
    "seller_id": 5,
    "variants_count": 3,
    "timestamp": "2026-04-15T10:00:00Z"
  }
}
```

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 422 | Sản phẩm chưa đủ dữ liệu hoặc variant chưa hợp lệ |

---

### POST /seller/products/{productId}/publish
**Mở bán sản phẩm**

**Quyền truy cập**: JWT Required (SELLER - owner)

**Response 200**: Đánh dấu sản phẩm có thể hiển thị / bán

---

### POST /seller/products/{productId}/unpublish
**Tạm ẩn sản phẩm**

**Quyền truy cập**: JWT Required (SELLER - owner)

**Response 200**: Ẩn sản phẩm khỏi storefront nhưng giữ dữ liệu

---

## Inventory Management

### POST /seller/inventory/adjust
**Điều chỉnh tồn kho theo SKU**

**Quyền truy cập**: JWT Required (SELLER - owner)

**Request Body**:
```json
{
  "sku_code": "NK-AIR-RED-XL",
  "delta": -5,
  "reason": "Hàng bị hỏng trong kho"
}
```

**Response 200**: Điều chỉnh tồn kho thành công

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 422 | stock_available âm hoặc sku_code không hợp lệ |

---

### GET /seller/inventory/{skuCode}/logs
**Lịch sử điều chỉnh tồn kho**

**Quyền truy cập**: JWT Required (SELLER - owner)

**Response 200**: Audit log nhập/xuất/điều chỉnh theo SKU

---

## Inventory Query

### GET /inventory/{skuCode}
**Kiểm tra tồn kho theo SKU**

**Quyền truy cập**: JWT Required

**Response 200**:
```json
{
  "sku_code": "NK-AIR-RED-XL",
  "stock_total": 100,
  "stock_locked": 5,
  "stock_available": 95
}
```

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 404 | SKU không tồn tại |

---

### PUT /inventory/{skuCode}/restock
**Nhập thêm hàng (Seller)**

**Quyền truy cập**: JWT Required (SELLER - owner)

**Request Body**:
```json
{
  "quantity": 50,
  "reason": "Nhập hàng từ nhà cung cấp",
  "note": "Lô hàng tháng 4/2026"
}
```

**Response 200**: Nhập thêm hàng thành công

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 422 | sku_code không hợp lệ hoặc quantity không hợp lệ |

---

## Category Management

### GET /categories
**Danh sách danh mục**

**Quyền truy cập**: Public

**Response 200**: Toàn bộ cây danh mục

---

### POST /admin/categories
**Tạo danh mục mới**

**Quyền truy cập**: JWT Required (ADMIN)

**Request Body**:
```json
{
  "name": "Áo Thun Nam",
  "slug": "ao-thun-nam",
  "parent_id": "507f1f77bcf86cd799439010",
  "level": 2
}
```

**Response 201**: Tạo danh mục thành công

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 409 | slug đã tồn tại |

---

### PUT /admin/categories/{categoryId}
**Cập nhật danh mục**

**Quyền truy cập**: JWT Required (ADMIN)

**Response 200**: Cập nhật danh mục thành công

---

### DELETE /admin/categories/{categoryId}
**Xóa danh mục**

**Quyền truy cập**: JWT Required (ADMIN)

---

## 🛒 Cart Endpoints

> **Ghi chú**: Cart Service (port `:8083`, MongoDB, TTL 30 ngày) đã được gộp vào Product Service.

**Mô tả**: Giỏ hàng đa seller · MongoDB · TTL 30 ngày

### GET /cart
**Lấy giỏ hàng hiện tại**

**Quyền truy cập**: JWT Required

**Mô tả**:
- Giỏ hàng được nhóm theo Seller
- Giá và stock được enrich real-time từ Product Service
- Cart item của Flash Sale đã ENDED sẽ không còn xuất hiện (JOB-07 xóa)

**Response 200**:
```json
{
  "cart_id": "507f1f77bcf86cd799439014",
  "user_id": 42,
  "sellers": [
    {
      "seller_id": 5,
      "seller_name": "Shop Nike VN",
      "seller_trust_score": 92,
      "items": [
        {
          "cart_item_id": 201,
          "sku_code": "NK-AIR-RED-XL",
          "product_id": "507f1f77bcf86cd799439012",
          "product_name": "Áo Thun Nike Air",
          "variant_name": "Đỏ / XL",
          "unit_price": 350000,
          "quantity": 2,
          "stock_available": 95,
          "is_flash": false,
          "fs_item_id": null,
          "flash_price": null,
          "flash_expires_at": null,
          "subtotal": 700000,
          "added_at": "2026-04-14T15:30:00Z"
        }
      ],
      "seller_subtotal": 700000
    },
    {
      "seller_id": 9,
      "seller_name": "Shop Adidas VN",
      "seller_trust_score": 88,
      "items": [
        {
          "cart_item_id": 202,
          "sku_code": "AD-ULTRA-BLK-10",
          "product_id": "507f1f77bcf86cd799439013",
          "product_name": "Giày Adidas Ultraboost",
          "variant_name": "Đen / EU 10",
          "unit_price": 500000,
          "quantity": 1,
          "stock_available": 50,
          "is_flash": true,
          "fs_item_id": 1001,
          "flash_price": 399999,
          "flash_expires_at": "2026-04-16T22:00:00Z",
          "subtotal": 500000,
          "added_at": "2026-04-14T16:00:00Z"
        }
      ],
      "seller_subtotal": 500000
    }
  ],
  "total_items": 3,
  "subtotal": 1200000,
  "discount_from_loyalty": 0,
  "total": 1200000
}
```

---

### POST /cart/items
**Thêm sản phẩm vào giỏ**

**Quyền truy cập**: JWT Required

**Mô tả**:
- Nếu SKU đã có trong giỏ → cộng thêm số lượng
- Giới hạn số lượng theo Trust Score: Silver ≤3 items/seller, Bronze ≤1 item/seller
- Flash Sale item kiểm tra `limit_per_user` trên Redis

**Request Body**:
```json
{
  "sku_code": "NK-AIR-RED-XL",
  "quantity": 2,
  "fs_item_id": null
}
```

**Validation Rules**:
| Field | Type | Rules |
|-------|------|-------|
| sku_code | string | Phải tồn tại; Unique |
| quantity | integer | > 0; ≤ 1000 |
| fs_item_id | long | Optional; nếu có phải là Flash Sale item APPROVED |

**Response 200**:
```json
{
  "cart_item_id": 201,
  "sku_code": "NK-AIR-RED-XL",
  "product_name": "Áo Thun Nike Air",
  "quantity": 2,
  "unit_price": 350000,
  "subtotal": 700000,
  "stock_available": 95,
  "message": "Thêm vào giỏ hàng thành công"
}
```

**Kafka Events**:
```json
{
  "topic": "cart.item_added",
  "payload": {
    "user_id": 42,
    "sku_code": "NK-AIR-RED-XL",
    "quantity": 2,
    "timestamp": "2026-04-15T10:00:00Z"
  }
}
```

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 409 | Vượt giới hạn Trust Score tier hoặc vượt Flash Sale limit_per_user |
| 422 | SKU hết hàng hoặc không tồn tại |

---

### PUT /cart/items/{itemId}
**Cập nhật số lượng**

**Quyền truy cập**: JWT Required

**Request Body**:
```json
{
  "quantity": 3
}
```

> Đặt quantity = 0 không hợp lệ; dùng DELETE để xóa.

**Response 200**: Cập nhật số lượng thành công

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 422 | quantity vượt quá stock_available |
| 404 | cart_item_id không tồn tại hoặc không thuộc user |

---

### DELETE /cart/items/{itemId}
**Xóa sản phẩm khỏi giỏ**

**Quyền truy cập**: JWT Required

**Response 200**: Xóa item thành công

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 404 | cart_item_id không tồn tại hoặc không thuộc user |

---

### DELETE /cart
**Xóa toàn bộ giỏ hàng**

**Quyền truy cập**: JWT Required

**Response 200**: Xóa toàn bộ giỏ hàng thành công

---

## 📊 Summary — Product + Cart Service

| Endpoint | Method | Auth |
|----------|--------|------|
| /products | GET | Public |
| /products | POST | JWT (SELLER) |
| /products/{id} | GET | Public |
| /products/{id} | PUT | JWT (SELLER) |
| /products/{id}/presigned-url | GET | JWT (SELLER) |
| /sellers/me/products | GET | JWT (SELLER) |
| /seller/products/{id}/variants | GET | JWT (SELLER) |
| /seller/products/{id}/variants | POST | JWT (SELLER) |
| /seller/variants/{id} | PUT | JWT (SELLER) |
| /seller/variants/{id} | DELETE | JWT (SELLER) |
| /seller/products/{id}/submit | POST | JWT (SELLER) |
| /seller/products/{id}/publish | POST | JWT (SELLER) |
| /seller/products/{id}/unpublish | POST | JWT (SELLER) |
| /seller/products/{id} | DELETE | JWT (SELLER) |
| /seller/inventory/adjust | POST | JWT (SELLER) |
| /inventory/{sku} | GET | JWT |
| /inventory/{sku}/restock | PUT | JWT (SELLER) |
| /categories | GET | Public |
| /admin/categories | POST | JWT (ADMIN) |
| /admin/categories/{id} | PUT | JWT (ADMIN) |
| /admin/categories/{id} | DELETE | JWT (ADMIN) |
| /admin/products/pending | GET | JWT (ADMIN) |
| /admin/products/{id}/approve | POST | JWT (ADMIN) |
| /admin/products/{id}/reject | POST | JWT (ADMIN) |
| /cart | GET | JWT |
| /cart | DELETE | JWT |
| /cart/items | POST | JWT |
| /cart/items/{id} | PUT | JWT |
| /cart/items/{id} | DELETE | JWT |

---

**Phiên bản:** v5.4  
**Cập nhật:** 2026-04-30
