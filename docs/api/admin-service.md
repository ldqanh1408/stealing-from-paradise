# 🛡️ Admin APIs

**Service Name**: Admin APIs (Cross-Service)  
**Base URL**: `/api/v1`  
**Status**: v5.3 RTS

**Mô tả**: Duyệt sản phẩm, quản lý user, trust score, điều hành hệ thống

---

## 📡 Kafka Integration

### Produces (Event Publisher)
- `product.approved` → Search Service (Index approved products)
- `product.rejected` → Notification Service (Rejection notification)

### Consumes (Event Subscriber)
- None directly

---

## 📦 Product Management

### GET /admin/products/pending
**Sản phẩm chờ duyệt**

**Quyền truy cập**: JWT Required (ADMIN)

**Query Params**:
| Param | Type | Mô tả |
|-------|------|-------|
| category_id | string | Lọc theo danh mục |
| seller_id | long | Lọc theo seller |
| page, size | integer | Phân trang |

**Response 200**: List of pending products

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

**Response 200**: Product approved, PRODUCTS.status = APPROVED, Kafka event produced

---

### POST /admin/products/{productId}/reject
**Từ chối sản phẩm**

**Quyền truy cập**: JWT Required (ADMIN)  
**Tags**: Kafka → product.rejected

**Request Body**:
```json
{
  "reason": "string",  // Lý do từ chối sản phẩm (Required)
  "note": "string"     // Ghi chú admin (Optional)
}
```

**Response 200**: Product rejected, PRODUCTS.status = REJECTED, notification sent

---

## 👥 User Management

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

**Response 200**: List of users with pagination

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

**Response 200**: Account locked

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

**Response 200**: Account unlocked

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

**Response 200**: Trust score updated, TRUST_SCORE_LOGS recorded

---

### GET /admin/users/{userId}/trust-score/logs
**Lịch sử trust score**

**Quyền truy cập**: JWT Required (ADMIN)

**Query Params**: Phân trang

**Response 200**: List of trust score logs

---

### GET /admin/users/{userId}/ban-history
**Lịch sử khóa/mở tài khoản**

**Quyền truy cập**: JWT Required (ADMIN)

**Response 200**: Array USER_BAN_HISTORY

---

### POST /admin/users/{userId}/unlock-product-posting
**Gỡ tạm dừng đăng sản phẩm (Seller)**

**Quyền truy cập**: JWT Required (ADMIN)

**Request Body**:
```json
{
  "note": "string"  // Lý do cho phép tiếp tục (Required)
}
```

**Response 200**: Product posting suspended flag reset

---

## ⭐ Trust Score Configuration

### GET /admin/trust-score-events-config
**Xem cấu hình delta sự kiện trust score**

**Quyền truy cập**: JWT Required (ADMIN)

**Response 200**: Array TRUST_SCORE_EVENTS_CONFIG

---

### PUT /admin/trust-score-events-config/{eventCode}
**Cập nhật delta / bật-tắt sự kiện**

**Quyền truy cập**: JWT Required (ADMIN)

**⚠️ Ghi chú**: Thay đổi có hiệu lực ngay, không áp dụng hồi tố

**Request Body** (all optional):
```json
{
  "delta": "integer",       // Delta mới (+/-)
  "description": "string",  // Mô tả mới
  "is_active": "boolean"    // Bật/tắt sự kiện
}
```

**Response 200**: Configuration updated

---

## 🎯 Appeal Management

### GET /admin/appeals
**Danh sách khiếu nại Trust Score chờ xét duyệt**

**Quyền truy cập**: JWT Required (ADMIN)

**Query Params**:
| Param | Type | Mô tả |
|-------|------|-------|
| status | string | PENDING \| APPROVED \| REJECTED (default: PENDING) |
| page, size | integer | Phân trang |

**Response 200**: List of appeals

---

### POST /admin/appeals/{appealId}/resolve
**Duyệt hoặc từ chối khiếu nại**

**Quyền truy cập**: JWT Required (ADMIN)  
**Tags**: Kafka → appeal.resolved

**Request Body**:
```json
{
  "action": "string",         // APPROVED | REJECTED (Required)
  "admin_note": "string"      // Ghi chú lý do quyết định (Required)
}
```

**Response 200**: Appeal resolved, notification sent

---

## 🚨 Failed Events Management

### GET /admin/failed-events
**Danh sách events thất bại (Admin)**

**Quyền truy cập**: JWT Required (ADMIN)

**Mô tả**: Danh sách Kafka event / scheduled task bị lỗi. Admin dùng để xem nguyên nhân, retry thủ công, hoặc mark RESOLVED.

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
**Tags**: Kafka → re-publish

**Mô tả**: Re-publish payload vào Kafka topic ban đầu. Bắt buộc idempotent.

**Request Body**: (không có body)

**Response 200**: Event được re-publish

---

### POST /admin/failed-events/{eventId}/resolve
**Đánh dấu event đã xử lý thủ công**

**Quyền truy cập**: JWT Required (ADMIN)

**Mô tả**: Dành cho trường hợp Admin xử lý ngoài hệ thống.

**Request Body**:
```json
{
  "resolution_note": "string"  // Mô tả cách xử lý (Required)
}
```

**Response 200**: Event marked as RESOLVED

---

## 📊 Summary

| Metric | Value |
|--------|-------|
| **Total Endpoints** | 14 |
| **Product Management** | 3 |
| **User Management** | 7 |
| **Trust Score Config** | 2 |
| **Appeal Management** | 2 |
| **Failed Events** | 3 |
| **Kafka Topics Produced** | 2 |
| **Kafka Topics Consumed** | 0 |

---

## 🔗 Integration Points

| Service | Topic | Direction | Mô tả |
|---------|-------|-----------|-------|
| **Search Service** | product.approved | → | Index approved products |
| **Notification Service** | product.rejected | → | Send rejection notifications |
| **Identity Service** | appeal.resolved | → | Send appeal decisions |
| **Refund Service** | (manual refund approval) | → | Approve/reject refunds |

---

**Last Updated**: 2026-04-28  
**Version**: v5.3 RTS

