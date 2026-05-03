# Flash Sale Service — API Reference

> Base path: `/api/v1` → Gateway routes to `flashsale-service:8085`
>
> Database: PostgreSQL
>
> Redis: Lua script cho atomic stock decrement

---

## Sessions (Public)

### GET /flash-sale/sessions
**Danh sách flash sale sessions**

**Quyền truy cập**: Public

**Mô tả:** Trả về các session UPCOMING và ACTIVE. Session ENDED không xuất hiện. Trạng thái được JOB-01 cập nhật mỗi phút.

**Query Params:**

| Param | Type | Mô tả |
|-------|------|-------|
| status | string | UPCOMING | ACTIVE (optional) |

**Response 200:**
```json
{
  "success": true,
  "data": {
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
}
```

> ⚠️ Client phải dùng `server_time` để tính countdown, không dùng đồng hồ client

---

### GET /flash-sale/sessions/{sessionId}
**Chi tiết session + items**

**Quyền truy cập**: Public

**Response 200:**
```json
{
  "success": true,
  "data": {
    "session_id": 3,
    "name": "Flash Sale 20h Thứ 6",
    "status": "UPCOMING",
    "start_time": "2025-11-01T20:00:00Z",
    "end_time": "2025-11-01T22:00:00Z",
    "items": [
      {
        "fs_item_id": 1,
        "sku_code": "SP001-S",
        "product_name": "Áo thun nam cotton",
        "image": "https://cdn.marketplace.vn/...",
        "flash_price": 199000,
        "original_price": 250000,
        "discount_percent": 20,
        "flash_stock": 100,
        "sold_qty": 0,
        "limit_per_user": 2,
        "status": "APPROVED"
      }
    ]
  }
}
```

---

## Sessions (Admin)

### POST /flash-sale/sessions
**Tạo session mới**

**Quyền truy cập**: JWT Required (ADMIN)

**Request Body:**
```json
{
  "name": "string",           // Tên flash sale session (Required)
  "start_time": "datetime",   // ISO 8601 (Required)
  "end_time": "datetime"      // ISO 8601 (Required)
}
```

**Response 201:** Session created (status = UPCOMING)

---

### GET /admin/flash-sale/sessions
**Danh sách tất cả session (Admin)**

**Quyền truy cập**: JWT Required (ADMIN)

**Query Params:**

| Param | Type | Mô tả |
|-------|------|-------|
| status | string | UPCOMING | ACTIVE | ENDED (optional) |
| page | integer | Default 0 |
| size | integer | Default 20 |

---

### PUT /admin/flash-sale/sessions/{sessionId}
**Cập nhật session**

**Quyền truy cập**: JWT Required (ADMIN)

> ⚠️ Chặn cập nhật nếu session đang ACTIVE hoặc ENDED

**Request Body** (all optional):
```json
{
  "name": "string",
  "start_time": "datetime",
  "end_time": "datetime"
}
```

**Response 200:** Session updated

---

### DELETE /admin/flash-sale/sessions/{sessionId}
**Xóa session**

**Quyền truy cập**: JWT Required (ADMIN)

> ⚠️ Chặn xóa nếu session ACTIVE hoặc có FS_ITEMS APPROVED

**Response 200:** Session deleted (soft delete)

---

## Items (Seller)

### POST /flash-sale/sessions/{sessionId}/items
**Đăng ký sản phẩm vào session**

**Quyền truy cập**: JWT Required (SELLER)

**Điều kiện hợp lệ (6 điều kiện):**
1. `session.status == UPCOMING`
2. Seller đã hoàn tất Stripe onboarding
3. `seller.trust_score >= ngưỡng` (có thể cấu hình)
4. `flash_price < variant.price` (giá flash phải thấp hơn giá gốc)
5. `flash_stock <= stock_available` (tồn flash không vượt quá tồn kho)
6. SKU chưa có FS_ITEM PENDING/APPROVED trong session này

**Request Body:**
```json
{
  "sku_code": "string",         // Mã SKU (Required)
  "flash_price": "decimal",     // Giá flash sale (Required)
  "flash_stock": "integer",     // Số lượng dành cho Flash Sale (Required)
  "limit_per_user": "integer"   // 1-10 (Required)
}
```

**Response 201:** FS_ITEM created (status = PENDING — chờ Admin duyệt)

---

## Items (Admin)

### POST /flash-sale/sessions/{sessionId}/items/{itemId}/approve
**Duyệt item**

**Quyền truy cập**: JWT Required (ADMIN)

**Request Body:**
```json
{
  "note": "string"    // Ghi chú duyệt (Optional)
}
```

**Response 200:** FS_ITEM status → APPROVED

---

### POST /admin/flash-sale/items/{itemId}/reject
**Từ chối Flash Sale Item**

**Quyền truy cập**: JWT Required (ADMIN)

**Request Body:**
```json
{
  "reject_reason": "string"    // Lý do từ chối, max 500 ký tự (Required)
}
```

**Response 200:** FS_ITEM status → REJECTED

---

## Buy (Buyer)

### POST /flash-sale/sessions/{sessionId}/buy
**Mua flash sale — Chịu tải cao**

**Quyền truy cập**: JWT Required (BUYER)

**Tags:** Redis Lua Script | Kafka → flash_sale.item_sold

**Request Body:**
```json
{
  "fs_item_id": "integer",    // ID flash sale item (Required)
  "quantity": "integer",      // Số lượng muốn mua (> 0) (Required)
  "address_id": "integer"     // ID địa chỉ giao hàng (Required)
}
```

**Response 201:**
```json
{
  "success": true,
  "data": {
    "order_id": 101,
    "fs_item_id": 1,
    "quantity": 1,
    "flash_price": 199000,
    "total": 199000,
    "message": "Mua flash sale thành công"
  }
}
```

**Errors:**
| Status | Mô tả |
|--------|-------|
| 409 | SOLD_OUT — Hết hàng (Redis atomic check) |
| 400 | LIMIT_EXCEEDED — Vượt giới hạn mua mỗi user |

---

## Reminders

### POST /flash-sale/sessions/{sessionId}/reminders
**Đăng ký nhắc nhở**

**Quyền truy cập**: JWT Required (BUYER)

**Request Body:** (no body)

**Response 201:** Reminder registered

**Errors:** 409 (already registered)

---

### DELETE /flash-sale/sessions/{sessionId}/reminders
**Hủy nhắc nhở**

**Quyền truy cập**: JWT Required

**Response 200:** Reminder cancelled

**Errors:** 404 (chưa đăng ký nhắc nhở)
