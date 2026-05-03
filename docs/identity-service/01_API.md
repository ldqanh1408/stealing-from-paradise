# Identity Service — API Reference

> Base path: `/api/v1` → Gateway routes to `identity-service:8081`
>
> Auth: JWT RS256 (Bearer token) — trừ endpoints được ghi chú `Public`

---

## Authentication Endpoints

### POST /auth/register
**Đăng ký tài khoản mới**

**Quyền truy cập**: Public

**Request Body:**
```json
{
  "username": "string",      // 3-50 ký tự, a-z, 0-9, dấu chấm/gạch dưới (Required)
  "email": "string",         // Email hợp lệ, unique (Required)
  "phone": "string",         // SĐT Việt Nam, unique (Required)
  "password": "string",      // Tối thiểu 8 ký tự, có chữ hoa + số (Required)
  "full_name": "string"      // 2-100 ký tự (Required)
}
```

**Response 201:**
```json
{
  "success": true,
  "data": {
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
}
```

**Errors:** 409 (duplicate), 400 (validation)

---

### POST /auth/login
**Đăng nhập, nhận JWT**

**Quyền truy cập**: Public

**Request Body:**
```json
{
  "credential": "string",    // username | email | phone (Required)
  "password": "string"       // (Required)
}
```

**Response 200:**
```json
{
  "success": true,
  "data": {
    "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refresh_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
    "token_type": "Bearer",
    "expires_in": 900,
    "refresh_expires_in": 604800,
    "user_id": 42,
    "username": "nguyen_van_a",
    "roles": ["BUYER", "SELLER"],
    "status": "ACTIVE",
    "trust_score": 80,
    "trust_tier": "PLATINUM"
  }
}
```

**Errors:** 401 (wrong credentials), 403 (account locked)

**Response 403 - Account Locked:**
```json
{
  "success": false,
  "errorCode": "ACCOUNT_LOCKED",
  "message": "Tài khoản bị khóa - Trust score quá thấp (< 10). Liên hệ support để khiếu nại.",
  "data": {
    "locked_until": "2026-05-15T10:00:00Z"
  }
}
```

---

### POST /auth/refresh
**Làm mới access token**

**Quyền truy cập**: Public

**Request Body:**
```json
{
  "refresh_token": "string"
}
```

**Response 200:**
```json
{
  "success": true,
  "data": {
    "access_token": "eyJhbGc...",
    "refresh_token": "eyJhbGc...",
    "token_type": "Bearer",
    "expires_in": 900
  }
}
```

**Errors:** 401 (expired/invalid refresh token)

---

### POST /auth/logout
**Đăng xuất, thu hồi token**

**Quyền truy cập**: JWT Required

**Request Body:**
```json
{
  "refresh_token": "string"   // (Optional) Thu hồi luôn refresh token
}
```

**Response 200:** Token revoked (thêm vào Redis blocklist)

---

## User Profile

### GET /users/me
**Thông tin cá nhân**

**Quyền truy cập**: JWT Required

**Response 200:**
```json
{
  "success": true,
  "data": {
    "user_id": 42,
    "username": "nguyen_van_a",
    "email": "a@example.com",
    "phone": "0901234567",
    "full_name": "Nguyễn Văn A",
    "avatar_url": "https://cdn.marketplace.vn/avatars/42/abc.jpg",
    "roles": ["BUYER", "SELLER"],
    "status": "ACTIVE",
    "trust_score": 80,
    "trust_tier": "PLATINUM",
    "product_posting_suspended": false,
    "address_count": 2,
    "created_at": "2025-11-01T08:00:00Z"
  }
}
```

---

### PUT /users/me
**Cập nhật thông tin cá nhân**

**Quyền truy cập**: JWT Required

**Request Body** (all optional):
```json
{
  "full_name": "string",        // 2-100 ký tự
  "phone": "string",            // SĐT mới (unique)
  "avatar_url": "string"        // URL ảnh đại diện
}
```

**Response 200:** User updated

**Errors:** 409 (phone đã tồn tại), 400 (validation)

---

### GET /users/me/avatar/presigned-url
**Lấy pre-signed URL upload avatar**

**Quyền truy cập**: JWT Required

**Query Params:**

| Param | Type | Required | Mô tả |
|-------|------|----------|-------|
| file_name | string | ✓ | Tên file gốc (vd: avatar.jpg) |
| content_type | string | ✓ | MIME type: image/jpeg | image/png | image/webp |

**Response 200:**
```json
{
  "success": true,
  "data": {
    "presigned_url": "https://minio.internal/...",
    "object_url": "https://cdn.marketplace.vn/avatars/42/uuid.jpg",
    "expires_in": 900
  }
}
```

---

## Address Management

