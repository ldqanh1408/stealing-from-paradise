# 📋 Order Service API

**Service Name**: Order Service  
**Port**: `:8087`  
**Base URL**: `/api/v1`  
**Status**: v5.3 RTS

**Mô tả**: Checkout, quản lý đơn hàng, Saga CQRS · Axon

---

## 📡 Kafka Integration

### Produces (Event Publisher)
- `order.created` → Inventory Service (Lock stock)
- `order.cancelled` → Cart, Loyalty Services (Refund points, unlock stock)
- `order.shipped` → Notification Service (Shipping update)
- `order.delivered` → Identity, Loyalty Services (Credit points, trust score)
- `order.returned` → Refund, Inventory Services (RTS workflow)
- `order.checkout_completed` → Cart Service (Clear cart)

### Consumes (Event Subscriber)
- `payment.success` ← Payment Service (Mark order as PAID)

---

## 📦 Order Creation & Management

### POST /orders/checkout
**Tạo đơn hàng từ giỏ**

**Quyền truy cập**: JWT Required (BUYER)  
**Tags**: Kafka → order.created · Multi-Vendor Split

**Mô tả**:
- Tạo 1 PARENT_ORDER + N ORDERS (1 sub-order per Seller từ các item trong giỏ)
- Lọc item theo address_id và thực hiện checkout cho tất cả
- Hệ thống tự động split thanh toán theo Seller
- Điểm Loyalty được ghi nhận ở trạng thái PENDING

**Request Body**:
```json
{
  "address_id": "long",                          // ID địa chỉ giao hàng (Required)
  "item_ids": ["long"],                          // Danh sách cart_item_id muốn checkout (Required)
  "use_loyalty_points": "boolean",               // Dùng điểm thưởng để giảm giá (Optional, default: false)
  "loyalty_points_to_use": "integer"             // Số điểm muốn dùng (tối đa 20% giá trị đơn) (Optional)
}
```

**Response 201**: Tạo đơn hàng thành công

---

### GET /orders
**Danh sách đơn hàng của Buyer**

**Quyền truy cập**: JWT Required (BUYER)

**Query Params**:
| Param | Type | Mô tả |
|-------|------|-------|
| status | string | PENDING \| PAID \| SHIPPING \| DELIVERED \| RETURNED \| CANCELLED \| PARTIALLY_REFUNDED \| REFUNDED |
| from_date | date | Ngày bắt đầu (ISO 8601, Optional) |
| to_date | date | Ngày kết thúc (ISO 8601, Optional) |
| page | integer | Trang hiện tại (default: 0) |
| size | integer | Kích thước trang (default: 20, max: 100) |

**Response 200**: Danh sách đơn hàng

---

### GET /orders/{orderId}
**Chi tiết đơn hàng con**

**Quyền truy cập**: JWT Required (BUYER \| SELLER - owner)

**Response 200**: Chi tiết đơn hàng kèm items, tracking, shipping address

---

### GET /orders/parent/{parentOrderId}
**Chi tiết đơn cha**

**Quyền truy cập**: JWT Required (BUYER)

**Response 200**: Đơn cha kèm toàn bộ sub-orders và thông tin thanh toán

---

### POST /orders/{orderId}/cancel
**Hủy đơn hàng**

**Quyền truy cập**: JWT Required (BUYER \| SELLER)  
**Tags**: Kafka → order.cancelled

**Mô tả**: Hủy đơn sẽ trừ Trust Score Buyer theo event_code `BUYER_CANCEL_EXCESSIVE` nếu tổng hủy trong 30 ngày vượt ngưỡng.

**Request Body**:
```json
{
  "reason": "string",        // Lý do hủy đơn (Required)
  "note": "string"           // Ghi chú bổ sung (Optional)
}
```

**Response 200**: Hủy thành công, stock được giải phóng

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 409 | Đơn không ở trạng thái PENDING |
| 403 | User không phải chủ đơn |

---

### PUT /orders/{orderId}/tracking
**Cập nhật tracking number (Seller)**

**Quyền truy cập**: JWT Required (SELLER - owner)  
**Tags**: Kafka → order.shipped

**Request Body**:
```json
{
  "tracking_number": "string",   // Mã vận đơn từ đơn vị vận chuyển (Required)
  "carrier": "string",           // Tên đơn vị vận chuyển (ViettelPost, GHN, GHTK…) (Optional)
  "note": "string"               // Ghi chú giao hàng (Optional)
}
```

**Response 200**: status → SHIPPING, Kafka order.shipped published

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 409 | Đơn không ở trạng thái PAID |
| 403 | Seller không phải chủ của sub-order |

---

### POST /orders/{orderId}/confirm-received
**Xác nhận đã nhận hàng**

**Quyền truy cập**: JWT Required (BUYER)  
**Tags**: Kafka → order.delivered

**Request Body**: (không có body)

**Response 200**: status → DELIVERED, điểm thưởng PENDING → CONFIRMED, trust_score Seller +5

---

### POST /orders/{orderId}/return-to-sender
**Seller xác nhận nhận lại hàng hoàn — kích hoạt Full Refund tự động**

**Quyền truy cập**: JWT Required (SELLER)  
**Tags**: Kafka → order.returned | RTS (Return To Sender)

**Mô tả** [RTS]: Khi đơn vị vận chuyển hoàn hàng về Seller, Seller chủ động gọi API này. Hệ thống sẽ:
1. Chuyển `ORDERS.status → RETURNED`
2. Tự động tạo REFUNDS (type=FULL, initiated_by=SELLER, refund_reason_type=RETURN_TO_SENDER)
3. Cộng lại `stock_available` cho từng SKU
4. Produce Kafka event `order.returned`
5. Thực hiện Stripe refund tự động
6. Ghi bằng chứng ảnh vào REFUND_ITEMS.return_evidence_images

**Request Body** (multipart/form-data):
```json
{
  "evidence_images": ["file"],           // Ảnh chụp gói hàng (1-5 ảnh, bắt buộc)
  "return_tracking_number": "string",    // Mã vận đơn hoàn hàng (Optional)
  "note": "string"                       // Ghi chú thêm của Seller (Optional)
}
```

**Response 200**: RTS confirmed, refund initiated

---

### GET /sellers/me/orders
**Đơn hàng của Seller**

**Quyền truy cập**: JWT Required (SELLER)

**Query Params**:
| Param | Type | Mô tả |
|-------|------|-------|
| status | string | Lọc theo trạng thái đơn |
| from_date / to_date | date | Lọc theo khoảng thời gian |
| page, size | integer | Phân trang |

**Response 200**: Danh sách sub-orders thuộc Seller

---

## 📊 Summary

| Metric | Value |
|--------|-------|
| **Total Endpoints** | 8 |
| **Checkout Endpoints** | 1 |
| **Query Endpoints** | 3 |
| **Order Management** | 4 |
| **Kafka Topics Produced** | 6 |
| **Kafka Topics Consumed** | 1 |

---

## 🔗 Integration Points

| Service | Topic | Direction | Mô tả |
|---------|-------|-----------|-------|
| **Inventory Service** | order.created | → | Lock stock |
| **Payment Service** | payment.success | ← | Mark as PAID |
| **Cart Service** | order.checkout_completed | → | Clear cart after checkout |
| **Loyalty Service** | order.delivered | → | Credit points |
| **Loyalty Service** | order.cancelled | → | Refund points |
| **Notification Service** | order.shipped | → | Shipping notification |
| **Refund Service** | order.returned | → | RTS refund processing |

---

**Last Updated**: 2026-04-28  
**Version**: v5.3 RTS

