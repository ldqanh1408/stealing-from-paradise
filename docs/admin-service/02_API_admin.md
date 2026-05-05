# 🛡️ Admin APIs

**Mô tả**: Duyệt sản phẩm, quản lý user, trust score, flash sale, refund, failed events, điều hành hệ thống  
**Base URL**: `/api/v1`  
**Quyền truy cập**: Tất cả endpoint yêu cầu JWT + ADMIN role

---

## 📚 Mục Lục

1. [Product Management](#product-management)
2. [User Management](#user-management)
3. [Flash Sale Management](#flash-sale-management)
4. [Refund Management](#refund-management)
5. [Failed Events Management](#failed-events-management)

---

## Product Management

### GET /admin/products/pending
**Sản phẩm chờ duyệt**

**Query Params**:
| Param | Type | Mô tả |
|-------|------|-------|
| category_id | string | Lọc theo danh mục |
| seller_id | long | Lọc theo seller |
| page, size | integer | Phân trang |

**Response 200**: Danh sách sản phẩm đang chờ duyệt với phân trang

---

### POST /admin/products/{productId}/approve
**Duyệt sản phẩm**

**Tags**: Kafka → product.approved

**Request Body**:
```json
{
  "note": "Sản phẩm đạt chuẩn"
}
```

**Response 200**:
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

**Kafka Events**:
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

### POST /admin/products/{productId}/reject
**Từ chối sản phẩm**

**Tags**: Kafka → product.rejected

**Request Body**:
```json
{
  "reason": "Hình ảnh không đạt chất lượng, thiếu ảnh đa góc độ",
  "note": "Vui lòng cung cấp ít nhất 3 ảnh góc khác nhau"
}
```

**Response 200**: Sản phẩm bị từ chối, Seller nhận notification kèm lý do

---

## User Management

### GET /admin/users
**Danh sách người dùng**

**Query Params**:
| Param | Type | Mô tả |
|-------|------|-------|
| status | string | ACTIVE \| LOCKED |
| role | string | BUYER \| SELLER \| ADMIN |
| trust_score_min | integer | Điểm tối thiểu [0–100] |
| trust_score_max | integer | Điểm tối đa [0–100] |
| product_posting_suspended | boolean | true = chỉ Seller bị đình chỉ |
| q | string | Tìm theo username, email, phone |
| page, size | integer | Phân trang |

**Response 200**:
```json
{
  "content": [
    {
      "user_id": 5,
      "username": "shop_nike_vn",
      "email": "shop@nike.vn",
      "full_name": "Shop Nike Vietnam",
      "status": "ACTIVE",
      "trust_score": 92,
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

### POST /admin/users/{userId}/lock
**Khóa tài khoản**

**Tags**: Revoke JWTs | Kafka → account.locked

**Mô tả**: Identity Service tức thì thêm toàn bộ JTI vào Redis blocklist.

**Request Body**:
```json
{
  "reason": "Vi phạm chính sách bán hàng",
  "locked_until": "2026-05-15T10:00:00Z"
}
```

**Validation Rules**:
| Field | Type | Rules |
|-------|------|-------|
| reason | string | Required; 1–500 ký tự |
| locked_until | datetime | Optional; null = vĩnh viễn |

**Response 200**:
```json
{
  "user_id": 5,
  "status": "LOCKED",
  "lock_reason": "Vi phạm chính sách bán hàng",
  "locked_until": "2026-05-15T10:00:00Z",
  "message": "Tài khoản đã bị khóa"
}
```

**Kafka Events**:
```json
{
  "topic": "account.locked",
  "payload": {
    "user_id": 5,
    "lock_reason": "Vi phạm chính sách bán hàng",
    "locked_until": "2026-05-15T10:00:00Z",
    "locked_by": 1,
    "locked_at": "2026-04-15T10:00:00Z"
  }
}
```

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 409 | Tài khoản đã bị LOCKED |

---

### POST /admin/users/{userId}/unlock
**Mở khóa tài khoản**

**Tags**: Kafka → account.unlocked

**Request Body**:
```json
{
  "reason": "Đã xử lý xong tranh chấp, cho phép hoạt động trở lại"
}
```

**Response 200**: Tài khoản được mở khóa, Seller nhận notification

---

### POST /admin/users/{userId}/unlock-product-posting
**Gỡ tạm dừng đăng sản phẩm (Seller)**

**Tag**: NEW v5.0

**Tags**: Kafka → seller.posting_resumed

**Request Body**:
```json
{
  "note": "Seller đã khắc phục vi phạm, cho phép đăng bài trở lại"
}
```

**Response 200**: product_posting_suspended = false, Seller nhận notification

---

## Flash Sale Management

### GET /admin/flash-sale/sessions
**Danh sách Flash Sale Sessions (toàn bộ trạng thái)**

**Tag**: NEW v5.1 — Gap A

**Query Params**:
| Param | Type | Mô tả |
|-------|------|-------|
| status | string | UPCOMING \| ACTIVE \| ENDED (optional) |
| page, size | integer | Phân trang |

---

### PUT /admin/flash-sale/sessions/{sessionId}
**Cập nhật Flash Sale Session**

**Tag**: NEW v5.1 — Gap A

**⚠️ Chặn**: Không thể cập nhật nếu session đang ACTIVE hoặc ENDED

**Request Body** (all optional):
```json
{
  "name": "Flash Sale 20h Thứ 6 - Đặc biệt",
  "start_time": "2026-04-15T20:00:00Z",
  "end_time": "2026-04-15T22:30:00Z"
}
```

---

### DELETE /admin/flash-sale/sessions/{sessionId}
**Xóa Flash Sale Session**

**Tag**: NEW v5.1 — Gap A

**⚠️ Chặn**: Không thể xóa nếu session ACTIVE hoặc có FS_ITEMS APPROVED

---

### POST /flash-sale/sessions/{sessionId}/items/{itemId}/approve
**Duyệt Flash Sale Item**

**Tags**: Kafka → flash_sale.item_approved

**Request Body**:
```json
{
  "note": "Sản phẩm đạt tiêu chí, giảm giá đủ 20%"
}
```

**Response 200**: FS_ITEMS.status = APPROVED

---

### POST /admin/flash-sale/items/{itemId}/reject
**Từ chối Flash Sale Item**

**Tags**: Kafka → flash_sale.item_rejected | NEW v5.1 — Gap A

**Request Body**:
```json
{
  "reject_reason": "Giá Flash Sale chưa đạt mức giảm tối thiểu 20% so với giá gốc"
}
```

**Response 200**: FS_ITEMS.status = REJECTED

---

## Refund Management

### GET /admin/refunds
**Tất cả yêu cầu hoàn tiền**

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
**Duyệt hoàn tiền thủ công [NEW v5.3 — Tracking Number]**

**Tags**: Stripe Refund API | Kafka → refund.admin_approved

**Request Body**:
```json
{
  "admin_note": "Hoàn do giao hàng không thành công, shipper mang lại lần 3",
  "adjust_amount": null,
  "caused_by": "SELLER",
  "tracking_number": "VT123456789"
}
```

> Xem chi tiết đầy đủ tại [Refund API — Admin Refund APIs](02_API_refund_service.md#admin-refund-apis)

---

### POST /admin/refunds/{refundId}/reject
**Từ chối yêu cầu hoàn tiền**

**Tags**: Kafka → refund.rejected

**Request Body**:
```json
{
  "reject_reason": "Không đủ bằng chứng sản phẩm lỗi",
  "fraud_evidence": false
}
```

---

## Failed Events Management

### GET /admin/failed-events
**Danh sách events thất bại (DLQ)**

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

### POST /admin/failed-events/{eventId}/retry
**Retry thủ công event thất bại**

**Tags**: Kafka → re-publish | NEW v5.1 — Gap A

**Mô tả**: Re-publish payload vào Kafka topic ban đầu. Bắt buộc idempotent.

**Request Body**: (không có body — `{}`)

**Response 200**:
```json
{
  "event_id": 42,
  "status": "PENDING",
  "message": "Event đã được re-publish vào Kafka",
  "retry_at": "2026-04-15T10:00:00Z"
}
```

**Kafka Events**:
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

### POST /admin/failed-events/{eventId}/resolve
**Đánh dấu event đã xử lý thủ công**

**Tag**: NEW v5.1 — Gap A

**Mô tả**: Dành cho trường hợp Admin xử lý ngoài hệ thống (manual intervention).

**Request Body**:
```json
{
  "resolution_note": "Đã xử lý trực tiếp trên database, loyalty points đã được cộng thủ công"
}
```

**Response 200**: event.status = RESOLVED

---

## 📊 Summary — Admin APIs

| Endpoint | Method | Mô tả |
|----------|--------|-------|
| /admin/products/pending | GET | Sản phẩm chờ duyệt |
| /admin/products/{id}/approve | POST | Duyệt sản phẩm |
| /admin/products/{id}/reject | POST | Từ chối sản phẩm |
| /admin/categories | POST | Tạo danh mục |
| /admin/categories/{id} | PUT | Cập nhật danh mục |
| /admin/categories/{id} | DELETE | Xóa danh mục |
| /admin/users | GET | Danh sách users |
| /admin/users/{id}/lock | POST | Khóa tài khoản |
| /admin/users/{id}/unlock | POST | Mở khóa tài khoản |
| /admin/users/{id}/unlock-product-posting | POST | Gỡ tạm dừng đăng bài |
| /admin/flash-sale/sessions | GET | Danh sách sessions |
| /admin/flash-sale/sessions/{id} | PUT | Cập nhật session |
| /admin/flash-sale/sessions/{id} | DELETE | Xóa session |
| /flash-sale/sessions | POST | Tạo session mới |
| /flash-sale/sessions/{sid}/items/{iid}/approve | POST | Duyệt FS item |
| /admin/flash-sale/items/{id}/reject | POST | Từ chối FS item |
| /admin/refunds | GET | Danh sách refunds |
| /admin/refunds/{id}/approve | POST | Duyệt hoàn tiền |
| /admin/refunds/{id}/reject | POST | Từ chối hoàn tiền |
| /admin/failed-events | GET | Danh sách events lỗi |
| /admin/failed-events/{id}/retry | POST | Retry event |
| /admin/failed-events/{id}/resolve | POST | Mark resolved |

---

**Phiên bản:** v5.4  
**Cập nhật:** 2026-04-30