### GET /users/me/addresses
**Danh sách địa chỉ**

**Quyền truy cập**: JWT Required

**Response 200:**
```json
{
  "success": true,
  "data": [
    {
      "address_id": 1,
      "street": "123 Nguyễn Huệ",
      "ward": "Phường Bến Nghé",
      "district": "Quận 1",
      "city": "TP. Hồ Chí Minh",
      "is_default": true,
      "phone": "0901234567"
    }
  ]
}
```

---

### POST /users/me/addresses
**Thêm địa chỉ mới**

**Quyền truy cập**: JWT Required

**Request Body:**
```json
{
  "street": "string",      // (Required)
  "ward": "string",        // (Required)
  "district": "string",    // (Required)
  "city": "string",        // (Required)
  "is_default": "boolean", // Mặc định hay không
  "phone": "string"        // (Required)
}
```

**Response 201:** Address created

---

### PUT /users/me/addresses/{addressId}
**Cập nhật địa chỉ**

**Quyền truy cập**: JWT Required

**Request Body** (all optional):
```json
{
  "street": "string",
  "ward": "string",
  "district": "string",
  "city": "string",
  "is_default": "boolean",
  "phone": "string"
}
```

**Response 200:** Address updated

---

### DELETE /users/me/addresses/{addressId}
**Xóa địa chỉ**

**Quyền truy cập**: JWT Required

**Response 200:** Address deleted

**Errors:** 404 (not found), 400 (cannot delete default without replacement)

---

## Role Management

### POST /users/me/roles/seller
**Đăng ký trở thành Seller**

**Quyền truy cập**: JWT Required

**Request Body:**
```json
{
  "shop_name": "string"    // Tên shop (Required)
}
```

**Response 201:**
```json
{
  "success": true,
  "data": {
    "user_id": 42,
    "roles": ["BUYER", "SELLER"],
    "trust_score": 80,
    "seller_id": 10
  }
}
```

---

## Trust Score

### GET /users/me/trust-score/logs
**Lịch sử biến động Trust Score**

**Quyền truy cập**: JWT Required

**Query Params:**

| Param | Type | Mô tả |
|-------|------|-------|
| page | integer | Default 0 |
| size | integer | Default 20, max 100 |

**Response 200:**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "log_id": 1,
        "event_code": "ORDER_COMPLETED",
        "delta": 5,
        "reason": "Giao hàng thành công",
        "changed_by": "SYSTEM",
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

## Support — Trust Score Appeal

### GET /support/trust-score-appeal/presigned-url
**Lấy pre-signed URL upload bằng chứng khiếu nại**

**Quyền truy cập**: JWT Required

**Query Params:**

| Param | Type | Required | Mô tả |
|-------|------|----------|-------|
| file_name | string | ✓ | Tên file gốc |
| content_type | string | ✓ | image/jpeg | image/png | image/webp |

**Response 200:**
```json
{
  "presigned_url": "https://minio.internal/...",
  "object_url": "https://cdn.marketplace.vn/...",
  "expires_in": 900
}
```

---

### POST /support/trust-score-appeal
**Gửi khiếu nại Trust Score**

**Quyền truy cập**: JWT Required

**Request Body:**
```json
{
  "trust_score_log_id": "integer",  // ID log muốn khiếu nại (Required)
  "reason": "string",               // Lý do khiếu nại (Required)
  "evidence_urls": ["string"]       // Mảng URL ảnh bằng chứng từ MinIO
}
```

**Response 201:** Appeal created (status = PENDING)

---

## Loyalty Points

### GET /loyalty/balance
**Số dư điểm thưởng**

**Quyền truy cập**: JWT Required

**Response 200:**
```json
{
  "success": true,
  "data": {
    "user_id": 42,
    "available_points": 15200,
    "lifetime_earned": 50000,
    "lifetime_used": 34800,
    "lifetime_expired": 0,
    "tier": "PLATINUM",
    "tier_progress": {
      "current": 15200,
      "next_tier_at": 25000,
      "next_tier": "ELITE"
    }
  }
}
```

---

### GET /loyalty/transactions
**Lịch sử giao dịch điểm**

**Quyền truy cập**: JWT Required

**Query Params:**

| Param | Type | Mô tả |
|-------|------|-------|
| type | string | EARNED | USED | EXPIRED | REFUNDED |
| page | integer | Default 0 |
| size | integer | Default 20 |

**Response 200:**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "transaction_id": 1,
        "type": "EARNED",
        "delta": 500,
        "balance_after": 15200,
        "reference": "Order #100",
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

### GET /loyalty/estimate
**Tính điểm thưởng dự kiến cho đơn hàng**

**Quyền truy cập**: JWT Required

**Query Params:**

