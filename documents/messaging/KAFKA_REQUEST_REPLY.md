# Kafka Request-Reply Pattern

**Project**: stealing-from-paradise
**Generated**: 2026-05-10
**Source**: documents/messaging/KAFKA_REQUEST_REPLY.md (v5.5)
**Stable ID Prefix**: `KAFKA-RR-`

---

## Overview

The marketplace uses **Kafka Request-Reply** for inter-service queries that need an immediate synchronous-like response while maintaining loose coupling. Instead of REST/gRPC calls, a requester publishes to a `.request` topic and waits for a response on a `.response` topic with a matching `correlation_id`.

| Metric | Value |
|--------|-------|
| Total Request-Reply pairs | 8 pairs (16 topics) |
| Default timeout | 5-30 seconds, depending on payload size |
| Correlation mechanism | UUID per request |
| Status | MVP workaround (gRPC planned for future) |

**Note:** This pattern is an MVP workaround replacing gRPC temporarily. If gRPC is added later, these topics will be removed.

---

## Mechanism

```
Service A (Requester)                    Service B (Responder)
      |                                        |
      |- publish to {topic}.request --------->|
      |   payload: { correlation_id, ...data }  |
      |                                        | process request
      |                                        |
      |<- publish to {topic}.response ---------|
      |   payload: { correlation_id, ...result}  |
      |                                        |
      | match correlation_id -> unblock caller  |
```

1. **Requester** publishes a message to the `.request` topic with a `correlation_id` (UUID)
2. **Requester** blocks the thread (or uses `CompletableFuture`) waiting for a message with the same `correlation_id` on the `.response` topic
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
| 7 | `order.refund_presigned_url.request` | `order.refund_presigned_url.response` | Order | Payment | Get presigned URL for evidence upload |
| 8 | `search.index_data.request` | `search.index_data.response` | Search | Product | Fetch SKU documents and field snapshots for search indexing |

---

## Pair Details

### 1. Cart Product Info (`cart.product_info`)

**Requester**: Cart module (product-service, when user views cart)
**Responder**: Product-service catalog

```
Request:
{
  "correlation_id": "uuid",
  "product_id": "prod_abc123",
  "sku_id": "sku_xyz"
}

Response:
{
  "correlation_id": "uuid",
  "product_id": "prod_abc123",
  "name": "Ao thun trang",
  "price": 150000,
  "image_url": "https://cdn.../img.jpg",
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
  "correlation_id": "uuid",
  "items": [
    { "sku_id": "sku_xyz", "quantity": 2 },
    { "sku_id": "sku_abc", "quantity": 1 }
  ]
}

Response:
{
  "correlation_id": "uuid",
  "all_available": true,
  "results": [
    { "sku_id": "sku_xyz", "requested": 2, "available": 15, "sufficient": true },
    { "sku_id": "sku_abc", "requested": 1, "available": 0, "sufficient": false }
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
  "correlation_id": "uuid",
  "order_id": "ord_abc123",
  "payment_intent_id": "pi_stripe_xxx"
}

Response:
{
  "correlation_id": "uuid",
  "order_id": "ord_abc123",
  "status": "SUCCESS|PENDING|FAILED",
  "amount": 450000,
  "paid_at": "2026-05-01T10:00:00Z"
}
```

---

### 4. Cart Items (`order.cart_items`)

**Requester**: Order-service (when user clicks "Place Order")
**Responder**: Product-service cart module

```
Request:
{
  "correlation_id": "uuid",
  "user_id": 42,
  "selected_item_ids": ["cart_item_1", "cart_item_2"]
}

Response:
{
  "correlation_id": "uuid",
  "user_id": 42,
  "items": [
    {
      "cart_item_id": "cart_item_1",
      "product_id": "prod_abc",
      "sku_id": "sku_xyz",
      "product_name": "Ao thun",
      "price": 150000,
      "quantity": 2,
      "seller_id": 10,
      "image_url": "https://..."
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
  "correlation_id": "uuid",
  "user_id": 42,
  "address_id": 5
}
// address_id = null means "get default address"

Response:
{
  "correlation_id": "uuid",
  "address_id": 5,
  "recipient_name": "Nguyen Van A",
  "phone": "0901234567",
  "street": "123 Le Van Viet",
  "ward": "Phuong Hiep Phu",
  "district": "Quan 9",
  "city": "TP. Ho Chi Minh",
  "is_default": true
}
```

