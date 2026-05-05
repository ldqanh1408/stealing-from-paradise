# 🔐 Identity Service API

**Port**: `:8081`  
**Mô tả**: Đăng ký, đăng nhập, JWT, quản lý địa chỉ  
**Base URL**: `/api/v1`  
**Authentication**: JWT · RS256

---

## 📚 Mục Lục

1. [Authentication Endpoints](#authentication-endpoints)
2. [User Profile Endpoints](#user-profile-endpoints)
3. [Address Management Endpoints](#address-management-endpoints)
4. [Seller Registration](#seller-registration)

---

## Authentication Endpoints

### POST /auth/register
**Đăng ký tài khoản mới**

**Quyền truy cập**: Public (không cần JWT)

**Request Body**:
```json
{
  "username": "nguyen_van_a",
  "email": "a@example.com",
  "phone": "0901234567",
  "password": "SecurePass123!",
  "full_name": "Nguyễn Văn A"
}
```

**Validation Rules**:
| Field | Type | Rules |
|-------|------|-------|
| username | string | 3–50 ký tự, a-z, 0-9, dấu chấm/gạch dưới; Unique |
| email | string | Email hợp lệ; Unique |
| phone | string | SĐT Việt Nam; Unique |
| password | string | Tối thiểu 8 ký tự; ≥1 chữ hoa, ≥1 số |
| full_name | string | 2–100 ký tự |

**Response 201** — Tạo tài khoản thành công:
```json
{
  "user_id": 42,
  "username": "nguyen_van_a",
  "email": "a@example.com",
  "phone": "0901234567",
  "full_name": "Nguyễn Văn A",
  "status": "ACTIVE",
  "created_at": "2026-04-15T08:00:00Z"
}
```

**Kafka Events**:
```json
{
  "topic": "account.created",
  "payload": {
    "user_id": 42,
    "email": "a@example.com",
    "phone": "0901234567",
    "username": "nguyen_van_a",
    "timestamp": "2026-04-15T08:00:00Z",
    "source": "auth-service"
  }
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
  "credential": "a@example.com",
  "password": "SecurePass123!"
}
```

**Validation Rules**:
| Field | Type | Rules |
|-------|------|-------|
| credential | string | username \| email \| phone |
| password | string | Min 1 ký tự |

**Response 200**:
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refresh_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 900,
  "refresh_expires_in": 604800,
  "user_id": 42,
  "username": "nguyen_van_a",
  "email": "a@example.com",
  "phone": "0901234567",
  "full_name": "Nguyễn Văn A",
  "status": "ACTIVE",
  "created_at": "2026-04-15T08:05:00Z"
```

**Response 403 — Account Locked**:
```json
{
  "error": "ACCOUNT_LOCKED",
  "message": "Tài khoản bị khóa",
  "status_code": 403
}
```

**Kafka Events**:
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

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 200 | Đăng nhập thành công |
| 401 | Sai mật khẩu hoặc tài khoản không tồn tại |
| 403 | Tài khoản bị khóa (status = LOCKED) — kèm lock_reason |
| 400 | Validation thất bại |

---

### POST /auth/refresh
**Làm mới access token**

**Quyền truy cập**: Public

**Request Body**:
```json
{
  "refresh_token": "eyJhbGc..."
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

**Mô tả**: Đưa access token hiện tại vào Redis blocklist cho đến khi hết hạn. Nếu client gửi kèm `refresh_token`, hệ thống cũng thu hồi refresh token.

**Request Body** (optional):
```json
{
  "refresh_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "logout_all_devices": false
}
```

**Response 200**:
```json
{
  "message": "Đăng xuất thành công, token hiện tại bị vô hiệu hóa"
}
```

**Redis Side Effect**:
```
SET revoked_token:{jti} = 1 EX 900
// TTL = token expiration time (default 15 min)
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
  "status": "ACTIVE",
  "created_at": "2024-01-15T08:00:00Z"
}
```

---

### PUT /users/me
**Cập nhật thông tin cá nhân**

**Quyền truy cập**: JWT Required

**Request Body** (tất cả optional):
```json
{
  "full_name": "Nguyễn Văn A",
  "phone": "0901234567"
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
  "status": "ACTIVE",
  "created_at": "2024-01-15T08:00:00Z",
  "updated_at": "2026-04-15T10:30:00Z"
}
```

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 409 | phone mới đã tồn tại |
| 400 | Validation thất bại |
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

> Địa chỉ mặc định (is_default=true) luôn ở đầu

---

### POST /users/me/addresses
**Thêm địa chỉ mới**

**Quyền truy cập**: JWT Required

**Request Body**:
```json
{
  "province_id": 79,
  "district_id": 760,
  "full_address": "123 Nguyễn Trãi, Phường 2",
  "is_default": false
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
  "province_id": 79,
  "district_id": 760,
  "full_address": "456 Lê Lợi, Phường 1",
  "is_default": true
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

### POST /auth/register/seller
**Đăng ký tài khoản Seller mới**

**Quyền truy cập**: Public (không cần JWT)

**Request Body**: Giống `/auth/register`, tài khoản được tạo với role SELLER.

**Response 201**: Tương tự `/auth/register` với `roles: ["SELLER"]`.

---

### POST /users/me/change-password
**Đổi mật khẩu**

**Quyền truy cập**: JWT Required

**Request Body**:
```json
{
  "old_password": "OldPass123!",
  "new_password": "NewPass456!"
}
```

**Response 200**:
```json
{
  "message": "Đổi mật khẩu thành công"
}
```

---

## 🛡️ Admin Management

Các admin endpoints được route từ API Gateway dưới prefix `/admin/**` và được xử lý bởi **Identity Service** (user management, product moderation, flash sale, refund, failed events). Tất cả yêu cầu JWT + ADMIN role.

### Product Management

#### GET /admin/products/pending
**Sản phẩm chờ duyệt**

**Query Params**: category_id, seller_id, page, size

#### POST /admin/products/{productId}/approve
**Duyệt sản phẩm — Tags**: Kafka → product.approved

**Request Body**:
```json
{ "note": "Sản phẩm đạt chuẩn" }
```

#### POST /admin/products/{productId}/reject
**Từ chối sản phẩm — Tags**: Kafka → product.rejected

**Request Body**:
```json
{ "reason": "Hình ảnh không đạt chất lượng" }
```

---

### User Management

#### GET /admin/users
**Danh sách người dùng**

**Query Params**: status (ACTIVE|LOCKED), role (BUYER|SELLER|ADMIN), q, page, size

#### POST /admin/users/{userId}/lock
**Khóa tài khoản — Tags**: Revoke JWTs | Kafka → account.locked

**Request Body**:
```json
{ "reason": "Vi phạm chính sách", "locked_until": "2026-05-15T10:00:00Z" }
```

#### POST /admin/users/{userId}/unlock
**Mở khóa tài khoản — Tags**: Kafka → account.unlocked

#### POST /admin/users/{userId}/suspend-posting
**Tạm dừng đăng sản phẩm (Seller) — Tags**: Kafka → seller.posting_suspended

#### POST /admin/users/{userId}/unlock-product-posting
**Gỡ tạm dừng đăng sản phẩm — Tags**: Kafka → seller.posting_resumed

---

### Flash Sale Management

#### GET /admin/flash-sale/sessions
**Danh sách Flash Sale Sessions**. Query: status (UPCOMING|ACTIVE|ENDED), page, size

#### PUT /admin/flash-sale/sessions/{sessionId}
**Cập nhật session**. ⚠️ Không thể cập nhật nếu ACTIVE hoặc ENDED

#### DELETE /admin/flash-sale/sessions/{sessionId}
**Xóa session**. ⚠️ Không thể xóa nếu ACTIVE hoặc có FS_ITEMS APPROVED

#### POST /flash-sale/sessions/{sessionId}/items/{itemId}/approve
**Duyệt Flash Sale Item — Tags**: Kafka → flash_sale.item_approved

#### POST /admin/flash-sale/items/{itemId}/reject
**Từ chối Flash Sale Item — Tags**: Kafka → flash_sale.item_rejected

---

### Refund Management

#### GET /admin/refunds
**Tất cả yêu cầu hoàn tiền**. Query: status, type, seller_id, from_date, to_date, page, size

#### POST /admin/refunds/{refundId}/approve
**Duyệt hoàn tiền thủ công — Tags**: Kafka → refund.admin_approved

**Request Body**:
```json
{ "admin_note": "Đã xác nhận hoàn tiền", "adjust_amount": null, "caused_by": "SELLER" }
```

#### POST /admin/refunds/{refundId}/reject
**Từ chối yêu cầu hoàn tiền — Tags**: Kafka → refund.rejected

---

### Failed Events Management

#### GET /admin/failed-events
**Danh sách events thất bại (DLQ)**. Query: status (PENDING|DEAD|RESOLVED), topic_or_task, page, size

#### POST /admin/failed-events/{eventId}/retry
**Retry thủ công event thất bại — Tags**: Re-publish vào Kafka

#### POST /admin/failed-events/{eventId}/resolve
**Đánh dấu event đã xử lý thủ công**

---

## 📊 Summary — Identity Service

| Endpoint | Method | Auth |
|----------|--------|------|
| /auth/register | POST | Public |
| /auth/login | POST | Public |
| /auth/refresh | POST | Public |
| /auth/logout | POST | JWT |
| /users/me | GET | JWT |
| /users/me | PUT | JWT |
| /users/me/avatar/presigned-url | GET | JWT |
| /users/me/addresses | GET | JWT |
| /users/me/addresses | POST | JWT |
| /users/me/addresses/{id} | PUT | JWT |
| /users/me/addresses/{id} | DELETE | JWT |
| /auth/register/seller | POST | Public |
| /users/me/roles/seller | POST | JWT (BUYER) |
| /users/me/change-password | POST | JWT |

---

**Phiên bản:** v5.4  
**Cập nhật:** 2026-04-30
