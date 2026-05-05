# Kafka Request-Reply Pattern

**Version**: v5.4 | **Last Updated**: 2026-05-04  
**Defined in**: `common-lib` → `KafkaTopics.java`

---

## 🔗 Per-Service Documentation

Mỗi service có tài liệu Kafka riêng với chi tiết request-reply cycle:

| Service | Kafka Doc | Request-Reply Role |
|---------|-----------|-------------------|
| Identity Service (8081) | 🔗 [identity-service/KAFKA_EVENTS.md](../services/identity-service/KAFKA_EVENTS.md) | Responder: `order.address` |
| Product Service (8090) | 🔗 [product-service/KAFKA_EVENTS.md](../services/product-service/KAFKA_EVENTS.md) | Responder: `cart.product_info`, `order.stock_check`, `order.cart_items` |
| Order Service (8083) | 🔗 [order-service/KAFKA_EVENTS.md](../services/order-service/KAFKA_EVENTS.md) | Requester: 5 pairs |
| Payment Service (8082) | 🔗 [payment-service/KAFKA_EVENTS.md](../services/payment-service/KAFKA_EVENTS.md) | Responder: `order.payment_status`, `order.refunds` |

---

## Tại Sao Dùng Request-Reply?

Một số nghiệp vụ cần **kết quả ngay** (synchronous-like) nhưng vẫn muốn giữ loose coupling giữa các service — ví dụ Order Service cần kiểm tra tồn kho trước khi tạo đơn.

Thay vì dùng REST call trực tiếp (tạo tight coupling) hoặc gRPC (phức tạp hơn), hệ thống dùng **Kafka Request-Reply**: gửi message lên topic `.request`, đợi response trên topic `.response` với correlation ID.

> **Lưu ý**: Pattern này là **MVP workaround** thay thế gRPC tạm thời (ghi chú trong `KafkaTopics.java`). Nếu thêm gRPC sau này, các topic này sẽ bị xóa.

---

## Cơ Chế Hoạt Động

```
Service A (Requester)                    Service B (Responder)
      │                                        │
      │── publish to {topic}.request ─────────▶│
      │   payload: { correlationId, ...data }  │
      │                                        │ process
      │                                        │ request
      │◀─ publish to {topic}.response ─────────│
      │   payload: { correlationId, ...result} │
      │                                        │
      │ match correlationId → unblock caller   │
```

- **Requester** publish message lên `.request` topic, kèm `correlationId` (UUID)
- **Requester** block thread (hoặc dùng CompletableFuture) chờ message có cùng `correlationId` trên `.response` topic
- **Responder** consume `.request`, xử lý, publish result lên `.response`
- Timeout thường là **5-10 giây** — nếu quá thời gian → exception

---

## Tất Cả Request-Reply Topics

### 1. Cart ↔ Product Service

