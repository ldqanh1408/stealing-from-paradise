# ⚡ Flash Sale Service API

**Port**: `:8086`  
**Mô tả**: WebFlux · Redis Lua Script · Chống oversell · 50k req/s  
**Base URL**: `/api/v1`

---

## 📚 Mục Lục

1. [Public Session Endpoints](#public-session-endpoints)
2. [Seller Flash Sale Endpoints](#seller-flash-sale-endpoints)
3. [Admin Flash Sale Endpoints](#admin-flash-sale-endpoints)
4. [Buyer Flash Sale Endpoints](#buyer-flash-sale-endpoints)

---

## Public Session Endpoints

### GET /flash-sale/sessions
**Danh sách Flash Sale sessions**

**Quyền truy cập**: Public

**Mô tả**: Trả về các session UPCOMING và ACTIVE. Session ENDED không xuất hiện. Trạng thái được JOB-01 cập nhật mỗi phút.

**Query Params**:
| Param | Type | Mô tả |
|-------|------|-------|
| status | string | UPCOMING \| ACTIVE (optional) |

**Response 200**:
```json
{
  "server_time": "2026-04-15T19:58:00Z",
  "sessions": [
    {
      "session_id": 3,
      "name": "Flash Sale 20h Thứ 6",
      "status": "ACTIVE",
      "start_time": "2026-04-15T20:00:00Z",
      "end_time": "2026-04-15T22:00:00Z",
      "item_count": 15,
      "seconds_remaining": 120,
      "is_ended": false
    }
  ]
}
```

> ⚠️ Client phải dùng `server_time` để tính countdown, **không** dùng đồng hồ client

---

### GET /flash-sale/sessions/{sessionId}
**Chi tiết session + items**

**Quyền truy cập**: Public

**Response 200**: Chi tiết session kèm tất cả FS_ITEMS APPROVED

---

## Seller Flash Sale Endpoints

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
  "sku_code": "NK-AIR-RED-XL",
  "flash_price": 189999,
  "flash_stock": 50,
  "limit_per_user": 3
}
```

**Validation Rules**:
| Field | Type | Rules |
|-------|------|-------|
| sku_code | string | Phải tồn tại; unique per session |
| flash_price | decimal | > 0; < variant.price |
| flash_stock | integer | > 0; ≤ stock_available |
| limit_per_user | integer | 1–10 |

**Response 201**:
```json
{
  "fs_item_id": 1001,
  "session_id": 3,
  "sku_code": "NK-AIR-RED-XL",
  "flash_price": 189999,
  "flash_stock": 50,
  "sold_qty": 0,
  "limit_per_user": 3,
  "status": "PENDING",
  "message": "Sản phẩm đã gửi duyệt. Chờ Admin phê duyệt.",
  "created_at": "2026-04-15T10:00:00Z"
}
```

> FS_ITEMS.status = PENDING — chờ Admin duyệt

---

## Admin Flash Sale Endpoints

### POST /flash-sale/sessions
**Tạo session mới (Admin)**

**Quyền truy cập**: JWT Required (ADMIN)

**Request Body**:
```json
{
  "name": "Flash Sale 20h Thứ 6",
  "start_time": "2026-04-15T20:00:00Z",
  "end_time": "2026-04-15T22:00:00Z"
}
```

---

### GET /admin/flash-sale/sessions
**Danh sách Flash Sale Sessions (Admin — toàn bộ trạng thái)**

**Quyền truy cập**: JWT Required (ADMIN)  
**Tag**: NEW v5.1 — Gap A

**Mô tả**: Trả về toàn bộ FS_SESSIONS bao gồm UPCOMING, ACTIVE, ENDED.

**Query Params**:
| Param | Type | Mô tả |
|-------|------|-------|
| status | string | UPCOMING \| ACTIVE \| ENDED (optional) |
| page, size | integer | Phân trang |

---

### PUT /admin/flash-sale/sessions/{sessionId}
**Cập nhật Flash Sale Session (Admin)**

**Quyền truy cập**: JWT Required (ADMIN)  
**Tag**: NEW v5.1 — Gap A

**⚠️ Chặn**: Không thể cập nhật nếu session đang ACTIVE hoặc ENDED

**Request Body** (all optional):
```json
{
  "name": "Flash Sale 20h Thứ 6 - Updated",
  "start_time": "2026-04-15T20:00:00Z",
  "end_time": "2026-04-15T22:30:00Z"
}
```

---

### DELETE /admin/flash-sale/sessions/{sessionId}
**Xóa Flash Sale Session (Admin)**

**Quyền truy cập**: JWT Required (ADMIN)  
**Tag**: NEW v5.1 — Gap A

**⚠️ Chặn**: Không thể xóa nếu session ACTIVE hoặc có FS_ITEMS APPROVED

---

### POST /flash-sale/sessions/{sessionId}/items/{itemId}/approve
**Duyệt item (Admin)**

**Quyền truy cập**: JWT Required (ADMIN)  
**Tags**: Kafka → flash_sale.item_approved

**Request Body**:
```json
{
  "note": "Sản phẩm đạt tiêu chí Flash Sale"
}
```

**Response 200**: FS_ITEMS.status = APPROVED

---

### POST /admin/flash-sale/items/{itemId}/reject
**Từ chối Flash Sale Item (Admin)**

**Quyền truy cập**: JWT Required (ADMIN)  
**Tags**: Kafka → flash_sale.item_rejected | NEW v5.1 — Gap A

**Request Body**:
```json
{
  "reject_reason": "Giá Flash Sale chưa đạt mức giảm tối thiểu 20%"
}
```

**Response 200**: FS_ITEMS.status = REJECTED

---

## Buyer Flash Sale Endpoints

### POST /flash-sale/sessions/{sessionId}/buy
**⚡ Mua Flash Sale — Chịu tải cao**

**Quyền truy cập**: JWT Required (BUYER)  
**Tags**: Redis Lua Script | Kafka → flash_sale.item_sold

**Request Body**:
```json
{
  "fs_item_id": 1001,
  "quantity": 2,
  "address_id": 7
}
```

**Response 201**:
```json
{
  "order_id": 1002,
  "order_code": "OR-20260415-1002",
  "fs_item_id": 1001,
  "quantity": 2,
  "flash_price": 189999,
  "total": 379998,
  "status": "PENDING",
  "is_flash_sale": true,
  "timeout_at": "2026-04-15T20:10:00Z",
  "message": "Đơn hàng đã tạo. Thanh toán trong 10 phút."
}
```

**Kafka Events (Redis Side Effect)**:
```json
{
  "topic": "flash_sale.item_sold",
  "payload": {
    "fs_item_id": 1001,
    "session_id": 3,
    "sku_code": "NK-AIR-RED-XL",
    "quantity": 2,
    "flash_price": 189999,
    "sold_total": 45,
    "remaining_stock": 5,
    "timestamp": "2026-04-15T20:00:30Z"
  }
}
```

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 409 | SOLD_OUT — Hết hàng (Redis atomic check) |
| 400 | LIMIT_EXCEEDED — Vượt giới hạn mua mỗi user |

---

### POST /flash-sale/sessions/{sessionId}/reminders
**Đăng ký nhắc nhở Flash Sale**

**Quyền truy cập**: JWT Required (BUYER)

**Request Body**: (không có body)

**Response 201**: Đăng ký nhắc nhở thành công

---

### DELETE /flash-sale/sessions/{sessionId}/reminders
**Hủy nhắc nhở Flash Sale**

**Quyền truy cập**: JWT Required

**Response 200**: Hủy đăng ký nhắc nhở thành công

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 404 | User chưa đăng ký nhắc nhở cho session này |

---

## 📊 Summary

| Endpoint | Method | Auth |
|----------|--------|------|
| /flash-sale/sessions | GET | Public |
| /flash-sale/sessions/{id} | GET | Public |
| /flash-sale/sessions | POST | JWT (ADMIN) |
| /admin/flash-sale/sessions | GET | JWT (ADMIN) |
| /admin/flash-sale/sessions/{id} | PUT | JWT (ADMIN) |
| /admin/flash-sale/sessions/{id} | DELETE | JWT (ADMIN) |
| /flash-sale/sessions/{id}/items | POST | JWT (SELLER) |
| /flash-sale/sessions/{sid}/items/{iid}/approve | POST | JWT (ADMIN) |
| /admin/flash-sale/items/{id}/reject | POST | JWT (ADMIN) |
| /flash-sale/sessions/{id}/buy | POST | JWT (BUYER) |
| /flash-sale/sessions/{id}/reminders | POST | JWT (BUYER) |
| /flash-sale/sessions/{id}/reminders | DELETE | JWT |

**Kafka Topics published by Flash Sale Service**:
- `flash_sale.session_started` — Phiên bắt đầu
- `flash_sale.session_ended` — Phiên kết thúc
- `flash_sale.item_approved` — Item được duyệt
- `flash_sale.item_rejected` — Item bị từ chối
- `flash_sale.item_sold` — Item được mua (Redis Lua)
- `flash_sale.reminder` — JOB-02 gửi nhắc nhở

---

**Phiên bản:** v5.4  
**Cập nhật:** 2026-04-30  
**Cập nhật:** 2026-04-15
