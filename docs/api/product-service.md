# 📦 Product Service API

**Service Name**: Product Service  
**Port**: `:8082`  
**Base URL**: `/api/v1`  
**Status**: v5.3 RTS

**Mô tả**: Sản phẩm, variant, danh mục, kho hàng, ảnh (MinIO)

---

## 📡 Kafka Integration

### Produces (Event Publisher)
- `product.created` → Search Service (Index new product)
- `product.updated` → Search Service (Update product index)
- `product.deleted` → Search Service (Remove from index)

### Consumes (Event Subscriber)
- None directly

---

## 🏷️ Category Management

### GET /categories
**Danh sách danh mục**

**Quyền truy cập**: Public

**Response 200**: Trả về toàn bộ cây danh mục

---

### POST /admin/categories
**Tạo danh mục mới**

**Quyền truy cập**: JWT Required (ADMIN)

**Request Body**:
```json
{
  "name": "string",         // Tên danh mục (Required)
  "slug": "string",         // Slug unique (Required)
  "parent_id": "string",    // ID danh mục cha (Optional, nullable)
  "level": "integer"        // Cấp danh mục (Required)
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

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 409 | Danh mục đang có product con / sub-category |

---

## 📝 Product CRUD

### POST /products
**Tạo sản phẩm mới**

**Quyền truy cập**: JWT Required (SELLER)  
**Tags**: Kafka → product.created

**Mô tả**:
- Sản phẩm mới tạo ở trạng thái `DRAFT`
- Seller phải gọi `POST /seller/products/{id}/submit` để gửi duyệt
- Số lượng sản phẩm PENDING bị giới hạn theo Trust Score tier:
  - Bronze ≤3
  - Silver ≤10
  - Gold ≤30
  - Platinum+ không giới hạn

**Request Body**:
```json
{
  "name": "string",           // 5–200 ký tự (Required)
  "description": "string",    // HTML cho phép, tối đa 10000 ký tự (Required)
  "category_id": "string",    // MongoDB ObjectId danh mục lá (Required)
  "attributes": "object",     // Thuộc tính động theo danh mục (Optional)
  "images": ["string"]        // 1-10 URLs từ Presigned URL (Required)
}
```

**Response 201**: Tạo sản phẩm thành công (status = DRAFT)

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 422 | Seller đang PENDING quá giới hạn, hoặc product_posting_suspended = TRUE |
| 403 | User chưa có role SELLER hoặc Stripe KYC chưa hoàn tất |

---

### PUT /products/{productId}
**Cập nhật sản phẩm**

**Quyền truy cập**: JWT Required (SELLER - owner)  
**Tags**: Kafka → product.updated

**Request Body** (all optional):
```json
{
  "name": "string",
  "description": "string",
  "category_id": "string",
  "attributes": "object",
  "images": ["string"]
}
```

**Response 200**: Cập nhật sản phẩm thành công

---

### DELETE /products/{productId}
**Xóa mềm sản phẩm**

**Quyền truy cập**: JWT Required (SELLER - owner)  
**Tags**: Kafka → product.deleted

**Mô tả**:
- Soft delete — gán `deleted_at = NOW()`
- Sản phẩm không còn hiển thị trên storefront
- JOB-10 sẽ hard delete sau 90 ngày nếu `stock_locked == 0`

**Response 200**: Xóa mềm thành công

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 409 | Sản phẩm có stock_locked > 0 (đang bị giữ bởi đơn hàng PENDING) |

---

### GET /products/{productId}
**Chi tiết sản phẩm public**

**Quyền truy cập**: Public

**Response 200**: Thông tin chi tiết sản phẩm

---

### GET /sellers/me/products
**Sản phẩm của Seller**

**Quyền truy cập**: JWT Required (SELLER)

**Query Params**:
| Param | Type | Mô tả |
|-------|------|-------|
| status | string | DRAFT \| PENDING \| APPROVED \| REJECTED |
| page | integer | Trang hiện tại (default: 0) |
| size | integer | Số bản ghi/trang (default: 20, max: 100) |

**Response 200**: Danh sách sản phẩm của seller với phân trang

---

## 🎨 Variant CRUD

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
  "sku_code": "string",    // Mã SKU, unique (Required)
  "tier_name": "string",   // Tên phân loại, ví dụ: Đỏ / XL (Required)
  "price": "decimal"       // Giá bán của variant (Required)
}
```

**Response 201**: Tạo variant thành công

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
  "tier_name": "string",   // Tên phân loại mới
  "price": "decimal"       // Giá bán mới
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

## 📦 Product Lifecycle

### POST /seller/products/{productId}/submit
**Gửi sản phẩm duyệt**

**Quyền truy cập**: JWT Required (SELLER - owner)

**Response 200**: Chuyển status sang luồng duyệt

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

## 🏭 Inventory Management

### POST /seller/inventory/adjust
**Điều chỉnh tồn kho theo SKU**

**Quyền truy cập**: JWT Required (SELLER - owner)

**Request Body**:
```json
{
  "sku_code": "string",    // Mã SKU cần điều chỉnh (Required)
  "delta": "integer",      // Số lượng thay đổi (+/-) (Required)
  "reason": "string"       // Lý do điều chỉnh (Required)
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

**Response 200**: Trả về audit log nhập/xuất/điều chỉnh theo SKU

---

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
  "quantity": "integer",   // Số lượng nhập thêm (> 0) (Required)
  "reason": "string",      // Lý do nhập thêm hàng (Required)
  "note": "string"         // Ghi chú nội bộ (Optional)
}
```

**Response 200**: Nhập thêm hàng thành công

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 422 | sku_code không hợp lệ hoặc quantity không hợp lệ |

---

### GET /products/{productId}/presigned-url
**URL upload ảnh (MinIO)**

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

## 📊 Summary

| Metric | Value |
|--------|-------|
| **Total Endpoints** | 16 |
| **Category Endpoints** | 4 |
| **Product CRUD** | 6 |
| **Variant CRUD** | 5 |
| **Inventory Endpoints** | 5 |
| **Kafka Topics Produced** | 3 |
| **Kafka Topics Consumed** | 0 |

---

## 🔗 Integration Points

| Service | Topic | Direction | Mô tả |
|---------|-------|-----------|-------|
| **Search Service** | product.created | → | Index sản phẩm mới |
| **Search Service** | product.updated | → | Cập nhật index |
| **Search Service** | product.deleted | → | Xóa khỏi index |
| **Admin Service** | - | ← | Receives approval/rejection |

---

**Last Updated**: 2026-04-28  
**Version**: v5.3 RTS

