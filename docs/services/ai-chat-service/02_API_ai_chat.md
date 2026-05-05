# AI Chat Support — API Specification

> Base URL: `/api/ai`  
> Authentication: `Authorization: Bearer {JWT}`  
> Tất cả request/response dùng `application/json` trừ `/chat` dùng `text/event-stream`

---

## Tổng quan endpoints

| Method | Path | Mục đích | Auth |
|--------|------|----------|------|
| `POST` | `/chat` | Gửi message, nhận streaming response | JWT required |
| `GET` | `/chat/history` | Lấy lịch sử hội thoại | JWT required |
| `POST` | `/sessions` | Tạo session mới | JWT required |
| `DELETE` | `/sessions/{sessionId}` | Đóng session | JWT required |
| `POST` | `/confirm` | Xác nhận / từ chối action Mức 3 | JWT required |
| `GET` | `/suggest` | Gợi ý câu hỏi nhanh | Optional JWT |

---

## Rate Limiting

| Endpoint | Giới hạn | Header trả về |
|----------|----------|---------------|
| `POST /chat` | 20 req/phút/user | `X-RateLimit-Remaining` |
| `POST /confirm` | 10 req/phút/user | `X-RateLimit-Remaining` |
| Còn lại | 60 req/phút/user | `X-RateLimit-Remaining` |

Khi vượt giới hạn: HTTP 429 + header `X-RateLimit-Reset` (Unix timestamp lúc reset).

---

## POST /chat

Endpoint chính. Trả về **SSE stream** — frontend nhận từng token ngay khi LLM sinh ra.

### Request

```http
POST /api/ai/chat
Authorization: Bearer {JWT}
Content-Type: application/json
Accept: text/event-stream
```

**Body:**

```json
{
  "sessionId": "sess_01J...",
  "message":   "Đơn hàng ORD-2024-00892 đang ở đâu?",
  "type":      "TEXT"
}
```

| Field | Type | Required | Mô tả |
|-------|------|----------|-------|
| `sessionId` | string | No | Null nếu muốn tạo session mới tự động |
| `message` | string | Yes | Nội dung tin nhắn. Rỗng khi `type=LOAD_MORE` |
| `type` | enum | No | `TEXT` (default) \| `LOAD_MORE` \| `FOLLOW_UP` |

### SSE Event Types

Stream trả về nhiều loại event, phân biệt qua field `type`:

---

#### `delta` — token từ LLM

```
data: {"type":"delta","content":"Đơn hàng"}
data: {"type":"delta","content":" ORD-2024"}
```

Frontend append vào bubble chat khi nhận được.

---

#### `tool_start` — AI bắt đầu gọi Tool

```
data: {"type":"tool_start","toolName":"getOrderDetail"}
```

Frontend hiển thị "Đang truy xuất dữ liệu...".

---

#### `tool_done` — Tool đã trả về kết quả

```
data: {"type":"tool_done","toolName":"getOrderDetail","durationMs":142}
```

---

#### `products` — kết quả tìm kiếm sản phẩm

```json
{
  "type": "products",
  "items": [
    {
      "id":       "prod_abc123",
      "name":     "Áo thun trắng basic",
      "price":    175000,
      "imageUrl": "https://cdn.example.com/img/prod_abc123.jpg",
      "productUrl": "/products/prod_abc123",
      "brand":    "Uniqlo-style",
      "inStock":  true
    }
  ],
  "hasMore": true
}
```

Frontend render dạng Product Card grid. Hiển thị nút "Xem thêm" nếu `hasMore=true`.

---

#### `order` — kết quả tra cứu đơn hàng

```json
{
  "type": "order",
  "data": {
    "orderId":    "ORD-2024-00892",
    "status":     "SHIPPED",
    "eta":        "2026-05-05",
    "carrier":    "GHN",
    "totalAmount": 350000,
    "items": [
      { "name": "Áo thun trắng basic", "qty": 2, "price": 175000 }
    ]
  }
}
```

Frontend render dạng Order Card với timeline giao hàng.

---

