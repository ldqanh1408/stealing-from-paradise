# 🛒 Cart Service API

**Service Name**: Cart Service  
**Port**: `:8083`  
**Base URL**: `/api/v1`  
**Status**: v5.3 RTS

**Mô tả**: Giỏ hàng đa seller · MongoDB · TTL 30 ngày

---

## 📡 Kafka Integration

### Produces (Event Publisher)
- None (No events produced)

### Consumes (Event Subscriber)
- `order.checkout_completed` ← Order Service (Remove items from cart)

---

## 🛍️ Cart Endpoints

### GET /cart
**Lấy giỏ hàng hiện tại**

**Quyền truy cập**: JWT Required

**Mô tả**:
- Giỏ hàng được nhóm theo Seller
- Giá và stock được enrich real-time từ Product Service
- Cart item của Flash Sale đã ENDED sẽ không còn xuất hiện (JOB-07 xóa)

**Response 200**:
```json
{
  "sellers": [
    {
      "seller_id": 5,
      "seller_name": "Shop Nike VN",
      "items": [
        {
          "cart_item_id": 201,
          "sku_code": "NK-AIR-RED-XL",
          "product_name": "Áo Thun Nike Air",
          "variant_name": "Đỏ / XL",
          "unit_price": 350000,
          "quantity": 2,
          "stock_available": 95,
          "is_flash": false,
          "fs_item_id": null,
          "flash_price": null,
          "flash_expires_at": null
        }
      ]
    }
  ],
  "total_items": 2,
  "subtotal": 700000
}
```

---

### POST /cart/items
**Thêm sản phẩm vào giỏ**

**Quyền truy cập**: JWT Required

**Mô tả**:
- Nếu SKU đã có trong giỏ → cộng thêm số lượng
- Giới hạn số lượng theo Trust Score: Silver ≤3 items/seller, Bronze ≤1 item/seller
- Flash Sale item kiểm tra `limit_per_user` trên Redis

**Request Body**:
```json
{
  "sku_code": "string",      // SKU code của variant (Required)
  "quantity": "integer",     // Số lượng muốn thêm (> 0) (Required)
  "fs_item_id": "long"       // ID flash sale item — bắt buộc nếu mua trong Flash Sale (Optional)
}
```

**Response 200**: Thêm thành công, trả về cart item mới

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 409 | Vượt giới hạn Trust Score tier hoặc vượt Flash Sale limit_per_user |
| 422 | SKU hết hàng hoặc không tồn tại |

---

### PUT /cart/items/{itemId}
**Cập nhật số lượng**

**Quyền truy cập**: JWT Required

**Request Body**:
```json
{
  "quantity": "integer"      // Số lượng mới (> 0) — Đặt quantity = 0 không hợp lệ (Required)
}
```

**Response 200**: Cập nhật số lượng thành công

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 422 | quantity vượt quá stock_available |
| 404 | cart_item_id không tồn tại hoặc không thuộc user |

---

### DELETE /cart/items/{itemId}
**Xóa sản phẩm khỏi giỏ**

**Quyền truy cập**: JWT Required

**Response 200**: Xóa item thành công

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 404 | cart_item_id không tồn tại hoặc không thuộc user |

---

### DELETE /cart
**Xóa toàn bộ giỏ hàng**

**Quyền truy cập**: JWT Required

**Response 200**: Xóa toàn bộ giỏ hàng thành công

---

## 📊 Summary

| Metric | Value |
|--------|-------|
| **Total Endpoints** | 5 |
| **GET Endpoints** | 1 |
| **POST Endpoints** | 1 |
| **PUT Endpoints** | 1 |
| **DELETE Endpoints** | 2 |
| **Kafka Topics Produced** | 0 |
| **Kafka Topics Consumed** | 1 |

---

## 🔗 Integration Points

| Service | Topic | Direction | Mô tả |
|---------|-------|-----------|-------|
| **Order Service** | order.checkout_completed | ← | Remove items after checkout |
| **Product Service** | inventory (sync) | ← | Get real-time stock info |

---

**Last Updated**: 2026-04-28  
**Version**: v5.3 RTS

