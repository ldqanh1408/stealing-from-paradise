# BẢN HỢP NHẤT 2 TÀI LIỆU API V5.3 RTS

Tài liệu này giữ nguyên toàn bộ nội dung của cả hai nguồn, đặt tài liệu tổng quan trước và tài liệu chi tiết sau để tránh bỏ sót bất kỳ phần nào.

---

## PHẦN 1 — 02_API.md

# 📋 Marketplace API Specification v5.3 RTS [UNIFIED]

Tài liệu đặc tả API **đầy đủ** cho hệ thống **Multi-Vendor Marketplace**
- Tất cả request yêu cầu **HTTPS**
- Base URL: `/api/v1`
- Status: **v5.3 RTS — Unified**

### ✨ Tính năng v5.3
- ✅ Trust Score Tier (6 levels: BRONZE → ELITE)
- ✅ Notifications SSE/Pagination  
- ✅ Failed Events Management
- ✅ RETURNED order filter
- ✅ 422 Error Clarifications
- ✅ **Tracking number for refunds** (admin approval)

### 📚 Related Documentation

For complete understanding, also read:
- **[03_BUSINESS.md](03_BUSINESS.md)** - Business logic, workflows, policies
- **[04_POLICIES.md](04_POLICIES.md)** - System rules, configuration, schema
- **[05_OPERATIONS.md](05_OPERATIONS.md)** - Data retention, 23 cronjobs, cleanup

---

## 🛠️ Technology Stack

| Thành phần | Phiên bản |
|-----------|---------|
| **Java** | 25 |
| **Spring Boot** | 4.0.4 |
| **Authentication** | JWT · RS256 |
| **Payment Gateway** | Stripe Connect |
| **Trust Score** | v5.0 |
| **Message Queue** | Kafka |
| **Cache** | Redis |
| **Databases** | PostgreSQL, MongoDB, Elasticsearch |

---

## 📚 Table of Contents