🔗 **Product Service Kafka**: [product-service/KAFKA_EVENTS.md → Request-Reply #1](../services/product-service/KAFKA_EVENTS.md#1-cartproduct_info--cart--product-catalog)

| Topic | Direction | Purpose |
|-------|-----------|---------|
| `cart.product_info.request` | Cart → Product | Lấy thông tin sản phẩm (tên, giá, ảnh) để hiển thị trong giỏ hàng |
| `cart.product_info.response` | Product → Cart | Trả về ProductInfo |

**Requester**: Cart (product-service, khi user view cart)  
**Responder**: Product-service catalog

```json
// Request payload
{
  "correlationId": "uuid",
  "productId": "prod_abc123",
  "skuId": "sku_xyz"
}

// Response payload
{
  "correlationId": "uuid",
  "productId": "prod_abc123",
  "name": "Áo thun trắng",
  "price": 150000,
  "imageUrl": "https://cdn.../img.jpg",
  "available": true
}
```

---

### 2. Order ↔ Product Service (Stock Check)

🔗 **Order Service Kafka**: [order-service/KAFKA_EVENTS.md → Request-Reply #1](../services/order-service/KAFKA_EVENTS.md#1-orderstock_check--order--product-inventory)  
🔗 **Product Service Kafka**: [product-service/KAFKA_EVENTS.md → Request-Reply #2](../services/product-service/KAFKA_EVENTS.md#2-orderstock_check--order--product-inventory)

| Topic | Direction | Purpose |
|-------|-----------|---------|
| `order.stock_check.request` | Order → Product | Kiểm tra tồn kho trước khi tạo đơn |
| `order.stock_check.response` | Product → Order | Trả về available quantity |

**Requester**: Order-service (trong checkout flow, trước khi tạo order)  
**Responder**: Product-service inventory module

```json
// Request payload
{
  "correlationId": "uuid",
  "items": [
    { "skuId": "sku_xyz", "quantity": 2 },
    { "skuId": "sku_abc", "quantity": 1 }
  ]
}

// Response payload
{
  "correlationId": "uuid",
  "allAvailable": true,
  "results": [
    { "skuId": "sku_xyz", "requested": 2, "available": 15, "sufficient": true },
    { "skuId": "sku_abc", "requested": 1, "available": 0, "sufficient": false }
  ]
}
```

---

### 3. Order ↔ Payment Service (Payment Status)

🔗 **Order Service Kafka**: [order-service/KAFKA_EVENTS.md → Request-Reply #2](../services/order-service/KAFKA_EVENTS.md#2-orderpayment_status--order--payment)  
🔗 **Payment Service Kafka**: [payment-service/KAFKA_EVENTS.md → Request-Reply #1](../services/payment-service/KAFKA_EVENTS.md#1-orderpayment_status--order--payment)

| Topic | Direction | Purpose |
|-------|-----------|---------|
| `order.payment_status.request` | Order → Payment | Kiểm tra trạng thái thanh toán của một order |
| `order.payment_status.response` | Payment → Order | Trả về payment status |

**Requester**: Order-service (để sync trạng thái order với payment)  
**Responder**: Payment-service

```json
// Request payload
{
  "correlationId": "uuid",
  "orderId": "ord_abc123",
  "paymentIntentId": "pi_stripe_xxx"
}

// Response payload
{
  "correlationId": "uuid",
  "orderId": "ord_abc123",
  "status": "SUCCESS|PENDING|FAILED",
  "amount": 450000,
  "paidAt": "2026-05-01T10:00:00Z"
}
```

---

### 4. Order ↔ Product Service (Cart Items)

🔗 **Order Service Kafka**: [order-service/KAFKA_EVENTS.md → Request-Reply #3](../services/order-service/KAFKA_EVENTS.md#3-ordercart_items--order--product-cart)  
🔗 **Product Service Kafka**: [product-service/KAFKA_EVENTS.md → Request-Reply #3](../services/product-service/KAFKA_EVENTS.md#3-ordercart_items--order--product-cart)

| Topic | Direction | Purpose |
|-------|-----------|---------|
| `order.cart_items.request` | Order → Product | Lấy danh sách items trong cart của user để tạo order |
| `order.cart_items.response` | Product → Order | Trả về CartItems list |

**Requester**: Order-service (khi user nhấn "Đặt hàng")  
**Responder**: Product-service cart module

```json
// Request payload
{
  "correlationId": "uuid",
  "userId": 42,
  "selectedItemIds": ["cart_item_1", "cart_item_2"]
}

// Response payload
{
  "correlationId": "uuid",
  "userId": 42,
  "items": [
    {
      "cartItemId": "cart_item_1",
      "productId": "prod_abc",
      "skuId": "sku_xyz",
      "productName": "Áo thun",
      "price": 150000,
      "quantity": 2,
      "sellerId": 10,
      "imageUrl": "https://..."
    }
  ]
}
```

---

### 5. Order ↔ Identity Service (Address)

🔗 **Order Service Kafka**: [order-service/KAFKA_EVENTS.md → Request-Reply #4](../services/order-service/KAFKA_EVENTS.md#4-orderaddress--order--identity)  
🔗 **Identity Service Kafka**: [identity-service/KAFKA_EVENTS.md → Request-Reply](../services/identity-service/KAFKA_EVENTS.md#-request-reply)

| Topic | Direction | Purpose |
|-------|-----------|---------|
| `order.address.request` | Order → Identity | Lấy địa chỉ giao hàng của user |
| `order.address.response` | Identity → Order | Trả về Address |

**Requester**: Order-service (lúc checkout, lấy địa chỉ mặc định)  
**Responder**: Identity-service

```json
// Request payload
{
  "correlationId": "uuid",
  "userId": 42,
  "addressId": 5   // null = lấy địa chỉ mặc định
}

// Response payload
{
  "correlationId": "uuid",
  "addressId": 5,
  "recipientName": "Nguyễn Văn A",
  "phone": "0901234567",
  "street": "123 Lê Văn Việt",
  "ward": "Phường Hiệp Phú",
  "district": "Quận 9",
  "city": "TP. Hồ Chí Minh",
  "isDefault": true
}
```

---

### 6. Order ↔ Payment Service (Refunds)

🔗 **Order Service Kafka**: [order-service/KAFKA_EVENTS.md → Request-Reply #5](../services/order-service/KAFKA_EVENTS.md#5-orderrefunds--order--payment)  
🔗 **Payment Service Kafka**: [payment-service/KAFKA_EVENTS.md → Request-Reply #2](../services/payment-service/KAFKA_EVENTS.md#2-orderrefunds--order--payment)

| Topic | Direction | Purpose |
|-------|-----------|---------|
| `order.refunds.request` | Order → Payment | Lấy thông tin refunds liên quan đến order |
| `order.refunds.response` | Payment → Order | Trả về Refunds list |

**Requester**: Order-service (khi user xem chi tiết order, cần biết trạng thái refund)  
**Responder**: Payment-service refund module

```json
// Request payload
{
  "correlationId": "uuid",
  "orderId": "ord_abc123"
}

// Response payload
{
  "correlationId": "uuid",
  "orderId": "ord_abc123",
  "refunds": [
    {
      "refundId": "ref_001",
      "amount": 150000,
      "status": "PENDING|SUCCESS|REJECTED",
      "reason": "Hàng bị lỗi",
      "createdAt": "2026-05-01T10:00:00Z"
    }
  ]
}
```

---

## Tóm Tắt Tất Cả Topics

| Request Topic | Response Topic | Requester | Responder | Full Cycle Doc |
|--------------|----------------|-----------|-----------|----------------|
| `cart.product_info.request` | `cart.product_info.response` | Cart (product-svc) | Product catalog | 🔗 [Product → #1](../services/product-service/KAFKA_EVENTS.md#1-cartproduct_info--cart--product-catalog) |
| `order.stock_check.request` | `order.stock_check.response` | 🔗 [Order](../services/order-service/KAFKA_EVENTS.md) | 🔗 [Product](../services/product-service/KAFKA_EVENTS.md) | 🔗 [Order → #1](../services/order-service/KAFKA_EVENTS.md#1-orderstock_check--order--product-inventory) |
| `order.payment_status.request` | `order.payment_status.response` | 🔗 [Order](../services/order-service/KAFKA_EVENTS.md) | 🔗 [Payment](../services/payment-service/KAFKA_EVENTS.md) | 🔗 [Order → #2](../services/order-service/KAFKA_EVENTS.md#2-orderpayment_status--order--payment) |
| `order.cart_items.request` | `order.cart_items.response` | 🔗 [Order](../services/order-service/KAFKA_EVENTS.md) | 🔗 [Product](../services/product-service/KAFKA_EVENTS.md) | 🔗 [Order → #3](../services/order-service/KAFKA_EVENTS.md#3-ordercart_items--order--product-cart) |
| `order.address.request` | `order.address.response` | 🔗 [Order](../services/order-service/KAFKA_EVENTS.md) | 🔗 [Identity](../services/identity-service/KAFKA_EVENTS.md) | 🔗 [Order → #4](../services/order-service/KAFKA_EVENTS.md#4-orderaddress--order--identity) |
| `order.refunds.request` | `order.refunds.response` | 🔗 [Order](../services/order-service/KAFKA_EVENTS.md) | 🔗 [Payment](../services/payment-service/KAFKA_EVENTS.md) | 🔗 [Order → #5](../services/order-service/KAFKA_EVENTS.md#5-orderrefunds--order--payment) |

---

## Lưu Ý Khi Phát Triển

### Thêm request-reply topic mới
1. Khai báo cả 2 hằng số (request + response) trong `KafkaTopics.java`
2. Responder: implement `@KafkaListener` trên `.request`, publish lên `.response` với cùng `correlationId`
3. Requester: dùng `KafkaReplyingTemplate` hoặc tự implement `CompletableFuture` + correlation map
4. Đặt timeout hợp lý (recommend: 5s) — nếu quá timeout, throw exception và rollback

### Không dùng request-reply khi
- Data không cần thiết ngay (dùng fire-and-forget event thay thế)
- Kết quả chỉ cần eventual consistency
- Fan-out (1 producer → nhiều consumer)

### Khi chuyển sang gRPC (tương lai)
Thay thế từng cặp request-reply bằng gRPC service definition. Xóa các topic tương ứng khỏi `KafkaTopics.java` sau khi migration xong.

---

## 🔗 Related Documents

| Document | Link |
|----------|------|
| Main Kafka Index Catalog | 🔗 [KAFKA_EVENTS.md](KAFKA_EVENTS.md) |
| Identity Service Kafka | 🔗 [identity-service/KAFKA_EVENTS.md](../services/identity-service/KAFKA_EVENTS.md) |
| Product Service Kafka | 🔗 [product-service/KAFKA_EVENTS.md](../services/product-service/KAFKA_EVENTS.md) |
| Order Service Kafka | 🔗 [order-service/KAFKA_EVENTS.md](../services/order-service/KAFKA_EVENTS.md) |
| Payment Service Kafka | 🔗 [payment-service/KAFKA_EVENTS.md](../services/payment-service/KAFKA_EVENTS.md) |

---

*Last Updated: 2026-05-04 · v5.4*
