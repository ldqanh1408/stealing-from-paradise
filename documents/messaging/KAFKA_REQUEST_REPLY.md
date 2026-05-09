# Kafka Request-Reply Pattern

**Project**: stealing-from-paradise
**Generated**: 2026-05-10
**Source**: docs/messaging/11_KAFKA_REQUEST_REPLY.md (v5.4)
**Stable ID Prefix**: `KAFKA-RR-`

---

## Overview

The marketplace uses **Kafka Request-Reply** for inter-service queries that need an immediate synchronous-like response while maintaining loose coupling. Instead of REST/gRPC calls, a requester publishes to a `.request` topic and waits for a response on a `.response` topic with a matching `correlationId`.

| Metric | Value |
|--------|-------|
| Total Request-Reply pairs | 6 pairs (12 topics) |
| Default timeout | 5-10 seconds |
| Correlation mechanism | UUID per request |
| Status | MVP workaround (gRPC planned for future) |

**Note:** This pattern is an MVP workaround replacing gRPC temporarily. If gRPC is added later, these topics will be removed.

---

## Mechanism

```
Service A (Requester)                    Service B (Responder)
      |                                        |
      |- publish to {topic}.request --------->|
      |   payload: { correlationId, ...data }  |
      |                                        | process request
      |                                        |
      |<- publish to {topic}.response ---------|
      |   payload: { correlationId, ...result}  |
      |                                        |
      | match correlationId -> unblock caller  |
```

1. **Requester** publishes a message to the `.request` topic with a `correlationId` (UUID)
2. **Requester** blocks the thread (or uses `CompletableFuture`) waiting for a message with the same `correlationId` on the `.response` topic
3. **Responder** consumes `.request`, processes it, and publishes the result to `.response`
4. **Timeout** is typically 5-10 seconds; exceeding it throws an exception and rolls back

---

## All Request-Reply Pairs

| # | Request Topic | Response Topic | Requester | Responder | Purpose |
|---|--------------|----------------|-----------|-----------|---------|
| 1 | `cart.product_info.request` | `cart.product_info.response` | Cart (Product internal) | Product catalog | Get product info for cart display |
| 2 | `order.stock_check.request` | `order.stock_check.response` | Order | Product | Validate stock before checkout |
| 3 | `order.payment_status.request` | `order.payment_status.response` | Order | Payment | Check payment status of an order |
| 4 | `order.cart_items.request` | `order.cart_items.response` | Order | Product | Fetch cart items during checkout |
| 5 | `order.address.request` | `order.address.response` | Order | Identity | Get buyer shipping address |
| 6 | `order.refunds.request` | `order.refunds.response` | Order | Payment | Get refund info for an order |

---

## Pair Details

### 1. Cart Product Info (`cart.product_info`)

**Requester**: Cart module (product-service, when user views cart)
**Responder**: Product-service catalog

```
Request:
{
  "correlationId": "uuid",
  "productId": "prod_abc123",
  "skuId": "sku_xyz"
}

Response:
{
  "correlationId": "uuid",
  "productId": "prod_abc123",
  "name": "Ao thun trang",
  "price": 150000,
  "imageUrl": "https://cdn.../img.jpg",
  "available": true
}
```

---

### 2. Stock Check (`order.stock_check`)

**Requester**: Order-service (during checkout, before creating order)
**Responder**: Product-service inventory module

```
Request:
{
  "correlationId": "uuid",
  "items": [
    { "skuId": "sku_xyz", "quantity": 2 },
    { "skuId": "sku_abc", "quantity": 1 }
  ]
}

Response:
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

### 3. Payment Status (`order.payment_status`)

**Requester**: Order-service (to sync order status with payment)
**Responder**: Payment-service

```
Request:
{
  "correlationId": "uuid",
  "orderId": "ord_abc123",
  "paymentIntentId": "pi_stripe_xxx"
}

Response:
{
  "correlationId": "uuid",
  "orderId": "ord_abc123",
  "status": "SUCCESS|PENDING|FAILED",
  "amount": 450000,
  "paidAt": "2026-05-01T10:00:00Z"
}
```

---

### 4. Cart Items (`order.cart_items`)

**Requester**: Order-service (when user clicks "Place Order")
**Responder**: Product-service cart module

```
Request:
{
  "correlationId": "uuid",
  "userId": 42,
  "selectedItemIds": ["cart_item_1", "cart_item_2"]
}

Response:
{
  "correlationId": "uuid",
  "userId": 42,
  "items": [
    {
      "cartItemId": "cart_item_1",
      "productId": "prod_abc",
      "skuId": "sku_xyz",
      "productName": "Ao thun",
      "price": 150000,
      "quantity": 2,
      "sellerId": 10,
      "imageUrl": "https://..."
    }
  ]
}
```

---

### 5. Address (`order.address`)

**Requester**: Order-service (at checkout, get default shipping address)
**Responder**: Identity-service

```
Request:
{
  "correlationId": "uuid",
  "userId": 42,
  "addressId": 5
}
// addressId = null means "get default address"

Response:
{
  "correlationId": "uuid",
  "addressId": 5,
  "recipientName": "Nguyen Van A",
  "phone": "0901234567",
  "street": "123 Le Van Viet",
  "ward": "Phuong Hiep Phu",
  "district": "Quan 9",
  "city": "TP. Ho Chi Minh",
  "isDefault": true
}
```

---

### 6. Refunds (`order.refunds`)

**Requester**: Order-service (when user views order details, needs refund status)
**Responder**: Payment-service refund module

```
Request:
{
  "correlationId": "uuid",
  "orderId": "ord_abc123"
}

Response:
{
  "correlationId": "uuid",
  "orderId": "ord_abc123",
  "refunds": [
    {
      "refundId": "ref_001",
      "amount": 150000,
      "status": "PENDING|SUCCESS|REJECTED",
      "reason": "Hang bi loi",
      "createdAt": "2026-05-01T10:00:00Z"
    }
  ]
}
```

---

## Developer Guide

### Adding a New Request-Reply Pair

1. Declare both constants (request + response) in `KafkaTopics.java` (common-lib)
2. **Responder**: Implement `@KafkaListener` on `.request`, publish to `.response` with the same `correlationId`
3. **Requester**: Use `KafkaReplyingTemplate` or implement `CompletableFuture` + correlation map
4. Set a reasonable timeout (recommended: 5 seconds); throw exception and rollback on timeout

### When NOT to Use Request-Reply

- Data is not needed immediately (use fire-and-forget event instead)
- Result only needs eventual consistency
- Fan-out scenarios (1 producer -> many consumers)

### gRPC Migration Path (Future)

When migrating to gRPC:
- Replace each request-reply pair with a gRPC service definition
- Remove the corresponding topics from `KafkaTopics.java`
- Keep request/response payload structures compatible

---

## Cross-References

| Document | Path |
|----------|------|
| Main Kafka Catalog | [KAFKA_CATALOG.md](KAFKA_CATALOG.md) |
| Checkout BR | [br-checkout.md](../business-rules/order-service/br-checkout.md) |
| Payment BR | [br-payment.md](../business-rules/payment-service/br-payment.md) |
| Catalog BR | [br-catalog.md](../business-rules/product-service/br-catalog.md) |
| Auth BR | [br-auth.md](../business-rules/identity-service/br-auth.md) |

---

*Generated: 2026-05-10 | Sources: KAFKA_EVENTS.md (v5.5), 11_KAFKA_REQUEST_REPLY.md (v5.4)*
