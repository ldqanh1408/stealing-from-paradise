# Notification Service — API Reference

> Base path: `/api/v1` → Gateway routes to `notification-service:8087`
>
> Database: MongoDB (TTL 90 ngày)
>
> Real-time: SSE (Server-Sent Events) + Redis Pub/Sub

---

## Real-time Stream

### GET /notifications/stream
**Kết nối SSE real-time**

**Quyền truy cập**: JWT Required

**Response:** `text/event-stream`

**SSE Format:**
```
data: {"notif_id":"64f3a...","type":"REFUND_APPROVED","title":"Hoàn tiền thành công","body":"Yêu cầu hoàn 350.000đ đã được duyệt","priority":"NORMAL","metadata":{"deeplink":"/orders/100/refunds/88"},"created_at":"2025-10-05T14:00:00Z"}
```

**Ghi chú:**
- Redis Pub/Sub buffer: 60 giây
- Header `Last-Event-ID` để replay event bị bỏ lỡ
- Dùng `EventSource` API phía client
- Không có query params — dùng `GET /notifications` cho lịch sử

---

## Notification History

### GET /notifications
**Danh sách thông báo (Pagination)**

**Quyền truy cập**: JWT Required

**Query Params:**

| Param | Type | Mô tả |
|-------|------|-------|
| is_read | boolean | true = đã đọc | false = chưa đọc |
| page | integer | Default 0 |
| size | integer | Default 20 |

**Response 200:**
```json
{
  "success": true,
  "data": {
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
    "page": 0,
    "size": 20,
    "total_elements": 24,
    "total_pages": 2,
    "last": false
  },
  "unread_count": 5
}
```

---

### PATCH /notifications/{notifId}/read
**Đánh dấu đã đọc**

**Quyền truy cập**: JWT Required

**Request Body:** (no body)

**Response 200:** Notification marked as read

---

### PATCH /notifications/read-all
**Đánh dấu tất cả đã đọc**

**Quyền truy cập**: JWT Required

**Request Body:** (no body)

**Response 200:**
```json
{
  "success": true,
  "data": {
    "updated_count": 5
  }
}
```

---

### GET /notifications/unread-count
**Đếm thông báo chưa đọc**

**Quyền truy cập**: JWT Required

**Response 200:**
```json
{
  "success": true,
  "data": {
    "unread_count": 5
  }
}
```