1. [🔐 Identity Service](#-identity-service)
2. [📦 Product Service](#-product-service)
3. [🔍 Search Service](#-search-service)
4. [🛒 Cart Service](#-cart-service)
5. [📋 Order Service](#-order-service)
6. [↩️ Refund API](#-refund-api)
7. [💳 Payment Service](#-payment-service)
8. [⭐ Loyalty Service](#-loyalty-service)
9. [⚡ Flash Sale Service](#-flash-sale-service)
10. [🔔 Notification Service](#-notification-service)
11. [🧭 Kafka Topics](#-kafka-topics-catalog)
12. [🛡️ Admin APIs](#-admin-apis)

---

# 🔐 Identity Service

**Port**: `:8081`  
**Mô tả**: Đăng ký, đăng nhập, JWT, quản lý địa chỉ, trust score

## Authentication Endpoints

### POST /auth/register
**Đăng ký tài khoản mới** 

**Quyền truy cập**: Public (không cần JWT)

**Request Body**:
```json
{
  "username": "string",      // 3–50 ký tự, a-z, 0-9, dấu chấm/gạch dưới (Required)
  "email": "string",         // Email hợp lệ, unique (Required)
  "phone": "string",         // SĐT Việt Nam, unique (Required)
  "password": "string",      // Tối thiểu 8 ký tự, có chữ hoa + số (Required)
  "full_name": "string"      // 2–100 ký tự (Required)
}
```

**Response 201** - Tạo tài khoản thành công:
```json
{
  "user_id": 42,
  "username": "nguyen_van_a",
  "email": "a@example.com",
  "phone": "0901234567",
  "full_name": "Nguyễn Văn A",
  "roles": ["BUYER"],
  "status": "ACTIVE",
  "trust_score": 80,
  "trust_tier": "PLATINUM",
  "created_at": "2025-11-01T08:00:00Z"
}
```

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 409 | username / email / phone đã tồn tại |
| 400 | Validation thất bại |

---

### POST /auth/login
**Đăng nhập, nhận JWT**

**Quyền truy cập**: Public

**Request Body**:
```json
{
  "credential": "string",    // username | email | phone (Required)
  "password": "string"       // Mật khẩu (Required)
}
```

**Response 200**:
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiI0MiIsImlhdCI6MTcxMzcwMDAwMCwiZXhwIjoxNzEzNzAwOTAwfQ...",
  "refresh_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiI0MiIsInR0bCI6IjcgZGF5cyIsImlhdCI6MTcxMzcwMDAwMH0...",
  "token_type": "Bearer",
  "expires_in": 900,
  "refresh_expires_in": 604800,
  "user_id": 42,
  "username": "nguyen_van_a",
  "email": "a@example.com",
  "phone": "0901234567",
  "full_name": "Nguyễn Văn A",
  "roles": ["BUYER", "SELLER"],
  "status": "ACTIVE",
  "trust_score": 80,
  "trust_tier": "PLATINUM"
}
```

**Response 403 - Account Locked**:
```json
{
  "error": "ACCOUNT_LOCKED",
  "message": "Tài khoản bị khóa",
  "lock_reason": "Trust score quá thấp (< 10). Liên hệ support để khiếu nại.",
  "locked_until": "2026-05-15T10:00:00Z",
  "status_code": 403
}
```

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 200 | Đăng nhập thành công |
| 401 | Sai mật khẩu hoặc tài khoản không tồn tại |
| 403 | Tài khoản bị khóa (status = LOCKED) — kèm lock_reason |
| 400 | Validation thất bại (credential/password format sai) |

---

### POST /auth/refresh
**Làm mới access token**

**Quyền truy cập**: Public

**Request Body**:
```json
{
  "refresh_token": "string"  // Refresh token (Required)
}
```

**Response 200**:
```json
{
  "access_token": "eyJhbGc...",
  "refresh_token": "eyJhbGc...",
  "token_type": "Bearer",
  "expires_in": 900
}
```

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 200 | Trả về access_token mới (refresh_token xoay vòng) |
| 401 | Refresh token hết hạn hoặc không hợp lệ |

---

### POST /auth/logout
**Đăng xuất, thu hồi token**

**Quyền truy cập**: JWT Required  
**Tags**: Revoke JWTs | NEW v5.0

**Mô tả**: Logout sẽ đưa access token hiện tại vào Redis blocklist cho đến khi hết hạn. Nếu client gửi kèm `refresh_token`, hệ thống cũng thu hồi refresh token tương ứng.

**Request Body** (optional):
```json
{
  "refresh_token": "string",        // Refresh token cần thu hồi (Optional)
  "logout_all_devices": "boolean"   // TRUE = thu hồi toàn bộ token trên mọi thiết bị (Optional, default: false)
}
```

**Response 200**:
```json
{
  "message": "Đăng xuất thành công, token hiện tại bị vô hiệu hóa"
}
```

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 401 | Access token không hợp lệ hoặc đã hết hạn |

---

## User Profile Endpoints

### GET /users/me
**Lấy thông tin tài khoản**

**Quyền truy cập**: JWT Required

**Response 200**:
```json
{
  "user_id": 42,
  "username": "nguyen_van_a",
  "email": "a@example.com",
  "phone": "0901234567",
  "full_name": "Nguyễn Văn A",
  "avatar_url": "https://cdn.marketplace.vn/avatars/42.jpg",
  "roles": ["BUYER", "SELLER"],
  "status": "ACTIVE",
  "trust_score": 85,
  "trust_tier": "GOLD",
  "appeal_count": 1,
  "product_posting_suspended": false,
  "lock_reason": null,
  "locked_until": null,
  "created_at": "2024-01-15T08:00:00Z"
}
```

**Ghi chú**: 
- **Trust Tiers** [UNIFIED]: 
  - BRONZE: 0–39
  - SILVER: 40–59
  - GOLD: 60–79
  - PLATINUM: 80–89
  - DIAMOND: 90–99
  - ELITE: 100
- Mặc định khi tạo = 80 (PLATINUM)

---

### PUT /users/me
**Cập nhật thông tin cá nhân**

**Quyền truy cập**: JWT Required

**Request Body** (tất cả optional):
```json
{
  "full_name": "string",    // Họ tên mới (2–100 ký tự)
  "avatar_url": "string",   // URL ảnh đại diện từ MinIO (đã upload qua presigned-url)
  "phone": "string"         // SĐT mới (phải xác minh OTP trước khi đổi)
}
```

**Response 200**: 
```json
{
  "user_id": 42,
  "username": "nguyen_van_a",
  "email": "a@example.com",
  "phone": "0901234567",
  "full_name": "Nguyễn Văn A",
  "avatar_url": "https://cdn.marketplace.vn/avatars/42.jpg",
  "roles": ["BUYER", "SELLER"],
  "status": "ACTIVE",
  "trust_score": 85,
  "trust_tier": "GOLD",
  "appeal_count": 1,
  "product_posting_suspended": false,
  "created_at": "2024-01-15T08:00:00Z",
  "updated_at": "2026-04-15T10:30:00Z"
}
```

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 200 | Cập nhật thành công |
| 409 | phone mới đã tồn tại |
| 400 | Validation thất bại (format không hợp lệ) |
| 401 | Token không hợp lệ hoặc hết hạn |

---

### GET /users/me/avatar/presigned-url
**Lấy Presigned URL upload Avatar lên MinIO**

**Quyền truy cập**: JWT Required  
**Tag**: GAP-PATCH

**Mô tả**: Trả về Pre-signed PUT URL để upload ảnh avatar lên MinIO bucket `user-avatars`. TTL: 15 phút.

**Query Params**:
| Param | Type | Required | Mô tả |
|-------|------|----------|-------|
| content_type | string | ✓ | MIME type: image/jpeg \| image/png \| image/webp |

**Response 200**:
```json
{
  "upload_url": "https://minio.../user-avatars/42/uuid.jpg?X-Amz-Signature=...",
  "object_key": "42/uuid.jpg",
  "cdn_url": "https://cdn.marketplace.vn/avatars/42/uuid.jpg",
  "expires_in": 900
}
```

**Hướng dùng**: 
1. FE gọi endpoint này để lấy `upload_url`
2. FE PUT file lên `upload_url`
3. FE lưu `cdn_url` vào request body của `PUT /users/me`

---

## Address Management Endpoints

### GET /users/me/addresses
**Danh sách địa chỉ của user**

**Quyền truy cập**: JWT Required

**Response 200**:
```json
[
  {
    "address_id": 7,
    "province_id": 79,
    "district_id": 760,
    "full_address": "123 Nguyễn Trãi, Phường 2",
    "is_default": true
  }
]
```

**Ghi chú**: Địa chỉ mặc định (is_default=true) luôn ở đầu

---

### POST /users/me/addresses
**Thêm địa chỉ mới**

**Quyền truy cập**: JWT Required

**Request Body**:
```json
{
  "province_id": "integer",  // ID tỉnh/thành theo chuẩn VNPOST (Required)
  "district_id": "integer",  // ID quận/huyện (Required)
  "full_address": "string",  // Địa chỉ chi tiết (Required)
  "is_default": "boolean"    // Đặt làm địa chỉ mặc định (Optional, default: false)
}
```

**Response 201**: Địa chỉ được tạo thành công

---

### PUT /users/me/addresses/{addressId}
**Cập nhật / đặt mặc định địa chỉ**

**Quyền truy cập**: JWT Required

**Request Body** (all optional):
```json
{
  "province_id": "integer",
  "district_id": "integer",
  "full_address": "string",
  "is_default": "boolean"
}
```

**Response 200**: Cập nhật thành công

---

### DELETE /users/me/addresses/{addressId}
**Xóa địa chỉ**

**Quyền truy cập**: JWT Required

**Response 200**: Xóa địa chỉ thành công

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 400 | Không thể xóa địa chỉ mặc định duy nhất — phải đặt địa chỉ khác làm mặc định trước |
| 404 | addressId không tồn tại hoặc không thuộc user này |

---

## Seller Registration

### POST /users/me/roles/seller
**Đăng ký trở thành Seller**

**Quyền truy cập**: JWT Required (BUYER role)

**Request Body**: (không có body)

**Response 200**: Đăng ký Seller thành công

---

## Trust Score Appeal Endpoints

### GET /support/trust-score-appeal/presigned-url
**Lấy Presigned URL upload ảnh bằng chứng khiếu nại**

**Quyền truy cập**: JWT Required  
**Tag**: GAP-PATCH R1

**Mô tả**: Trả về Pre-signed PUT URL để upload ảnh bằng chứng khiếu nại lên MinIO bucket `appeal-evidence`. TTL: 15 phút.

**Query Params**:
| Param | Type | Required | Mô tả |
|-------|------|----------|-------|
| file_name | string | ✓ | Tên file gốc (dùng để detect extension) |
| content_type | string | ✓ | MIME type: image/jpeg \| image/png \| image/webp |

**Response 200**:
```json
{
  "presigned_url": "https://minio.internal/appeal-evidence/appeals/42/uuid-abc.jpg?X-Amz-Signature=...",
  "object_url": "https://cdn.marketplace.vn/appeal-evidence/appeals/42/uuid-abc.jpg",
  "expires_in": 900
}
```

---

### POST /support/trust-score-appeal
**Gửi khiếu nại trust score**

**Quyền truy cập**: JWT Required  
**Tag**: NEW v5.0

**Mô tả**: Tối đa 3 lần/năm theo `USERS.appeal_count`. JOB-20 reset appeal_count về 0 vào ngày 1/1 hàng năm.

**Request Body**:
```json
{
  "log_id": "long",              // ID bản ghi TRUST_SCORE_LOGS (Required)
  "reason": "string",            // Giải thích của user, tối đa 500 ký tự (Required)
  "evidence_urls": ["string"]    // Ảnh/tài liệu bằng chứng từ presigned-url (Optional)
}
```

**Response 201**: Khiếu nại được tạo

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 409 | Đã đạt giới hạn 3 lần/năm (appeal_count ≥ 3) |
| 404 | log_id không tồn tại hoặc không thuộc user này |

---

### GET /users/me/trust-score/logs
**Lịch sử thay đổi Trust Score**

**Quyền truy cập**: JWT Required  
**Tag**: NEW v5.1 — Gap A

**Mô tả**: Trả về toàn bộ bản ghi `TRUST_SCORE_LOGS` của user. Retention: 2 năm (JOB-11).

**Query Params**:
| Param | Type | Mô tả |
|-------|------|-------|
| page | integer | Trang hiện tại (default: 0) |
| size | integer | Số bản ghi/trang (default: 20, max: 100) |

**Response 200**:
```json
{
  "content": [
    {
      "log_id": 1042,
      "event_code": "BUYER_CANCEL_EXCESSIVE",
      "delta": -5,
      "score_after": 72,
      "changed_by": "SYSTEM",
      "reason": "Hủy đơn quá 5 lần trong 30 ngày",
      "created_at": "2025-11-01T03:00:00Z"
    }
  ],
  "total_elements": 38,
  "total_pages": 2
}
```

---

---

# 📦 Product Service

**Port**: `:8082`  
**Mô tả**: Sản phẩm, variant, danh mục, kho hàng, ảnh (MinIO)

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

## Product Lifecycle

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

## Inventory Management

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

## Category Management

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

## Product CRUD

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

---

# 🔍 Search Service

**Port**: `:8089`
**Mô tả**: Elasticsearch · Full-text · Faceted filter · Aggregation

### GET /search/products
**Tìm kiếm sản phẩm**

**Quyền truy cập**: Public

**Query Params**:
| Param | Type | Mô tả |
|-------|------|-------|
| q | string | Từ khóa tìm kiếm (full-text) |
| category_id | string | Lọc theo danh mục (kèm danh mục con) |
| price_min | decimal | Giá tối thiểu |
| price_max | decimal | Giá tối đa |
| in_stock | boolean | Chỉ hiện hàng còn (default: true) |
| is_flash | boolean | Chỉ hiện sản phẩm đang flash sale |
| sort | string | relevance \| price_asc \| price_desc \| newest \| sold_desc |
| page | integer | Trang hiện tại (default: 0) |
| size | integer | Kích thước trang (default: 20, max: 100) |

**Response 200**: Danh sách sản phẩm có phân trang

---

### GET /search/products/suggest
**Autocomplete gợi ý tìm kiếm**

**Quyền truy cập**: Public

**Query Params**:
| Param | Type | Required | Mô tả |
|-------|------|----------|-------|
| q | string | ✓ | Chuỗi người dùng đang gõ (tối thiểu 2 ký tự) |
| size | integer | - | Số gợi ý trả về (default: 5, max: 10) |

**Response 200**:
```json
{
  "suggestions": [
    "áo thun nam",
    "áo thun nữ cotton",
    "áo thun oversize"
  ]
}
```

---

---

# 🛒 Cart Service

**Port**: `:8083`
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
  "sellers": [
    {
      "seller_id": 5,
      "seller_name": "Shop Nike VN",
      "items": [
        {
          "cart_item_id": 201,
          "sku_code": "NK-AIR-RED-XL",
          "product_name": "Áo Thun Nike Air",
          "variant_name": "Đỏ / XL",
          "unit_price": 350000,
          "quantity": 2,
          "stock_available": 95,
          "is_flash": false,
          "fs_item_id": null,
          "flash_price": null,
          "flash_expires_at": null
        }
      ]
    }
  ],
  "total_items": 2,
  "subtotal": 700000
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
  "sku_code": "string",      // SKU code của variant (Required)
  "quantity": "integer",     // Số lượng muốn thêm (> 0) (Required)
  "fs_item_id": "long"       // ID flash sale item — bắt buộc nếu mua trong Flash Sale (Optional)
}
```

**Response 200**: Thêm thành công, trả về cart item mới

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
  "quantity": "integer"      // Số lượng mới (> 0) — Đặt quantity = 0 không hợp lệ (Required)
}
```

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

---

# 📋 Order Service

**Port**: `:8087`
**Mô tả**: Checkout, quản lý đơn hàng, Saga CQRS · Axon

### POST /orders/checkout
**Tạo đơn hàng từ giỏ**

**Quyền truy cập**: JWT Required (BUYER)
**Tags**: Kafka → order.created · Multi-Vendor Split

**Mô tả**:
- Tạo 1 PARENT_ORDER + N ORDERS (1 sub-order per Seller từ các item trong giỏ)
- Lọc item theo address_id và thực hiện checkout cho tất cả
- Hệ thống tự động split thanh toán theo Seller
- Điểm Loyalty được ghi nhận ở trạng thái PENDING

**Request Body**:
```json
{
  "address_id": "long",                          // ID địa chỉ giao hàng (Required)
  "item_ids": ["long"],                          // Danh sách cart_item_id muốn checkout (Required)
  "use_loyalty_points": "boolean",               // Dùng điểm thưởng để giảm giá (Optional, default: false)
  "loyalty_points_to_use": "integer"             // Số điểm muốn dùng (tối đa 20% giá trị đơn) (Optional)
}
```

**Response 201**: Tạo đơn hàng thành công
```json
{
  "parent_order_id": 55,
  "order_code": "PO-20251001-55",
  "orders": [
    {
      "order_id": 100,
      "order_code": "OR-20251001-100",
      "seller_id": 5,
      "seller_name": "Shop Nike VN",
      "total_amt": 700000,
      "final_amt": 650000,
      "status": "PENDING",
      "created_at": "2025-10-01T10:00:00Z"
    },
    {
      "order_id": 101,
      "order_code": "OR-20251001-101",
      "seller_id": 9,
      "seller_name": "Shop Adidas VN",
      "total_amt": 500000,
      "final_amt": 500000,
      "status": "PENDING",
      "created_at": "2025-10-01T10:00:00Z"
    }
  ],
  "total_amount": 1200000,
  "loyalty_discount": 50000,
  "loyalty_points_used": 50,
  "final_amount": 1150000,
  "items_count": 3,
  "created_at": "2025-10-01T10:00:00Z"
}
```

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 201 | Checkout thành công |
| 422 | Một số item hết hàng hoặc không hợp lệ |
| 409 | Địa chỉ không tồn tại hoặc không thuộc user |
| 400 | Loyalty points vượt giới hạn hoặc validation thất bại |

---

### GET /orders
**Danh sách đơn hàng của Buyer**

**Quyền truy cập**: JWT Required (BUYER)

**Query Params**:
| Param | Type | Mô tả |
|-------|------|-------|
| status | string | PENDING \| PAID \| SHIPPING \| DELIVERED \| RETURNED \| CANCELLED \| PARTIALLY_REFUNDED \| REFUNDED (Optional) |
| from_date | date | Ngày bắt đầu (ISO 8601, Optional) |
| to_date | date | Ngày kết thúc (ISO 8601, Optional) |
| page | integer | Trang hiện tại (default: 0) |
| size | integer | Kích thước trang (default: 20, max: 100) |

**Response 200**:
```json
{
  "content": [
    {
      "order_id": 100,
      "parent_order_id": 55,
      "order_code": "OR-20251001-100",
      "seller_id": 5,
      "seller_name": "Shop Nike VN",
      "status": "PAID",
      "total_amt": 700000,
      "final_amt": 700000,
      "is_flash_sale": false,
      "item_count": 2,
      "created_at": "2025-10-01T10:00:00Z",
      "updated_at": "2025-10-01T10:05:00Z"
    }
  ],
  "total_elements": 12,
  "total_pages": 1,
  "page_number": 0,
  "page_size": 20
}
```

---

### GET /orders/{orderId}
**Chi tiết đơn hàng con**

**Quyền truy cập**: JWT Required (BUYER \| SELLER - owner)

**Response 200**:
```json
{
  "order_id": 100,
  "parent_order_id": 55,
  "order_code": "OR-20251001-100",
  "seller_id": 5,
  "seller_name": "Shop Nike VN",
  "buyer_id": 42,
  "buyer_name": "Nguyễn Văn A",
  "status": "SHIPPING",
  "total_amt": 700000,
  "final_amt": 700000,
  "is_flash_sale": false,
  "cancelled_by": null,
  "cancel_reason": null,
  "shipping_address": {
    "full_address": "123 Nguyễn Trãi, Phường 2, Q.3, TP.HCM",
    "province_id": 79,
    "district_id": 760
  },
  "tracking_number": "VT123456789",
  "carrier": "ViettelPost",
  "shipping_deadline": "2025-10-04T10:00:00Z",
  "items": [
    {
      "order_item_id": 501,
      "sku_code": "NK-AIR-RED-XL",
      "product_name": "Áo Thun Nike Air",
      "variant_name": "Đỏ / XL",
      "image_snapshot": "https://cdn.marketplace.vn/products-media/...",
      "price_snapshot": 350000,
      "quantity": 2,
      "refunded_quantity": 0,
      "fs_item_id": null
    }
  ],
  "created_at": "2025-10-01T10:00:00Z",
  "updated_at": "2025-10-01T12:00:00Z"
}
```

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 200 | Chi tiết lấy thành công |
| 403 | User không phải Buyer/Seller chủ của đơn |
| 404 | orderId không tồn tại |

---

### GET /orders/parent/{parentOrderId}
**Chi tiết đơn cha**

**Quyền truy cập**: JWT Required (BUYER)

**Response 200**: Đơn cha kèm toàn bộ sub-orders và thông tin thanh toán

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 403 | Parent order không thuộc về Buyer này |

---

### POST /orders/{orderId}/cancel
**Hủy đơn hàng**

**Quyền truy cập**: JWT Required (BUYER \| SELLER)
**Tags**: Kafka → order.cancelled

**⚠️ Cảnh báo**: Hủy đơn sẽ trừ Trust Score Buyer theo event_code `BUYER_CANCEL_EXCESSIVE` nếu tổng hủy trong 30 ngày vượt ngưỡng.

**Request Body**:
```json
{
  "reason": "string",        // Lý do hủy đơn (Required)
  "note": "string"           // Ghi chú bổ sung (Optional)
}
```

**Response 200**: Hủy thành công, stock được giải phóng

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 409 | Đơn không ở trạng thái PENDING |
| 403 | User không phải chủ đơn |

---

### PUT /orders/{orderId}/tracking
**Cập nhật tracking number (Seller)**

**Quyền truy cập**: JWT Required (SELLER - owner)
**Tags**: Kafka → order.shipped

**Request Body**:
```json
{
  "tracking_number": "string",   // Mã vận đơn từ đơn vị vận chuyển (Required)
  "carrier": "string",           // Tên đơn vị vận chuyển (ViettelPost, GHN, GHTK…) (Optional)
  "note": "string"               // Ghi chú giao hàng (Optional)
}
```

**Response 200**: status → SHIPPING, Kafka order.shipped published

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 409 | Đơn không ở trạng thái PAID |
| 403 | Seller không phải chủ của sub-order |

---

### POST /orders/{orderId}/confirm-received
**Xác nhận đã nhận hàng**

**Quyền truy cập**: JWT Required (BUYER)
**Tags**: Kafka → order.delivered

**Request Body**: (không có body)

**Response 200**: status → DELIVERED, điểm thưởng PENDING → CONFIRMED, trust_score Seller +5

---

### POST /orders/{orderId}/return-to-sender
**Seller xác nhận nhận lại hàng hoàn — kích hoạt Full Refund tự động**

**Quyền truy cập**: JWT Required (SELLER)
**Tags**: Kafka → order.returned | RTS (Return To Sender)

**Mô tả** [RTS]: Khi đơn vị vận chuyển hoàn hàng về Seller (gọi không nghe / sai địa chỉ), Seller chủ động gọi API này. Hệ thống sẽ:
1. Chuyển `ORDERS.status → RETURNED`
2. Tự động tạo REFUNDS (type=FULL, initiated_by=SELLER, refund_reason_type=RETURN_TO_SENDER)
3. Cộng lại `stock_available` cho từng SKU (atomic operation)
4. Produce Kafka event `order.returned`
5. Thực hiện Stripe refund tự động (không cần Admin duyệt)
6. Ghi bằng chứng ảnh vào REFUND_ITEMS.return_evidence_images

**Request Body** (multipart/form-data):
```json
{
  "evidence_images": ["file"],           // Ảnh chụp gói hàng (1-5 ảnh, bắt buộc)
  "return_tracking_number": "string",    // Mã vận đơn hoàn hàng (Optional)
  "note": "string"                       // Ghi chú thêm của Seller, tối đa 500 ký tự (Optional)
}
```

**Response 200**:
```json
{
  "order_id": 1001,
  "order_code": "OR-20251001-1001",
  "order_status": "RETURNED",
  "refund_id": 99,
  "refund_code": "RF-20251001-99",
  "refund_status": "PENDING",
  "refund_amount": 250000,
  "return_tracking_number": "VT999888777",
  "evidence_count": 2,
  "estimated_refund_days": 3,
  "stripe_refund_id": "re_3Px5Ab...",
  "message": "Hàng hoàn đã được ghi nhận. Hệ thống đang tự động hoàn tiền cho Buyer.",
  "seller_notification": {
    "status": "sent",
    "message": "Xác nhận hàng hoàn đã được lưu. Tồn kho đã được cộng lại."
  },
  "buyer_notification": {
    "status": "sent",
    "message": "Seller đã nhận lại hàng hoàn. Tiền đang được hoàn về tài khoản của bạn."
  },
  "created_at": "2025-10-01T14:30:00Z"
}
```

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 200 | Xác nhận RTS thành công |
| 409 | Đơn hàng đã ở trạng thái RETURNED hoặc đã có refund đang xử lý |
| 422 | Trạng thái đơn không hợp lệ — chỉ cho phép khi order.status = SHIPPING |
| 403 | Không phải Seller của đơn hàng |
| 400 | evidence_images không hợp lệ hoặc không cung cấp |

---

### GET /sellers/me/orders
**Đơn hàng của Seller**

**Quyền truy cập**: JWT Required (SELLER)

**Query Params**:
| Param | Type | Mô tả |
|-------|------|-------|
| status | string | Lọc theo trạng thái đơn |
| from_date / to_date | date | Lọc theo khoảng thời gian |
| page, size | integer | Phân trang |

**Response 200**: Danh sách sub-orders thuộc Seller, mỗi item kèm thông tin Buyer và ORDER_ITEMS summary

---

---

# ↩️ Refund API

**Mô tả**: Full Refund · Partial Refund (multi-seller) · Admin Override · Stripe Reversal (v4.1)

**Nguyên tắc**:
- Mỗi ORDERS thuộc 1 Seller → mỗi REFUNDS gắn với 1 sub-order
- Full Refund trên parent order tạo N bản ghi REFUNDS (N = số seller)
- Liên kết qua `group_ref` UUID chung

## Full Refund

### POST /orders/parent/{parentOrderId}/refund
**Full Refund toàn bộ đơn cha**

**Quyền truy cập**: JWT Required (BUYER)
**Tags**: Stripe Refund API | Kafka → refund.full_requested | NEW v4.1

**Điều kiện**:
```
- order.status == "PAID"        // chưa ship
- transaction.status == "SUCCESS"  // đã thanh toán
- refunds_pending == 0             // không có refund PENDING
- Nếu BẤT KỲ sub-order nào SHIPPING/DELIVERED → Reject 422
```

**Request Body**:
```json
{
  "reason": "string",           // Lý do hủy đơn (Required)
  "evidence_images": ["string"] // Mảng URL ảnh bằng chứng (Optional)
}
```

**Response 201**:
```json
{
  "group_ref": "550e8400-e29b-41d4-a716-446655440000",
  "type": "FULL",
  "total_amount": 800000,
  "status": "PENDING",
  "refunds": [
    {
      "refund_id": 88,
      "order_id": 100,
      "seller_id": 5,
      "amount": 500000,
      "item_count": 3
    },
    {
      "refund_id": 89,
      "order_id": 101,
      "seller_id": 9,
      "amount": 300000,
      "item_count": 1
    }
  ],
  "loyalty_points_to_return": 62,
  "estimated_days": 3
}
```

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 201 | Tất cả REFUNDS tạo thành công |
| 409 | Đã có refund PENDING trong parent order này |
| 422 | Có sub-order ở trạng thái SHIPPING/DELIVERED |
| 403 | Parent order không thuộc về buyer |

---

### GET /orders/parent/{parentOrderId}/refund
**Trạng thái Full Refund của đơn cha**

**Quyền truy cập**: JWT Required (BUYER \| ADMIN)

**Response 200**:
```json
{
  "group_ref": "550e8400-...",
  "type": "FULL",
  "overall_status": "SUCCESS",
  "total_amount": 800000,
  "refunds": [
    {
      "refund_id": 88,
      "order_id": 100,
      "status": "SUCCESS",
      "refund_ref": "re_3Px..."
    },
    {
      "refund_id": 89,
      "order_id": 101,
      "status": "SUCCESS",
      "refund_ref": "re_3Py..."
    }
  ]
}
```

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 404 | Không có Full Refund nào cho parent order |

---

## Partial Refund

### POST /orders/{orderId}/refunds
**Partial Refund — 1 sub-order (1 seller)**

**Quyền truy cập**: JWT Required (BUYER)
**Tags**: Stripe Refund API | Kafka → refund.requested

**Điều kiện**:
```
- order.status IN ["PAID", "SHIPPING", "DELIVERED", "PARTIALLY_REFUNDED"]
- qty_to_refund > 0
- qty_to_refund ≤ (item.quantity - item.refunded_quantity)
- Không có REFUND_ITEM.status = PENDING cho cùng item
- Nếu status = DELIVERED: NOW() - order.updated_at ≤ 7 ngày
```

**Request Body**:
```json
{
  "reason": "string",                // Lý do hoàn chung (Required)
  "items": [                         // Danh sách items cần hoàn (Required)
    {
      "order_item_id": "long",       // ID của ORDER_ITEM (Required)
      "quantity": "integer",         // Số lượng cần hoàn (Required)
      "item_reason": "string"        // Lý do hoàn riêng cho item này (Optional)
    }
  ],
  "evidence_images": ["string"]      // Ảnh bằng chứng (MinIO URLs) (Optional)
}
```

**Response 201**: Yêu cầu tạo thành công

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 400 | qty_to_refund vượt quá available, hoặc item không thuộc order |
| 409 | Đã có REFUND_ITEM PENDING cho item này |
| 422 | Order status không hợp lệ hoặc quá 7 ngày |

---

### POST /orders/parent/{parentOrderId}/refunds/partial
**Partial Refund — nhiều sub-orders / sellers**

**Quyền truy cập**: JWT Required (BUYER)
**Tags**: Kafka → refund.requested (per seller) | NEW v4.1

**Mô tả**: System tự động nhóm items theo sub-order, tạo REFUNDS riêng cho mỗi seller, liên kết bằng `group_ref`. Payment Service xử lý song song.

**Request Body**:
```json
{
  "reason": "string",          // Lý do hoàn chung (Required)
  "items": [                   // Items từ nhiều sub-orders/sellers (Required)
    {
      "order_item_id": "long",  // ID ORDER_ITEM (Required)
      "quantity": "integer",    // Số lượng (Required)
      "item_reason": "string"   // Lý do riêng (Optional)
    }
  ],
  "evidence_images": ["string"] // Ảnh bằng chứng (Optional)
}
```

**Server-side Grouping Logic**:
```
group_ref = UUID.randomUUID()
order_100 (seller_5): items [501, 502] → REFUNDS id=90, amount=375000
order_101 (seller_9): items [601]      → REFUNDS id=91, amount=200000
// Mỗi REFUNDS cùng group_ref
// Publish 2 Kafka events: refund.requested (per seller)
```

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 201 | Tất cả REFUNDS tạo thành công |
| 400 | Item không thuộc parent order, hoặc qty vượt available |
| 409 | Có item đang có PENDING refund |

---

## Query Refund APIs

### GET /orders/{orderId}/refunds
**Lịch sử hoàn tiền của 1 sub-order**

**Quyền truy cập**: JWT Required (BUYER \| SELLER - owner \| ADMIN)

**Response 200**:
```json
[
  {
    "refund_id": 88,
    "group_ref": "550e8400-...",
    "type": "PARTIAL",
    "status": "SUCCESS",
    "amount": 350000,
    "adjust_amount": null,
    "admin_note": null,
    "reviewed_by": null,
    "reviewed_at": null,
    "refund_ref": "re_3PxABC...",
    "created_at": "2025-10-05T14:00:00Z"
  }
]
```

---

### GET /orders/{orderId}/refunds/{refundId}
**Chi tiết 1 yêu cầu hoàn tiền**

**Quyền truy cập**: JWT Required (BUYER \| ADMIN)

**Response 200**: Full detail: REFUNDS + REFUND_ITEMS + admin_note

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 403 | Buyer không phải owner của order |

---

### GET /orders/refunds
**Tất cả yêu cầu hoàn tiền của Buyer**

**Quyền truy cập**: JWT Required (BUYER)

**Query Params**:
| Param | Type | Mô tả |
|-------|------|-------|
| status | string | PENDING \| SUCCESS \| FAILED \| REJECTED |
| type | string | FULL \| PARTIAL |
| from_date / to_date | date | ISO 8601 |
| page, size | integer | Phân trang |

**Response 200**: Danh sách refunds, sorted by created_at DESC

---

### GET /orders/{orderId}/refunds/presigned-url
**Lấy MinIO Pre-signed URL để upload ảnh bằng chứng hoàn tiền**

**Quyền truy cập**: JWT Required (BUYER - owner)
**Tag**: NEW v5.1 — Gap A

**Mô tả**: Trả về Pre-signed PUT URL từ MinIO với TTL **15 phút**.

**Query Params**:
| Param | Type | Required | Mô tả |
|-------|------|----------|-------|
| file_name | string | ✓ | Tên file gốc (vd: evidence.jpg) |
| content_type | string | ✓ | MIME type: image/jpeg \| image/png \| image/webp |

**Response 200**:
```json
{
  "presigned_url": "https://minio.internal/refund-evidence/orders/100/uuid-abc.jpg?X-Amz-Signature=...",
  "object_url": "https://cdn.marketplace.vn/refund-evidence/orders/100/uuid-abc.jpg",
  "expires_in": 900
}
```

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 403 | orderId không thuộc về Buyer |
| 422 | content_type không hợp lệ |

---

## Admin Refund APIs

### GET /admin/refunds
**Tất cả yêu cầu hoàn tiền (Admin)**

**Quyền truy cập**: JWT Required (ADMIN)

**Query Params**:
| Param | Type | Mô tả |
|-------|------|-------|
| status | string | PENDING \| SUCCESS \| FAILED \| REJECTED |
| type | string | FULL \| PARTIAL |
| seller_id | long | Lọc theo seller bị ảnh hưởng |
| group_ref | uuid | Lọc theo nhóm Full Refund |
| from_date / to_date | date | Khoảng thời gian |
| page, size | integer | Phân trang |

---

### POST /admin/refunds/{refundId}/approve
**Duyệt hoàn tiền thủ công**

**Quyền truy cập**: JWT Required (ADMIN)
**Tags**: Stripe Refund API | Kafka → refund.admin_approved

**Khi nào dùng**:
1. Tự động xử lý thất bại (FAILED) — Admin retry thủ công
2. Tranh chấp Buyer vs Seller — Admin phán quyết
3. Stripe webhook miss — Admin force approve

**Request Body**:
```json
{
  "admin_note": "string",        // Lý do Admin can thiệp (Required)
  "adjust_amount": "decimal",    // Override số tiền hoàn (Optional)
  "caused_by": "string",         // NEW v5.1: SELLER | BUYER (Optional)
  "tracking_number": "string"    // NEW v5.3: Mã vận đơn hoàn (Optional - for partial refund tracking)
}
```

**Mô tả trường mới (v5.3)**:
- `tracking_number`: Mã vận đơn hoàn hàng (nếu có). Lưu vào `REFUND_ITEMS` để theo dõi hàng phản lại.
  - Áp dụng khi Admin xác nhận Refund có liên quan đến hàng hoàn về
  - Giúp audit trail & truy vết vận chuyển

**Side Effects**:
```
1. Stripe: refunds.create({ payment_intent, amount: adjust_amount ?? refund.amount })
2. REFUNDS.status = SUCCESS, reviewed_by = adminId, reviewed_at = NOW()
3. Nếu tracking_number được cung cấp:
   → UPDATE REFUND_ITEMS SET tracking_number = ?, updated_at = NOW()
   → Ghi vào REFUND_ITEMS.return_evidence (audit log)
4. Publish refund.admin_approved (kèm tracking_number nếu có)
5. USERS.trust_score[seller] -= 5 (auto deduct nếu caused_by=SELLER)
6. Push notification đến Buyer (kèm tracking_number nếu có)
```

**Response 200**: Stripe call thành công
```json
{
  "refund_id": "uuid",
  "status": "SUCCESS",
  "amount": 500000,
  "tracking_number": "VC123456789",  // NEW v5.3: tracking number được ghi nhận
  "reviewed_by": "admin_id",
  "reviewed_at": "2025-12-15T10:30:00Z"
}
```

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 422 | Refund không ở trạng thái PENDING hoặc FAILED |
| 400 | tracking_number format không hợp lệ (nếu được cung cấp) |

---

### POST /admin/refunds/{refundId}/reject
**Từ chối yêu cầu hoàn tiền**

**Quyền truy cập**: JWT Required (ADMIN)
**Tags**: Kafka → refund.rejected

**Request Body**:
```json
{
  "reject_reason": "string",      // Lý do từ chối (Required)
  "fraud_evidence": "boolean"     // NEW v5.1: true = trừ điểm Buyer (Optional)
}
```

**Response 200**: REFUNDS.status = REJECTED, push notification đến Buyer

---

---

# 💳 Payment Service

**Port**: `:8085`
**Mô tả**: Stripe Connect · Destination Charges · Transfer API · Webhooks

### POST /stripe/onboarding/start
**Bắt đầu onboarding Stripe (Seller)**

**Quyền truy cập**: JWT Required (SELLER)
**Tags**: Stripe Connect

**Mô tả**: Gọi Stripe API `accountLinks.create` để tạo onboarding URL. URL hợp lệ trong 24 giờ, sau đó tự null bởi JOB-15.

**Request Body**: (không có body)

**Response 201**:
```json
{
  "onboarding_url": "https://connect.stripe.com/setup/e/acct_xxx/...",
  "expires_at": "2025-10-02T10:00:00Z"
}
```

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 409 | Seller đã có Stripe account (details_submitted = true) |

---

### GET /stripe/onboarding/status
**Kiểm tra trạng thái Stripe account**

**Quyền truy cập**: JWT Required (SELLER)

**Response 200**:
```json
{
  "stripe_account_id": "acct_1OxABC",
  "details_submitted": true,
  "charges_enabled": true,
  "payouts_enabled": true,
  "onboarding_status": "COMPLETE",
  "onboarding_url": null
}
```

**Ghi chú**:
- PENDING = chưa bắt đầu
- IN_PROGRESS = đang KYC
- COMPLETE = đã xong
- SUSPENDED = bị Stripe đình chỉ

---

### POST /stripe/onboarding/refresh-link
**Tạo lại onboarding link (hết hạn)**

**Quyền truy cập**: JWT Required (SELLER)
**Tags**: Stripe Connect

**Request Body**: (không có body)

**Response 200**:
```json
{
  "onboarding_url": "https://connect.stripe.com/setup/e/acct_xxx/new-link",
  "expires_at": "2025-10-03T10:00:00Z"
}
```

---

### GET /payments/parent-order/{parentOrderId}
**Thông tin giao dịch thanh toán**

**Quyền truy cập**: JWT Required (BUYER \| ADMIN)

**Response 200**:
```json
{
  "transaction_id": 301,
  "parent_order_id": 55,
  "amount": 1330000,
  "method": "STRIPE",
  "status": "SUCCESS",
  "stripe_pi_id": "pi_3PxABC...",
  "application_fee": 66500,
  "trans_ref": "TXN-20251001-301",
  "paid_at": "2025-10-01T10:05:00Z",
  "remaining_seconds": null
}
```

**Ghi chú**: `remaining_seconds` chỉ có giá trị khi status = PENDING

---

### POST /stripe/webhooks
**Nhận Stripe Webhook events**

**Events xử lý**:
| Event | Xử lý |
|-------|-------|
| payment_intent.succeeded | TRANSACTIONS → SUCCESS |
| payment_intent.payment_failed | TRANSACTIONS → FAILED |
| charge.refunded | REFUNDS → SUCCESS |
| account.updated | Sync SELLER_STRIPE_ACCOUNTS |
| transfer.created | Ghi stripe_transfer_id |

---

---

# ⭐ Loyalty Service

**Port**: `:8084`
**Mô tả**: Điểm thưởng tích lũy · Tính qua Order Service

### GET /loyalty/balance
**Số dư điểm thưởng**

**Quyền truy cập**: JWT Required

**Response 200**:
```json
{
  "user_id": 42,
  "available_points": 1250,
  "pending_points": 300,
  "expired_points": 50,
  "total_earned": 2000,
  "conversion_rate": 200
}
```

**Ghi chú**:
- `conversion_rate`: số điểm tương đương 1.000 VNĐ
- `pending_points`: điểm từ đơn chưa DELIVERED

---

### GET /loyalty/transactions
**Lịch sử giao dịch điểm**

**Quyền truy cập**: JWT Required

**Query Params**:
| Param | Type | Mô tả |
|-------|------|-------|
| type | string | EARNED \| USED \| EXPIRED \| REFUNDED |
| status | string | PENDING \| CONFIRMED |

---

### GET /loyalty/estimate
**Ước tính điểm sẽ nhận / có thể dùng**

**Quyền truy cập**: JWT Required

**Query Params**:
| Param | Type | Required | Mô tả |
|-------|------|----------|-------|
| order_amount | decimal | ✓ | Tổng giá trị đơn hàng (VNĐ) |
| points_to_use | integer | - | Số điểm muốn dùng (preview discount) |

**Response 200**:
```json
{
  "points_to_earn": 350,
  "max_points_usable": 175,
  "discount_if_max_use": 875,
  "conversion_rate": 200,
  "cap_percent": 20
}
```

---

---

# ⚡ Flash Sale Service

**Port**: `:8086`
**Mô tả**: WebFlux · Redis Lua Script · Chống oversell · 50k req/s

### GET /flash-sale/sessions
**Danh sách flash sale sessions**

**Quyền truy cập**: Public

**Mô tả**: Trả về các session UPCOMING và ACTIVE. Session ENDED không xuất hiện. Trạng thái được JOB-01 cập nhật mỗi phút.

**Query Params**:
| Param | Type | Mô tả |
|-------|------|-------|
| status | string | UPCOMING \| ACTIVE (optional) |

**Response 200**:
```json
{
  "server_time": "2025-11-01T19:58:00Z",
  "sessions": [
    {
      "session_id": 3,
      "name": "Flash Sale 20h Thứ 6",
      "status": "UPCOMING",
      "start_time": "2025-11-01T20:00:00Z",
      "end_time": "2025-11-01T22:00:00Z",
      "item_count": 15
    }
  ]
}
```

**⚠️ Ghi chú**: Client phải dùng `server_time` để tính countdown, không dùng đồng hồ client

---

### GET /flash-sale/sessions/{sessionId}
**Chi tiết session + items**

**Quyền truy cập**: Public

**Response 200**: Chi tiết session kèm tất cả FS_ITEMS APPROVED

---

### POST /flash-sale/sessions
**Tạo session mới (Admin)**

**Quyền truy cập**: JWT Required (ADMIN)

**Request Body**:
```json
{
  "name": "string",         // Tên flash sale session (Required)
  "start_time": "datetime", // Thời điểm bắt đầu (ISO 8601) (Required)
  "end_time": "datetime"    // Thời điểm kết thúc (ISO 8601) (Required)
}
```

---

### GET /admin/flash-sale/sessions
**Danh sách Flash Sale Sessions (Admin — toàn bộ trạng thái)**

**Quyền truy cập**: JWT Required (ADMIN)
**Tag**: NEW v5.1 — Gap A

**Mô tả**: Trả về toàn bộ FS_SESSIONS bao gồm UPCOMING, ACTIVE, ENDED.

**Query Params**:
| Param | Type | Mô tả |
|-------|------|-------|
| status | string | UPCOMING \| ACTIVE \| ENDED (optional) |
| page, size | integer | Phân trang |

---

### PUT /admin/flash-sale/sessions/{sessionId}
**Cập nhật Flash Sale Session (Admin)**

**Quyền truy cập**: JWT Required (ADMIN)
**Tag**: NEW v5.1 — Gap A

**⚠️ Chặn**: Cập nhật nếu session đang ACTIVE hoặc ENDED

**Request Body** (all optional):
```json
{
  "name": "string",
  "start_time": "datetime",
  "end_time": "datetime"
}
```

---

### DELETE /admin/flash-sale/sessions/{sessionId}
**Xóa Flash Sale Session (Admin)**

**Quyền truy cập**: JWT Required (ADMIN)
**Tag**: NEW v5.1 — Gap A

**⚠️ Chặn**: Xóa nếu session ACTIVE hoặc có FS_ITEMS APPROVED

---

### POST /flash-sale/sessions/{sessionId}/items
**Đăng ký sản phẩm vào session**

**Quyền truy cập**: JWT Required (SELLER)
**Tags**: Kafka → (pending admin approval)

**Điều kiện hợp lệ (6 điều kiện)**:
```
1. session.status == UPCOMING
2. seller.details_submitted == true
3. seller.trust_score >= ngưỡng config
4. flash_price < variant.price
5. flash_stock <= stock_available
6. sku chưa có FS_ITEM PENDING/APPROVED trong session này
```

**Request Body**:
```json
{
  "sku_code": "string",          // Mã SKU tham gia flash sale (Required)
  "flash_price": "decimal",      // Giá flash sale (Required)
  "flash_stock": "integer",      // Số lượng dành cho Flash Sale (Required)
  "limit_per_user": "integer"    // Giới hạn mua tối đa mỗi user (1–10) (Required)
}
```

**Response 201**: FS_ITEMS.status = PENDING — chờ Admin duyệt

---

### POST /flash-sale/sessions/{sessionId}/items/{itemId}/approve
**Duyệt item (Admin)**

**Quyền truy cập**: JWT Required (ADMIN)
**Tags**: Kafka → flash_sale.item_approved

**Request Body**:
```json
{
  "note": "string"  // Ghi chú duyệt item (Optional)
}
```

**Response 200**: FS_ITEMS.status = APPROVED

---

### POST /admin/flash-sale/items/{itemId}/reject
**Từ chối Flash Sale Item (Admin)**

**Quyền truy cập**: JWT Required (ADMIN)
**Tags**: Kafka → flash_sale.item_rejected | NEW v5.1 — Gap A

**Request Body**:
```json
{
  "reject_reason": "string"  // Lý do từ chối, tối đa 500 ký tự (Required)
}
```

**Response 200**: FS_ITEMS.status = REJECTED

---

### POST /flash-sale/sessions/{sessionId}/buy
**⚡ Mua flash sale — Chịu tải cao**

**Quyền truy cập**: JWT Required (BUYER)
**Tags**: Redis Lua Script | Kafka → flash_sale.item_sold

**Request Body**:
```json
{
  "fs_item_id": "long",      // ID flash sale item (Required)
  "quantity": "integer",     // Số lượng muốn mua (> 0) (Required)
  "address_id": "long"       // ID địa chỉ giao hàng (Required)
}
```

**Response 201**: Chốt đơn thành công

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 409 | SOLD_OUT — Hết hàng (Redis atomic check) |
| 400 | LIMIT_EXCEEDED — Vượt giới hạn mua mỗi user |

---

### POST /flash-sale/sessions/{sessionId}/reminders
**Đăng ký nhắc nhở**

**Quyền truy cập**: JWT Required (BUYER)

**Request Body**: (không có body)

---

### DELETE /flash-sale/sessions/{sessionId}/reminders
**Hủy nhắc nhở**

**Quyền truy cập**: JWT Required

**Response 200**: Hủy đăng ký nhắc nhở thành công

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 404 | User chưa đăng ký nhắc nhở cho session này |

---

---

# 🔔 Notification Service

**Port**: `:8088`
**Mô tả**: WebFlux · SSE · Redis Pub/Sub · MongoDB TTL 90 ngày

### GET /notifications/stream
**Kết nối SSE real-time (text/event-stream)**

**Quyền truy cập**: JWT Required

**[UNIFIED v5.3]** Endpoint SSE (Server-Sent Events) — trả về `text/event-stream`. Connection giữ mở, server push event khi có thông báo. Dùng `EventSource` API.

**SSE Format**:
```
data: {"notif_id":"64f3a...","type":"REFUND_APPROVED","title":"Hoàn tiền thành công","body":"Yêu cầu hoàn 350.000đ đã được duyệt","priority":"NORMAL","metadata":{"deeplink":"/orders/100/refunds/88"},"created_at":"2025-10-05T14:00:00Z"}
```

**Ghi chú**:
- Redis Pub/Sub buffer: 60 giây
- Header `Last-Event-ID` để replay event bị bỏ lỡ
- Không có query params — dùng `GET /notifications` cho lịch sử

---

### GET /notifications
**Danh sách thông báo (Pagination)**

**Quyền truy cập**: JWT Required

**[UNIFIED v5.3]** Endpoint REST trả về danh sách có phân trang từ MongoDB.

**Query Params**:
| Param | Type | Mô tả |
|-------|------|-------|
| is_read | boolean | true = đã đọc \| false = chưa đọc (optional) |
| page, size | integer | Phân trang (default 0, 20) |

**Response 200**:
```json
{
  "content": [
    {
      "notif_id": "64f3a...",
      "type": "REFUND_APPROVED",
      "title": "Hoàn tiền thành công",
      "body": "Yêu cầu hoàn 350.000đ đã được duyệt",
      "is_read": false,
      "priority": "NORMAL",
      "metadata": { "deeplink": "/orders/100/refunds/88" },
      "created_at": "2025-10-05T14:00:00Z",
      "expires_at": "2026-01-05T14:00:00Z"
    }
  ],
  "total_elements": 24,
  "unread_count": 5
}
```

---

### PATCH /notifications/{notifId}/read
**Đánh dấu đã đọc**

**Quyền truy cập**: JWT Required

**Request Body**: (không có body)

**Response 200**: MG_NOTIFICATIONS.is_read = true

---

### PATCH /notifications/read-all
**Đánh dấu tất cả đã đọc**

**Quyền truy cập**: JWT Required

**Request Body**: (không có body)

**Response 200**:
```json
{
  "updated_count": 5
}
```

---

### GET /notifications/unread-count
**Đếm thông báo chưa đọc**

**Quyền truy cập**: JWT Required

**Response 200**:
```json
{
  "unread_count": 5
}
```

---

---

# 🧭 Kafka Topics Catalog

Tổng hợp toàn bộ Kafka topics:

| Topic | Nguồn | Consumer | Ghi chú |
|-------|-------|----------|--------|
| **account.auto_locked** | Identity/Worker | Notification Service | Tự khóa tài khoản |
| **account.locked** | Identity/Worker | Search/Notification | Khóa tài khoản, ẩn dữ liệu |
| **account.unlocked** | Identity | Notification Service | Mở khóa tài khoản |
| **flash_sale.session_started** | Flash Sale | Notification Service | Phiên Flash Sale bắt đ��u |
| **flash_sale.session_ended** | Flash Sale | Notification Service | Phiên Flash Sale kết thúc |
| **flash_sale.item_approved** | Admin | Notification Service | Item được duyệt |
| **flash_sale.item_rejected** | Admin | Notification Service | Item bị từ chối |
| **flash_sale.item_sold** | Flash Sale | Inventory/cache | Cập nhật sold_qty |
| **flash_sale.reminder** | JOB-02 | Notification Service | Nhắc nhở Flash Sale |
| **order.auto_cancelled** | JOB-13 | Notification Service | Đơn bị hủy tự động |
| **order.cancelled** | Order Service | Flash Sale/Loyalty | Hủy đơn |
| **order.created** | Order Service | Inventory/Search | Giữ khóa stock |
| **order.delivered** | Order Service | Identity/Loyalty | Cộng điểm |
| **order.shipped** | Order Service | Notification Service | Báo trạng thái giao hàng |
| **order.returned** | Order Service | Refund/Notification | RTS - hàng hoàn |
| **order.checkout_completed** | Order Service | Cart Service | Xóa item đã mua |
| **payment.failed** | Payment Service | Order/Notification | Thanh toán thất bại |
| **payment.success** | Payment Service | Order Service | Thanh toán thành công |
| **product.approved** | Admin | Search/Identity | Index sản phẩm |
| **product.auto_hidden** | JOB-16 | Search/Notification | Ẩn sản phẩm rejected |
| **product.rejected** | Admin | Notification Service | Seller nhận lý do từ chối |
| **product.updated** | Product Service | Search Service | Đồng bộ cập nhật |
| **product.deleted** | Product Service | Search Service | Deindex sản phẩm |
| **refund.admin_approved** | Refund Admin | Notification Service | Refund được duyệt |
| **refund.rejected** | Refund Admin | Notification Service | Refund bị từ chối |
| **refund.requested** | Buyer | Notification Service | Yêu cầu hoàn tiền |
| **refund.stripe_auto** | Payment Service | Order/Loyalty | Stripe Chargeback |
| **seller.posting_resumed** | Identity | Notification Service | Mở lại quyền đăng bài |
| **seller.posting_suspended** | Identity | Notification Service | Tạm dừng quyền đăng bài |
| **seller.order_cancelled** | Order Service | Identity | Seller hủy đơn |
| **stripe.account_suspended** | Payment | Notification Service | Stripe Express bị đình chỉ |
| **trust_score.warning** | Identity | Notification Service | Cảnh báo điểm tín nhiệm |
| **inventory.adjusted** | Inventory | Search Service | Đồng bộ tồn kho |
| **appeal.resolved** | Identity | Notification Service | Kết quả xét duyệt khiếu nại |
| **category.updated** | Product Service | Search Service | Admin sửa danh mục |

---

---

# 🛡️ Admin APIs

**Mô tả**: Duyệt sản phẩm, quản lý user, trust score, điều hành hệ thống

## Product Management

### GET /admin/products/pending
**Sản phẩm chờ duyệt**

**Quyền truy cập**: JWT Required (ADMIN)

**Query Params**:
| Param | Type | Mô tả |
|-------|------|-------|
| category_id | string | Lọc theo danh mục |
| seller_id | long | Lọc theo seller |
| page, size | integer | Phân trang |

---

### POST /admin/products/{productId}/approve
**Duyệt sản phẩm**

**Quyền truy cập**: JWT Required (ADMIN)
**Tags**: Kafka → product.approved

**Request Body**:
```json
{
  "note": "string"  // Ghi chú duyệt sản phẩm (Optional)
}
```

---

### POST /admin/products/{productId}/reject
**Từ chối sản phẩm**

**Quyền truy cập**: JWT Required (ADMIN)

**Request Body**:
```json
{
  "reason": "string",  // Lý do từ chối sản phẩm (Required)
  "note": "string"     // Ghi chú admin (Optional)
}
```

---

## User Management

### GET /admin/users
**Danh sách người dùng**

**Quyền truy cập**: JWT Required (ADMIN)

**Query Params**:
| Param | Type | Mô tả |
|-------|------|-------|
| status | string | ACTIVE \| LOCKED |
| role | string | BUYER \| SELLER \| ADMIN |
| trust_score_min / trust_score_max | integer | Khoảng Trust Score [0–100] |
| product_posting_suspended | boolean | true = chỉ Seller bị đình chỉ |
| q | string | Tìm theo username, email, phone |
| page, size | integer | Phân trang |

**Response 200**: Danh sách USERS với phân trang

---

### POST /admin/users/{userId}/lock
**Khóa tài khoản**

**Quyền truy cập**: JWT Required (ADMIN)
**Tags**: Revoke JWTs | Kafka → account.locked

**Mô tả**: Identity Service tức thì thêm toàn bộ JTI vào Redis blocklist.

**Request Body**:
```json
{
  "reason": "string",            // Lý do khóa (Required)
  "locked_until": "datetime"     // Thời điểm tự mở khóa (Optional - null = vĩnh viễn)
}
```

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 409 | Tài khoản đã bị LOCKED |

---

### POST /admin/users/{userId}/unlock
**Mở khóa tài khoản**

**Quyền truy cập**: JWT Required (ADMIN)
**Tags**: Kafka → account.unlocked

**Request Body**:
```json
{
  "reason": "string"  // Lý do mở khóa (Required)
}
```

---

### POST /admin/users/{userId}/trust-score
**Điều chỉnh trust score thủ công**

**Quyền truy cập**: JWT Required (ADMIN)

**Request Body**:
```json
{
  "delta": "integer",  // Số điểm thay đổi (+/-) (Required)
  "reason": "string"   // Lý do (Required)
}
```

**Response 200**: Trust score cập nhật, TRUST_SCORE_LOGS ghi

---

### GET /admin/users/{userId}/trust-score/logs
**Lịch sử trust score**

**Quyền truy cập**: JWT Required (ADMIN)

**Query Params**: Phân trang

---

### GET /admin/users/{userId}/ban-history
**Lịch sử khóa/mở tài khoản**

**Quyền truy cập**: JWT Required (ADMIN)
**Tag**: NEW v5.0

**Response 200**: Array USER_BAN_HISTORY

---

### POST /admin/users/{userId}/unlock-product-posting
**Gỡ tạm dừng đăng sản phẩm (Seller)**

**Quyền truy cập**: JWT Required (ADMIN)
**Tag**: NEW v5.0

**Request Body**:
```json
{
  "note": "string"  // Lý do cho phép tiếp tục (Required)
}
```

---

## Trust Score Configuration

### GET /admin/trust-score-events-config
**Xem cấu hình delta sự kiện trust score**

**Quyền truy cập**: JWT Required (ADMIN)
**Tag**: NEW v5.0

**Response 200**: Array TRUST_SCORE_EVENTS_CONFIG

---

### PUT /admin/trust-score-events-config/{eventCode}
**Cập nhật delta / bật-tắt sự kiện**

**Quyền truy cập**: JWT Required (ADMIN)
**Tag**: NEW v5.0

**⚠️ Ghi chú**: Thay đổi có hiệu lực ngay, không áp dụng hồi tố

**Request Body** (all optional):
```json
{
  "delta": "integer",       // Delta mới (+/-)
  "description": "string",  // Mô tả mới
  "is_active": "boolean"    // Bật/tắt sự kiện
}
```

---

## Appeal Management

### GET /admin/appeals
**Danh sách khiếu nại Trust Score chờ xét duyệt**

**Quyền truy cập**: JWT Required (ADMIN)
**Tag**: GAP-PATCH

**Query Params**:
| Param | Type | Mô tả |
|-------|------|-------|
| status | string | PENDING \| APPROVED \| REJECTED (default: PENDING) |
| page, size | integer | Phân trang |

---

### POST /admin/appeals/{appealId}/resolve
**Duyệt hoặc từ chối khiếu nại**

**Quyền truy cập**: JWT Required (ADMIN)
**Tags**: Kafka → appeal.resolved | GAP-PATCH

**Request Body**:
```json
{
  "action": "string",         // APPROVED | REJECTED (Required)
  "admin_note": "string"      // Ghi chú lý do quyết định (Required)
}
```

**Response 200**: Xử lý thành công

---

## Failed Events Management

### GET /admin/failed-events
**Danh sách events thất bại (Admin)**

**Quyền truy cập**: JWT Required (ADMIN)

**[UNIFIED v5.3]** Danh sách Kafka event / scheduled task bị lỗi. Admin dùng để xem nguyên nhân, retry thủ công, hoặc mark RESOLVED.

**Query Params**:
| Param | Type | Mô tả |
|-------|------|-------|
| status | string | PENDING \| DEAD \| RESOLVED \| MANUAL_INTERVENTION |
| topic_or_task | string | Lọc theo topic Kafka hoặc tên task |
| page, size | integer | Phân trang |

**Response 200**:
```json
{
  "content": [
    {
      "event_id": 42,
      "topic_or_task": "order.delivered",
      "payload": {"orderId": 1001, "userId": 42},
      "error_reason": "Loyalty Service connection timeout",
      "retry_count": 5,
      "status": "DEAD",
      "created_at": "2025-10-05T14:00:00Z",
      "updated_at": "2025-10-05T15:30:00Z"
    }
  ],
  "total_elements": 3,
  "total_pages": 1
}
```

---

### POST /admin/failed-events/{eventId}/retry
**Retry thủ công event thất bại**

**Quyền truy cập**: JWT Required (ADMIN)
**Tags**: Kafka → re-publish | NEW v5.1 — Gap A

**Mô tả**: Re-publish payload vào Kafka topic ban đầu. Bắt buộc idempotent.

**Request Body**: (không có body)

**Response 200**: Event được re-publish

---

### POST /admin/failed-events/{eventId}/resolve
**Đánh dấu event đã xử lý thủ công**

**Quyền truy cập**: JWT Required (ADMIN)
**Tag**: NEW v5.1 — Gap A

**Mô tả**: Dành cho trường hợp Admin xử lý ngoài hệ thống.

**Request Body**:
```json
{
  "resolution_note": "string"  // Mô tả cách xử lý (Required)
}
```

---

---

## 📊 Summary

### 🔐 Total API Endpoints by Service

| Service | Port | Endpoints |
|---------|------|-----------|
| Identity Service | 8081 | 18 |
| Product Service | 8082 | 16 |
| Search Service | 8089 | 2 |
| Cart Service | 8083 | 5 |
| Order Service | 8087 | 8 |
| Refund API | - | 8 |
| Payment Service | 8085 | 5 |
| Loyalty Service | 8084 | 3 |
| Flash Sale Service | 8086 | 11 |
| Notification Service | 8088 | 5 |
| Admin APIs | - | 14 |
| **TOTAL** | - | **95+** |

### 🎯 Authentication Requirements

- **Public Endpoints**: Không cần JWT (các trang chủ, tìm kiếm công khai, v.v.)
- **Protected Endpoints**: JWT Required (header: `Authorization: Bearer <token>`)
- **Admin Endpoints**: JWT Required + ADMIN role
- **Seller Endpoints**: JWT Required + SELLER role (hoặc owner check)
- **Buyer Endpoints**: JWT Required + BUYER role

### 📍 Key Concepts

- **Trust Score Tiers**: 6 levels từ BRONZE (0-39) đến ELITE (90-99)
- **Order Status**: PENDING → PAID → SHIPPING → DELIVERED
- **Refund Types**: FULL (toàn đơn), PARTIAL (theo item)
- **Kafka Events**: 35+ topics cho event-driven architecture
- **MinIO Integration**: Presigned URLs cho upload file
- **Stripe Connect**: Seller onboarding và payment processing

---

## 📚 Complete Documentation Reference

### Supplementary API Documentation
All API details are in this document. For business context see related docs below.

### Business Logic & Requirements
- **[03_BUSINESS.md](03_BUSINESS.md)**
  - 9 workflows (authentication, products, orders, payments, refunds, flash sales, loyalty, trust score)
  - Policies: trust score, refund, flash sale, loyalty, data retention, security
  - Complete user journeys for Buyer, Seller, Admin
  - NEW v5.3: Tracking number for refunds, Return To Sender (RTS) workflow

### System Configuration & Rules
- **[04_POLICIES.md](04_POLICIES.md)**
  - Trust Score tier configuration (BRONZE to ELITE)
  - Account lifecycle management
  - Flash sale participation rules
  - JWT revocation, User ban history

### Operations & Data Retention
- **[05_OPERATIONS.md](05_OPERATIONS.md)**
  - 23 cronjobs with SQL logic (JOB-01 to JOB-22)
  - Retention periods for each table
  - Flash sale reconciliation (JOB-21)
  - Auto-delivered stale orders (JOB-22)
  - External storage policy (Redis, MinIO)

### Architecture & Diagrams
- **[01_OVERVIEW.md](01_OVERVIEW.md)** - Architecture & tech stack
- **[07_BUSINESS_FLOWS.md](07_BUSINESS_FLOWS.md)** - Luồng nghiệp vụ tổng hợp (Mermaid)
- **[erd.mermaid](erd.mermaid)** - Complete Entity-Relationship Diagram
- **[00_INDEX.md](00_INDEX.md)** - Navigation & quick links

---

## 🔗 How to Use This API Spec with Other Documents

| API Endpoint | Related Business Logic | System Policy | Data Retention |
|-------------|----------------------|-----------------|-----------------|
| POST /auth/register | User Registration | Account Lifecycle | USERS table |
| POST /orders | Order Creation | Order Workflow | ORDERS retention |
| PUT /admin/refunds/{id}/approve | Refund Approval (with tracking) | Refund Policy | Refund cleanup |
| GET /flash-sale/sessions | Flash Sale | Flash Sale Policy | FS_ITEMS retention (JOB-08) |
| POST /loyalty/use-points | Loyalty Points | Loyalty Policy | POINT_TRANSACTIONS (JOB-03) |
| PUT /admin/users/{id}/lock | Account Lock | Account Lifecycle | USER_BAN_HISTORY (JOB-17) |

---

## 📚 Detailed Request/Response Examples & Error Formats

### Full Example: POST /orders/checkout (Multi-Vendor Split)

**Request**:
```json
{
  "address_id": 7,
  "item_ids": [201, 202, 203],
  "use_loyalty_points": true,
  "loyalty_points_to_use": 50
}
```

**Response 201** (Multi-Vendor Example with 2 Sellers):
```json
{
  "parent_order_id": 55,
  "order_code": "PO-20251001-55",
  "orders": [
    {
      "order_id": 100,
      "order_code": "OR-20251001-100",
      "seller_id": 5,
      "seller_name": "Shop Nike VN",
      "seller_trust_score": 92,
      "total_amt": 700000,
      "final_amt": 650000,
      "status": "PENDING",
      "items_count": 2,
      "items": [
        {
          "order_item_id": 501,
          "sku_code": "NK-AIR-RED-XL",
          "product_name": "Áo Thun Nike Air",
          "quantity": 2,
          "unit_price": 350000,
          "total": 700000
        }
      ],
      "created_at": "2025-10-01T10:00:00Z"
    },
    {
      "order_id": 101,
      "order_code": "OR-20251001-101",
      "seller_id": 9,
      "seller_name": "Shop Adidas VN",
      "seller_trust_score": 88,
      "total_amt": 500000,
      "final_amt": 500000,
      "status": "PENDING",
      "items_count": 1,
      "items": [
        {
          "order_item_id": 601,
          "sku_code": "AD-ULTRA-BLK-10",
          "product_name": "Giày Adidas Ultraboost",
          "quantity": 1,
          "unit_price": 500000,
          "total": 500000
        }
      ],
      "created_at": "2025-10-01T10:00:00Z"
    }
  ],
  "shipping_address": {
    "address_id": 7,
    "full_address": "123 Nguyễn Trãi, Phường 2, Q.3, TP.HCM",
    "province_id": 79,
    "district_id": 760
  },
  "payment": {
    "total_amount": 1200000,
    "loyalty_discount": 50000,
    "loyalty_points_used": 50,
    "final_amount": 1150000,
    "currency": "VND"
  },
  "total_items": 3,
  "total_sellers": 2,
  "payment_status": "PENDING",
  "timeout_at": "2025-10-01T10:30:00Z",
  "created_at": "2025-10-01T10:00:00Z"
}
```

---

### Full Example: POST /orders/{orderId}/return-to-sender (RTS - NEW v5.3)

**Request** (multipart/form-data):
```
Content-Type: multipart/form-data

[files]
evidence_images: [file1.jpg, file2.jpg]

[fields]
return_tracking_number: "VT999888777"
note: "Hoàn do không gọi được Buyer, địa chỉ sai"
```

**Response 200**:
```json
{
  "order_id": 1001,
  "order_code": "OR-20251001-1001",
  "order_status": "RETURNED",
  "refund_id": 99,
  "refund_code": "RF-20251001-99",
  "refund_status": "PENDING",
  "refund_amount": 250000,
  "return_tracking_number": "VT999888777",
  "evidence_count": 2,
  "estimated_refund_days": 3,
  "stripe_refund_id": "re_3Px5Ab...",
  "message": "Hàng hoàn đã được ghi nhận. Hệ thống đang tự động hoàn tiền cho Buyer.",
  "seller_notification": {
    "status": "sent",
    "message": "Xác nhận hàng hoàn đã được lưu. Tồn kho đã được cộng lại."
  },
  "buyer_notification": {
    "status": "sent",
    "message": "Seller đã nhận lại hàng hoàn. Tiền đang được hoàn về tài khoản của bạn."
  },
  "created_at": "2025-10-01T14:30:00Z"
}
```

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 200 | Xác nhận RTS thành công |
| 409 | Đơn hàng đã ở trạng thái RETURNED hoặc đã có refund đang xử lý |
| 422 | Trạng thái đơn không hợp lệ — chỉ cho phép khi order.status = SHIPPING |
| 403 | Không phải Seller của đơn hàng |
| 400 | evidence_images không hợp lệ hoặc không cung cấp |

---

### Full Example: POST /admin/refunds/{refundId}/approve (Tracking Number - NEW v5.3)

**Request**:
```json
{
  "admin_note": "Hoàn do giao hàng không thành công, shipper mang lại lần 3",
  "adjust_amount": null,
  "caused_by": "SELLER",
  "tracking_number": "VT123456789"
}
```

**Response 200**:
```json
{
  "refund_id": 88,
  "refund_code": "RF-20251005-88",
  "status": "SUCCESS",
  "type": "PARTIAL",
  "amount": 500000,
  "adjust_amount": null,
  "tracking_number": "VC123456789",
  "return_evidence": [
    {
      "type": "tracking",
      "tracking_number": "VT123456789",
      "recorded_at": "2026-04-15T10:30:00Z"
    }
  ],
  "reviewed_by": 1,
  "admin_id": 1,
  "admin_name": "Admin User",
  "admin_note": "Hoàn do giao hàng không thành công...",
  "reviewed_at": "2026-04-15T10:30:00Z",
  "stripe_refund_id": "re_3Px5Ab...",
  "trust_score_adjustment": {
    "seller_id": 5,
    "delta": -5,
    "event_code": "SELLER_CAUSED_REFUND",
    "new_score": 87,
    "triggered": true
  },
  "loyalty_adjustment": {
    "buyer_id": 42,
    "points_returned": 50,
    "status": "refunded"
  },
  "notifications": {
    "buyer": {
      "status": "sent",
      "message": "Hoàn tiền được duyệt. Mã vận đơn hoàn: VT123456789"
    },
    "seller": {
      "status": "sent",
      "message": "Refund đã được xử lý. Trust score - 5 điểm."
    }
  },
  "stripe_response": {
    "id": "re_3Px5Ab",
    "object": "refund",
    "status": "succeeded",
    "amount": 500000,
    "currency": "vnd",
    "created": 1713177000,
    "metadata": {
      "tracking_number": "VT123456789"
    }
  },
  "created_at": "2025-10-05T14:00:00Z",
  "updated_at": "2026-04-15T10:30:00Z"
}
```

---

### Full Example: GET /loyalty/balance (Detailed Point Breakdown)

**Response 200**:
```json
{
  "user_id": 42,
  "loyalty_account_id": 123,
  "available_points": 1250,
  "pending_points": 300,
  "expired_points": 50,
  "total_earned": 2000,
  "total_used": 650,
  "conversion_rate": 200,
  "note": "1 point = 1/200 of 200,000 VND = 1,000 VND",
  "max_usable_per_order": 275,
  "max_usable_percentage": 0.20,
  "expiry_policy": {
    "expiry_days": 365,
    "next_expiry_date": "2026-10-05",
    "points_expiring_soon": 0
  },
  "tier_benefits": {
    "tier": "PLATINUM",
    "trust_score": 80,
    "earning_rate": "5%",
    "max_discount_rate": "20%"
  },
  "recent_transactions": [
    {
      "transaction_id": 501,
      "type": "EARNED",
      "delta": 300,
      "status": "PENDING",
      "order_id": 100,
      "order_code": "OR-20251001-100",
      "created_at": "2025-10-01T10:00:00Z",
      "expires_at": "2026-10-01T10:00:00Z"
    }
  ]
}
```

---

## Error Response Formats

### Standard Error Response

```json
{
  "error": "RESOURCE_NOT_FOUND",
  "message": "Không tìm thấy resource",
  "details": "Order với ID 9999 không tồn tại",
  "status_code": 404,
  "timestamp": "2026-04-15T10:30:00Z",
  "path": "/api/v1/orders/9999",
  "request_id": "req-abc123def456"
}
```

### Validation Error Response

```json
{
  "error": "VALIDATION_FAILED",
  "message": "Lỗi validation",
  "status_code": 400,
  "violations": [
    {
      "field": "loyalty_points_to_use",
      "value": 1000,
      "message": "Không thể dùng quá 20% giá trị đơn",
      "constraint": "LOYALTY_POINTS_MAX_PERCENTAGE",
      "max_allowed": 230
    }
  ]
}
```

### Account Locked Error

```json
{
  "error": "ACCOUNT_LOCKED",
  "message": "Tài khoản bị khóa",
  "lock_reason": "Trust score quá thấp (< 10). Liên hệ support để khiếu nại.",
  "locked_until": "2026-05-15T10:00:00Z",
  "status_code": 403
}
```

### Invalid Order State Error

```json
{
  "error": "INVALID_ORDER_STATE",
  "message": "Trạng thái đơn không hợp lệ",
  "details": "Chỉ cho phép RTS khi order.status = SHIPPING",
  "current_status": "DELIVERED",
  "status_code": 422
}
```

---

## Query Parameters — Complete Specifications

### Order Filtering (GET /orders)

```
GET /orders?
  status=SHIPPING&
  from_date=2025-10-01T00:00:00Z&
  to_date=2025-10-31T23:59:59Z&
  page=0&
  size=20
```

| Param | Type | Range | Example | Notes |
|-------|------|-------|---------|-------|
| status | string | PENDING\|PAID\|SHIPPING\|DELIVERED\|RETURNED\|CANCELLED\|PARTIALLY_REFUNDED\|REFUNDED | SHIPPING | Optional filter |
| from_date | ISO 8601 | - | 2025-10-01T00:00:00Z | Inclusive |
| to_date | ISO 8601 | - | 2025-10-31T23:59:59Z | Inclusive |
| page | integer | 0-∞ | 0 | Zero-indexed |
| size | integer | 1-100 | 20 | Default 20 |

---

## Key Implementation Notes for v5.3

### 1. Tracking Number for Refunds
- **Field**: `tracking_number` in `POST /admin/refunds/{refundId}/approve`
- **Storage**: Saved to `REFUND_ITEMS.tracking_number`
- **Audit**: Recorded in `REFUND_ITEMS.return_evidence`
- **Notification**: Buyer receives tracking info in notification
- **Use Cases**:
  - When refund is related to return shipping
  - When admin needs to track goods movement
  - For legal compliance and audit trail

### 2. Return To Sender (RTS) Flow
- **Trigger**: Seller calls `POST /orders/{orderId}/return-to-sender`
- **Conditions**: Order must be in SHIPPING state
- **Evidence**: Required 1-5 images of return package
- **Auto-Refund**: No admin approval needed
- **Stock Recovery**: Immediate atomic increment
- **Notifications**: Both Buyer & Seller notified

### 3. Multi-Vendor Payment Split
- **Parent Order**: Single payment transaction
- **Sub-Orders**: One per seller
- **Stripe Handling**: Automatic split via Stripe Connect
- **Transfer Tracking**: Each transfer recorded in SELLER_TRANSFERS
- **Partial Refunds**: Transfer Reversal per seller

### 4. Loyalty Points Management
- **Earning**: PENDING until order DELIVERED
- **Usage**: Can use only CONFIRMED/AVAILABLE points
- **Refund**: Automatic return when order refunded
- **Expiry**: 365 days from EARNED transaction date
- **Max Usage**: 20% of order value

---

1. **Tracking Number for Refunds (v5.3)**
   - See: `POST /admin/refunds/{refundId}/approve` (tracking_number parameter)
   - See: BUSINESS_DOC - "Admin Duyệt Hoàn Tiền" section
   - Database: Save to REFUND_ITEMS.tracking_number

2. **Return To Sender (RTS) - NEW v5.3**
   - See: BUSINESS_DOC - "Luồng: Hàng Hoàn Về Seller"
   - No Admin approval needed - automatic refund
   - Seller provides tracking number & evidence

3. **Cronjob Integration**
   - All cleanup operations are defined in DATA_RETENTION_POLICY
   - See specific JOB-XX for logic and schedule
   - Example: JOB-13 auto-cancels PENDING orders every 5 minutes

4. **Trust Score Tier System**
   - See: SYSTEM_POLICY - Trust Score policy
   - 6 tiers: BRONZE (0-39) → ELITE (90-99)
   - Used to determine Flash Sale limits, refund capabilities, etc.

---


---

## PHẦN 2 — API_DETAILED_JSON_v5_3_RTS.md

# 📋 API Detailed JSON Request/Response v5.3 RTS [COMPLETE]

**Phiên bản:** v5.3 RTS
**Cập nhật:** 2026-04-15
**Trạng thái:** Production-Ready ✅

Tài liệu này trình bày **chi tiết** toàn bộ JSON request/response, Kafka payloads, và validation rules cho các API endpoints. Dựa trên ERD, nghiệp vụ hệ thống, và v5.3 RTS updates.

---

## 📚 Mục Lục

1. [🔐 Identity Service APIs](#-identity-service-apis)
2. [📦 Product Service APIs](#-product-service-apis)
3. [🔍 Search Service APIs](#-search-service-apis)
4. [🛒 Cart Service APIs](#-cart-service-apis)
5. [📋 Order Service APIs](#-order-service-apis)
6. [↩️ Refund APIs](#-refund-apis)
7. [💳 Payment Service APIs](#-payment-service-apis)
8. [⭐ Loyalty Service APIs](#-loyalty-service-apis)
9. [⚡ Flash Sale Service APIs](#-flash-sale-service-apis)
10. [🔔 Notification Service APIs](#-notification-service-apis)
11. [🛡️ Admin APIs](#-admin-apis)
12. [🧭 Kafka Topics & Payloads](#-kafka-topics--payloads)
13. [❌ Error Response Formats](#-error-response-formats)

---

# 🔐 Identity Service APIs

**Port:** `:8081`

## POST /auth/register

**Đăng ký tài khoản mới**

### Request

```json
{
  "username": "nguyen_van_a",
  "email": "a@example.com",
  "phone": "0901234567",
  "password": "SecurePass123!",
  "full_name": "Nguyễn Văn A"
}
```

### Validation Rules

| Field | Type | Rules |
|-------|------|-------|
| username | string | 3-50 chars, a-z, 0-9, dot, underscore; Unique |
| email | string | Valid email format; Unique |
| phone | string | Vietnam format; Unique |
| password | string | Min 8 chars; ≥1 uppercase, ≥1 number |
| full_name | string | 2-100 chars |

### Response 201 (Success)

```json
{
  "user_id": 42,
  "username": "nguyen_van_a",
  "email": "a@example.com",
  "phone": "0901234567",
  "full_name": "Nguyễn Văn A",
  "roles": ["BUYER"],
  "status": "ACTIVE",
  "trust_score": 80,
  "trust_tier": "PLATINUM",
  "avatar_url": null,
  "created_at": "2026-04-15T08:00:00Z"
}
```

### Kafka Events

```json
{
  "topic": "account.created",
  "payload": {
    "user_id": 42,
    "email": "a@example.com",
    "phone": "0901234567",
    "timestamp": "2026-04-15T08:00:00Z",
    "source": "auth-service"
  }
}
```

---

## POST /auth/login

**Đăng nhập, nhận JWT**

### Request

```json
{
  "credential": "a@example.com",
  "password": "SecurePass123!"
}
```

### Validation Rules

| Field | Type | Rules |
|-------|------|-------|
| credential | string | username \| email \| phone |
| password | string | Min 1 char |

### Response 200 (Success)

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiI0MiIsImlhdCI6MTcxMzcwMDAwMCwiZXhwIjoxNzEzNzAwOTAwLCJqdGkiOiJ1dWlkLWtleTEifQ...",
  "refresh_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiI0MiIsInR0bCI6IjcgZGF5cyIsImlhdCI6MTcxMzcwMDAwMCwianRpIjoicmVmcmVzaC11dWlkIn0...",
  "token_type": "Bearer",
  "expires_in": 900,
  "refresh_expires_in": 604800,
  "user_id": 42,
  "username": "nguyen_van_a",
  "email": "a@example.com",
  "phone": "0901234567",
  "full_name": "Nguyễn Văn A",
  "roles": ["BUYER", "SELLER"],
  "status": "ACTIVE",
  "trust_score": 80,
  "trust_tier": "PLATINUM",
  "avatar_url": "https://cdn.marketplace.vn/avatars/42.jpg"
}
```

### Response 403 (Account Locked)

```json
{
  "error": "ACCOUNT_LOCKED",
  "message": "Tài khoản bị khóa",
  "lock_reason": "Trust score quá thấp (< 10). Liên hệ support để khiếu nại.",
  "locked_until": "2026-05-15T10:00:00Z",
  "status_code": 403
}
```

### Kafka Events

```json
{
  "topic": "account.login",
  "payload": {
    "user_id": 42,
    "login_time": "2026-04-15T08:05:00Z",
    "ip_address": "192.168.1.1",
    "device": "chrome/mobile"
  }
}
```

---

## POST /auth/logout

**Đăng xuất, thu hồi token**

### Request

```json
{
  "refresh_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "logout_all_devices": false
}
```

### Response 200

```json
{
  "message": "Đăng xuất thành công, token hiện tại bị vô hiệu hóa"
}
```

### Redis Side Effect

```
SET revoked_token:{jti} = 1 EX 900
// TTL = token expiration time (default 15 min)
```

---

## GET /users/me

**Lấy thông tin tài khoản hiện tại**

### Query Parameters

Không có

### Response 200

```json
{
  "user_id": 42,
  "username": "nguyen_van_a",
  "email": "a@example.com",
  "phone": "0901234567",
  "full_name": "Nguyễn Văn A",
  "avatar_url": "https://cdn.marketplace.vn/avatars/42.jpg",
  "roles": ["BUYER", "SELLER"],
  "status": "ACTIVE",
  "trust_score": 85,
  "trust_tier": "GOLD",
  "appeal_count": 1,
  "product_posting_suspended": false,
  "lock_reason": null,
  "locked_until": null,
  "created_at": "2024-01-15T08:00:00Z"
}
```

---

## POST /users/me/trust-score-appeal

**Gửi khiếu nại trust score**

### Request

```json
{
  "log_id": 1042,
  "reason": "Tôi không hủy đơn quá số lần cho phép. Có bug hệ thống.",
  "evidence_urls": [
    "https://cdn.marketplace.vn/appeal-evidence/appeals/42/uuid-abc.jpg",
    "https://cdn.marketplace.vn/appeal-evidence/appeals/42/uuid-def.jpg"
  ]
}
```

### Validation Rules

| Field | Type | Rules |
|-------|------|-------|
| log_id | long | Phải tồn tại trong TRUST_SCORE_LOGS của user |
| reason | string | Max 500 chars |
| evidence_urls | array | 0-5 URLs từ presigned URLs |

### Response 201

```json
{
  "appeal_id": 15,
  "user_id": 42,
  "log_id": 1042,
  "status": "PENDING",
  "reason": "Tôi không hủy đơn quá số lần cho phép. Có bug hệ thống.",
  "evidence_urls": [
    "https://cdn.marketplace.vn/appeal-evidence/appeals/42/uuid-abc.jpg",
    "https://cdn.marketplace.vn/appeal-evidence/appeals/42/uuid-def.jpg"
  ],
  "created_at": "2026-04-15T10:00:00Z"
}
```

### Kafka Events

```json
{
  "topic": "appeal.submitted",
  "payload": {
    "appeal_id": 15,
    "user_id": 42,
    "log_id": 1042,
    "event_code": "EXCESSIVE_CANCELLATION",
    "old_score": 72,
    "current_score": 72,
    "timestamp": "2026-04-15T10:00:00Z"
  }
}
```

---

## GET /users/me/trust-score/logs

**Lịch sử thay đổi Trust Score**

### Query Parameters

```
page=0&size=20
```

### Response 200

```json
{
  "content": [
    {
      "log_id": 1042,
      "event_code": "BUYER_CANCEL_EXCESSIVE",
      "delta": -5,
      "score_before": 77,
      "score_after": 72,
      "changed_by": "SYSTEM",
      "reason": "Hủy đơn > 5 lần trong 30 ngày (rolling)",
      "created_at": "2026-04-14T03:00:00Z"
    },
    {
      "log_id": 1041,
      "event_code": "FIRST_ORDER_COMPLETED",
      "delta": 5,
      "score_before": 80,
      "score_after": 85,
      "changed_by": "SYSTEM",
      "reason": "Hoàn thành đơn hàng đầu tiên",
      "created_at": "2026-04-01T15:30:00Z"
    }
  ],
  "total_elements": 38,
  "total_pages": 2,
  "page_number": 0,
  "page_size": 20
}
```

---

# 📦 Product Service APIs

**Port:** `:8082`

## POST /products

**Tạo sản phẩm mới (Seller)**

### Request

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

### Validation Rules

| Field | Type | Rules |
|-------|------|-------|
| name | string | 5-200 chars |
| description | string | Max 10000 chars (HTML allowed) |
| category_id | string | Leaf category only |
| images | array | 1-10 URLs; JPEG/PNG/WebP |

### Response 201

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

### Kafka Events

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

## POST /seller/products/{productId}/variants

**Tạo variant sản phẩm**

### Request

```json
{
  "sku_code": "NK-AIR-RED-XL",
  "tier_name": "Đỏ / XL",
  "price": 350000
}
```

### Validation Rules

| Field | Type | Rules |
|-------|------|-------|
| sku_code | string | Unique; 3-50 chars; alphanumeric + dash |
| tier_name | string | 1-100 chars |
| price | decimal | > 0; max 9,999,999,999 |

### Response 201

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

---

## POST /seller/products/{productId}/submit

**Gửi sản phẩm duyệt**

### Request

```json
{}
```

### Response 200

```json
{
  "product_id": "507f1f77bcf86cd799439012",
  "status": "PENDING",
  "message": "Sản phẩm đã được gửi duyệt"
}
```

### Kafka Events

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

---

# 🔍 Search Service APIs

**Port:** `:8089`

## GET /search/products

**Tìm kiếm sản phẩm**

### Query Parameters

```
q=áo thun&category_id=507f1f77bcf86cd799439011&price_min=100000&price_max=500000&in_stock=true&sort=price_asc&page=0&size=20
```

### Response 200

```json
{
  "total_results": 156,
  "page": 0,
  "size": 20,
  "total_pages": 8,
  "products": [
    {
      "product_id": "507f1f77bcf86cd799439012",
      "name": "Áo Thun Nike Air Nam",
      "seller_id": 5,
      "seller_name": "Shop Nike VN",
      "seller_trust_score": 92,
      "category_id": "507f1f77bcf86cd799439011",
      "category_name": "Áo Thun Nam",
      "price_min": 250000,
      "price_max": 450000,
      "images": [
        "https://cdn.marketplace.vn/products-media/products/5/101/uuid-front.jpg"
      ],
      "stock_available": 95,
      "is_flash": true,
      "flash_price": 189999,
      "rating_avg": 4.7,
      "rating_count": 245,
      "sold_count": 1200,
      "created_at": "2026-04-01T08:00:00Z"
    }
  ],
  "facets": {
    "price_ranges": [
      {
        "range": "0-100000",
        "count": 32
      },
      {
        "range": "100000-500000",
        "count": 98
      }
    ],
    "sellers": [
      {
        "seller_id": 5,
        "seller_name": "Shop Nike VN",
        "count": 45
      }
    ]
  }
}
```

---

# 🛒 Cart Service APIs

**Port:** `:8083`

## GET /cart

**Lấy giỏ hàng**

### Query Parameters

Không có

### Response 200

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

## POST /cart/items

**Thêm sản phẩm vào giỏ**

### Request

```json
{
  "sku_code": "NK-AIR-RED-XL",
  "quantity": 2,
  "fs_item_id": null
}
```

### Validation Rules

| Field | Type | Rules |
|-------|------|-------|
| sku_code | string | Phải tồn tại; Unique |
| quantity | integer | > 0; ≤ 1000 |
| fs_item_id | long | Optional; nếu có phải là Flash Sale item APPROVED |

### Response 200

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

### Kafka Events

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

---

# 📋 Order Service APIs

**Port:** `:8087`

## POST /orders/checkout

**Tạo đơn hàng từ giỏ (Multi-Vendor)**

### Request

```json
{
  "address_id": 7,
  "item_ids": [201, 202],
  "use_loyalty_points": true,
  "loyalty_points_to_use": 50
}
```

### Validation Rules

| Field | Type | Rules |
|-------|------|-------|
| address_id | long | Phải tồn tại; thuộc user |
| item_ids | array | 1-50 items; không trùng |
| use_loyalty_points | boolean | Optional; default false |
| loyalty_points_to_use | integer | ≤ 20% of total amount; ≤ available points |

### Response 201 (Multi-Vendor Example)

```json
{
  "parent_order_id": 55,
  "order_code": "PO-20251001-55",
  "orders": [
    {
      "order_id": 100,
      "order_code": "OR-20251001-100",
      "seller_id": 5,
      "seller_name": "Shop Nike VN",
      "seller_trust_score": 92,
      "total_amt": 700000,
      "final_amt": 650000,
      "status": "PENDING",
      "items": [
        {
          "order_item_id": 501,
          "sku_code": "NK-AIR-RED-XL",
          "product_name": "Áo Thun Nike Air",
          "variant_name": "Đỏ / XL",
          "image_snapshot": "https://cdn.marketplace.vn/products-media/products/5/101/uuid.jpg",
          "price_snapshot": 350000,
          "quantity": 2,
          "subtotal": 700000
        }
      ],
      "created_at": "2026-10-01T10:00:00Z"
    },
    {
      "order_id": 101,
      "order_code": "OR-20251001-101",
      "seller_id": 9,
      "seller_name": "Shop Adidas VN",
      "seller_trust_score": 88,
      "total_amt": 500000,
      "final_amt": 500000,
      "status": "PENDING",
      "items": [
        {
          "order_item_id": 601,
          "sku_code": "AD-ULTRA-BLK-10",
          "product_name": "Giày Adidas Ultraboost",
          "variant_name": "Đen / EU 10",
          "image_snapshot": "https://cdn.marketplace.vn/products-media/products/9/201/uuid.jpg",
          "price_snapshot": 500000,
          "quantity": 1,
          "subtotal": 500000
        }
      ],
      "created_at": "2026-10-01T10:00:00Z"
    }
  ],
  "shipping_address": {
    "address_id": 7,
    "full_address": "123 Nguyễn Trãi, Phường 2, Q.3, TP.HCM",
    "province_id": 79,
    "district_id": 760
  },
  "payment": {
    "total_amount": 1200000,
    "loyalty_discount": 50000,
    "loyalty_points_used": 50,
    "final_amount": 1150000,
    "currency": "VND"
  },
  "total_items": 3,
  "total_sellers": 2,
  "payment_status": "PENDING",
  "timeout_at": "2026-10-01T10:30:00Z",
  "created_at": "2026-10-01T10:00:00Z"
}
```

### Kafka Events

```json
{
  "topic": "order.created",
  "payload": {
    "parent_order_id": 55,
    "user_id": 42,
    "orders": [
      {
        "order_id": 100,
        "seller_id": 5,
        "total_amount": 700000,
        "items_count": 1
      },
      {
        "order_id": 101,
        "seller_id": 9,
        "total_amount": 500000,
        "items_count": 1
      }
    ],
    "total_amount": 1200000,
    "loyalty_points_used": 50,
    "timestamp": "2026-10-01T10:00:00Z"
  }
}
```

---

## POST /orders/{orderId}/cancel

**Hủy đơn hàng**

### Request

```json
{
  "reason": "Tôi muốn hủy đơn này",
  "note": "Đơn đặt nhầm"
}
```

### Response 200

```json
{
  "order_id": 100,
  "order_code": "OR-20251001-100",
  "status": "CANCELLED",
  "cancelled_by": "BUYER",
  "cancel_reason": "Tôi muốn hủy đơn này",
  "cancelled_at": "2026-04-15T11:00:00Z"
}
```

### Kafka Events

```json
{
  "topic": "order.cancelled",
  "payload": {
    "order_id": 100,
    "parent_order_id": 55,
    "user_id": 42,
    "seller_id": 5,
    "cancelled_by": "BUYER",
    "cancel_reason": "Tôi muốn hủy đơn này",
    "total_amount": 700000,
    "loyalty_points_refunded": 25,
    "timestamp": "2026-04-15T11:00:00Z"
  }
}
```

---

## PUT /orders/{orderId}/tracking

**Cập nhật tracking number (Seller)**

### Request

```json
{
  "tracking_number": "VT123456789",
  "carrier": "ViettelPost",
  "note": "Giao hàng dự kiến 2-3 ngày"
}
```

### Response 200

```json
{
  "order_id": 100,
  "order_code": "OR-20251001-100",
  "status": "SHIPPING",
  "tracking_number": "VT123456789",
  "carrier": "ViettelPost",
  "shipping_deadline": "2026-10-04T10:00:00Z",
  "updated_at": "2026-10-01T12:00:00Z"
}
```

### Kafka Events

```json
{
  "topic": "order.shipped",
  "payload": {
    "order_id": 100,
    "user_id": 42,
    "seller_id": 5,
    "tracking_number": "VT123456789",
    "carrier": "ViettelPost",
    "shipped_at": "2026-10-01T12:00:00Z"
  }
}
```

---

## POST /orders/{orderId}/confirm-received

**Xác nhận nhận hàng**

### Request

```json
{}
```

### Response 200

```json
{
  "order_id": 100,
  "order_code": "OR-20251001-100",
  "status": "DELIVERED",
  "delivered_at": "2026-10-03T14:30:00Z",
  "loyalty_points_confirmed": 25,
  "seller_trust_score_delta": 5
}
```

### Kafka Events

```json
{
  "topic": "order.delivered",
  "payload": {
    "order_id": 100,
    "user_id": 42,
    "seller_id": 5,
    "total_amount": 700000,
    "loyalty_points": 25,
    "delivered_at": "2026-10-03T14:30:00Z",
    "timestamp": "2026-10-03T14:30:00Z"
  }
}
```

---

## POST /orders/{orderId}/return-to-sender

**Seller xác nhận hàng hoàn (RTS) [NEW v5.3]**

### Request (multipart/form-data)

```
Content-Type: multipart/form-data

[files]
evidence_images: [file1.jpg, file2.jpg]

[fields]
return_tracking_number: VT999888777
note: Hoàn do không gọi được Buyer, địa chỉ sai
```

### Response 200

```json
{
  "order_id": 1001,
  "order_code": "OR-20251001-1001",
  "order_status": "RETURNED",
  "refund_id": 99,
  "refund_code": "RF-20251001-99",
  "refund_status": "PENDING",
  "refund_amount": 250000,
  "return_tracking_number": "VT999888777",
  "evidence_count": 2,
  "estimated_refund_days": 3,
  "stripe_refund_id": "re_3Px5Ab...",
  "message": "Hàng hoàn đã được ghi nhận. Hệ thống đang tự động hoàn tiền cho Buyer.",
  "seller_notification": {
    "status": "sent",
    "message": "Xác nhận hàng hoàn đã được lưu. Tồn kho đã được cộng lại."
  },
  "buyer_notification": {
    "status": "sent",
    "message": "Seller đã nhận lại hàng hoàn. Tiền đang được hoàn về tài khoản của bạn."
  },
  "created_at": "2026-10-01T14:30:00Z"
}
```

### Kafka Events (RTS)

```json
{
  "topic": "order.returned",
  "payload": {
    "order_id": 1001,
    "parent_order_id": 1000,
    "user_id": 42,
    "seller_id": 5,
    "refund_id": 99,
    "refund_reason_type": "RETURN_TO_SENDER",
    "return_tracking_number": "VT999888777",
    "total_amount": 250000,
    "evidence_count": 2,
    "timestamp": "2026-10-01T14:30:00Z"
  }
}
```

---

# ↩️ Refund APIs

## POST /orders/parent/{parentOrderId}/refund

**Full Refund toàn bộ đơn cha (Buyer)**

### Request

```json
{
  "reason": "Tôi không cần hàng nữa",
  "evidence_images": [
    "https://cdn.marketplace.vn/refund-evidence/orders/55/uuid-abc.jpg"
  ]
}
```

### Response 201

```json
{
  "group_ref": "550e8400-e29b-41d4-a716-446655440000",
  "type": "FULL",
  "total_amount": 1200000,
  "status": "PENDING",
  "refunds": [
    {
      "refund_id": 88,
      "refund_code": "RF-20251005-88",
      "order_id": 100,
      "seller_id": 5,
      "amount": 700000,
      "item_count": 1,
      "status": "PENDING"
    },
    {
      "refund_id": 89,
      "refund_code": "RF-20251005-89",
      "order_id": 101,
      "seller_id": 9,
      "amount": 500000,
      "item_count": 1,
      "status": "PENDING"
    }
  ],
  "loyalty_points_to_return": 62,
  "estimated_days": 3,
  "created_at": "2026-10-05T14:00:00Z"
}
```

### Kafka Events

```json
{
  "topic": "refund.requested",
  "payload": {
    "group_ref": "550e8400-e29b-41d4-a716-446655440000",
    "type": "FULL",
    "parent_order_id": 55,
    "user_id": 42,
    "refunds": [
      {
        "refund_id": 88,
        "seller_id": 5,
        "amount": 700000
      },
      {
        "refund_id": 89,
        "seller_id": 9,
        "amount": 500000
      }
    ],
    "total_amount": 1200000,
    "timestamp": "2026-10-05T14:00:00Z"
  }
}
```

---

## POST /orders/{orderId}/refunds

**Partial Refund 1 sub-order (Buyer)**

### Request

```json
{
  "reason": "Sản phẩm bị lỗi",
  "items": [
    {
      "order_item_id": 501,
      "quantity": 1,
      "item_reason": "Áo bị nhuộm màu"
    }
  ],
  "evidence_images": [
    "https://cdn.marketplace.vn/refund-evidence/orders/100/uuid-damage.jpg"
  ]
}
```

### Response 201

```json
{
  "refund_id": 88,
  "refund_code": "RF-20251005-88",
  "order_id": 100,
  "type": "PARTIAL",
  "status": "PENDING",
  "total_amount": 700000,
  "refund_amount": 350000,
  "items": [
    {
      "order_item_id": 501,
      "quantity": 1,
      "refund_amount": 350000,
      "item_reason": "Áo bị nhuộm màu"
    }
  ],
  "evidence_images": [
    "https://cdn.marketplace.vn/refund-evidence/orders/100/uuid-damage.jpg"
  ],
  "estimated_days": 3,
  "created_at": "2026-10-05T14:00:00Z"
}
```

---

## POST /admin/refunds/{refundId}/approve

**Admin duyệt hoàn tiền [NEW v5.3 - Tracking Number]**

### Request

```json
{
  "admin_note": "Hoàn do giao hàng không thành công, shipper mang lại lần 3",
  "adjust_amount": null,
  "caused_by": "SELLER",
  "tracking_number": "VT123456789"
}
```

### Validation Rules

| Field | Type | Rules |
|-------|------|-------|
| admin_note | string | Required; 1-1000 chars |
| adjust_amount | decimal | Optional; ≥ 0 |
| caused_by | string | Optional; SELLER \| BUYER |
| tracking_number | string | Optional; format: [A-Z]{2}[0-9]{9} |

### Response 200

```json
{
  "refund_id": 88,
  "refund_code": "RF-20251005-88",
  "status": "SUCCESS",
  "type": "PARTIAL",
  "amount": 500000,
  "adjust_amount": null,
  "tracking_number": "VT123456789",
  "return_evidence": [
    {
      "type": "tracking",
      "tracking_number": "VT123456789",
      "recorded_at": "2026-04-15T10:30:00Z"
    }
  ],
  "reviewed_by": 1,
  "admin_id": 1,
  "admin_name": "Admin User",
  "admin_note": "Hoàn do giao hàng không thành công...",
  "reviewed_at": "2026-04-15T10:30:00Z",
  "stripe_refund_id": "re_3Px5Ab2K1234567...",
  "trust_score_adjustment": {
    "seller_id": 5,
    "delta": -5,
    "event_code": "SELLER_CAUSED_REFUND",
    "new_score": 87,
    "triggered": true
  },
  "loyalty_adjustment": {
    "buyer_id": 42,
    "points_returned": 50,
    "status": "refunded"
  },
  "notifications": {
    "buyer": {
      "status": "sent",
      "message": "Hoàn tiền được duyệt. Mã vận đơn hoàn: VT123456789"
    },
    "seller": {
      "status": "sent",
      "message": "Refund đã được xử lý. Trust score - 5 điểm."
    }
  },
  "created_at": "2026-10-05T14:00:00Z",
  "updated_at": "2026-04-15T10:30:00Z"
}
```

### Kafka Events

```json
{
  "topic": "refund.admin_approved",
  "payload": {
    "refund_id": 88,
    "order_id": 100,
    "user_id": 42,
    "seller_id": 5,
    "amount": 500000,
    "tracking_number": "VT123456789",
    "caused_by": "SELLER",
    "admin_id": 1,
    "admin_note": "Hoàn do giao hàng không thành công...",
    "trust_score_delta": -5,
    "loyalty_points_returned": 50,
    "approved_at": "2026-04-15T10:30:00Z"
  }
}
```

---

# 💳 Payment Service APIs

**Port:** `:8085`

## POST /stripe/onboarding/start

**Bắt đầu Stripe KYC (Seller)**

### Request

```json
{}
```

### Response 201

```json
{
  "onboarding_url": "https://connect.stripe.com/setup/e/acct_1OxABC123456789/...",
  "stripe_account_id": "acct_1OxABC123456789",
  "expires_at": "2026-04-16T10:00:00Z"
}
```

---

## GET /stripe/onboarding/status

**Kiểm tra trạng thái Stripe account**

### Response 200

```json
{
  "stripe_account_id": "acct_1OxABC123456789",
  "account_status": "ACTIVE",
  "details_submitted": true,
  "charges_enabled": true,
  "payouts_enabled": true,
  "onboarding_status": "COMPLETE",
  "onboarding_url": null
}
```

---

## GET /payments/parent-order/{parentOrderId}

**Thông tin giao dịch thanh toán**

### Response 200

```json
{
  "transaction_id": 301,
  "parent_order_id": 55,
  "amount": 1200000,
  "method": "STRIPE",
  "status": "SUCCESS",
  "stripe_pi_id": "pi_3PxABC2K1234567...",
  "application_fee": 60000,
  "application_fee_percentage": 5.0,
  "trans_ref": "TXN-20251001-301",
  "paid_at": "2026-10-01T10:05:00Z",
  "sellers": [
    {
      "seller_id": 5,
      "seller_name": "Shop Nike VN",
      "order_id": 100,
      "amount": 700000,
      "fee": 35000,
      "net_amount": 665000,
      "stripe_transfer_id": "tr_3PxABC2K98765432",
      "transfer_status": "SUCCEEDED"
    },
    {
      "seller_id": 9,
      "seller_name": "Shop Adidas VN",
      "order_id": 101,
      "amount": 500000,
      "fee": 25000,
      "net_amount": 475000,
      "stripe_transfer_id": "tr_3PxABC2K98765433",
      "transfer_status": "SUCCEEDED"
    }
  ]
}
```

---

# ⭐ Loyalty Service APIs

**Port:** `:8084`

## GET /loyalty/balance

**Số dư điểm thưởng**

### Response 200

```json
{
  "user_id": 42,
  "loyalty_account_id": 123,
  "available_points": 1250,
  "pending_points": 300,
  "expired_points": 50,
  "total_earned": 2000,
  "total_used": 650,
  "conversion_rate": 200,
  "note": "1 point = 1/200 of 200,000 VND = 1,000 VND",
  "max_usable_per_order": 275,
  "max_usable_percentage": 0.20,
  "expiry_policy": {
    "expiry_days": 365,
    "next_expiry_date": "2026-10-05",
    "points_expiring_soon": 0
  },
  "tier_benefits": {
    "tier": "PLATINUM",
    "trust_score": 80,
    "earning_rate": "5%",
    "max_discount_rate": "20%"
  },
  "recent_transactions": [
    {
      "transaction_id": 501,
      "type": "EARNED",
      "delta": 300,
      "status": "PENDING",
      "order_id": 100,
      "order_code": "OR-20251001-100",
      "created_at": "2026-10-01T10:00:00Z",
      "expires_at": "2026-10-01T10:00:00Z"
    }
  ]
}
```

---

## GET /loyalty/estimate

**Ước tính điểm sẽ nhận / có thể dùng**

### Query Parameters

```
order_amount=1200000&points_to_use=50
```

### Response 200

```json
{
  "order_amount": 1200000,
  "points_to_earn": 350,
  "points_to_earn_formula": "order_amount * 5% / 1000 = 1200000 * 0.05 / 1000 = 60",
  "available_points": 1250,
  "max_points_usable": 240,
  "max_points_usable_formula": "20% of order_amount = 1200000 * 0.20 / 1000 = 240",
  "conversion_rate": 200,
  "points_requested": 50,
  "discount_if_use_50": 250000,
  "cap_percent": 20
}
```

---

# ⚡ Flash Sale Service APIs

**Port:** `:8086`

## GET /flash-sale/sessions

**Danh sách Flash Sale sessions**

### Query Parameters

```
status=ACTIVE&page=0&size=20
```

### Response 200

```json
{
  "server_time": "2026-04-15T19:58:00Z",
  "sessions": [
    {
      "session_id": 3,
      "name": "Flash Sale 20h Thứ 6",
      "status": "ACTIVE",
      "start_time": "2026-04-15T20:00:00Z",
      "end_time": "2026-04-15T22:00:00Z",
      "item_count": 15,
      "seconds_remaining": 120,
      "is_ended": false
    }
  ]
}
```

---

## POST /flash-sale/sessions/{sessionId}/items

**Đăng ký sản phẩm Flash Sale (Seller)**

### Request

```json
{
  "sku_code": "NK-AIR-RED-XL",
  "flash_price": 189999,
  "flash_stock": 50,
  "limit_per_user": 3
}
```

### Validation Rules

| Field | Type | Rules |
|-------|------|-------|
| sku_code | string | Phải tồn tại; unique per session |
| flash_price | decimal | > 0; < variant.price |
| flash_stock | integer | > 0; ≤ stock_available |
| limit_per_user | integer | 1-10 |

### Response 201

```json
{
  "fs_item_id": 1001,
  "session_id": 3,
  "sku_code": "NK-AIR-RED-XL",
  "flash_price": 189999,
  "flash_stock": 50,
  "sold_qty": 0,
  "limit_per_user": 3,
  "status": "PENDING",
  "message": "Sản phẩm đã gửi duyệt. Chờ Admin phê duyệt.",
  "created_at": "2026-04-15T10:00:00Z"
}
```

---

## POST /flash-sale/sessions/{sessionId}/buy

**Mua Flash Sale [Chịu tải cao - Redis Lua]**

### Request

```json
{
  "fs_item_id": 1001,
  "quantity": 2,
  "address_id": 7
}
```

### Response 201

```json
{
  "order_id": 1002,
  "order_code": "OR-20260415-1002",
  "fs_item_id": 1001,
  "quantity": 2,
  "flash_price": 189999,
  "total": 379998,
  "status": "PENDING",
  "is_flash_sale": true,
  "timeout_at": "2026-04-15T20:10:00Z",
  "message": "Đơn hàng đã tạo. Thanh toán trong 10 phút."
}
```

### Kafka Events (Redis Side Effect)

```json
{
  "topic": "flash_sale.item_sold",
  "payload": {
    "fs_item_id": 1001,
    "session_id": 3,
    "quantity": 2,
    "sold_total": 45,
    "remaining_stock": 5,
    "timestamp": "2026-04-15T20:00:30Z"
  }
}
```

---

# 🔔 Notification Service APIs

**Port:** `:8088`

## GET /notifications/stream

**Kết nối SSE real-time**

### Headers Required

```
Authorization: Bearer <access_token>
```

### Response (text/event-stream)

```
data: {"notif_id":"64f3a","type":"REFUND_APPROVED","title":"Hoàn tiền thành công","body":"Yêu cầu hoàn 350.000đ đã được duyệt","priority":"NORMAL","metadata":{"deeplink":"/orders/100/refunds/88"},"created_at":"2026-04-15T10:00:00Z"}

data: {"notif_id":"64f3b","type":"ORDER_SHIPPED","title":"Đơn hàng đang giao","body":"Mã vận đơn: VT123456789","priority":"NORMAL","metadata":{"deeplink":"/orders/100"},"created_at":"2026-04-15T10:05:00Z"}
```

---

## GET /notifications

**Danh sách thông báo (Pagination)**

### Query Parameters

```
is_read=false&page=0&size=20
```

### Response 200

```json
{
  "content": [
    {
      "notif_id": "64f3a",
      "type": "REFUND_APPROVED",
      "title": "Hoàn tiền thành công",
      "body": "Yêu cầu hoàn 350.000đ đã được duyệt",
      "is_read": false,
      "priority": "HIGH",
      "metadata": {
        "deeplink": "/orders/100/refunds/88",
        "refund_id": 88
      },
      "created_at": "2026-04-15T10:00:00Z",
      "expires_at": "2026-07-15T10:00:00Z"
    }
  ],
  "total_elements": 24,
  "unread_count": 5,
  "page_number": 0,
  "page_size": 20
}
```

---

## PATCH /notifications/{notifId}/read

**Đánh dấu đã đọc**

### Response 200

```json
{
  "notif_id": "64f3a",
  "is_read": true
}
```

---

# 🛡️ Admin APIs

## GET /admin/users

**Danh sách người dùng**

### Query Parameters

```
status=ACTIVE&role=SELLER&trust_score_min=50&trust_score_max=100&page=0&size=20
```

### Response 200

```json
{
  "content": [
    {
      "user_id": 5,
      "username": "shop_nike_vn",
      "email": "shop@nike.vn",
      "full_name": "Shop Nike Vietnam",
      "roles": ["SELLER", "BUYER"],
      "status": "ACTIVE",
      "trust_score": 92,
      "trust_tier": "DIAMOND",
      "product_posting_suspended": false,
      "locked_until": null,
      "created_at": "2024-01-15T08:00:00Z",
      "updated_at": "2026-04-14T15:30:00Z"
    }
  ],
  "total_elements": 42,
  "total_pages": 3
}
```

---

## POST /admin/users/{userId}/lock

**Khóa tài khoản**

### Request

```json
{
  "reason": "Vi phạm chính sách",
  "locked_until": "2026-05-15T10:00:00Z"
}
```

### Validation Rules

| Field | Type | Rules |
|-------|------|-------|
| reason | string | Required; 1-500 chars |
| locked_until | datetime | Optional; null = vĩnh viễn |

### Response 200

```json
{
  "user_id": 5,
  "status": "LOCKED",
  "lock_reason": "Vi phạm chính sách",
  "locked_until": "2026-05-15T10:00:00Z",
  "message": "Tài khoản đã bị khóa"
}
```

### Kafka Events

```json
{
  "topic": "account.locked",
  "payload": {
    "user_id": 5,
    "lock_reason": "Vi phạm chính sách",
    "locked_until": "2026-05-15T10:00:00Z",
    "locked_by": 1,
    "locked_at": "2026-04-15T10:00:00Z"
  }
}
```

---

## POST /admin/users/{userId}/trust-score

**Điều chỉnh Trust Score thủ công**

### Request

```json
{
  "delta": 10,
  "reason": "Khiếu nại được phê duyệt - Appeal approved"
}
```

### Response 200

```json
{
  "user_id": 5,
  "old_score": 92,
  "new_score": 102,
  "capped_score": 100,
  "delta": 10,
  "reason": "Khiếu nại được phê duyệt - Appeal approved",
  "changed_by": "ADMIN",
  "admin_id": 1,
  "changed_at": "2026-04-15T10:00:00Z"
}
```

### Kafka Events

```json
{
  "topic": "trust_score.adjusted",
  "payload": {
    "user_id": 5,
    "old_score": 92,
    "new_score": 100,
    "delta": 10,
    "event_code": "ADMIN_OVERRIDE",
    "reason": "Khiếu nại được phê duyệt",
    "admin_id": 1,
    "timestamp": "2026-04-15T10:00:00Z"
  }
}
```

---

## POST /admin/products/{productId}/approve

**Duyệt sản phẩm**

### Request

```json
{
  "note": "Sản phẩm đạt chuẩn"
}
```

### Response 200

```json
{
  "product_id": "507f1f77bcf86cd799439012",
  "seller_id": 5,
  "name": "Áo Thun Nike Air Nam",
  "status": "APPROVED",
  "variants_count": 3,
  "approved_at": "2026-04-15T10:00:00Z"
}
```

### Kafka Events

```json
{
  "topic": "product.approved",
  "payload": {
    "product_id": "507f1f77bcf86cd799439012",
    "seller_id": 5,
    "name": "Áo Thun Nike Air Nam",
    "variants_count": 3,
    "approved_by": 1,
    "approved_at": "2026-04-15T10:00:00Z"
  }
}
```

---

## GET /admin/failed-events

**Danh sách events thất bại (DLQ)**

### Query Parameters

```
status=DEAD&topic_or_task=order.delivered&page=0&size=20
```

### Response 200

```json
{
  "content": [
    {
      "event_id": 42,
      "topic_or_task": "order.delivered",
      "payload": {
        "order_id": 1001,
        "user_id": 42,
        "seller_id": 5
      },
      "error_reason": "Loyalty Service connection timeout after 5 retries",
      "retry_count": 5,
      "status": "DEAD",
      "created_at": "2026-04-14T14:00:00Z",
      "updated_at": "2026-04-15T09:30:00Z"
    }
  ],
  "total_elements": 3,
  "total_pages": 1
}
```

---

## POST /admin/failed-events/{eventId}/retry

**Retry thủ công event thất bại**

### Request

```json
{}
```

### Response 200

```json
{
  "event_id": 42,
  "status": "PENDING",
  "message": "Event đã được re-publish vào Kafka",
  "retry_at": "2026-04-15T10:00:00Z"
}
```

### Kafka Events

```json
{
  "topic": "order.delivered",
  "payload": {
    "order_id": 1001,
    "user_id": 42,
    "seller_id": 5,
    "retry_event_id": 42,
    "retry_timestamp": "2026-04-15T10:00:00Z"
  }
}
```

---

# 🧭 Kafka Topics & Payloads

## Core Topics

### account.created

```json
{
  "user_id": 42,
  "email": "a@example.com",
  "phone": "0901234567",
  "username": "nguyen_van_a",
  "roles": ["BUYER"],
  "trust_score": 80,
  "timestamp": "2026-04-15T08:00:00Z"
}
```

### order.created

```json
{
  "parent_order_id": 55,
  "user_id": 42,
  "orders": [
    {
      "order_id": 100,
      "seller_id": 5,
      "total_amount": 700000,
      "items": [
        {
          "item_id": 501,
          "sku_code": "NK-AIR-RED-XL",
          "quantity": 2,
          "price": 350000
        }
      ]
    }
  ],
  "total_amount": 1200000,
  "loyalty_points_used": 50,
  "timestamp": "2026-10-01T10:00:00Z"
}
```

### order.shipped

```json
{
  "order_id": 100,
  "user_id": 42,
  "seller_id": 5,
  "tracking_number": "VT123456789",
  "carrier": "ViettelPost",
  "shipped_at": "2026-10-01T12:00:00Z"
}
```

### order.delivered

```json
{
  "order_id": 100,
  "user_id": 42,
  "seller_id": 5,
  "total_amount": 700000,
  "loyalty_points": 25,
  "delivered_at": "2026-10-03T14:30:00Z"
}
```

### order.returned (RTS - NEW v5.3)

```json
{
  "order_id": 1001,
  "parent_order_id": 1000,
  "user_id": 42,
  "seller_id": 5,
  "refund_id": 99,
  "refund_reason_type": "RETURN_TO_SENDER",
  "return_tracking_number": "VT999888777",
  "total_amount": 250000,
  "evidence_count": 2,
  "timestamp": "2026-10-01T14:30:00Z"
}
```

### refund.admin_approved (NEW v5.3)

```json
{
  "refund_id": 88,
  "order_id": 100,
  "user_id": 42,
  "seller_id": 5,
  "amount": 500000,
  "tracking_number": "VT123456789",
  "caused_by": "SELLER",
  "admin_id": 1,
  "admin_note": "Hoàn do giao hàng không thành công...",
  "trust_score_delta": -5,
  "loyalty_points_returned": 50,
  "approved_at": "2026-04-15T10:30:00Z"
}
```

### trust_score.warning

```json
{
  "user_id": 42,
  "old_score": 32,
  "new_score": 27,
  "threshold": 30,
  "event_code": "EXCESSIVE_CANCELLATION",
  "message": "Trust score của bạn đang dưới 30 điểm",
  "timestamp": "2026-04-15T10:00:00Z"
}
```

### flash_sale.item_sold

```json
{
  "fs_item_id": 1001,
  "session_id": 3,
  "sku_code": "NK-AIR-RED-XL",
  "quantity": 2,
  "flash_price": 189999,
  "sold_total": 45,
  "remaining_stock": 5,
  "timestamp": "2026-04-15T20:00:30Z"
}
```

### payment.success

```json
{
  "transaction_id": 301,
  "parent_order_id": 55,
  "user_id": 42,
  "amount": 1200000,
  "stripe_pi_id": "pi_3PxABC2K1234567",
  "paid_at": "2026-10-01T10:05:00Z",
  "sellers": [
    {
      "seller_id": 5,
      "order_id": 100,
      "amount": 700000,
      "fee": 35000,
      "net_amount": 665000
    }
  ]
}
```

---

# ❌ Error Response Formats

## Standard Error

```json
{
  "error": "RESOURCE_NOT_FOUND",
  "message": "Không tìm thấy resource",
  "details": "Order với ID 9999 không tồn tại",
  "status_code": 404,
  "timestamp": "2026-04-15T10:30:00Z",
  "path": "/api/v1/orders/9999",
  "request_id": "req-abc123def456"
}
```

## Validation Error

```json
{
  "error": "VALIDATION_FAILED",
  "message": "Lỗi validation",
  "status_code": 400,
  "violations": [
    {
      "field": "loyalty_points_to_use",
      "value": 1000,
      "message": "Không thể dùng quá 20% giá trị đơn",
      "constraint": "LOYALTY_POINTS_MAX_PERCENTAGE",
      "max_allowed": 230
    }
  ]
}
```

## Invalid State Error

```json
{
  "error": "INVALID_STATE",
  "message": "Trạng thái không hợp lệ",
  "current_state": "SHIPPING",
  "allowed_states": ["PENDING", "PAID"],
  "status_code": 422
}
```

## Account Locked Error

```json
{
  "error": "ACCOUNT_LOCKED",
  "message": "Tài khoản bị khóa",
  "lock_reason": "Trust score quá thấp (< 10). Liên hệ support để khiếu nại.",
  "locked_until": "2026-05-15T10:00:00Z",
  "status_code": 403
}
```

---

## 📊 Summary

- **Total Endpoints**: 95+
- **API Services**: 11 (Identity, Product, Search, Cart, Order, Refund, Payment, Loyalty, Flash Sale, Notification, Admin)
- **Kafka Topics**: 35+
- **Error Types**: 10+
- **Request/Response Examples**: 60+
- **JSON Payloads**: Complete with nested objects
- **Kafka Payloads**: All event types with detailed fields

---

**Tài liệu cập nhật:** 2026-04-15
**Phiên bản:** 5.3 RTS Unified
**Trạng thái:** ✅ Production-Ready
