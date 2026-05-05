# 📋 Order Service API

**Port**: `:8083`  
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

**Request Body**:
```json
{
  "address_id": 7,
  "item_ids": [201, 202]
}
```

**Validation Rules**:
| Field | Type | Rules |
|-------|------|-------|
| address_id | long | Phải tồn tại; thuộc user |
| item_ids | array | 1–50 items; không trùng |

**Response 201**:
```json
{
  "parent_order_id": 55,
  "orders": [
    {
      "order_id": 100,
      "order_code": "OR-20251001-100",
      "seller_id": 5,
      "total_amt": 700000,
      "final_amt": 650000,
      "status": "PENDING",
      "items": [
        {
          "order_item_id": 501,
          "sku_code": "NK-AIR-RED-XL",
          "name_snapshot": "Áo Thun Nike Air",
          "image_snapshot": "https://cdn.marketplace.vn/products-media/products/5/101/uuid.jpg",
          "price_snapshot": 350000,
          "quantity": 2
        }
      ],
      "created_at": "2026-10-01T10:00:00Z"
    },
    {
      "order_id": 101,
      "order_code": "OR-20251001-101",
      "seller_id": 9,
      "total_amt": 500000,
      "final_amt": 500000,
      "status": "PENDING",
      "items": [
        {
          "order_item_id": 601,
          "sku_code": "AD-ULTRA-BLK-10",
          "name_snapshot": "Giày Adidas Ultraboost",
          "image_snapshot": "https://cdn.marketplace.vn/products-media/products/9/201/uuid.jpg",
          "price_snapshot": 500000,
          "quantity": 1
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
  "total_amount": 1200000,
  "total_items": 3,
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
    "timestamp": "2026-10-01T10:00:00Z"
  }
}
```

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 422 | Một số item hết hàng hoặc không hợp lệ |
| 409 | Địa chỉ không tồn tại hoặc không thuộc user |

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
      "status": "PAID",
      "total_amt": 700000,
      "final_amt": 700000,
      "created_at": "2025-10-01T10:00:00Z"
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
  "customer_id": 42,
  "status": "SHIPPING",
  "total_amt": 700000,
  "final_amt": 700000,
  "cancelled_by": null,
  "cancel_reason": null,
  "shipping_address": {
    "full_address": "123 Nguyễn Trãi, Phường 2, Q.3, TP.HCM",
    "province_id": 79,
    "district_id": 760
  },
  "tracking_number": "VT123456789",
  "shipping_deadline": "2025-10-04T10:00:00Z",
  "items": [
    {
      "order_item_id": 501,
      "sku_code": "NK-AIR-RED-XL",
      "name_snapshot": "Áo Thun Nike Air",
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
  "delivered_at": "2026-10-03T14:30:00Z"
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
    "delivered_at": "2026-10-03T14:30:00Z",
    "timestamp": "2026-10-03T14:30:00Z"
  }
}
```

**Side Effects**: status → DELIVERED

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
  "refund_status": "PENDING",
  "return_tracking_number": "VT999888777",
  "estimated_refund_days": 3,
  "stripe_refund_id": "re_3Px5Ab...",
  "message": "Hàng hoàn đã được ghi nhận. Hệ thống đang tự động hoàn tiền cho Buyer.",
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
**Yêu cầu hoàn tiền một phần (1 sub-order)**

**Quyền truy cập**: JWT Required (BUYER)

**Tags**: Kafka → refund.requested

**Request Body**:
```json
{
  "reason": "Sản phẩm bị lỗi, không đúng mô tả",
  "items": [
    {
      "order_item_id": 501,
      "quantity": 1,
      "item_reason": "Áo bị rách đường may"
    }
  ],
  "evidence_images": ["https://cdn.marketplace.vn/refund-evidence/orders/100/uuid-abc.jpg"]
}
```

**Validation Rules**:
| Field | Type | Rules |
|-------|------|-------|
| reason | string | Required; 1–1000 ký tự |
| items | array | Required; min 1, max 50 |
| order_item_id | long | Phải thuộc order |
| quantity | integer | ≤ order_item.quantity - refunded_quantity |
| item_reason | string | Required; 1–500 ký tự |
| evidence_images | array | Optional; tối đa 5 URLs từ MinIO |

**Response 201**:
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
    "message": "Yêu cầu hoàn tiền đã được ghi nhận"
  }
}
```

---

### POST /orders/parent/{parentOrderId}/refund
**Yêu cầu hoàn tiền toàn bộ (Full Refund)**

**Quyền truy cập**: JWT Required (BUYER)

**Tags**: Kafka → refund.full_requested

**Request Body**:
```json
{
  "reason": "Đơn hàng không đúng, tất cả sản phẩm đều bị lỗi",
  "evidence_images": ["https://cdn.marketplace.vn/refund-evidence/orders/100/uuid-abc.jpg"]
}
```

**Response 201**: Full refund request submitted

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

**Response 200**:
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
        "order_item_id": 501,
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

**Query Params**:
| Param | Type | Mô tả |
|-------|------|-------|
| status | string | PENDING | SUCCESS | FAILED | REJECTED |
| type | string | FULL | PARTIAL |
| from_date | date | ISO 8601 |
| to_date | date | ISO 8601 |
| page | integer | Default 0 |
| size | integer | Default 20 |

---

### GET /orders/{orderId}/refunds/presigned-url
**Pre-signed URL upload ảnh bằng chứng hoàn tiền**

**Quyền truy cập**: JWT Required (BUYER)

**Query Params**:
| Param | Type | Required | Mô tả |
|-------|------|----------|-------|
| file_name | string | ✓ | Tên file gốc |
| content_type | string | ✓ | image/jpeg | image/png | image/webp |

**Response 200**:
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

**Errors**: 403 (not owner), 422 (invalid content_type)

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
| /orders/{id}/refunds/{refundId} | GET | JWT (BUYER\|ADMIN) |
| /orders/{id}/refunds/presigned-url | GET | JWT (BUYER) |
| /orders/parent/{id}/refunds/partial | POST | JWT (BUYER) |

---

**Phiên bản:** v5.4  
**Cập nhật:** 2026-04-30
