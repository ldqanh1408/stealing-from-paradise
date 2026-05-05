# 🧭 Kafka Events & Topics Catalog

**Project**: stealing-from-paradise Marketplace  
**Version**: v5.5  
**Last Updated**: 2026-05-05

> Tài liệu này là **index catalog** — mỗi service có file riêng với chi tiết đầy đủ.  
> Xem per-service docs để biết payload, consumer actions và request-reply cycles.

---

## 📊 Overview

| Metric | Value |
|--------|-------|
| **Total Topics** | 47 (35 event topics + 12 request-reply topics) |
| **Event Producers** | 8 services (Identity, Product, Order, Payment, Flash Sale, Admin, Worker) |
| **Event Consumers** | 8 services |
| **Max Consumers per Topic** | 4+ (Notification Service) |
| **Retention Policy** | 7–365 days (depends on topic type) |
| **Request-Reply Topics** | 12 topics (6 pairs) — 🔗 [11_KAFKA_REQUEST_REPLY.md](11_KAFKA_REQUEST_REPLY.md) |

---

## 📁 Per-Service Kafka Docs

| # | Service | Produces | Consumes | Request-Reply | Link |
|---|---------|----------|----------|---------------|------|
| 1 | **Identity** (8081) | account.*, appeal.*, loyalty.*, seller.*, trust_score.* | order.delivered, order.cancelled, refund.admin_approved, seller.order_cancelled, stripe.account_suspended | Responder: order.address | 🔗 [identity-service/KAFKA_EVENTS.md](identity-service/KAFKA_EVENTS.md) |
| 2 | **Product + Cart + Inventory** (8090) | product.*, category.*, inventory.*, product.pending_review | order.created, order.cancelled, order.returned, order.checkout_completed, order.auto_cancelled, flash_sale.* | Responder: cart.product_info, order.stock_check, order.cart_items | 🔗 [product-service/KAFKA_EVENTS.md](product-service/KAFKA_EVENTS.md) |
| 3 | **Search** (8091) | — (consumer-only) | product.*, category.*, inventory.*, account.locked, order.created | — | 🔗 [search-service/KAFKA_EVENTS.md](search-service/KAFKA_EVENTS.md) |
| 4 | **Order** (8083) | order.*, seller.order_cancelled | payment.*, refund.stripe_auto, refund.rts_completed, stripe.transfer.reversed | Requester: order.stock_check, order.payment_status, order.cart_items, order.address, order.refunds | 🔗 [order-service/KAFKA_EVENTS.md](order-service/KAFKA_EVENTS.md) |
| 5 | **Payment + Refund** (8082) | payment.*, refund.*, stripe.* | order.returned | Responder: order.payment_status, order.refunds | 🔗 [payment-service/KAFKA_EVENTS.md](payment-service/KAFKA_EVENTS.md) |
| 6 | **Flash Sale** (8085) | flash_sale.* | — | — | 🔗 [flashsale-service/KAFKA_EVENTS.md](flashsale-service/KAFKA_EVENTS.md) |
| 7 | **Notification** (8092) | — (consumer-only) | 20+ topics từ tất cả services (SSE + MongoDB) | — | 🔗 [notification-service/KAFKA_EVENTS.md](notification-service/KAFKA_EVENTS.md) |
| 8 | **Admin** (routes trong services) | product.approved, product.rejected, product.auto_hidden | — | — | 🔗 [admin-service/KAFKA_EVENTS.md](admin-service/KAFKA_EVENTS.md) |
| 9 | **Worker** (8086) | flash_sale.reminder | order.created, payment.success | — | 🔗 [worker-service/KAFKA_EVENTS.md](worker-service/KAFKA_EVENTS.md) |
| 10 | **AI Chat** (8093) | ai_chat.*, tool_call.* | — | — | 🔗 [ai-chat-service/KAFKA_EVENTS.md](ai-chat-service/KAFKA_EVENTS.md) |

---

## 🔄 Event Flow Chains

