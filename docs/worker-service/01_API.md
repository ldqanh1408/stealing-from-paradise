# Worker Service — API Reference

> Base path: `/api/v1` → Gateway routes to `worker-service:8089`
>
> Database: PostgreSQL
>
> Responsibilities: Failed Events management, Scheduled Jobs, Outbox processing

---

## Failed Events Management (Admin)

### GET /admin/failed-events
**Danh sách events thất bại**

**Quyền truy cập**: JWT Required (ADMIN)

**Mô tả:** Danh sách Kafka event / scheduled task bị lỗi. Admin xem nguyên nhân, retry thủ công, hoặc mark RESOLVED.

**Query Params:**

| Param | Type | Mô tả |
|-------|------|-------|
| status | string | PENDING | DEAD | RESOLVED | MANUAL_INTERVENTION |
| topic_or_task | string | Lọc theo topic Kafka hoặc tên task |
| page | integer | Default 0 |
| size | integer | Default 20 |

**Response 200:**
```json
{
  "success": true,
  "data": {
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
    "page": 0,
    "size": 20,
    "total_elements": 3,
    "total_pages": 1,
    "last": true
  }
}
```

---

### POST /admin/failed-events/{eventId}/retry
**Retry thủ công event thất bại**

**Quyền truy cập**: JWT Required (ADMIN)

**Mô tả:** Re-publish payload vào Kafka topic ban đầu. Bắt buộc idempotent.

**Request Body:** (no body)

**Response 200:**
```json
{
  "success": true,
  "message": "Event re-published to topic order.delivered",
  "data": {
    "event_id": 42,
    "topic_or_task": "order.delivered",
    "status": "PENDING"
  }
}
```

---

### POST /admin/failed-events/{eventId}/resolve
**Đánh dấu event đã xử lý thủ công**

**Quyền truy cập**: JWT Required (ADMIN)

**Mô tả:** Dành cho trường hợp Admin đã xử lý ngoài hệ thống.

**Request Body:**
```json
{
  "resolution_note": "string"    // Mô tả cách xử lý (Required)
}
```

**Response 200:**
```json
{
  "success": true,
  "data": {
    "event_id": 42,
    "status": "RESOLVED",
    "resolution_note": "Đã xử lý thủ công qua Stripe dashboard"
  }
}
```
