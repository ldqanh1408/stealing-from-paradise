# 🔔 Notification Service API

**Service Name**: Notification Service  
**Port**: `:8088`  
**Base URL**: `/api/v1`  
**Status**: v5.3 RTS

**Mô tả**: WebFlux · SSE · Redis Pub/Sub · MongoDB TTL 90 ngày

---

## 📡 Kafka Integration

### Produces (Event Publisher)
- None (Notification aggregator only)

### Consumes (Event Subscriber)
- `account.locked` ← Identity Service
- `account.auto_locked` ← Worker Service
- `account.unlocked` ← Identity Service
- `flash_sale.session_started` ← Flash Sale Service
- `flash_sale.session_ended` ← Flash Sale Service
- `flash_sale.item_approved` ← Admin/Flash Sale Service
- `flash_sale.item_rejected` ← Flash Sale Service
- `order.auto_cancelled` ← Worker Service (JOB-13)
- `order.shipped` ← Order Service
- `refund.approved` ← Payment Service
- `refund.rejected` ← Payment Service
- `refund.requested` ← Payment Service
- `product.rejected` ← Admin Service
- `seller.posting_suspended` ← Identity Service
- `seller.posting_resumed` ← Identity Service
- `appeal.resolved` ← Identity Service
- `loyalty.points_earned` ← Identity Service (NEW - consolidated)

---

## 📡 Real-Time Notifications (SSE)

### GET /notifications/stream
**Kết nối SSE real-time (text/event-stream)**

**Quyền truy cập**: JWT Required

**Mô tả**: Endpoint SSE (Server-Sent Events) — trả về `text/event-stream`. Connection giữ mở, server push event khi có thông báo. Dùng `EventSource` API.

**SSE Format**:
```
data: {"notif_id":"64f3a...","type":"REFUND_APPROVED","title":"Hoàn tiền thành công","body":"Yêu cầu hoàn 350.000đ đã được duyệt","priority":"NORMAL","metadata":{"deeplink":"/orders/100/refunds/88"},"created_at":"2025-10-05T14:00:00Z"}
```

**Ghi chú**:
- Redis Pub/Sub buffer: 60 giây
- Header `Last-Event-ID` để replay event bị bỏ lỡ
- Không có query params — dùng `GET /notifications` cho lịch sử

**Headers**:
```
Content-Type: text/event-stream
Cache-Control: no-cache
Connection: keep-alive
```

**Client Example (JavaScript)**:
```javascript
const eventSource = new EventSource('/api/v1/notifications/stream', {
  headers: { 'Authorization': 'Bearer ' + token }
});

eventSource.onmessage = (event) => {
  const notification = JSON.parse(event.data);
  console.log('Notification:', notification);
};

eventSource.onerror = () => {
  eventSource.close();
};
```

---

## 📋 Pagination Endpoints

### GET /notifications
**Danh sách thông báo (Pagination)**

**Quyền truy cập**: JWT Required

**Mô tả**: Endpoint REST trả về danh sách có phân trang từ MongoDB.

**Query Params**:
| Param | Type | Mô tả |
|-------|------|-------|
| is_read | boolean | true = đã đọc \| false = chưa đọc (optional) |
| page | integer | Trang hiện tại (default: 0) |
| size | integer | Số bản ghi/trang (default: 20, max: 100) |

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

## ✅ Mark as Read

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

## 📊 Unread Count

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

## 📧 Notification Types (Partial List)

| Type | Source | Trigger |
|------|--------|---------|
| ACCOUNT_LOCKED | Identity | Account locked |
| ACCOUNT_AUTO_LOCKED | Worker | Trust score too low |
| ACCOUNT_UNLOCKED | Identity | Account unlocked |
| FLASH_SALE_STARTED | Flash Sale | Session started |
| FLASH_SALE_ENDED | Flash Sale | Session ended |
| FLASH_SALE_ITEM_APPROVED | Flash Sale | Item approved by admin |
| FLASH_SALE_ITEM_REJECTED | Flash Sale | Item rejected |
| ORDER_SHIPPED | Order | Order shipped |
| REFUND_APPROVED | Payment | Refund approved |
| REFUND_REJECTED | Payment | Refund rejected |
| REFUND_REQUESTED | Payment | Refund requested |
| PRODUCT_REJECTED | Admin | Product rejected |
| SELLER_POSTING_SUSPENDED | Identity | Posting suspended |
| SELLER_POSTING_RESUMED | Identity | Posting resumed |
| APPEAL_RESOLVED | Identity | Appeal decision |
| LOYALTY_POINTS_EARNED | Identity | Points credited |

---

## 📊 Summary

| Metric | Value |
|--------|-------|
| **Total Endpoints** | 5 |
| **Real-time Endpoints** | 1 (SSE) |
| **Pagination Endpoints** | 1 |
| **Mark as Read Endpoints** | 2 |
| **Count Endpoints** | 1 |
| **Kafka Topics Produced** | 0 |
| **Kafka Topics Consumed** | 15+ |

---

## 🔗 Integration Points

| Service | Topic | Direction | Mô tả |
|---------|-------|-----------|-------|
| **Identity Service** | account.locked | ← | Account lock notifications |
| **Identity Service** | account.unlocked | ← | Account unlock notifications |
| **Identity Service** | appeal.resolved | ← | Appeal decision notifications |
| **Identity Service** | loyalty.points_earned | ← | Points earned notifications |
| **Flash Sale Service** | flash_sale.session_started | ← | Session start notifications |
| **Flash Sale Service** | flash_sale.session_ended | ← | Session end notifications |
| **Flash Sale Service** | flash_sale.item_approved | ← | Item approval notifications |
| **Flash Sale Service** | flash_sale.item_rejected | ← | Item rejection notifications |
| **Order Service** | order.shipped | ← | Shipping notifications |
| **Payment Service** | refund.requested | ← | Refund request notifications |
| **Payment Service** | refund.admin_approved | ← | Refund approval notifications |
| **Payment Service** | refund.rejected | ← | Refund rejection notifications |
| **Admin Service** | product.rejected | ← | Product rejection notifications |
| **Worker Service** | order.auto_cancelled | ← | Auto-cancel notifications |

---

**Last Updated**: 2026-04-28  
**Version**: v5.3 RTS

