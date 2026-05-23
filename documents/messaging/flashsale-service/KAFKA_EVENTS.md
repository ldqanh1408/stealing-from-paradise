# Kafka Events -- Flash Sale Service

> Service: flashsale-service (Port 8085)
> Source: `docs/services/flashsale-service/flashsale_service_flow.md`, `docs/services/flashsale-service/KAFKA_EVENTS.md`
> Generated: 2026-05-10

---

## Events Produced

### flash_sale.session_started

| Field | Value |
|-------|-------|
| **Consumers** | Product Service, Notification Service |
| **Trigger** | Redis ZSET worker transitions session UPCOMING -> ACTIVE at start_time |
| **Latency** | ~0ms (Redis ZSET trigger) |

**Payload:**
```json
{
  "event": "flash_sale.session_started",
  "session_id": 1,
  "name": "Flash Sale 8h sang",
  "start_time": "2026-05-10T08:00:00Z",
  "end_time": "2026-05-10T10:00:00Z",
  "timestamp": "2026-05-10T08:00:00Z"
}
```

**Consumer Actions:**
- **Product Service**: Queries fs_items for session, fetches variant prices, calculates `flash_price = sku_price * (1 - discount/100)`, emits `flash_sale.price_sync` to Search Service
- **Notification Service**: Sends SSE reminders to subscribed users

---

### flash_sale.session_ended

| Field | Value |
|-------|-------|
| **Consumers** | Product Service, Notification Service |
| **Trigger** | Redis ZSET worker transitions session ACTIVE -> ENDED at end_time |

**Payload:**
```json
{
  "event": "flash_sale.session_ended",
  "session_id": 1,
  "name": "Flash Sale 8h sang",
  "timestamp": "2026-05-10T10:00:00Z"
}
```

**Consumer Actions:**
- **Product Service**: Resets prices to original, emits `flash_sale.price_sync` (deactivate) to Search Service. Cart module removes expired flash items (JOB-07 equivalent).
- **Notification Service**: Sends session end notification

---

### flash_sale.session_created

| Field | Value |
|-------|-------|
| **Consumers** | Audit log |
| **Trigger** | Admin creates session via POST /flash-sales |

**Payload:**
```json
{
  "event": "flash_sale.session_created",
  "session_id": 1,
  "name": "Flash Sale 8h sang",
  "start_time": "2026-05-10T08:00:00Z",
  "end_time": "2026-05-10T10:00:00Z",
  "discount_percentage": 20.00,
  "registration_deadline": "2026-05-10T07:45:00Z",
  "timestamp": "2026-05-09T15:00:00Z"
}
```

---

### flash_sale.item_registered

| Field | Value |
|-------|-------|
| **Consumers** | Notification Service |
| **Trigger** | Seller registers product via POST /flash-sales/{id}/items |

**Payload:**
```json
{
  "event": "flash_sale.item_registered",
  "session_id": 1,
  "fs_item_id": 123,
  "product_id": "uuid-cua-product",
  "seller_id": "uuid-cua-seller",
  "discount_applied": 20.00,
  "registered_at": "2026-05-09T07:50:00Z",
  "timestamp": "2026-05-09T07:50:00Z"
}
```

---

### flash_sale.item_purchased

| Field | Value |
|-------|-------|
| **Consumers** | Checkout Service (inventory reservation) |
| **Trigger** | Customer submits flash sale purchase |

**Payload:**
```json
{
  "event": "flash_sale.item_purchased",
  "session_id": 1,
  "fs_item_id": 123,
  "product_id": "uuid-cua-product",
  "user_id": 42,
  "variant_id": "uuid-cua-variant",
  "flash_price": 200000,
  "original_price": 250000,
  "quantity": 2,
  "order_id": 5678,
  "timestamp": "2026-05-10T08:01:00Z"
}
```

**Consumer Actions:**
- **Checkout Service**: Handles inventory reservation, stock deduction, and order creation

---

### flash_sale.reminder

| Field | Value |
|-------|-------|
| **Trigger** | Customer sets a reminder for an upcoming flash sale session |
| **Consumers** | Notification Service |

**Payload:**
```json
{
  "session_id": 10,
  "customer_id": 42,
  "session_name": "Flash Sale 12/12",
  "start_time": "2026-05-12T12:00:00Z",
  "timestamp": "2026-05-12T10:00:00Z"
}
```

---

## Events Consumed

Flash Sale Service does NOT consume events from Kafka. All triggers are from REST API or Redis ZSET worker.

---

## Request-Reply (None)

Flash Sale Service does not participate in Kafka request-reply patterns.

---

## Related Topics (via Product Service)

### flash_sale.price_sync (Product Service -> Search Service)

Published by Product Service on receiving `flash_sale.session_started` or `flash_sale.session_ended`.

**Activate payload:**
```json
{
  "event": "flash_sale.price_sync",
  "action": "activate",
  "session_id": 1,
  "items": [
    {
      "sku_id": "sku-001",
      "product_id": "prod-123",
      "flash_price": 160000,
      "original_price": 200000,
      "has_discount": true,
      "discount_pct": 20
    }
  ],
  "timestamp": "2026-05-10T08:00:00Z"
}
```

**Deactivate payload:**
```json
{
  "event": "flash_sale.price_sync",
  "action": "deactivate",
  "session_id": 1,
  "items": [
    { "sku_id": "sku-001", "product_id": "prod-123" }
  ],
  "timestamp": "2026-05-10T10:00:00Z"
}
```

---

## Event Flow Summary

```
Admin creates session
  -> flash_sale.session_created [audit]

Seller registers product
  -> flash_sale.item_registered [Notification Service]

Redis Worker at start_time
  -> flash_sale.session_started [Product Service, Notification Service]
    -> Product Service calculates flash prices
      -> flash_sale.price_sync (activate) [Search Service]

Redis Worker at end_time
  -> flash_sale.session_ended [Product Service, Notification Service]
    -> Product Service resets prices
      -> flash_sale.price_sync (deactivate) [Search Service]

Customer purchases
  -> flash_sale.item_purchased [Checkout Service]
```

---

## Redis Keys Used

| Key | Type | Purpose |
|-----|------|---------|
| `flash_sale:triggers` | Sorted Set | Time-based triggers for session start/end |