#### `confirmation_required` — action Mức 3 cần xác nhận

```json
{
  "type":        "confirmation_required",
  "confirmId":   "conf_01J...",
  "actionType":  "CANCEL_ORDER",
  "payload": {
    "orderId":     "ORD-2024-00892",
    "orderTotal":  350000,
    "itemCount":   2
  },
  "expiresAt": "2026-05-03T10:05:00Z"
}
```

Frontend render 2 nút `[Xác nhận]` và `[Hủy bỏ]` kèm đồng hồ đếm ngược. Dùng `confirmId` để gọi `POST /confirm`.

---

#### `done` — kết thúc stream

```json
{
  "type":       "done",
  "messageId":  "msg_01J...",
  "sessionId":  "sess_01J...",
  "tokensUsed": 312
}
```

---

#### `error` — lỗi trong quá trình xử lý

```json
{
  "type":    "error",
  "code":    "LLM_TIMEOUT",
  "message": "Hệ thống đang bận, vui lòng thử lại."
}
```

Lỗi xảy ra trong stream không thể đổi HTTP status — gửi event error rồi đóng stream với `data: [DONE]`.

### HTTP Errors (trước khi stream bắt đầu)

| HTTP | Code | Khi nào |
|------|------|---------|
| 401 | `UNAUTHORIZED` | JWT không hợp lệ hoặc hết hạn |
| 422 | `INVALID_SESSION_STATUS` | Session đã CLOSED hoặc EXPIRED |
| 429 | `RATE_LIMIT_EXCEEDED` | Vượt 20 req/phút |

---

## GET /chat/history

Lấy lịch sử hội thoại của một session. Phân trang bằng cursor.

### Request

```http
GET /api/ai/chat/history?sessionId=sess_01J...&limit=20
Authorization: Bearer {JWT}
```

| Param | Type | Default | Mô tả |
|-------|------|---------|-------|
| `sessionId` | string | — | **Required.** ID session cần lấy lịch sử |
| `limit` | integer | 20 | Số message trả về, tối đa 50 |
| `before` | integer | — | Cursor: lấy messages có `sequence_no` nhỏ hơn giá trị này |

### Response 200

```json
{
  "sessionId": "sess_01J...",
  "messages": [
    {
      "id":         "msg_01J...",
      "role":       "USER",
      "content":    "Đơn hàng của tôi đâu?",
      "toolName":   null,
      "sequenceNo": 1,
      "createdAt":  "2026-05-03T10:00:00Z"
    },
    {
      "id":         "msg_02J...",
      "role":       "TOOL_CALL",
      "content":    "{\"name\":\"getOrderDetail\",\"args\":{\"orderId\":\"ORD-2024-00892\"}}",
      "toolName":   "getOrderDetail",
      "sequenceNo": 2,
      "createdAt":  "2026-05-03T10:00:01Z"
    },
    {
      "id":         "msg_03J...",
      "role":       "ASSISTANT",
      "content":    "Đơn hàng ORD-2024-00892 đang được giao bởi GHN...",
      "toolName":   null,
      "sequenceNo": 4,
      "createdAt":  "2026-05-03T10:00:02Z"
    }
  ],
  "hasMore":    false,
  "nextCursor": null
}
```

> `role` có thể là: `USER` | `ASSISTANT` | `TOOL_CALL` | `TOOL_RESULT`

---

## POST /sessions

Tạo session mới khi user mở cửa sổ chat. Có thể bỏ qua và để `/chat` tự tạo khi `sessionId=null`.

### Request

```http
POST /api/ai/sessions
Authorization: Bearer {JWT}
Content-Type: application/json
```

**Body:**

```json
{
  "context": {
    "currentPage": "product_detail",
    "productId":   "prod_abc123"
  }
}
```

| Field | Type | Required | Mô tả |
|-------|------|----------|-------|
| `context.currentPage` | string | No | Trang user đang đứng: `home`, `product_detail`, `order`, `cart` |
| `context.productId` | string | No | Gợi ý context cho AI nếu user đang xem sản phẩm cụ thể |

