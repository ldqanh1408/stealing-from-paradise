# 🔔 Notification Service API

**Port**: `:8088`  
**Mô tả**: WebFlux · SSE · Redis Pub/Sub · MongoDB TTL 90 ngày  
**Base URL**: `/api/v1`

---

## GET /notifications/stream
**Kết nối SSE real-time (text/event-stream)**

**Quyền truy cập**: JWT Required

**[UNIFIED v5.3]** Endpoint SSE (Server-Sent Events) — trả về `text/event-stream`. Connection giữ mở, server push event khi có thông báo. Dùng `EventSource` API.

**Headers Required**:
```
Authorization: Bearer <access_token>
```

**SSE Format** (`text/event-stream`):
```
data: {"notif_id":"64f3a","type":"REFUND_APPROVED","title":"Hoàn tiền thành công","body":"Yêu cầu hoàn 350.000đ đã được duyệt","priority":"NORMAL","metadata":{"deeplink":"/orders/100/refunds/88"},"created_at":"2026-04-15T10:00:00Z"}

data: {"notif_id":"64f3b","type":"ORDER_SHIPPED","title":"Đơn hàng đang giao","body":"Mã vận đơn: VT123456789","priority":"NORMAL","metadata":{"deeplink":"/orders/100"},"created_at":"2026-04-15T10:05:00Z"}
```

**Ghi chú**:
- Redis Pub/Sub buffer: 60 giây
- Header `Last-Event-ID` để replay event bị bỏ lỡ
- Không có query params — dùng `GET /notifications` cho lịch sử

---

## GET /notifications
**Danh sách thông báo (Pagination)**

**Quyền truy cập**: JWT Required

**[UNIFIED v5.3]** Endpoint REST trả về danh sách có phân trang từ MongoDB.

**Query Params**:
| Param | Type | Mô tả |
|-------|------|-------|
| is_read | boolean | true = đã đọc \| false = chưa đọc (optional) |
| page | integer | Trang hiện tại (default: 0) |
| size | integer | Kích thước trang (default: 20) |

**Response 200**:
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

**Quyền truy cập**: JWT Required

**Request Body**: (không có body)

**Response 200**:
```json
{
  "notif_id": "64f3a",
  "is_read": true
}
```

---

## PATCH /notifications/read-all
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

## GET /notifications/unread-count
**Đếm thông báo chưa đọc**

**Quyền truy cập**: JWT Required

**Response 200**:
```json
{
  "unread_count": 5
}
```

---

## 📊 Notification Types

| Type | Mô tả | Priority |
|------|-------|----------|
| ORDER_CREATED | Đơn hàng mới | NORMAL |
| ORDER_PAID | Thanh toán thành công | HIGH |
| ORDER_SHIPPED | Đơn đang giao | NORMAL |
| ORDER_DELIVERED | Giao hàng thành công | NORMAL |
| ORDER_CANCELLED | Đơn bị hủy | HIGH |
| ORDER_AUTO_CANCELLED | Đơn bị hủy tự động (JOB-13) | HIGH |
| REFUND_REQUESTED | Yêu cầu hoàn tiền | NORMAL |
| REFUND_APPROVED | Hoàn tiền được duyệt | HIGH |
| REFUND_REJECTED | Hoàn tiền bị từ chối | HIGH |
| FLASH_SALE_STARTING | Flash Sale sắp bắt đầu | HIGH |
| FLASH_SALE_ENDED | Flash Sale kết thúc | LOW |
| FS_ITEM_APPROVED | Item Flash Sale được duyệt | NORMAL |
| FS_ITEM_REJECTED | Item Flash Sale bị từ chối | NORMAL |
| PRODUCT_APPROVED | Sản phẩm được duyệt | NORMAL |
| PRODUCT_REJECTED | Sản phẩm bị từ chối | HIGH |
| TRUST_SCORE_WARNING | Cảnh báo điểm tín nhiệm | HIGH |
| ACCOUNT_LOCKED | Tài khoản bị khóa | URGENT |
| ACCOUNT_UNLOCKED | Tài khoản được mở khóa | HIGH |
| APPEAL_RESOLVED | Kết quả xét duyệt khiếu nại | HIGH |
| STRIPE_ACCOUNT_SUSPENDED | Stripe bị đình chỉ | URGENT |
| SELLER_POSTING_SUSPENDED | Tạm dừng đăng sản phẩm | HIGH |
| SELLER_POSTING_RESUMED | Mở lại quyền đăng bài | NORMAL |

## 📊 Summary

| Endpoint | Method | Auth |
|----------|--------|------|
| /notifications/stream | GET | JWT |
| /notifications | GET | JWT |
| /notifications/{id}/read | PATCH | JWT |
| /notifications/read-all | PATCH | JWT |
| /notifications/unread-count | GET | JWT |

**Kafka Topics consumed by Notification Service**:
- `account.auto_locked`, `account.locked`, `account.unlocked`
- `flash_sale.session_started`, `flash_sale.session_ended`
- `flash_sale.item_approved`, `flash_sale.item_rejected`
- `flash_sale.reminder` (JOB-02)
- `order.auto_cancelled`, `order.cancelled`, `order.shipped`
- `payment.failed`
- `product.auto_hidden`, `product.rejected`
- `refund.admin_approved`, `refund.rejected`, `refund.requested`
- `seller.posting_resumed`, `seller.posting_suspended`
- `stripe.account_suspended`
- `trust_score.warning`
- `appeal.resolved`

---

**Phiên bản:** v5.4  
**Cập nhật:** 2026-04-30  
**Cập nhật:** 2026-04-15
