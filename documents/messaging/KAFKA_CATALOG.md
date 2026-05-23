## Kafka Events Catalog
Service: platform
Generated: 2026-05-09 | Updated: 2026-05-23 (payload alignment + product.auto_hidden removed)

> **2026-05-23 changelog:**
> - `product.pending_review`, `product.updated`, `variant.price_updated`, `variant.stock_updated` payloads updated to include all required fields (`productId`, `sellerId`, `categoryId`, `name`, `submittedAt`, `rejectCount`, `status`, `timestamp`, `productId`, `originalPrice`, `stockStatus`).
> - `stock.reservation.expired` trigger updated: handled by `ReservationCleanupScheduler` in product-service (cron every minute), not JOB-13.
> - Flash Sale Flow updated to reflect actual implementation: `flash_sale.session_ended` restores prices and emits `flash_sale.price_sync`; cart cleanup and inventory update are out of scope for MVP.
> - `product.auto_hidden` event REMOVED from catalog, KafkaTopics.java, and kafka/create-topics.sh. Search Service uses `product.updated` (status change to INACTIVE) for index updates instead.

> **2026-05-10 MVP changes** (xem `MVP_ANALYSIS.md` §3):
>
> **NEW events** (5):
> - `stock.reservation.expired` (product → order, notification) — MUST
> - `order.payment_timeout` (order → order self, notification) — MUST
> - `stock.reservation.confirmed` / `released` (product, audit) — SHOULD
> - `seller.transfer.eligible` / `paid_out` / `failed` (payment → notification) — SHOULD
>
> **OBSOLETE events** (xóa khỏi catalog):
> - `flash_sale.item_approved`, `flash_sale.item_rejected` — auto-approve
>
> **RE-ACTIVATED events** (đính chính 2026-05-10 v3 — xem `MVP_ANALYSIS.md`):
> - `seller.order_cancelled` (order → payment, notification, product) — MUST cho workflow seller cancel. ✅ Documented + UC-008 + BR-026 hoàn thành.
> - `product.pending_review`, `product.approved`, `product.rejected` (product → notification, search) — MUST cho workflow admin review. ✅ Documented in product KAFKA_EVENTS.md + P3-11 đã APPROVED 2026-05-10 → áp dụng vào `database-entities.md` §3 (status enum 7 giá trị + 4 cột reviewer).

### Overview

| Metric | Value |
|--------|-------|
| Total Topics | 62 (48 event + 14 request-reply) |
| Event Producers | Product, Order, Payment, Flash Sale, AI Chat |
| Event Consumers | All 9 services |
| Retention Policy | 7–365 days depending on topic type |
| Request-Reply Doc | [KAFKA_REQUEST_REPLY.md](KAFKA_REQUEST_REPLY.md) |

> **Migration Note**: The former worker-service (port 8086) has been migrated to ai-chat-service (port 8093). Worker responsibilities (JOB-13, JOB-22, DLQ) are now distributed across order-service, payment-service, and notification-service per the v5.0 distributed cronjob model.
>
> **2026-05-12 Note**: 3 flash sale topics (`flash_sale.item_approved`, `flash_sale.item_rejected`, `flash_sale.item_sold`) are marked OBSOLETE but still present in KafkaTopics.java for backward compatibility. These are excluded from the active count.

### Event Topics by Service

| Service | Produces | Consumes |
|---------|----------|----------|
| identity-service | — (does NOT produce domain events) | order.delivered, order.cancelled, refund.admin_approved, refund.rejected |
| product-service | product.* (incl. pending_review, approved, rejected), category.*, inventory.*, stock.reservation.* | order.created, order.cancelled, flash_sale.* |
| order-service | order.*, order.payment_timeout, seller.order_cancelled, order.checkout_completed | payment.*, refund.*, stock.reservation.expired |
| payment-service | payment.*, stripe.*, seller.transfer.*, payout.*, refund.stripe_auto | payment.requested, order.delivered, order.cancelled |
| refund-service | refund.* | refund.requested, refund.full_requested, order.returned, order.refunds.request, order.payment_status.request |
| flashsale-service | flash_sale.*, flash_sale.reminder | — |
| search-service | — (consumer-only) | product.*, category.*, inventory.* |
| notification-service | — (consumer-only) | 22 topics from all services |
| ai-chat-service | ai_chat.* | — |

### Kafka Request-Reply Pairs (7 pairs, 14 topics)

| Request Topic | Response Topic | Requester | Responder |
|--------------|----------------|-----------|-----------|
| cart.product_info.request | cart.product_info.response | Cart (Product) | Product catalog |
| order.stock_check.request | order.stock_check.response | Order | Product |
| order.payment_status.request | order.payment_status.response | Order | Payment |
| order.cart_items.request | order.cart_items.response | Order | Product |
| order.address.request | order.address.response | Order | Identity |
| order.refunds.request | order.refunds.response | Order | Refund |
| order.refund_presigned_url.request | order.refund_presigned_url.response | Order | Refund |

### Retention Policies

| Domain | Retention | Rationale |
|--------|-----------|------------|
| product.* | 30 days | Product lifecycle tracking |
| order.* | 30 days | Order history and audit |
| payment.*, refund.* | 90 days | Payment compliance and refunds |
| flash_sale.* | 7 days | Session-based events |

### Partitioning Strategy

| Domain | Partition Key | Rationale |
|--------|---------------|-----------|
| order.* | order_id | Same-order events ordered |
| payment.* | transaction_id | Payment events sequential |
| product.* | product_id | Product events sequential |
| flash_sale.* | session_id | Session events ordered |

Default: 3 partitions per topic, Replication Factor: 3 (HA).

### Event Schema (Base Envelope)

All events follow this structure:

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

### Consumer Patterns

**Idempotency** -- all consumers MUST deduplicate by `event_id`:

```java
public void onEvent(KafkaEvent event) {
  if (processedEventRepo.isProcessed(event.event_id)) {
    return; // Already processed
  }
  processEvent(event);
  processedEventRepo.markProcessed(event.event_id);
}
```

**Consumer Group** -- each service uses a unique `groupId` per topic:

```java
@KafkaListener(
  topics = "order.created",
  groupId = "order-service-order",
  containerFactory = "kafkaListenerContainerFactory"
)
public void onPaymentSuccess(PaymentSuccessEvent event) {
    // handle
}
```

### Monitoring & Observability

| Metric | Description |
|--------|-------------|
| Messages/sec | Throughput per topic |
| Latency (p50/p95/p99) | End-to-end event processing time |
| Error rate | Failed events per minute |
| Consumer lag | Offset lag per consumer group |
| Partition skew | Imbalance across partitions |

**Dead Letter Queue (DLQ)**: Failed events after 3 retries go to the `FAILED_EVENTS` table. Admin can retry via `POST /admin/failed-events/{id}/retry`.

### Event Flow Chains

**Shopping Flow:**
```
[Order] order.created → [Product] lock stock
                      → [Search] update sold count
[Payment] payment.success → [Order] mark PAID
[Order] order.shipped → [Notification] tracking update
[Order] order.delivered → [Notification] delivery confirmation
```

**Refund Flow:**
```
[Payment] refund.requested → [Notification] notify seller
[Payment] refund.admin_approved → [Notification] notify buyer + seller
[Payment] refund.stripe_auto → [Order] mark refunded
```

**Flash Sale Flow:**
```
[FlashSale] flash_sale.session_started → [Notification] open session
                                 → [Product] apply flash prices, emit flash_sale.price_sync (activate)
[FlashSale] flash_sale.session_ended → [Notification] close session
                                  → [Product] restore original prices, emit flash_sale.price_sync (deactivate)
```