---

### 6. Refunds (`order.refunds`)

**Requester**: Order-service (when user views order details, needs refund status)
**Responder**: Payment-service refund module

```
Request:
{
  "correlation_id": "uuid",
  "order_id": "ord_abc123"
}

Response:
{
  "correlation_id": "uuid",
  "order_id": "ord_abc123",
  "refunds": [
    {
      "refund_id": "ref_001",
      "amount": 150000,
      "status": "PENDING|SUCCESS|REJECTED",
      "reason": "Hang bi loi",
      "created_at": "2026-05-01T10:00:00Z"
    }
  ]
}
```

---

### 7. Refund Presigned URL (`order.refund_presigned_url`)

**Requester**: Order-service (when buyer needs to upload refund evidence)
**Responder**: Payment-service

```
Request:
{
  "correlation_id": "uuid",
  "order_id": "ord_abc123",
  "refund_id": "ref_001",
  "file_name": "evidence_img1.jpg",
  "content_type": "image/jpeg"
}

Response:
{
  "correlation_id": "uuid",
  "presigned_url": "https://storage.googleapis.com/...",
  "object_key": "refunds/ref_001/evidence_img1.jpg",
  "expires_at": "2026-05-12T10:15:00Z"
}
```

---

### 8. Search Index Data (`search.index_data`)

**Requester**: Search-service (full reindex and event handlers)
**Responder**: Product-service catalog/indexing module
**Timeout**: 30 seconds (larger than checkout request-reply because full reindex pages can carry many SKU documents)

Request types:

| requestType | Purpose | Required fields |
|-------------|---------|-----------------|
| `ACTIVE_PRODUCTS_PAGE` | Fetch one page of marketplace-visible SKU documents for full reindex | `page`, `size` |
| `PRODUCT_SKU_DOCUMENTS` | Fetch all SKU documents for one activated product | `productId` |
| `PRODUCT_SEARCH_FIELDS` | Fetch product-level fields for `product.updated` | `productId` |
| `CATEGORY_SEARCH_FIELDS` | Fetch category fields for `category.updated` | `categoryId` |

```
Request:
{
  "correlationId": "uuid",
  "requestType": "ACTIVE_PRODUCTS_PAGE",
  "page": 0,
  "size": 100
}

Response:
{
  "correlationId": "uuid",
  "requestType": "ACTIVE_PRODUCTS_PAGE",
  "success": true,
  "documents": [
    {
      "skuId": "uuid",
      "productId": "uuid",
      "productName": "Ao thun",
      "categoryId": "uuid",
      "price": 150000,
      "stockStatus": "in_stock",
      "isActive": true
    }
  ],
  "page": 0,
  "size": 100,
  "totalElements": 1234,
  "hasNext": true
}
```

Failure response:

```
{
  "correlationId": "uuid",
  "requestType": "PRODUCT_SKU_DOCUMENTS",
  "success": false,
  "errorMessage": "productId is required"
}
```

---

## Developer Guide

### Adding a New Request-Reply Pair

1. Declare both constants (request + response) in `KafkaTopics.java` (common-lib)
2. **Responder**: Implement `@KafkaListener` on `.request`, publish to `.response` with the same `correlation_id`
3. **Requester**: Use `KafkaReplyingTemplate` or implement `CompletableFuture` + correlation map
4. Set a reasonable timeout (recommended: 5 seconds for small operational reads, up to 30 seconds for large page payloads); throw exception and rollback on timeout

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

*Generated: 2026-05-10 | Sources: KAFKA_EVENTS.md (v5.5), KAFKA_REQUEST_REPLY.md (v5.5)*
