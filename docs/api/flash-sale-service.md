# ⚡ Flash Sale Service API

**Service Name**: Flash Sale Service  
**Port**: `:8086`  
**Base URL**: `/api/v1`  
**Status**: v5.3 RTS

**Mô tả**: WebFlux · Redis Lua Script · Chống oversell · 50k req/s

---

## 📡 Kafka Integration

### Produces (Event Publisher)
- `flash_sale.session_started` → Notification Service (Session started)
- `flash_sale.session_ended` → Notification Service (Session ended)
- `flash_sale.item_approved` → Notification Service (Item approved)
- `flash_sale.item_sold` → Inventory Service (Update sold qty)

### Consumes (Event Subscriber)
- None directly

---

## 📅 Flash Sale Sessions

### GET /flash-sale/sessions
**Danh sách flash sale sessions**

**Quyền truy cập**: Public

**Mô tả**: Trả về các session UPCOMING và ACTIVE. Session ENDED không xuất hiện. Trạng thái được JOB-01 cập nhật mỗi phút.

**Query Params**:
| Param | Type | Mô tả |
|-------|------|-------|
| status | string | UPCOMING \| ACTIVE (optional) |

**Response 200**:
```json
{
  "server_time": "2025-11-01T19:58:00Z",
  "sessions": [
    {
      "session_id": 3,
      "name": "Flash Sale 20h Thứ 6",
      "status": "UPCOMING",
      "start_time": "2025-11-01T20:00:00Z",
      "end_time": "2025-11-01T22:00:00Z",
      "item_count": 15
    }
  ]
}
```

**⚠️ Ghi chú**: Client phải dùng `server_time` để tính countdown, không dùng đồng hồ client

---

### GET /flash-sale/sessions/{sessionId}
**Chi tiết session + items**

**Quyền truy cập**: Public

**Response 200**: Chi tiết session kèm tất cả FS_ITEMS APPROVED

---

### POST /flash-sale/sessions
**Tạo session mới (Admin)**

**Quyền truy cập**: JWT Required (ADMIN)

**Request Body**:
```json
{
  "name": "string",         // Tên flash sale session (Required)
  "start_time": "datetime", // Thời điểm bắt đầu (ISO 8601) (Required)
  "end_time": "datetime"    // Thời điểm kết thúc (ISO 8601) (Required)
}
```

**Response 201**: Session tạo thành công

---

### GET /admin/flash-sale/sessions
**Danh sách Flash Sale Sessions (Admin — toàn bộ trạng thái)**

**Quyền truy cập**: JWT Required (ADMIN)

**Mô tả**: Trả về toàn bộ FS_SESSIONS bao gồm UPCOMING, ACTIVE, ENDED.

**Query Params**:
| Param | Type | Mô tả |
|-------|------|-------|
| status | string | UPCOMING \| ACTIVE \| ENDED (optional) |
| page, size | integer | Phân trang |

**Response 200**: Full session list

---

### PUT /admin/flash-sale/sessions/{sessionId}
**Cập nhật Flash Sale Session (Admin)**

**Quyền truy cập**: JWT Required (ADMIN)

**⚠️ Chặn**: Cập nhật nếu session đang ACTIVE hoặc ENDED

**Request Body** (all optional):
```json
{
  "name": "string",
  "start_time": "datetime",
  "end_time": "datetime"
}
```

**Response 200**: Session updated

---

### DELETE /admin/flash-sale/sessions/{sessionId}
**Xóa Flash Sale Session (Admin)**

**Quyền truy cập**: JWT Required (ADMIN)

**⚠️ Chặn**: Xóa nếu session ACTIVE hoặc có FS_ITEMS APPROVED

**Response 200**: Session deleted

---

## 🎯 Flash Sale Items

### POST /flash-sale/sessions/{sessionId}/items
**Đăng ký sản phẩm vào session**

**Quyền truy cập**: JWT Required (SELLER)  
**Tags**: Kafka → (pending admin approval)