### Shopping Flow
```
[Order] order.created ──→ [Product] lock stock
                       ──→ [Search] update sold count
[Payment] payment.success ──→ [Order] mark PAID
[Order] order.shipped ──→ [Notification] tracking update
[Order] order.delivered ──→ [Identity] trust score +5, confirm loyalty points
                        ──→ [Notification] delivery confirmation
```

### Refund Flow
```
[Payment] refund.requested ──→ [Notification] notify seller
[Payment] refund.admin_approved ──→ [Identity] return points, adjust trust score
                                ──→ [Notification] notify buyer + seller
[Payment] refund.stripe_auto ──→ [Order] mark refunded
                             ──→ [Identity] deduct points
```

### Flash Sale Flow
```
[Worker] flash_sale.reminder ──→ [Notification] SSE to subscribers
[FlashSale] flash_sale.session_started ──→ [Notification] open session
[FlashSale] flash_sale.item_sold ──→ [Product] update inventory
[FlashSale] flash_sale.session_ended ──→ [Notification] close session
                                     ──→ [Product] clear expired cart items
```

### Account Flow
```
[Identity] account.locked ──→ [Notification] lock notice
                         ──→ [Search] hide seller products
[Identity] account.unlocked ──→ [Notification] unlock notice
[Identity] seller.posting_suspended ──→ [Notification] suspension notice
```

### Trust Score Impact Chain
```
[Order] order.cancelled (excessive) → [Identity] account.auto_locked → [Notification] urgent notice
[Payment] refund.admin_approved (caused_by=SELLER) → [Identity] seller trust score -5
[Order] order.delivered → [Identity] seller trust score +5
```

---

## 🔄 Request-Reply Topics (12 topics — 6 pairs)

Xem đầy đủ: 🔗 [11_KAFKA_REQUEST_REPLY.md](11_KAFKA_REQUEST_REPLY.md)