| Param | Type | Required | Mô tả |
|-------|------|----------|-------|
| amount | decimal | ✓ | Tổng tiền đơn hàng (VNĐ) |

**Response 200:**
```json
{
  "success": true,
  "data": {
    "estimated_points": 250,
    "rate": "0.5%",
    "tier_multiplier": 1.5,
    "final_points": 375
  }
}
```

---

## Admin — User Management

### GET /admin/users
**Danh sách người dùng (Admin)**

**Quyền truy cập**: JWT Required (ADMIN)

**Query Params:**

| Param | Type | Mô tả |
|-------|------|-------|
| status | string | ACTIVE | LOCKED |
| role | string | BUYER | SELLER | ADMIN |
| trust_score_min | integer | | 
| trust_score_max | integer | |
| product_posting_suspended | boolean | |
| q | string | Tìm theo username, email, phone |
| page | integer | Default 0 |
| size | integer | Default 20 |

**Response 200:**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "user_id": 42,
        "username": "nguyen_van_a",
        "email": "a@example.com",
        "phone": "0901234567",
        "full_name": "Nguyễn Văn A",
        "status": "ACTIVE",
        "roles": ["BUYER", "SELLER"],
        "trust_score": 80,
        "trust_tier": "PLATINUM",
        "product_posting_suspended": false,
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

### POST /admin/users/{userId}/lock
**Khóa tài khoản**

**Quyền truy cập**: JWT Required (ADMIN)

**Request Body:**
```json
{
  "reason": "string",            // (Required)
  "locked_until": "datetime"     // null = vĩnh viễn
}
```

**Response 200:** User locked

**Errors:** 409 (already locked)

---

### POST /admin/users/{userId}/unlock
**Mở khóa tài khoản**

**Quyền truy cập**: JWT Required (ADMIN)

**Request Body:**
```json
{
  "reason": "string"    // (Required)
}
```

**Response 200:** User unlocked

---

### POST /admin/users/{userId}/trust-score
**Điều chỉnh trust score thủ công**

**Quyền truy cập**: JWT Required (ADMIN)

**Request Body:**
```json
{
  "delta": "integer",    // +/- điểm (Required)
  "reason": "string"     // (Required)
}
```

**Response 200:** Trust score adjusted

---

### GET /admin/users/{userId}/trust-score/logs
**Lịch sử trust score của user**

**Quyền truy cập**: JWT Required (ADMIN)

**Query Params:** page, size

---

### GET /admin/users/{userId}/ban-history
**Lịch sử khóa/mở tài khoản**

**Quyền truy cập**: JWT Required (ADMIN)

**Response 200:**
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "action": "LOCKED",
      "reason": "Trust score quá thấp",
      "performed_by": "SYSTEM",
      "locked_until": null,
      "created_at": "2025-11-01T08:00:00Z"
    }
  ]
}
```

---

### POST /admin/users/{userId}/unlock-product-posting
**Gỡ tạm dừng đăng sản phẩm (Seller)**

**Quyền truy cập**: JWT Required (ADMIN)

**Request Body:**
```json
{
  "note": "string"    // (Required)
}
```

**Response 200:** Product posting unlocked

---

## Admin — Trust Score Config

### GET /admin/trust-score-events-config
**Xem cấu hình delta sự kiện trust score**

**Quyền truy cập**: JWT Required (ADMIN)

**Response 200:**
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "event_code": "PRODUCT_REJECTED_FIRST",
      "delta": -5,
      "description": "Sản phẩm bị từ chối lần đầu",
      "is_active": true,
      "updated_at": "2025-11-01T08:00:00Z"
    }
  ]
}
```

---

### PUT /admin/trust-score-events-config/{eventCode}
**Cập nhật delta / bật-tắt sự kiện**

**Quyền truy cập**: JWT Required (ADMIN)

**Request Body** (all optional):
```json
{
  "delta": "integer",
  "description": "string",
  "is_active": "boolean"
}
```

---

## Admin — Appeals

### GET /admin/appeals
**Danh sách khiếu nại Trust Score**

**Quyền truy cập**: JWT Required (ADMIN)

**Query Params:**

| Param | Type | Mô tả |
|-------|------|-------|
| status | string | PENDING | APPROVED | REJECTED (default: PENDING) |
| page | integer | Default 0 |
| size | integer | Default 20 |

---

### POST /admin/appeals/{appealId}/resolve
**Duyệt hoặc từ chối khiếu nại**

**Quyền truy cập**: JWT Required (ADMIN)

**Request Body:**
```json
{
  "action": "string",       // APPROVED | REJECTED (Required)
  "adminNote": "string"     // Ghi chú (Required)
}
```

**Response 200:** Appeal resolved
