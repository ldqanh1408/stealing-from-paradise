# Order Service — API Reference

> Base path: `/api/v1` → Gateway routes to `order-service:8083`
>
> Database: PostgreSQL

---

## Orders

### POST /orders/checkout
**Tạo đơn hàng (Multi-Vendor Split)**

**Quyền truy cập**: JWT Required (BUYER)

**Request Body:**
```json
{
  "address_id": "integer",           // ID địa chỉ giao hàng (Required)
  "note": "string",                  // Ghi chú đơn hàng
  "items": [                         // (Required, min 1)
    {
      "sku_code": "string",          // (Required)
      "quantity": "integer"          // 1-99 (Required)
    }
  ],
  "use_points": "boolean",           // Dùng điểm thưởng (default: false)
  "payment_method": "string"         // STRIPE | VNPAY (default: STRIPE)
}
```

**Response 201:**
```json
{
  "success": true,
  "data": {
    "parent_order_id": 100,
    "orders": [
      {
        "order_id": 101,
        "seller_id": 10,
        "seller_name": "Shop ABC",
        "status": "PENDING",
        "items": [
          {
            "order_item_id": 1,
            "sku_code": "SP001-S",
            "name_snapshot": "Áo thun nam",
            "price_snapshot": 250000,
            "quantity": 2
          }
        ],
        "total": 500000,
        "shipping_fee": 30000
      }
    ],
    "final_amount": 530000,
    "payment_url": "https://checkout.stripe.com/...",
    "points_used": 0,
    "points_earned_estimate": 375
  }
}
```

---

### GET /orders
**Danh sách đơn hàng (Buyer)**

**Quyền truy cập**: JWT Required (BUYER)

**Query Params:**

| Param | Type | Mô tả |
|-------|------|-------|
| status | string | PENDING | PAID | SHIPPING | DELIVERED | CANCELLED | REFUNDED | RETURNED |
| from_date | date | ISO 8601 |
| to_date | date | ISO 8601 |
| sort | string | newest | oldest |
| page | integer | Default 0 |
| size | integer | Default 20 |

