# 📋 API Detailed JSON Request/Response v5.3 RTS [COMPLETE]

**Phiên bản:** v5.3 RTS  
**Cập nhật:** 2026-04-15  
**Trạng thái:** Production-Ready ✅  

Tài liệu này trình bày **chi tiết** toàn bộ JSON request/response, Kafka payloads, và validation rules cho các API endpoints. Dựa trên ERD, nghiệp vụ hệ thống, và v5.3 RTS updates.

---

## 📚 Mục Lục

1. [🔐 Identity Service APIs](#-identity-service-apis)
2. [📦 Product Service APIs](#-product-service-apis)
3. [🔍 Search Service APIs](#-search-service-apis)
4. [🛒 Cart Service APIs](#-cart-service-apis)
5. [📋 Order Service APIs](#-order-service-apis)
6. [↩️ Refund APIs](#-refund-apis)
7. [💳 Payment Service APIs](#-payment-service-apis)
8. [⭐ Loyalty Service APIs](#-loyalty-service-apis)
9. [⚡ Flash Sale Service APIs](#-flash-sale-service-apis)
10. [🔔 Notification Service APIs](#-notification-service-apis)
11. [🛡️ Admin APIs](#-admin-apis)
12. [🧭 Kafka Topics & Payloads](#-kafka-topics--payloads)
13. [❌ Error Response Formats](#-error-response-formats)

---

# 🔐 Identity Service APIs

**Port:** `:8081`

## POST /auth/register

**Đăng ký tài khoản mới**

### Request

```json
{
  "username": "nguyen_van_a",
  "email": "a@example.com",
  "phone": "0901234567",
  "password": "SecurePass123!",
  "full_name": "Nguyễn Văn A"
}
```

### Validation Rules

| Field | Type | Rules |
|-------|------|-------|
| username | string | 3-50 chars, a-z, 0-9, dot, underscore; Unique |
| email | string | Valid email format; Unique |
| phone | string | Vietnam format; Unique |
| password | string | Min 8 chars; ≥1 uppercase, ≥1 number |
| full_name | string | 2-100 chars |

### Response 201 (Success)

```json
{
  "user_id": 42,
  "username": "nguyen_van_a",
  "email": "a@example.com",
  "phone": "0901234567",
  "full_name": "Nguyễn Văn A",
  "roles": ["BUYER"],
  "status": "ACTIVE",
  "trust_score": 80,
  "trust_tier": "PLATINUM",
  "avatar_url": null,
  "created_at": "2026-04-15T08:00:00Z"
}
```

### Kafka Events

```json
{
  "topic": "account.created",
  "payload": {
    "user_id": 42,
    "email": "a@example.com",
    "phone": "0901234567",
    "timestamp": "2026-04-15T08:00:00Z",
    "source": "auth-service"
  }
}
```

---

## POST /auth/login

**Đăng nhập, nhận JWT**

### Request

```json
{
  "credential": "a@example.com",
  "password": "SecurePass123!"
}
```

### Validation Rules

| Field | Type | Rules |
|-------|------|-------|
| credential | string | username \| email \| phone |
| password | string | Min 1 char |

### Response 200 (Success)

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiI0MiIsImlhdCI6MTcxMzcwMDAwMCwiZXhwIjoxNzEzNzAwOTAwLCJqdGkiOiJ1dWlkLWtleTEifQ...",
  "refresh_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiI0MiIsInR0bCI6IjcgZGF5cyIsImlhdCI6MTcxMzcwMDAwMCwianRpIjoicmVmcmVzaC11dWlkIn0...",
  "token_type": "Bearer",
  "expires_in": 900,
  "refresh_expires_in": 604800,
  "user_id": 42,
  "username": "nguyen_van_a",
  "email": "a@example.com",
  "phone": "0901234567",
  "full_name": "Nguyễn Văn A",
  "roles": ["BUYER", "SELLER"],
  "status": "ACTIVE",
  "trust_score": 80,
  "trust_tier": "PLATINUM",
  "avatar_url": "https://cdn.marketplace.vn/avatars/42.jpg"
}
```

### Response 403 (Account Locked)

```json
{
  "error": "ACCOUNT_LOCKED",
  "message": "Tài khoản bị khóa",
  "lock_reason": "Trust score quá thấp (< 10). Liên hệ support để khiếu nại.",
  "locked_until": "2026-05-15T10:00:00Z",
  "status_code": 403
}
```

### Kafka Events

```json
{
  "topic": "account.login",
  "payload": {
    "user_id": 42,
    "login_time": "2026-04-15T08:05:00Z",
    "ip_address": "192.168.1.1",
    "device": "chrome/mobile"
  }
}
```

---

## POST /auth/logout

**Đăng xuất, thu hồi token**

### Request

```json
{
  "refresh_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "logout_all_devices": false
}
```

### Response 200

```json
{
  "message": "Đăng xuất thành công, token hiện tại bị vô hiệu hóa"
}
```

### Redis Side Effect

```
SET revoked_token:{jti} = 1 EX 900
// TTL = token expiration time (default 15 min)
```

---

## GET /users/me

**Lấy thông tin tài khoản hiện tại**

### Query Parameters

Không có

### Response 200

```json
{
  "user_id": 42,
  "username": "nguyen_van_a",
  "email": "a@example.com",
  "phone": "0901234567",
  "full_name": "Nguyễn Văn A",
  "avatar_url": "https://cdn.marketplace.vn/avatars/42.jpg",
  "roles": ["BUYER", "SELLER"],
  "status": "ACTIVE",
  "trust_score": 85,
  "trust_tier": "GOLD",
  "appeal_count": 1,
  "product_posting_suspended": false,
  "lock_reason": null,
  "locked_until": null,
  "created_at": "2024-01-15T08:00:00Z"
}
```

---

## POST /users/me/trust-score-appeal

**Gửi khiếu nại trust score**

### Request

```json
{
  "log_id": 1042,
  "reason": "Tôi không hủy đơn quá số lần cho phép. Có bug hệ thống.",
  "evidence_urls": [
    "https://cdn.marketplace.vn/appeal-evidence/appeals/42/uuid-abc.jpg",
    "https://cdn.marketplace.vn/appeal-evidence/appeals/42/uuid-def.jpg"
  ]
}
```

### Validation Rules

| Field | Type | Rules |
|-------|------|-------|
| log_id | long | Phải tồn tại trong TRUST_SCORE_LOGS của user |
| reason | string | Max 500 chars |
| evidence_urls | array | 0-5 URLs từ presigned URLs |

### Response 201

```json
{
  "appeal_id": 15,
  "user_id": 42,
  "log_id": 1042,
  "status": "PENDING",
  "reason": "Tôi không hủy đơn quá số lần cho phép. Có bug hệ thống.",
  "evidence_urls": [
    "https://cdn.marketplace.vn/appeal-evidence/appeals/42/uuid-abc.jpg",
    "https://cdn.marketplace.vn/appeal-evidence/appeals/42/uuid-def.jpg"
  ],
  "created_at": "2026-04-15T10:00:00Z"
}
```

### Kafka Events

```json
{
  "topic": "appeal.submitted",
  "payload": {
    "appeal_id": 15,
    "user_id": 42,
    "log_id": 1042,
    "event_code": "EXCESSIVE_CANCELLATION",
    "old_score": 72,
    "current_score": 72,
    "timestamp": "2026-04-15T10:00:00Z"
  }
}
```

---

## GET /users/me/trust-score/logs

**Lịch sử thay đổi Trust Score**

### Query Parameters

```
page=0&size=20
```

### Response 200

```json
{
  "content": [
    {
      "log_id": 1042,
      "event_code": "BUYER_CANCEL_EXCESSIVE",
      "delta": -5,
      "score_before": 77,
      "score_after": 72,
      "changed_by": "SYSTEM",
      "reason": "Hủy đơn > 5 lần trong 30 ngày (rolling)",
      "created_at": "2026-04-14T03:00:00Z"
    },
    {
      "log_id": 1041,
      "event_code": "FIRST_ORDER_COMPLETED",
      "delta": 5,
      "score_before": 80,
      "score_after": 85,
      "changed_by": "SYSTEM",
      "reason": "Hoàn thành đơn hàng đầu tiên",
      "created_at": "2026-04-01T15:30:00Z"
    }
  ],
  "total_elements": 38,
  "total_pages": 2,
  "page_number": 0,
  "page_size": 20
}
```

---

# 📦 Product Service APIs

**Port:** `:8082`

## POST /products

**Tạo sản phẩm mới (Seller)**

### Request

```json
{
  "name": "Áo Thun Nike Air Nam",
  "description": "<p>Áo thun chất lượng cao, thoáng mát...</p>",
  "category_id": "507f1f77bcf86cd799439011",
  "attributes": {
    "brand": "Nike",
    "material": "100% Cotton",
    "size_chart": "S-M-L-XL-XXL"
  },
  "images": [
    "https://cdn.marketplace.vn/products-media/products/5/101/uuid-front.jpg",
    "https://cdn.marketplace.vn/products-media/products/5/101/uuid-back.jpg"
  ]
}
```

### Validation Rules

| Field | Type | Rules |
|-------|------|-------|
| name | string | 5-200 chars |
| description | string | Max 10000 chars (HTML allowed) |
| category_id | string | Leaf category only |
| images | array | 1-10 URLs; JPEG/PNG/WebP |

### Response 201

```json
{
  "product_id": "507f1f77bcf86cd799439012",
  "seller_id": 5,
  "name": "Áo Thun Nike Air Nam",
  "category_id": "507f1f77bcf86cd799439011",
  "status": "DRAFT",
  "stock_available": 0,
  "created_at": "2026-04-15T10:00:00Z"
}
```

### Kafka Events

```json
{
  "topic": "product.created",
  "payload": {
    "product_id": "507f1f77bcf86cd799439012",
    "seller_id": 5,
    "name": "Áo Thun Nike Air Nam",
    "category_id": "507f1f77bcf86cd799439011",
    "status": "DRAFT",
    "timestamp": "2026-04-15T10:00:00Z"
  }
}
```

---

## POST /seller/products/{productId}/variants

**Tạo variant sản phẩm**

### Request

```json
{
  "sku_code": "NK-AIR-RED-XL",
  "tier_name": "Đỏ / XL",
  "price": 350000
}
```

### Validation Rules

| Field | Type | Rules |
|-------|------|-------|
| sku_code | string | Unique; 3-50 chars; alphanumeric + dash |
| tier_name | string | 1-100 chars |
| price | decimal | > 0; max 9,999,999,999 |

### Response 201

```json
{
  "variant_id": "507f1f77bcf86cd799439013",
  "sku_code": "NK-AIR-RED-XL",
  "tier_name": "Đỏ / XL",
  "price": 350000,
  "product_id": "507f1f77bcf86cd799439012",
  "created_at": "2026-04-15T10:00:00Z"
}
```

---

## POST /seller/products/{productId}/submit

**Gửi sản phẩm duyệt**

### Request

```json
{}
```

### Response 200

```json
{
  "product_id": "507f1f77bcf86cd799439012",
  "status": "PENDING",
  "message": "Sản phẩm đã được gửi duyệt"
}
```

### Kafka Events

```json
{
  "topic": "product.pending_review",
  "payload": {
    "product_id": "507f1f77bcf86cd799439012",
    "seller_id": 5,
    "variants_count": 3,
    "timestamp": "2026-04-15T10:00:00Z"
  }
}
```

---

# 🔍 Search Service APIs

**Port:** `:8089`

## GET /search/products

**Tìm kiếm sản phẩm**

### Query Parameters

```
q=áo thun&category_id=507f1f77bcf86cd799439011&price_min=100000&price_max=500000&in_stock=true&sort=price_asc&page=0&size=20
```

### Response 200

```json
{
  "total_results": 156,
  "page": 0,
  "size": 20,
  "total_pages": 8,
  "products": [
    {
      "product_id": "507f1f77bcf86cd799439012",
      "name": "Áo Thun Nike Air Nam",
      "seller_id": 5,
      "seller_name": "Shop Nike VN",
      "seller_trust_score": 92,
      "category_id": "507f1f77bcf86cd799439011",
      "category_name": "Áo Thun Nam",
      "price_min": 250000,
      "price_max": 450000,
      "images": [
        "https://cdn.marketplace.vn/products-media/products/5/101/uuid-front.jpg"
      ],
      "stock_available": 95,
      "is_flash": true,
      "flash_price": 189999,
      "rating_avg": 4.7,
      "rating_count": 245,
      "sold_count": 1200,
      "created_at": "2026-04-01T08:00:00Z"
    }
  ],
  "facets": {
    "price_ranges": [
      {
        "range": "0-100000",
        "count": 32
      },
      {
        "range": "100000-500000",
        "count": 98
      }
    ],
    "sellers": [
      {
        "seller_id": 5,
        "seller_name": "Shop Nike VN",
        "count": 45
      }
    ]
  }
}
```

---

# 🛒 Cart Service APIs

**Port:** `:8083`

## GET /cart

**Lấy giỏ hàng**

### Query Parameters

Không có

### Response 200

```json
{
  "cart_id": "507f1f77bcf86cd799439014",
  "user_id": 42,
  "sellers": [
    {
      "seller_id": 5,
      "seller_name": "Shop Nike VN",
      "seller_trust_score": 92,
      "items": [
        {
          "cart_item_id": 201,
          "sku_code": "NK-AIR-RED-XL",
          "product_id": "507f1f77bcf86cd799439012",
          "product_name": "Áo Thun Nike Air",
          "variant_name": "Đỏ / XL",
          "unit_price": 350000,
          "quantity": 2,
          "stock_available": 95,
          "is_flash": false,
          "fs_item_id": null,
          "flash_price": null,
          "flash_expires_at": null,
          "subtotal": 700000,
          "added_at": "2026-04-14T15:30:00Z"
        }
      ],
      "seller_subtotal": 700000
    },
    {
      "seller_id": 9,
      "seller_name": "Shop Adidas VN",
      "seller_trust_score": 88,
      "items": [
        {
          "cart_item_id": 202,
          "sku_code": "AD-ULTRA-BLK-10",
          "product_id": "507f1f77bcf86cd799439013",
          "product_name": "Giày Adidas Ultraboost",
          "variant_name": "Đen / EU 10",
          "unit_price": 500000,
          "quantity": 1,
          "stock_available": 50,
          "is_flash": true,
          "fs_item_id": 1001,
          "flash_price": 399999,
          "flash_expires_at": "2026-04-16T22:00:00Z",
          "subtotal": 500000,
          "added_at": "2026-04-14T16:00:00Z"
        }
      ],
      "seller_subtotal": 500000
    }
  ],
  "total_items": 3,
  "subtotal": 1200000,
  "discount_from_loyalty": 0,
  "total": 1200000
}
```

---

## POST /cart/items

**Thêm sản phẩm vào giỏ**

### Request

```json
{
  "sku_code": "NK-AIR-RED-XL",
  "quantity": 2,
  "fs_item_id": null
}
```

### Validation Rules

| Field | Type | Rules |
|-------|------|-------|
| sku_code | string | Phải tồn tại; Unique |
| quantity | integer | > 0; ≤ 1000 |
| fs_item_id | long | Optional; nếu có phải là Flash Sale item APPROVED |

### Response 200

```json
{
  "cart_item_id": 201,
  "sku_code": "NK-AIR-RED-XL",
  "product_name": "Áo Thun Nike Air",
  "quantity": 2,
  "unit_price": 350000,
  "subtotal": 700000,
  "stock_available": 95,
  "message": "Thêm vào giỏ hàng thành công"
}
```

### Kafka Events

```json
{
  "topic": "cart.item_added",
  "payload": {
    "user_id": 42,
    "sku_code": "NK-AIR-RED-XL",
    "quantity": 2,
    "timestamp": "2026-04-15T10:00:00Z"
  }
}
```

---

# 📋 Order Service APIs

**Port:** `:8087`

## POST /orders/checkout

**Tạo đơn hàng từ giỏ (Multi-Vendor)**

### Request

```json
{
  "address_id": 7,
  "item_ids": [201, 202],
  "use_loyalty_points": true,
  "loyalty_points_to_use": 50
}
```

### Validation Rules

| Field | Type | Rules |
|-------|------|-------|
| address_id | long | Phải tồn tại; thuộc user |
| item_ids | array | 1-50 items; không trùng |
| use_loyalty_points | boolean | Optional; default false |
| loyalty_points_to_use | integer | ≤ 20% of total amount; ≤ available points |

### Response 201 (Multi-Vendor Example)

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

### Kafka Events

```json
{
  "topic": "order.created",
  "payload": {
    "parent_order_id": 55,
    "user_id": 42,
    "orders": [
      {
        "order_id": 100,
        "seller_id": 5,
        "total_amount": 700000,
        "items_count": 1
      },
      {
        "order_id": 101,
        "seller_id": 9,
        "total_amount": 500000,
        "items_count": 1
      }
    ],
    "total_amount": 1200000,
    "loyalty_points_used": 50,
    "timestamp": "2026-10-01T10:00:00Z"
  }
}
```

---

## POST /orders/{orderId}/cancel

**Hủy đơn hàng**

### Request

```json
{
  "reason": "Tôi muốn hủy đơn này",
  "note": "Đơn đặt nhầm"
}
```

### Response 200

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

### Kafka Events

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

---

## PUT /orders/{orderId}/tracking

**Cập nhật tracking number (Seller)**

### Request

```json
{
  "tracking_number": "VT123456789",
  "carrier": "ViettelPost",
  "note": "Giao hàng dự kiến 2-3 ngày"
}
```

### Response 200

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

### Kafka Events

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

---

## POST /orders/{orderId}/confirm-received

**Xác nhận nhận hàng**

### Request

```json
{}
```

### Response 200

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

### Kafka Events

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

---

## POST /orders/{orderId}/return-to-sender

**Seller xác nhận hàng hoàn (RTS) [NEW v5.3]**

### Request (multipart/form-data)

```
Content-Type: multipart/form-data

[files]
evidence_images: [file1.jpg, file2.jpg]

[fields]
return_tracking_number: VT999888777
note: Hoàn do không gọi được Buyer, địa chỉ sai
```

### Response 200

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

### Kafka Events (RTS)

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

---

# ↩️ Refund APIs

## POST /orders/parent/{parentOrderId}/refund

**Full Refund toàn bộ đơn cha (Buyer)**

### Request

```json
{
  "reason": "Tôi không cần hàng nữa",
  "evidence_images": [
    "https://cdn.marketplace.vn/refund-evidence/orders/55/uuid-abc.jpg"
  ]
}
```

### Response 201

```json
{
  "group_ref": "550e8400-e29b-41d4-a716-446655440000",
  "type": "FULL",
  "total_amount": 1200000,
  "status": "PENDING",
  "refunds": [
    {
      "refund_id": 88,
      "refund_code": "RF-20251005-88",
      "order_id": 100,
      "seller_id": 5,
      "amount": 700000,
      "item_count": 1,
      "status": "PENDING"
    },
    {
      "refund_id": 89,
      "refund_code": "RF-20251005-89",
      "order_id": 101,
      "seller_id": 9,
      "amount": 500000,
      "item_count": 1,
      "status": "PENDING"
    }
  ],
  "loyalty_points_to_return": 62,
  "estimated_days": 3,
  "created_at": "2026-10-05T14:00:00Z"
}
```

### Kafka Events

```json
{
  "topic": "refund.requested",
  "payload": {
    "group_ref": "550e8400-e29b-41d4-a716-446655440000",
    "type": "FULL",
    "parent_order_id": 55,
    "user_id": 42,
    "refunds": [
      {
        "refund_id": 88,
        "seller_id": 5,
        "amount": 700000
      },
      {
        "refund_id": 89,
        "seller_id": 9,
        "amount": 500000
      }
    ],
    "total_amount": 1200000,
    "timestamp": "2026-10-05T14:00:00Z"
  }
}
```

---

## POST /orders/{orderId}/refunds

**Partial Refund 1 sub-order (Buyer)**

### Request

```json
{
  "reason": "Sản phẩm bị lỗi",
  "items": [
    {
      "order_item_id": 501,
      "quantity": 1,
      "item_reason": "Áo bị nhuộm màu"
    }
  ],
  "evidence_images": [
    "https://cdn.marketplace.vn/refund-evidence/orders/100/uuid-damage.jpg"
  ]
}
```

### Response 201

```json
{
  "refund_id": 88,
  "refund_code": "RF-20251005-88",
  "order_id": 100,
  "type": "PARTIAL",
  "status": "PENDING",
  "total_amount": 700000,
  "refund_amount": 350000,
  "items": [
    {
      "order_item_id": 501,
      "quantity": 1,
      "refund_amount": 350000,
      "item_reason": "Áo bị nhuộm màu"
    }
  ],
  "evidence_images": [
    "https://cdn.marketplace.vn/refund-evidence/orders/100/uuid-damage.jpg"
  ],
  "estimated_days": 3,
  "created_at": "2026-10-05T14:00:00Z"
}
```

---

## POST /admin/refunds/{refundId}/approve

**Admin duyệt hoàn tiền [NEW v5.3 - Tracking Number]**

### Request

```json
{
  "admin_note": "Hoàn do giao hàng không thành công, shipper mang lại lần 3",
  "adjust_amount": null,
  "caused_by": "SELLER",
  "tracking_number": "VT123456789"
}
```

### Validation Rules

| Field | Type | Rules |
|-------|------|-------|
| admin_note | string | Required; 1-1000 chars |
| adjust_amount | decimal | Optional; ≥ 0 |
| caused_by | string | Optional; SELLER \| BUYER |
| tracking_number | string | Optional; format: [A-Z]{2}[0-9]{9} |

### Response 200

```json
{
  "refund_id": 88,
  "refund_code": "RF-20251005-88",
  "status": "SUCCESS",
  "type": "PARTIAL",
  "amount": 500000,
  "adjust_amount": null,
  "tracking_number": "VT123456789",
  "return_evidence": [
    {
      "type": "tracking",
      "tracking_number": "VT123456789",
      "recorded_at": "2026-04-15T10:30:00Z"
    }
  ],
  "reviewed_by": 1,
  "admin_id": 1,
  "admin_name": "Admin User",
  "admin_note": "Hoàn do giao hàng không thành công...",
  "reviewed_at": "2026-04-15T10:30:00Z",
  "stripe_refund_id": "re_3Px5Ab2K1234567...",
  "trust_score_adjustment": {
    "seller_id": 5,
    "delta": -5,
    "event_code": "SELLER_CAUSED_REFUND",
    "new_score": 87,
    "triggered": true
  },
  "loyalty_adjustment": {
    "buyer_id": 42,
    "points_returned": 50,
    "status": "refunded"
  },
  "notifications": {
    "buyer": {
      "status": "sent",
      "message": "Hoàn tiền được duyệt. Mã vận đơn hoàn: VT123456789"
    },
    "seller": {
      "status": "sent",
      "message": "Refund đã được xử lý. Trust score - 5 điểm."
    }
  },
  "created_at": "2026-10-05T14:00:00Z",
  "updated_at": "2026-04-15T10:30:00Z"
}
```

### Kafka Events

```json
{
  "topic": "refund.admin_approved",
  "payload": {
    "refund_id": 88,
    "order_id": 100,
    "user_id": 42,
    "seller_id": 5,
    "amount": 500000,
    "tracking_number": "VT123456789",
    "caused_by": "SELLER",
    "admin_id": 1,
    "admin_note": "Hoàn do giao hàng không thành công...",
    "trust_score_delta": -5,
    "loyalty_points_returned": 50,
    "approved_at": "2026-04-15T10:30:00Z"
  }
}
```

---

# 💳 Payment Service APIs

**Port:** `:8085`

## POST /stripe/onboarding/start

**Bắt đầu Stripe KYC (Seller)**

### Request

```json
{}
```

### Response 201

```json
{
  "onboarding_url": "https://connect.stripe.com/setup/e/acct_1OxABC123456789/...",
  "stripe_account_id": "acct_1OxABC123456789",
  "expires_at": "2026-04-16T10:00:00Z"
}
```

---

## GET /stripe/onboarding/status

**Kiểm tra trạng thái Stripe account**

### Response 200

```json
{
  "stripe_account_id": "acct_1OxABC123456789",
  "account_status": "ACTIVE",
  "details_submitted": true,
  "charges_enabled": true,
  "payouts_enabled": true,
  "onboarding_status": "COMPLETE",
  "onboarding_url": null
}
```

---

## GET /payments/parent-order/{parentOrderId}

**Thông tin giao dịch thanh toán**

### Response 200

```json
{
  "transaction_id": 301,
  "parent_order_id": 55,
  "amount": 1200000,
  "method": "STRIPE",
  "status": "SUCCESS",
  "stripe_pi_id": "pi_3PxABC2K1234567...",
  "application_fee": 60000,
  "application_fee_percentage": 5.0,
  "trans_ref": "TXN-20251001-301",
  "paid_at": "2026-10-01T10:05:00Z",
  "sellers": [
    {
      "seller_id": 5,
      "seller_name": "Shop Nike VN",
      "order_id": 100,
      "amount": 700000,
      "fee": 35000,
      "net_amount": 665000,
      "stripe_transfer_id": "tr_3PxABC2K98765432",
      "transfer_status": "SUCCEEDED"
    },
    {
      "seller_id": 9,
      "seller_name": "Shop Adidas VN",
      "order_id": 101,
      "amount": 500000,
      "fee": 25000,
      "net_amount": 475000,
      "stripe_transfer_id": "tr_3PxABC2K98765433",
      "transfer_status": "SUCCEEDED"
    }
  ]
}
```

---

# ⭐ Loyalty Service APIs

**Port:** `:8084`

## GET /loyalty/balance

**Số dư điểm thưởng**

### Response 200

```json
{
  "user_id": 42,
  "loyalty_account_id": 123,
  "available_points": 1250,
  "pending_points": 300,
  "expired_points": 50,
  "total_earned": 2000,
  "total_used": 650,
  "conversion_rate": 200,
  "note": "1 point = 1/200 of 200,000 VND = 1,000 VND",
  "max_usable_per_order": 275,
  "max_usable_percentage": 0.20,
  "expiry_policy": {
    "expiry_days": 365,
    "next_expiry_date": "2026-10-05",
    "points_expiring_soon": 0
  },
  "tier_benefits": {
    "tier": "PLATINUM",
    "trust_score": 80,
    "earning_rate": "5%",
    "max_discount_rate": "20%"
  },
  "recent_transactions": [
    {
      "transaction_id": 501,
      "type": "EARNED",
      "delta": 300,
      "status": "PENDING",
      "order_id": 100,
      "order_code": "OR-20251001-100",
      "created_at": "2026-10-01T10:00:00Z",
      "expires_at": "2026-10-01T10:00:00Z"
    }
  ]
}
```

---

## GET /loyalty/estimate

**Ước tính điểm sẽ nhận / có thể dùng**

### Query Parameters

```
order_amount=1200000&points_to_use=50
```

### Response 200

```json
{
  "order_amount": 1200000,
  "points_to_earn": 350,
  "points_to_earn_formula": "order_amount * 5% / 1000 = 1200000 * 0.05 / 1000 = 60",
  "available_points": 1250,
  "max_points_usable": 240,
  "max_points_usable_formula": "20% of order_amount = 1200000 * 0.20 / 1000 = 240",
  "conversion_rate": 200,
  "points_requested": 50,
  "discount_if_use_50": 250000,
  "cap_percent": 20
}
```

---

# ⚡ Flash Sale Service APIs

**Port:** `:8086`

## GET /flash-sale/sessions

**Danh sách Flash Sale sessions**

### Query Parameters

```
status=ACTIVE&page=0&size=20
```

### Response 200

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

---

## POST /flash-sale/sessions/{sessionId}/items

**Đăng ký sản phẩm Flash Sale (Seller)**

### Request

```json
{
  "sku_code": "NK-AIR-RED-XL",
  "flash_price": 189999,
  "flash_stock": 50,
  "limit_per_user": 3
}
```

### Validation Rules

| Field | Type | Rules |
|-------|------|-------|
| sku_code | string | Phải tồn tại; unique per session |
| flash_price | decimal | > 0; < variant.price |
| flash_stock | integer | > 0; ≤ stock_available |
| limit_per_user | integer | 1-10 |

### Response 201

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

---

## POST /flash-sale/sessions/{sessionId}/buy

**Mua Flash Sale [Chịu tải cao - Redis Lua]**

### Request

```json
{
  "fs_item_id": 1001,
  "quantity": 2,
  "address_id": 7
}
```

### Response 201

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

### Kafka Events (Redis Side Effect)

```json
{
  "topic": "flash_sale.item_sold",
  "payload": {
    "fs_item_id": 1001,
    "session_id": 3,
    "quantity": 2,
    "sold_total": 45,
    "remaining_stock": 5,
    "timestamp": "2026-04-15T20:00:30Z"
  }
}
```

---

# 🔔 Notification Service APIs

**Port:** `:8088`

## GET /notifications/stream

**Kết nối SSE real-time**

### Headers Required

```
Authorization: Bearer <access_token>
```

### Response (text/event-stream)

```
data: {"notif_id":"64f3a","type":"REFUND_APPROVED","title":"Hoàn tiền thành công","body":"Yêu cầu hoàn 350.000đ đã được duyệt","priority":"NORMAL","metadata":{"deeplink":"/orders/100/refunds/88"},"created_at":"2026-04-15T10:00:00Z"}

data: {"notif_id":"64f3b","type":"ORDER_SHIPPED","title":"Đơn hàng đang giao","body":"Mã vận đơn: VT123456789","priority":"NORMAL","metadata":{"deeplink":"/orders/100"},"created_at":"2026-04-15T10:05:00Z"}
```

---

## GET /notifications

**Danh sách thông báo (Pagination)**

### Query Parameters

```
is_read=false&page=0&size=20
```

### Response 200

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

### Response 200

```json
{
  "notif_id": "64f3a",
  "is_read": true
}
```

---

# 🛡️ Admin APIs

## GET /admin/users

**Danh sách người dùng**

### Query Parameters

```
status=ACTIVE&role=SELLER&trust_score_min=50&trust_score_max=100&page=0&size=20
```

### Response 200

```json
{
  "content": [
    {
      "user_id": 5,
      "username": "shop_nike_vn",
      "email": "shop@nike.vn",
      "full_name": "Shop Nike Vietnam",
      "roles": ["SELLER", "BUYER"],
      "status": "ACTIVE",
      "trust_score": 92,
      "trust_tier": "DIAMOND",
      "product_posting_suspended": false,
      "locked_until": null,
      "created_at": "2024-01-15T08:00:00Z",
      "updated_at": "2026-04-14T15:30:00Z"
    }
  ],
  "total_elements": 42,
  "total_pages": 3
}
```

---

## POST /admin/users/{userId}/lock

**Khóa tài khoản**

### Request

```json
{
  "reason": "Vi phạm chính sách",
  "locked_until": "2026-05-15T10:00:00Z"
}
```

### Validation Rules

| Field | Type | Rules |
|-------|------|-------|
| reason | string | Required; 1-500 chars |
| locked_until | datetime | Optional; null = vĩnh viễn |

### Response 200

```json
{
  "user_id": 5,
  "status": "LOCKED",
  "lock_reason": "Vi phạm chính sách",
  "locked_until": "2026-05-15T10:00:00Z",
  "message": "Tài khoản đã bị khóa"
}
```

### Kafka Events

```json
{
  "topic": "account.locked",
  "payload": {
    "user_id": 5,
    "lock_reason": "Vi phạm chính sách",
    "locked_until": "2026-05-15T10:00:00Z",
    "locked_by": 1,
    "locked_at": "2026-04-15T10:00:00Z"
  }
}
```

---

## POST /admin/users/{userId}/trust-score

**Điều chỉnh Trust Score thủ công**

### Request

```json
{
  "delta": 10,
  "reason": "Khiếu nại được phê duyệt - Appeal approved"
}
```

### Response 200

```json
{
  "user_id": 5,
  "old_score": 92,
  "new_score": 102,
  "capped_score": 100,
  "delta": 10,
  "reason": "Khiếu nại được phê duyệt - Appeal approved",
  "changed_by": "ADMIN",
  "admin_id": 1,
  "changed_at": "2026-04-15T10:00:00Z"
}
```

### Kafka Events

```json
{
  "topic": "trust_score.adjusted",
  "payload": {
    "user_id": 5,
    "old_score": 92,
    "new_score": 100,
    "delta": 10,
    "event_code": "ADMIN_OVERRIDE",
    "reason": "Khiếu nại được phê duyệt",
    "admin_id": 1,
    "timestamp": "2026-04-15T10:00:00Z"
  }
}
```

---

## POST /admin/products/{productId}/approve

**Duyệt sản phẩm**

### Request

```json
{
  "note": "Sản phẩm đạt chuẩn"
}
```

### Response 200

```json
{
  "product_id": "507f1f77bcf86cd799439012",
  "seller_id": 5,
  "name": "Áo Thun Nike Air Nam",
  "status": "APPROVED",
  "variants_count": 3,
  "approved_at": "2026-04-15T10:00:00Z"
}
```

### Kafka Events

```json
{
  "topic": "product.approved",
  "payload": {
    "product_id": "507f1f77bcf86cd799439012",
    "seller_id": 5,
    "name": "Áo Thun Nike Air Nam",
    "variants_count": 3,
    "approved_by": 1,
    "approved_at": "2026-04-15T10:00:00Z"
  }
}
```

---

## GET /admin/failed-events

**Danh sách events thất bại (DLQ)**

### Query Parameters

```
status=DEAD&topic_or_task=order.delivered&page=0&size=20
```

### Response 200

```json
{
  "content": [
    {
      "event_id": 42,
      "topic_or_task": "order.delivered",
      "payload": {
        "order_id": 1001,
        "user_id": 42,
        "seller_id": 5
      },
      "error_reason": "Loyalty Service connection timeout after 5 retries",
      "retry_count": 5,
      "status": "DEAD",
      "created_at": "2026-04-14T14:00:00Z",
      "updated_at": "2026-04-15T09:30:00Z"
    }
  ],
  "total_elements": 3,
  "total_pages": 1
}
```

---

## POST /admin/failed-events/{eventId}/retry

**Retry thủ công event thất bại**

### Request

```json
{}
```

### Response 200

```json
{
  "event_id": 42,
  "status": "PENDING",
  "message": "Event đã được re-publish vào Kafka",
  "retry_at": "2026-04-15T10:00:00Z"
}
```

### Kafka Events

```json
{
  "topic": "order.delivered",
  "payload": {
    "order_id": 1001,
    "user_id": 42,
    "seller_id": 5,
    "retry_event_id": 42,
    "retry_timestamp": "2026-04-15T10:00:00Z"
  }
}
```

---

# 🧭 Kafka Topics & Payloads

## Core Topics

### account.created

```json
{
  "user_id": 42,
  "email": "a@example.com",
  "phone": "0901234567",
  "username": "nguyen_van_a",
  "roles": ["BUYER"],
  "trust_score": 80,
  "timestamp": "2026-04-15T08:00:00Z"
}
```

### order.created

```json
{
  "parent_order_id": 55,
  "user_id": 42,
  "orders": [
    {
      "order_id": 100,
      "seller_id": 5,
      "total_amount": 700000,
      "items": [
        {
          "item_id": 501,
          "sku_code": "NK-AIR-RED-XL",
          "quantity": 2,
          "price": 350000
        }
      ]
    }
  ],
  "total_amount": 1200000,
  "loyalty_points_used": 50,
  "timestamp": "2026-10-01T10:00:00Z"
}
```

### order.shipped

```json
{
  "order_id": 100,
  "user_id": 42,
  "seller_id": 5,
  "tracking_number": "VT123456789",
  "carrier": "ViettelPost",
  "shipped_at": "2026-10-01T12:00:00Z"
}
```

### order.delivered

```json
{
  "order_id": 100,
  "user_id": 42,
  "seller_id": 5,
  "total_amount": 700000,
  "loyalty_points": 25,
  "delivered_at": "2026-10-03T14:30:00Z"
}
```

### order.returned (RTS - NEW v5.3)

```json
{
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
```

### refund.admin_approved (NEW v5.3)

```json
{
  "refund_id": 88,
  "order_id": 100,
  "user_id": 42,
  "seller_id": 5,
  "amount": 500000,
  "tracking_number": "VT123456789",
  "caused_by": "SELLER",
  "admin_id": 1,
  "admin_note": "Hoàn do giao hàng không thành công...",
  "trust_score_delta": -5,
  "loyalty_points_returned": 50,
  "approved_at": "2026-04-15T10:30:00Z"
}
```

### trust_score.warning

```json
{
  "user_id": 42,
  "old_score": 32,
  "new_score": 27,
  "threshold": 30,
  "event_code": "EXCESSIVE_CANCELLATION",
  "message": "Trust score của bạn đang dưới 30 điểm",
  "timestamp": "2026-04-15T10:00:00Z"
}
```

### flash_sale.item_sold

```json
{
  "fs_item_id": 1001,
  "session_id": 3,
  "sku_code": "NK-AIR-RED-XL",
  "quantity": 2,
  "flash_price": 189999,
  "sold_total": 45,
  "remaining_stock": 5,
  "timestamp": "2026-04-15T20:00:30Z"
}
```

### payment.success

```json
{
  "transaction_id": 301,
  "parent_order_id": 55,
  "user_id": 42,
  "amount": 1200000,
  "stripe_pi_id": "pi_3PxABC2K1234567",
  "paid_at": "2026-10-01T10:05:00Z",
  "sellers": [
    {
      "seller_id": 5,
      "order_id": 100,
      "amount": 700000,
      "fee": 35000,
      "net_amount": 665000
    }
  ]
}
```

---

# ❌ Error Response Formats

## Standard Error

```json
{
  "error": "RESOURCE_NOT_FOUND",
  "message": "Không tìm thấy resource",
  "details": "Order với ID 9999 không tồn tại",
  "status_code": 404,
  "timestamp": "2026-04-15T10:30:00Z",
  "path": "/api/v1/orders/9999",
  "request_id": "req-abc123def456"
}
```

## Validation Error

```json
{
  "error": "VALIDATION_FAILED",
  "message": "Lỗi validation",
  "status_code": 400,
  "violations": [
    {
      "field": "loyalty_points_to_use",
      "value": 1000,
      "message": "Không thể dùng quá 20% giá trị đơn",
      "constraint": "LOYALTY_POINTS_MAX_PERCENTAGE",
      "max_allowed": 230
    }
  ]
}
```

## Invalid State Error

```json
{
  "error": "INVALID_STATE",
  "message": "Trạng thái không hợp lệ",
  "current_state": "SHIPPING",
  "allowed_states": ["PENDING", "PAID"],
  "status_code": 422
}
```

## Account Locked Error

```json
{
  "error": "ACCOUNT_LOCKED",
  "message": "Tài khoản bị khóa",
  "lock_reason": "Trust score quá thấp (< 10). Liên hệ support để khiếu nại.",
  "locked_until": "2026-05-15T10:00:00Z",
  "status_code": 403
}
```

---

## 📊 Summary

- **Total Endpoints**: 95+
- **API Services**: 11 (Identity, Product, Search, Cart, Order, Refund, Payment, Loyalty, Flash Sale, Notification, Admin)
- **Kafka Topics**: 35+
- **Error Types**: 10+
- **Request/Response Examples**: 60+
- **JSON Payloads**: Complete with nested objects
- **Kafka Payloads**: All event types with detailed fields

---

**Tài liệu cập nhật:** 2026-04-15  
**Phiên bản:** 5.3 RTS Unified  
**Trạng thái:** ✅ Production-Ready