### Response 201

```json
{
  "sessionId":  "sess_01J...",
  "status":     "ACTIVE",
  "createdAt":  "2026-05-03T10:00:00Z",
  "expiresAt":  "2026-05-03T10:30:00Z"
}
```

`expiresAt` = thời điểm session hết hạn nếu không có hoạt động (idle 30 phút). Được gia hạn tự động mỗi khi có request.

---

## DELETE /sessions/{sessionId}

Đóng session, giải phóng Redis cache và tài nguyên.

### Request

```http
DELETE /api/ai/sessions/sess_01J...
Authorization: Bearer {JWT}
```

### Response 200

```json
{
  "sessionId": "sess_01J...",
  "status":    "CLOSED",
  "closedAt":  "2026-05-03T10:15:00Z"
}
```

> **Best practice:** Gọi endpoint này trong `window.addEventListener('beforeunload', ...)` để tránh session zombie tiêu tốn tài nguyên Redis.

### HTTP Errors

| HTTP | Code | Khi nào |
|------|------|---------|
| 404 | `SESSION_NOT_FOUND` | sessionId không tồn tại hoặc không thuộc user này |
| 422 | `INVALID_SESSION_STATUS` | Session đã CLOSED rồi |

---

## POST /confirm

Xác nhận hoặc từ chối một action Mức 3 đang chờ. Được gọi sau khi user nhận event `confirmation_required` từ stream `/chat`.

### Request

```http
POST /api/ai/confirm
Authorization: Bearer {JWT}
Content-Type: application/json
```

**Body:**

```json
{
  "confirmId":  "conf_01J...",
  "sessionId":  "sess_01J...",
  "decision":   "CONFIRMED"
}
```

| Field | Type | Required | Mô tả |
|-------|------|----------|-------|
| `confirmId` | string | Yes | UUID nhận từ event `confirmation_required` |
| `sessionId` | string | Yes | Session hiện tại |
| `decision` | enum | Yes | `CONFIRMED` \| `REJECTED` |

### Response 200 — CONFIRMED

```json
{
  "confirmId": "conf_01J...",
  "status":    "CONFIRMED",
  "executionResult": {
    "success": true,
    "message": "Đơn hàng ORD-2024-00892 đã được hủy thành công. Hoàn tiền trong 3–5 ngày làm việc."
  }
}
```

### Response 200 — REJECTED

```json
{
  "confirmId": "conf_01J...",
  "status":    "REJECTED",
  "message":   "Đã hủy yêu cầu. Dữ liệu không thay đổi."
}
```

### HTTP Errors

| HTTP | Code | Khi nào |
|------|------|---------|
| 400 | `CONFIRMATION_EXPIRED` | Token quá 5 phút hoặc Redis TTL đã hết |
| 400 | `CONFIRMATION_ALREADY_USED` | Token này đã được dùng rồi (chống double-submit) |
| 403 | `CONFIRMATION_FORBIDDEN` | userId trong JWT không khớp với owner của confirm token |
| 422 | `ACTION_REJECTED_BY_SERVICE` | Core Service từ chối (đơn đã SHIPPED, không thể hủy...) |
| 503 | `DOWNSTREAM_ERROR` | Core Service không phản hồi khi thực thi |

---

## GET /suggest

Trả về danh sách gợi ý hiển thị khi ô input trống. Không gọi LLM.

### Request

```http
GET /api/ai/suggest?context=order&limit=4
Authorization: Bearer {JWT}   (optional)
```

| Param | Type | Default | Mô tả |
|-------|------|---------|-------|
| `context` | string | `home` | Trang hiện tại: `home` \| `product` \| `order` \| `cart` |
| `limit` | integer | 4 | Số gợi ý trả về, tối đa 6 |

### Response 200

```json
{
  "suggestions": [
    { "id": "s1", "text": "Kiểm tra đơn hàng gần nhất của tôi", "icon": "order" },
    { "id": "s2", "text": "Chính sách đổi trả hàng",             "icon": "policy" },
    { "id": "s3", "text": "Tìm áo thun trắng nam size M",        "icon": "search" },
    { "id": "s4", "text": "Hủy đơn hàng",                        "icon": "cancel" }
  ]
}
```

