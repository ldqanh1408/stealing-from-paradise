# 🔐 Identity Service API

**Port**: `:8081`  
**Mô tả**: Đăng ký, đăng nhập, JWT, quản lý địa chỉ, trust score, **điểm thưởng Loyalty** (gộp từ Loyalty Service `:8084`)  
**Base URL**: `/api/v1`  
**Authentication**: JWT · RS256

---

## 📚 Mục Lục

1. [Authentication Endpoints](#authentication-endpoints)
2. [User Profile Endpoints](#user-profile-endpoints)
3. [Address Management Endpoints](#address-management-endpoints)
4. [Seller Registration](#seller-registration)
5. [Trust Score Appeal Endpoints](#trust-score-appeal-endpoints)
6. [⭐ Loyalty Endpoints](#-loyalty-endpoints) *(gộp từ Loyalty Service)*

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
  "roles": ["BUYER"],
  "status": "ACTIVE",
  "trust_score": 80,
  "trust_tier": "PLATINUM",
  "avatar_url": null,
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
    "roles": ["BUYER"],
    "trust_score": 80,
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
  "roles": ["BUYER", "SELLER"],
  "status": "ACTIVE",
  "trust_score": 80,
  "trust_tier": "PLATINUM",
  "avatar_url": "https://cdn.marketplace.vn/avatars/42.jpg"
}
```

**Response 403 — Account Locked**:
```json
{
  "error": "ACCOUNT_LOCKED",
  "message": "Tài khoản bị khóa",
  "lock_reason": "Trust score quá thấp (< 10). Liên hệ support để khiếu nại.",
  "locked_until": "2026-05-15T10:00:00Z",
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

**Trust Tiers** [UNIFIED]:
| Tier | Điểm |
|------|------|
| BRONZE | 0–39 |
| SILVER | 40–59 |
| GOLD | 60–79 |
| PLATINUM | 80–89 |
| DIAMOND | 90–99 |
| ELITE | 100 |

> Mặc định khi tạo tài khoản = 80 (PLATINUM)

---

### PUT /users/me
**Cập nhật thông tin cá nhân**

**Quyền truy cập**: JWT Required

**Request Body** (tất cả optional):
```json
{
  "full_name": "Nguyễn Văn A",
  "avatar_url": "https://cdn.marketplace.vn/avatars/42/uuid.jpg",
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

## Trust Score Appeal Endpoints

### GET /support/trust-score-appeal/presigned-url
**Lấy Presigned URL upload ảnh bằng chứng khiếu nại**

**Quyền truy cập**: JWT Required  
**Tag**: GAP-PATCH R1

**Mô tả**: Trả về Pre-signed PUT URL để upload ảnh bằng chứng lên MinIO bucket `appeal-evidence`. TTL: 15 phút.

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
  "log_id": 1042,
  "reason": "Tôi không hủy đơn quá số lần cho phép. Có bug hệ thống.",
  "evidence_urls": [
    "https://cdn.marketplace.vn/appeal-evidence/appeals/42/uuid-abc.jpg",
    "https://cdn.marketplace.vn/appeal-evidence/appeals/42/uuid-def.jpg"
  ]
}
```

**Validation Rules**:
| Field | Type | Rules |
|-------|------|-------|
| log_id | long | Phải tồn tại trong TRUST_SCORE_LOGS của user |
| reason | string | Tối đa 500 ký tự |
| evidence_urls | array | 0–5 URLs từ presigned URLs |

**Response 201**:
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

**Kafka Events**:
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

### GET /support/trust-score-appeal
**Danh sách khiếu nại của tôi**

**Quyền truy cập**: JWT Required

**Response 200**: Danh sách các appeal của user kèm trạng thái (PENDING/APPROVED/REJECTED).

---

## ⭐ Loyalty Endpoints

> **Ghi chú**: Loyalty Service (port `:8084`) đã được gộp vào Identity Service. Các endpoint loyalty được đặt dưới Identity Service.

### GET /loyalty/balance
**Số dư điểm thưởng**

**Quyền truy cập**: JWT Required

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
      "created_at": "2026-10-01T10:00:00Z",
      "expires_at": "2026-10-01T10:00:00Z"
    }
  ]
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
| page, size | integer | Phân trang |

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

## 📊 Summary — Identity + Loyalty Service

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
| /support/trust-score-appeal/presigned-url | GET | JWT |
| /support/trust-score-appeal | POST | JWT |
| /support/trust-score-appeal | GET | JWT |
| /users/me/trust-score/logs | GET | JWT |
| /loyalty/balance | GET | JWT |
| /loyalty/transactions | GET | JWT |
| /loyalty/estimate | GET | JWT |

---

**Phiên bản:** v5.4  
**Cập nhật:** 2026-04-30