| Request Topic | Response Topic | Requester | Responder | Cycle Doc |
|--------------|----------------|-----------|-----------|-----------|
| `cart.product_info.request` | `cart.product_info.response` | Cart (Product internal) | Product catalog | 🔗 [Product → Request-Reply](product-service/KAFKA_EVENTS.md#1-cartproduct_info--cart--product-catalog) |
| `order.stock_check.request` | `order.stock_check.response` | 🔗 [Order](order-service/KAFKA_EVENTS.md) | 🔗 [Product](product-service/KAFKA_EVENTS.md) | 🔗 [Order → #1](order-service/KAFKA_EVENTS.md#1-orderstock_check--order--product-inventory) |
| `order.payment_status.request` | `order.payment_status.response` | 🔗 [Order](order-service/KAFKA_EVENTS.md) | 🔗 [Payment](payment-service/KAFKA_EVENTS.md) | 🔗 [Order → #2](order-service/KAFKA_EVENTS.md#2-orderpayment_status--order--payment) |
| `order.cart_items.request` | `order.cart_items.response` | 🔗 [Order](order-service/KAFKA_EVENTS.md) | 🔗 [Product](product-service/KAFKA_EVENTS.md) | 🔗 [Order → #3](order-service/KAFKA_EVENTS.md#3-ordercart_items--order--product-cart) |
| `order.address.request` | `order.address.response` | 🔗 [Order](order-service/KAFKA_EVENTS.md) | 🔗 [Identity](identity-service/KAFKA_EVENTS.md) | 🔗 [Order → #4](order-service/KAFKA_EVENTS.md#4-orderaddress--order--identity) |
| `order.refunds.request` | `order.refunds.response` | 🔗 [Order](order-service/KAFKA_EVENTS.md) | 🔗 [Payment](payment-service/KAFKA_EVENTS.md) | 🔗 [Order → #5](order-service/KAFKA_EVENTS.md#5-orderrefunds--order--payment) |

---

## 📊 Kafka Topic Configuration

### Retention Policies

| Domain | Retention | Use Case |
|--------|-----------|----------|
| account.* | 7 days | Account security events |
| product.* | 30 days | Product lifecycle tracking |
| order.* | 30 days | Order history & audit |
| payment.*, refund.* | 90 days | Payment compliance & refunds |
| flash_sale.* | 7 days | Session-based events |
| loyalty.* | 365 days | Annual loyalty audit |
| appeal.* | 365 days | Legal appeal records |

### Partitioning Strategy

```
Key (Partition By):
- account.* → user_id (same user events go to same partition)
- order.* → order_id (same order events ordered)
- payment.* → transaction_id (payment events sequential)
- product.* → product_id (product events sequential)
- flash_sale.* → session_id (session events ordered)

Default: 3 partitions per topic
Replication Factor: 3 (HA)
```

---

## 📋 Event Schema Registry

All events follow this base structure:

```json
{
  "event_id": "evt_YYYYMMDD_NNN",
  "event_type": "domain.action",
  "timestamp": "ISO 8601",
  "correlation_id": "uuid",
  "source_service": "service-name",
  "version": 1,
  "data": { }
}
```

---

## 🛠️ Consuming Events (Developer Guide)

### Consumer Group Pattern

```java
@KafkaListener(
  topics = "payment.success",
  groupId = "order-service-payment",
  containerFactory = "kafkaListenerContainerFactory"
)
public void onPaymentSuccess(PaymentSuccessEvent event) {
    if (!isValidEvent(event)) return;
    Order order = orderRepository.findById(event.parent_order_id);
    order.setStatus("PAID");
    orderRepository.save(order);
    notificationService.sendPaymentConfirmation(order);
}
```

### Idempotency Pattern

```java
public void onEvent(KafkaEvent event) {
  if (processedEventRepo.isProcessed(event.event_id)) {
    return; // Already processed, skip
  }
  processEvent(event);
  processedEventRepo.markProcessed(event.event_id);
}
```

---

## 📈 Monitoring & Observability

### Key Metrics per Topic
- Messages/sec (throughput)
- Latency (p50, p95, p99)
- Error rate
- Consumer lag
- Partition skew

### Dead Letter Queue

Worker Service manages DLQ retry — failed events after 3 retries go to `FAILED_EVENTS` table.  
Admin retry via: `POST /admin/failed-events/{id}/retry`

---

## 🔗 Related Documents

| Document | Link |
|----------|------|
| Request-Reply Pattern (chi tiết) | 🔗 [11_KAFKA_REQUEST_REPLY.md](11_KAFKA_REQUEST_REPLY.md) |
| Identity Service Kafka | 🔗 [identity-service/KAFKA_EVENTS.md](identity-service/KAFKA_EVENTS.md) |
| Product Service Kafka | 🔗 [product-service/KAFKA_EVENTS.md](product-service/KAFKA_EVENTS.md) |
| Search Service Kafka | 🔗 [search-service/KAFKA_EVENTS.md](search-service/KAFKA_EVENTS.md) |
| Order Service Kafka | 🔗 [order-service/KAFKA_EVENTS.md](order-service/KAFKA_EVENTS.md) |
| Payment Service Kafka | 🔗 [payment-service/KAFKA_EVENTS.md](payment-service/KAFKA_EVENTS.md) |
| Flash Sale Service Kafka | 🔗 [flashsale-service/KAFKA_EVENTS.md](flashsale-service/KAFKA_EVENTS.md) |
| Notification Service Kafka | 🔗 [notification-service/KAFKA_EVENTS.md](notification-service/KAFKA_EVENTS.md) |
| Admin Service Kafka | 🔗 [admin-service/KAFKA_EVENTS.md](admin-service/KAFKA_EVENTS.md) |
| Worker Service Kafka | 🔗 [worker-service/KAFKA_EVENTS.md](worker-service/KAFKA_EVENTS.md) |
| Architecture Map | 🔗 [ARCHITECTURE_MAP.md](ARCHITECTURE_MAP.md) |

---

**Version**: v5.5 · **Total Topics**: 47 (35 event + 12 request-reply) · **Status**: Production Ready