**Điều kiện hợp lệ (6 điều kiện)**:
```
1. session.status == UPCOMING
2. seller.details_submitted == true
3. seller.trust_score >= ngưỡng config
4. flash_price < variant.price
5. flash_stock <= stock_available
6. sku chưa có FS_ITEM PENDING/APPROVED trong session này
```

**Request Body**:
```json
{
  "sku_code": "string",          // Mã SKU tham gia flash sale (Required)
  "flash_price": "decimal",      // Giá flash sale (Required)
  "flash_stock": "integer",      // Số lượng dành cho Flash Sale (Required)
  "limit_per_user": "integer"    // Giới hạn mua tối đa mỗi user (1–10) (Required)
}
```

**Response 201**: FS_ITEMS.status = PENDING — chờ Admin duyệt

---

### POST /flash-sale/sessions/{sessionId}/items/{itemId}/approve
**Duyệt item (Admin)**

**Quyền truy cập**: JWT Required (ADMIN)  
**Tags**: Kafka → flash_sale.item_approved

**Request Body**:
```json
{
  "note": "string"  // Ghi chú duyệt item (Optional)
}
```

**Response 200**: FS_ITEMS.status = APPROVED, Kafka event produced

---

### POST /admin/flash-sale/items/{itemId}/reject
**Từ chối Flash Sale Item (Admin)**

**Quyền truy cập**: JWT Required (ADMIN)  
**Tags**: Kafka → flash_sale.item_rejected

**Request Body**:
```json
{
  "reject_reason": "string"  // Lý do từ chối, tối đa 500 ký tự (Required)
}
```

**Response 200**: FS_ITEMS.status = REJECTED

---

## 🛍️ Flash Sale Purchase

### POST /flash-sale/sessions/{sessionId}/buy
**⚡ Mua flash sale — Chịu tải cao**

**Quyền truy cập**: JWT Required (BUYER)  
**Tags**: Redis Lua Script | Kafka → flash_sale.item_sold

**Mô tả**: Atomic purchase using Redis Lua script to prevent oversell at high concurrency.

**Request Body**:
```json
{
  "fs_item_id": "long",      // ID flash sale item (Required)
  "quantity": "integer",     // Số lượng muốn mua (> 0) (Required)
  "address_id": "long"       // ID địa chỉ giao hàng (Required)
}
```

**Response 201**: Chốt đơn thành công, item added to cart

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 409 | SOLD_OUT — Hết hàng (Redis atomic check) |
| 400 | LIMIT_EXCEEDED — Vượt giới hạn mua mỗi user |

---

## 🔔 Reminders

### POST /flash-sale/sessions/{sessionId}/reminders
**Đăng ký nhắc nhở**

**Quyền truy cập**: JWT Required (BUYER)

**Request Body**: (không có body)

**Response 200**: Reminder registered

---

### DELETE /flash-sale/sessions/{sessionId}/reminders
**Hủy nhắc nhở**

**Quyền truy cập**: JWT Required

**Response 200**: Hủy đăng ký nhắc nhở thành công

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 404 | User chưa đăng ký nhắc nhở cho session này |

---

## 📊 Summary

| Metric | Value |
|--------|-------|
| **Total Endpoints** | 11 |
| **Session Endpoints** | 6 |
| **Item Endpoints** | 3 |
| **Purchase Endpoints** | 1 |
| **Reminder Endpoints** | 2 (NEW) |
| **Kafka Topics Produced** | 4 |
| **Kafka Topics Consumed** | 0 |

---

## 🔗 Integration Points

| Service | Topic | Direction | Mô tả |
|---------|-------|-----------|-------|
| **Notification Service** | flash_sale.session_started | → | Notify users of session start |
| **Notification Service** | flash_sale.session_ended | → | Notify users of session end |
| **Notification Service** | flash_sale.item_approved | → | Notify seller of approval |
| **Inventory Service** | flash_sale.item_sold | → | Update sold count, sync stock |
| **Cart Service** | (sync) | ← | Flash sale items in cart |

---

**Last Updated**: 2026-04-28  
**Version**: v5.3 RTS

