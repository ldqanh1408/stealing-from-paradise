# 📋 Order Service API

**Port**: `:8087`  
**Mô tả**: Checkout, quản lý đơn hàng, Saga CQRS · Axon  
**Base URL**: `/api/v1`

---

## 📚 Mục Lục

1. [Checkout](#checkout)
2. [Buyer Order Endpoints](#buyer-order-endpoints)
3. [Seller Order Endpoints](#seller-order-endpoints)

---

## Checkout

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
  "address_id": 7,
  "item_ids": [201, 202],
  "use_loyalty_points": true,
  "loyalty_points_to_use": 50
}
```

**Validation Rules**:
| Field | Type | Rules |
|-------|------|-------|
| address_id | long | Phải tồn tại; thuộc user |
| item_ids | array | 1–50 items; không trùng |
| use_loyalty_points | boolean | Optional; default false |
| loyalty_points_to_use | integer | ≤ 20% of total amount; ≤ available points |

**Response 201**:
```json
{
  "parent_order_id": 55,
  "order_code": "PO-20251001-55",
  "orders": [
    {
      "order_id": 100,
      "order_code": "OR-20251001-100",
      "seller_id": 5,
      "seller_name": "Shop Nike VN",
      "seller_trust_score": 92,
      "total_amt": 700000,
      "final_amt": 650000,
      "status": "PENDING",
      "items": [
        {
          "order_item_id": 501,
          "sku_code": "NK-AIR-RED-XL",
          "product_name": "Áo Thun Nike Air",
          "variant_name": "Đỏ / XL",
          "image_snapshot": "https://cdn.marketplace.vn/products-media/products/5/101/uuid.jpg",
          "price_snapshot": 350000,
          "quantity": 2,
          "subtotal": 700000
        }
      ],
      "created_at": "2026-10-01T10:00:00Z"
    },
    {
      "order_id": 101,
      "order_code": "OR-20251001-101",
      "seller_id": 9,
      "seller_name": "Shop Adidas VN",
      "seller_trust_score": 88,
      "total_amt": 500000,
      "final_amt": 500000,
      "status": "PENDING",
      "items": [
        {
          "order_item_id": 601,
          "sku_code": "AD-ULTRA-BLK-10",
          "product_name": "Giày Adidas Ultraboost",
          "variant_name": "Đen / EU 10",
          "image_snapshot": "https://cdn.marketplace.vn/products-media/products/9/201/uuid.jpg",
          "price_snapshot": 500000,
          "quantity": 1,
          "subtotal": 500000
        }
      ],
      "created_at": "2026-10-01T10:00:00Z"
    }
  ],
  "shipping_address": {
    "address_id": 7,
    "full_address": "123 Nguyễn Trãi, Phường 2, Q.3, TP.HCM",
    "province_id": 79,
    "district_id": 760
  },
  "payment": {
    "total_amount": 1200000,
    "loyalty_discount": 50000,
    "loyalty_points_used": 50,
    "final_amount": 1150000,
    "currency": "VND"
  },
  "total_items": 3,
  "total_sellers": 2,
  "payment_status": "PENDING",
  "timeout_at": "2026-10-01T10:30:00Z",
  "created_at": "2026-10-01T10:00:00Z"
}
```

**Kafka Events**:
```json
{
  "topic": "order.created",
  "payload": {
    "parent_order_id": 55,
    "user_id": 42,
    "orders": [
      { "order_id": 100, "seller_id": 5, "total_amount": 700000, "items_count": 1 },
      { "order_id": 101, "seller_id": 9, "total_amount": 500000, "items_count": 1 }
    ],
    "total_amount": 1200000,
    "loyalty_points_used": 50,
    "timestamp": "2026-10-01T10:00:00Z"
  }
}
```

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 422 | Một số item hết hàng hoặc không hợp lệ |
| 409 | Địa chỉ không tồn tại hoặc không thuộc user |
| 400 | Loyalty points vượt giới hạn hoặc validation thất bại |

---

## Buyer Order Endpoints

### GET /orders
**Danh sách đơn hàng của Buyer**

**Quyền truy cập**: JWT Required (BUYER)

**Query Params**:
| Param | Type | Mô tả |
|-------|------|-------|
| status | string | PENDING \| PAID \| SHIPPING \| DELIVERED \| RETURNED \| CANCELLED \| PARTIALLY_REFUNDED \| REFUNDED |
| from_date | date | ISO 8601 (Optional) |
| to_date | date | ISO 8601 (Optional) |
| page | integer | Trang hiện tại (default: 0) |
| size | integer | Kích thước trang (default: 20, max: 100) |

**Response 200**:
```json
{
  "content": [
    {
      "order_id": 100,
      "parent_order_id": 55,
      "order_code": "OR-20251001-100",
      "seller_id": 5,
      "seller_name": "Shop Nike VN",
      "status": "PAID",
      "total_amt": 700000,
      "final_amt": 700000,
      "is_flash_sale": false,
      "item_count": 2,
      "created_at": "2025-10-01T10:00:00Z",
      "updated_at": "2025-10-01T10:05:00Z"
    }
  ],
  "total_elements": 12,
  "total_pages": 1,
  "page_number": 0,
  "page_size": 20
}
```

---

### GET /orders/{orderId}
**Chi tiết đơn hàng con**

**Quyền truy cập**: JWT Required (BUYER \| SELLER - owner)

**Response 200**:
```json
{
  "order_id": 100,
  "parent_order_id": 55,
  "order_code": "OR-20251001-100",
  "seller_id": 5,
  "seller_name": "Shop Nike VN",
  "buyer_id": 42,
  "buyer_name": "Nguyễn Văn A",
  "status": "SHIPPING",
  "total_amt": 700000,
  "final_amt": 700000,
  "is_flash_sale": false,
  "cancelled_by": null,
  "cancel_reason": null,
  "shipping_address": {
    "full_address": "123 Nguyễn Trãi, Phường 2, Q.3, TP.HCM",
    "province_id": 79,
    "district_id": 760
  },
  "tracking_number": "VT123456789",
  "carrier": "ViettelPost",
  "shipping_deadline": "2025-10-04T10:00:00Z",
  "items": [
    {
      "order_item_id": 501,
      "sku_code": "NK-AIR-RED-XL",
      "product_name": "Áo Thun Nike Air",
      "variant_name": "Đỏ / XL",
      "image_snapshot": "https://cdn.marketplace.vn/products-media/...",
      "price_snapshot": 350000,
      "quantity": 2,
      "refunded_quantity": 0,
      "fs_item_id": null
    }
  ],
  "created_at": "2025-10-01T10:00:00Z",
  "updated_at": "2025-10-01T12:00:00Z"
}
```

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 403 | User không phải Buyer/Seller chủ của đơn |
| 404 | orderId không tồn tại |

---

### GET /orders/parent/{parentOrderId}
**Chi tiết đơn cha**

**Quyền truy cập**: JWT Required (BUYER)

**Response 200**: Đơn cha kèm toàn bộ sub-orders và thông tin thanh toán

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 403 | Parent order không thuộc về Buyer này |

---

### POST /orders/{orderId}/cancel
**Hủy đơn hàng**

**Quyền truy cập**: JWT Required (BUYER \| SELLER)  
**Tags**: Kafka → order.cancelled

**⚠️ Cảnh báo**: Hủy đơn sẽ trừ Trust Score Buyer theo event_code `BUYER_CANCEL_EXCESSIVE` nếu tổng hủy trong 30 ngày vượt ngưỡng.

**Request Body**:
```json
{
  "reason": "Tôi muốn hủy đơn này",
  "note": "Đơn đặt nhầm"
}
```

**Response 200**:
```json
{
  "order_id": 100,
  "order_code": "OR-20251001-100",
  "status": "CANCELLED",
  "cancelled_by": "BUYER",
  "cancel_reason": "Tôi muốn hủy đơn này",
  "cancelled_at": "2026-04-15T11:00:00Z"
}
```

**Kafka Events**:
```json
{
  "topic": "order.cancelled",
  "payload": {
    "order_id": 100,
    "parent_order_id": 55,
    "user_id": 42,
    "seller_id": 5,
    "cancelled_by": "BUYER",
    "cancel_reason": "Tôi muốn hủy đơn này",
    "total_amount": 700000,
    "loyalty_points_refunded": 25,
    "timestamp": "2026-04-15T11:00:00Z"
  }
}
```

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 409 | Đơn không ở trạng thái PENDING |
| 403 | User không phải chủ đơn |

---

### POST /orders/{orderId}/confirm-received
**Xác nhận đã nhận hàng**

**Quyền truy cập**: JWT Required (BUYER)  
**Tags**: Kafka → order.delivered

**Request Body**: (không có body — `{}`)

**Response 200**:
```json
{
  "order_id": 100,
  "order_code": "OR-20251001-100",
  "status": "DELIVERED",
  "delivered_at": "2026-10-03T14:30:00Z",
  "loyalty_points_confirmed": 25,
  "seller_trust_score_delta": 5
}
```

**Kafka Events**:
```json
{
  "topic": "order.delivered",
  "payload": {
    "order_id": 100,
    "user_id": 42,
    "seller_id": 5,
    "total_amount": 700000,
    "loyalty_points": 25,
    "delivered_at": "2026-10-03T14:30:00Z",
    "timestamp": "2026-10-03T14:30:00Z"
  }
}
```

**Side Effects**: status → DELIVERED, điểm thưởng PENDING → CONFIRMED, trust_score Seller +5

---

## Seller Order Endpoints

### PUT /orders/{orderId}/tracking
**Cập nhật tracking number (Seller)**

**Quyền truy cập**: JWT Required (SELLER - owner)  
**Tags**: Kafka → order.shipped

**Request Body**:
```json
{
  "tracking_number": "VT123456789",
  "carrier": "ViettelPost",
  "note": "Giao hàng dự kiến 2-3 ngày"
}
```

**Response 200**:
```json
{
  "order_id": 100,
  "order_code": "OR-20251001-100",
  "status": "SHIPPING",
  "tracking_number": "VT123456789",
  "carrier": "ViettelPost",
  "shipping_deadline": "2026-10-04T10:00:00Z",
  "updated_at": "2026-10-01T12:00:00Z"
}
```

**Kafka Events**:
```json
{
  "topic": "order.shipped",
  "payload": {
    "order_id": 100,
    "user_id": 42,
    "seller_id": 5,
    "tracking_number": "VT123456789",
    "carrier": "ViettelPost",
    "shipped_at": "2026-10-01T12:00:00Z"
  }
}
```

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 409 | Đơn không ở trạng thái PAID |
| 403 | Seller không phải chủ của sub-order |

---

### POST /orders/{orderId}/return-to-sender
**Seller xác nhận nhận lại hàng hoàn — kích hoạt Full Refund tự động**

**Quyền truy cập**: JWT Required (SELLER)  
**Tags**: Kafka → order.returned | RTS (Return To Sender) | NEW v5.3

**Mô tả** [RTS]: Khi đơn vị vận chuyển hoàn hàng về Seller (gọi không nghe / sai địa chỉ), Seller chủ động gọi API này. Hệ thống sẽ:
1. Chuyển `ORDERS.status → RETURNED`
2. Tự động tạo REFUNDS (type=FULL, initiated_by=SELLER, refund_reason_type=RETURN_TO_SENDER)
3. Cộng lại `stock_available` cho từng SKU (atomic operation)
4. Produce Kafka event `order.returned`
5. Thực hiện Stripe refund tự động (không cần Admin duyệt)
6. Ghi bằng chứng ảnh vào REFUND_ITEMS.return_evidence_images

**Request Body** (multipart/form-data):
```
Content-Type: multipart/form-data

[files]
evidence_images: [file1.jpg, file2.jpg]

[fields]
return_tracking_number: VT999888777
note: Hoàn do không gọi được Buyer, địa chỉ sai
```

**Response 200**:
```json
{
  "order_id": 1001,
  "order_code": "OR-20251001-1001",
  "order_status": "RETURNED",
  "refund_id": 99,
  "refund_code": "RF-20251001-99",
  "refund_status": "PENDING",
  "refund_amount": 250000,
  "return_tracking_number": "VT999888777",
  "evidence_count": 2,
  "estimated_refund_days": 3,
  "stripe_refund_id": "re_3Px5Ab...",
  "message": "Hàng hoàn đã được ghi nhận. Hệ thống đang tự động hoàn tiền cho Buyer.",
  "seller_notification": {
    "status": "sent",
    "message": "Xác nhận hàng hoàn đã được lưu. Tồn kho đã được cộng lại."
  },
  "buyer_notification": {
    "status": "sent",
    "message": "Seller đã nhận lại hàng hoàn. Tiền đang được hoàn về tài khoản của bạn."
  },
  "created_at": "2026-10-01T14:30:00Z"
}
```

**Kafka Events (RTS)**:
```json
{
  "topic": "order.returned",
  "payload": {
    "order_id": 1001,
    "parent_order_id": 1000,
    "user_id": 42,
    "seller_id": 5,
    "refund_id": 99,
    "refund_reason_type": "RETURN_TO_SENDER",
    "return_tracking_number": "VT999888777",
    "total_amount": 250000,
    "evidence_count": 2,
    "timestamp": "2026-10-01T14:30:00Z"
  }
}
```

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 409 | Đơn hàng đã ở trạng thái RETURNED hoặc đã có refund đang xử lý |
| 422 | Trạng thái đơn không hợp lệ — chỉ cho phép khi order.status = SHIPPING |
| 403 | Không phải Seller của đơn hàng |
| 400 | evidence_images không hợp lệ hoặc không cung cấp |

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

**Response 200**: Danh sách sub-orders thuộc Seller, kèm thông tin Buyer và ORDER_ITEMS summary

---

## 📊 Order Status Flow

```
PENDING → PAID → SHIPPING → DELIVERED
                    ↓
                 RETURNED (RTS)
PENDING → CANCELLED
PAID/SHIPPING/DELIVERED → PARTIALLY_REFUNDED / REFUNDED
```

### GET /sellers/me/dashboard
**Dashboard tổng quan cho Seller**

**Quyền truy cập**: JWT Required (SELLER)

**Response 200**: Tổng quan đơn hàng, doanh thu, sản phẩm đang bán.

---

## ↩️ Refund Endpoints (Buyer)

### POST /orders/{orderId}/refunds
**Tạo refund một phần (1 sub-order)**

**Quyền truy cập**: JWT Required (BUYER)

---

### POST /orders/parent/{parentOrderId}/refund
**Tạo refund toàn bộ (tất cả sub-orders)**

**Quyền truy cập**: JWT Required (BUYER)

---

### POST /orders/parent/{parentOrderId}/refunds/partial
**Tạo refund một phần nhiều seller**

**Quyền truy cập**: JWT Required (BUYER)

---

### GET /orders/{orderId}/refunds
**Lịch sử refund của sub-order**

**Quyền truy cập**: JWT Required (BUYER\|SELLER\|ADMIN)

---

### GET /orders/refunds
**Tất cả refund của Buyer**

**Quyền truy cập**: JWT Required (BUYER)

---

### GET /orders/parent/{parentOrderId}/refund
**Trạng thái refund toàn bộ của parent order**

**Quyền truy cập**: JWT Required (BUYER\|ADMIN)

---

## 📊 Order Status Flow

```
PENDING → PAID → SHIPPING → DELIVERED
                    ↓
                 RETURNED (RTS)
PENDING → CANCELLED
PAID/SHIPPING/DELIVERED → PARTIALLY_REFUNDED / REFUNDED
```

## 📊 Summary

| Endpoint | Method | Auth |
|----------|--------|------|
| /orders/checkout | POST | JWT (BUYER) |
| /orders | GET | JWT (BUYER) |
| /orders/{id} | GET | JWT (BUYER\|SELLER) |
| /orders/parent/{id} | GET | JWT (BUYER) |
| /orders/{id}/cancel | POST | JWT (BUYER\|SELLER) |
| /orders/{id}/confirm-received | POST | JWT (BUYER) |
| /orders/{id}/tracking | PUT | JWT (SELLER) |
| /orders/{id}/return-to-sender | POST | JWT (SELLER) |
| /sellers/me/orders | GET | JWT (SELLER) |
| /sellers/me/dashboard | GET | JWT (SELLER) |
| /orders/{id}/refunds | POST | JWT (BUYER) |
| /orders/{id}/refunds | GET | JWT (BUYER\|SELLER\|ADMIN) |
| /orders/refunds | GET | JWT (BUYER) |
| /orders/parent/{id}/refund | POST | JWT (BUYER) |
| /orders/parent/{id}/refund | GET | JWT (BUYER\|ADMIN) |
| /orders/parent/{id}/refunds/partial | POST | JWT (BUYER) |

---

**Phiên bản:** v5.4  
**Cập nhật:** 2026-04-30
