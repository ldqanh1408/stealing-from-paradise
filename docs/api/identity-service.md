# 🔐 Identity Service API (+ Loyalty)

**Service Name**: Identity Service + Loyalty (Consolidated)
**Port**: `:8081`  
**Base URL**: `/api/v1`  
**Status**: v5.3 RTS

**Mô tả**: Đăng ký, đăng nhập, JWT, quản lý địa chỉ, trust score, khiếu nại + Điểm thưởng tích lũy

---

## 📡 Kafka Integration

### Produces (Event Publisher)
- `account.locked` → Notification Service (Account locked notification)
- `account.auto_locked` → Notification Service (Auto-locked due to low trust score)
- `account.unlocked` → Notification Service (Account unlocked notification)
- `appeal.resolved` → Notification Service (Appeal decision sent)
- `loyalty.points_earned` → Notification Service (Points credited)

### Consumes (Event Subscriber)
- `order.delivered` ← Order Service (Receive points to credit)
- `order.cancelled` ← Order Service (Refund points)

---

## 🔑 Authentication Endpoints

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
**Tags**: Revoke JWTs

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

## 👤 User Profile Endpoints

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

## 📍 Address Management Endpoints

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

## 👨‍💼 Seller Registration

### POST /users/me/roles/seller
**Đăng ký trở thành Seller**

**Quyền truy cập**: JWT Required (BUYER role)

**Request Body**: (không có body)

**Response 200**: Đăng ký Seller thành công

---

## ⭐ Trust Score Appeal Endpoints

### GET /support/trust-score-appeal/presigned-url
**Lấy Presigned URL upload ảnh bằng chứng khiếu nại**

**Quyền truy cập**: JWT Required

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

## ⭐ Loyalty Service Endpoints

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
  }
}
```

**Ghi chú**:
- `conversion_rate`: số điểm tương đương 1.000 VNĐ
- `pending_points`: điểm từ đơn chưa DELIVERED
- Các điểm hết hạn sau 365 ngày

---

### GET /loyalty/transactions
**Lịch sử giao dịch điểm**

**Quyền truy cập**: JWT Required

**Query Params**:
| Param | Type | Mô tả |
|-------|------|-------|
| type | string | EARNED \| USED \| EXPIRED \| REFUNDED |
| status | string | PENDING \| CONFIRMED |
| page | integer | Trang hiện tại (default: 0) |
| size | integer | Số bản ghi/trang (default: 20, max: 100) |

**Response 200**:
```json
{
  "content": [
    {
      "transaction_id": 501,
      "type": "EARNED",
      "delta": 300,
      "status": "PENDING",
      "order_id": 100,
      "order_code": "OR-20251001-100",
      "created_at": "2025-10-01T10:00:00Z",
      "expires_at": "2026-10-01T10:00:00Z"
    },
    {
      "transaction_id": 502,
      "type": "USED",
      "delta": -50,
      "status": "CONFIRMED",
      "order_id": 101,
      "discount_amount": 250000,
      "created_at": "2025-10-05T14:30:00Z"
    }
  ],
  "total_elements": 15,
  "total_pages": 1
}
```

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
  "cap_percent": 20,
  "order_amount": 1000000,
  "discount_preview": {
    "points_to_use": 50,
    "discount_amount": 250000
  }
}
```

---

## 📊 Summary

| Metric | Value |
|--------|-------|
| **Total Endpoints** | 21 |
| **Auth Endpoints** | 4 |
| **Profile Endpoints** | 5 |
| **Address Endpoints** | 4 |
| **Seller Endpoints** | 1 |
| **Trust Score Endpoints** | 3 |
| **Loyalty Endpoints** | 3 (NEW - consolidated) |
| **Kafka Topics Produced** | 5 |
| **Kafka Topics Consumed** | 2 |

---

## 🔗 Integration Points

| Service | Topic | Direction | Mô tả |
|---------|-------|-----------|-------|
| **Worker Service** | account.locked | ← | JWT revocation |
| **Notification Service** | account.locked | ← | Lock notifications |
| **Notification Service** | appeal.resolved | ← | Appeal resolution |
| **Search Service** | account.locked | ← | Hide seller products |
| **Order Service** | order.delivered | → | Listen for points credit |
| **Order Service** | order.cancelled | → | Listen for points refund |
| **Notification Service** | loyalty.points_earned | ← | Points earned notifications |

---

**Last Updated**: 2026-04-28  
**Version**: v5.3 RTS