**Response 200:**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "parent_order_id": 100,
        "final_amount": 530000,
        "status": "PENDING",
        "order_count": 2,
        "created_at": "2025-11-01T08:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "total_elements": 1,
    "total_pages": 1,
    "last": true
  }
}
```

---

### GET /orders/{orderId}
**Chi tiết đơn hàng (sub-order)**

**Quyền truy cập**: JWT Required (BUYER | SELLER)

**Response 200:**
```json
{
  "success": true,
  "data": {
    "order_id": 101,
    "order_code": "ORD-20251101-ABCD",
    "parent_order_id": 100,
    "seller_id": 10,
    "seller_name": "Shop ABC",
    "buyer_id": 42,
    "buyer_name": "Nguyễn Văn A",
    "status": "PAID",
    "items": [
      {
        "order_item_id": 1,
        "sku_code": "SP001-S",
        "name_snapshot": "Áo thun nam",
        "price_snapshot": 250000,
        "quantity": 2,
        "refunded_quantity": 0,
        "image": "https://cdn.marketplace.vn/..."
      }
    ],
    "final_amount": 530000,
    "shipping_address": {
      "street": "123 Nguyễn Huệ",
      "city": "TP. Hồ Chí Minh"
    },
    "tracking_number": null,
    "delivered_at": null,
    "created_at": "2025-11-01T08:00:00Z"
  }
}
```

---

### GET /orders/parent/{parentOrderId}
**Chi tiết đơn cha**

**Quyền truy cập**: JWT Required (BUYER | ADMIN)

**Response 200:** Tổng hợp tất cả sub-orders + trạng thái thanh toán

---

### POST /orders/{orderId}/cancel
**Hủy đơn hàng (Buyer)**

**Quyền truy cập**: JWT Required (BUYER)

**Request Body:**
```json
{
  "reason": "string"     // Lý do hủy (Required)
}
```

**Response 200:** Order cancelled (chỉ khi status = PENDING)

---

### PUT /orders/{orderId}/tracking
**Cập nhật tracking number (Seller)**

**Quyền truy cập**: JWT Required (SELLER)

**Request Body:**
```json
{
  "tracking_number": "string",   // Mã vận đơn (Required)
  "carrier": "string"            // GHN | GHTK | VNPost | etc (Required)
}
```

**Response 200:** Order status → SHIPPING

---

### POST /orders/{orderId}/confirm-received
**Xác nhận đã nhận hàng (Buyer)**

**Quyền truy cập**: JWT Required (BUYER)

**Response 200:** Order status → DELIVERED

---

### POST /orders/{orderId}/return-to-sender
**Trả hàng / hoàn hàng (Buyer) — RTS**

**Quyền truy cập**: JWT Required (BUYER)

**Request Body (multipart/form-data):**
```json
{
  "reason": "string",              // Lý do trả hàng (Required)
  "return_items": [                // (Required)
    {
      "order_item_id": "integer",
      "quantity": "integer"
    }
  ],
  "evidence_images": ["file"],     // Upload multipart, tối đa 5 ảnh
  "note": "string"
}
```

**Response 200:**
```json
{
  "success": true,
  "data": {
    "order_id": 101,
    "rts_ref": "RTS-20251101-XXXX",
    "status": "RETURN_REQUESTED",
    "refund_group_ref": "uuid-abc",
    "message": "Yêu cầu trả hàng đã được ghi nhận"
  }
}
```

---

## Seller Orders

### GET /sellers/me/orders
**Danh sách đơn hàng của Seller**

**Quyền truy cập**: JWT Required (SELLER)

**Query Params:**

| Param | Type | Mô tả |
|-------|------|-------|
| status | string | PENDING | PAID | SHIPPING | DELIVERED | CANCELLED | REFUNDED | RETURNED |
| from_date | date | |
| to_date | date | |
| sort | string | newest | oldest |
| page | integer | Default 0 |
| size | integer | Default 20 |

---

## Refunds (Buyer)

### POST /orders/{orderId}/refunds
**Yêu cầu hoàn tiền một phần (1 seller)**

**Quyền truy cập**: JWT Required (BUYER)

**Request Body:**
```json
{
  "reason": "string",               // Lý do hoàn tiền (Required)
  "items": [
    {
      "order_item_id": "integer",
      "quantity": "integer",
      "item_reason": "string"
    }
  ],
  "evidence_images": ["string"]     // URLs từ MinIO
}
```

**Response 201:**
```json
{
  "success": true,
  "data": {
    "group_ref": "uuid",
    "order_id": 101,
    "type": "PARTIAL",
    "status": "PENDING",
    "total_amount": 500000,
    "refund_amount": 250000,
    "item_count": 1,
    "estimated_days": 3,
    "message": "Yêu cầu hoàn tiền đã được ghi nhận"
  }
}
```

---

### POST /orders/parent/{parentOrderId}/refund
**Yêu cầu hoàn tiền toàn bộ (Full Refund)**

**Quyền truy cập**: JWT Required (BUYER)

**Request Body:**
```json
{
  "reason": "string",
  "evidence_images": ["string"]
}
```

**Response 201:** Full refund request submitted

---

### POST /orders/parent/{parentOrderId}/refunds/partial
**Yêu cầu hoàn tiền một phần (multi-seller)**

**Quyền truy cập**: JWT Required (BUYER)

---

### GET /orders/parent/{parentOrderId}/refund
**Trạng thái Full Refund**

**Quyền truy cập**: JWT Required (BUYER | ADMIN)

---

### GET /orders/{orderId}/refunds
**Lịch sử hoàn tiền của 1 sub-order**

**Quyền truy cập**: JWT Required (BUYER | SELLER | ADMIN)

---

### GET /orders/{orderId}/refunds/{refundId}
**Chi tiết 1 yêu cầu hoàn tiền**

**Quyền truy cập**: JWT Required (BUYER | ADMIN)

**Response 200:**
```json
{
  "success": true,
  "data": {
    "refund_id": 88,
    "transaction_id": 200,
    "order_id": 101,
    "type": "PARTIAL",
    "amount": 250000,
    "status": "SUCCESS",
    "items": [
      {
        "order_item_id": 1,
        "quantity": 1,
        "refund_amount": 250000
      }
    ],
    "admin_note": null,
    "reviewed_by": null,
    "created_at": "2025-11-01T08:00:00Z"
  }
}
```

---

### GET /orders/refunds
**Tất cả yêu cầu hoàn tiền của Buyer**

**Quyền truy cập**: JWT Required (BUYER)

**Query Params:**

| Param | Type | Mô tả |
|-------|------|-------|
| status | string | PENDING | SUCCESS | FAILED | REJECTED |
| type | string | FULL | PARTIAL |
| from_date | date | |
| to_date | date | |
| page | integer | Default 0 |
| size | integer | Default 20 |

---

### GET /orders/{orderId}/refunds/presigned-url
**Pre-signed URL upload ảnh bằng chứng hoàn tiền**

**Quyền truy cập**: JWT Required (BUYER)

**Query Params:**

| Param | Type | Required | Mô tả |
|-------|------|----------|-------|
| file_name | string | ✓ | Tên file gốc |
| content_type | string | ✓ | image/jpeg | image/png | image/webp |

**Response 200:**
```json
{
  "success": true,
  "data": {
    "presigned_url": "https://minio.internal/refund-evidence/orders/100/uuid-abc.jpg",
    "object_url": "https://cdn.marketplace.vn/refund-evidence/orders/100/uuid-abc.jpg",
    "expires_in": 900
  }
}
```

**Errors:** 403 (not owner), 422 (invalid content_type)

---

## Reviews

### POST /orders/{orderId}/reviews
**Đánh giá sản phẩm (Buyer)**

**Quyền truy cập**: JWT Required (BUYER)

**Request Body:**
```json
{
  "items": [
    {
      "order_item_id": "integer",
      "rating": "integer",       // 1-5 (Required)
      "content": "string",       // Nội dung đánh giá
      "images": ["string"]       // Array image_id
    }
  ]
}
```

**Response 201:** Reviews created

---

### GET /products/{productId}/reviews
**Đánh giá của sản phẩm (Public)**

**Quyền truy cập**: Public

**Query Params:** rating, sort (newest | oldest), page, size