> Không có JWT → trả gợi ý chung. Có JWT → cá nhân hóa dựa trên lịch sử đơn hàng của user.

---

## Chuẩn lỗi chung

Mọi lỗi HTTP đều trả về cùng một cấu trúc:

```json
{
  "error": {
    "code":       "RATE_LIMIT_EXCEEDED",
    "message":    "Quá nhiều yêu cầu, thử lại sau 30 giây.",
    "retryAfter": 30
  }
}
```

| HTTP | Code | Ý nghĩa |
|------|------|---------|
| 401 | `UNAUTHORIZED` | JWT không hợp lệ, hết hạn, hoặc bị blacklist |
| 403 | `FORBIDDEN` | JWT hợp lệ nhưng không có quyền (xem đơn của người khác) |
| 404 | `SESSION_NOT_FOUND` | sessionId không tồn tại |
| 422 | `INVALID_SESSION_STATUS` | Session đã CLOSED hoặc EXPIRED |
| 429 | `RATE_LIMIT_EXCEEDED` | Vượt giới hạn request/phút |
| 503 | `LLM_UNAVAILABLE` | LLM provider timeout sau 2 lần retry |
| 503 | `DOWNSTREAM_ERROR` | Core Service không phản hồi |

---

## Response Headers

| Header | Có ở endpoint | Ý nghĩa |
|--------|---------------|---------|
| `X-RateLimit-Limit` | Tất cả | Giới hạn tối đa (20 hoặc 10) |
| `X-RateLimit-Remaining` | Tất cả | Số request còn lại trong window hiện tại |
| `X-RateLimit-Reset` | Khi 429 | Unix timestamp lúc window reset |
| `X-Session-Expires` | `/chat`, `/confirm` | ISO timestamp lúc session hết hạn |

---

## Luồng gọi API — UC-21 (tra cứu thông tin)

```
1. Mở chat widget
   POST /api/ai/sessions
   ← { sessionId: "sess_01J..." }

2. User nhắn: "Đơn hàng ORD-2024-00892 đâu?"
   POST /api/ai/chat
   → SSE: tool_start "getOrderDetail"
   → SSE: tool_done  "getOrderDetail" (142ms)
   → SSE: delta "Đơn hàng ORD-2024-00892 đang được giao..."
   → SSE: order { status: "SHIPPED", eta: "2026-05-05" }
   → SSE: done { messageId, tokensUsed: 289 }

3. Đóng chat widget
   DELETE /api/ai/sessions/sess_01J...
```

## Luồng gọi API — UC-22 (action Mức 3)

```
1. User nhắn: "Hủy đơn ORD-2024-00892"
   POST /api/ai/chat
   → SSE: delta "Anh/chị muốn hủy đơn hàng..."
   → SSE: confirmation_required {
            confirmId: "conf_01J...",
            actionType: "CANCEL_ORDER",
            expiresAt: "2026-05-03T10:05:00Z"
          }
   → SSE: done

2. Frontend render [Xác nhận] và [Hủy bỏ]
   User bấm [Xác nhận]

3. POST /api/ai/confirm
   { confirmId: "conf_01J...", decision: "CONFIRMED" }
   ← { status: "CONFIRMED", executionResult: { success: true } }

4. Frontend ẩn 2 nút, hiển thị kết quả
```

## Luồng tìm kiếm sản phẩm + xem thêm

```
1. POST /api/ai/chat { message: "Tìm áo thun trắng nam size M dưới 300k" }
   → SSE: products { items: [5 sản phẩm], hasMore: true }
   → SSE: delta "Em tìm được 5 gợi ý phù hợp..."
   → SSE: done

2. User bấm "Xem thêm"
   POST /api/ai/chat { sessionId: "...", message: "", type: "LOAD_MORE" }
   → SSE: products { items: [5 sản phẩm tiếp], hasMore: false }
   → SSE: done
```
