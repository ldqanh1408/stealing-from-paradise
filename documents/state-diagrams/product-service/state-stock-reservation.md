# State Diagram: STOCK_RESERVATION

**Stable ID:** `STATE-PRODUCT-003`

> **Entity**: ENTITY-PRODUCT-005 (STOCK_RESERVATION)
> **Status Column**: `stock_reservation.status` (VARCHAR 50)
> **Last Updated**: 2026-05-09

---

## State Machine

```mermaid
stateDiagram-v2
    [*] --> pending : Customer clicks "Dat hang"

    pending --> confirmed : Payment succeeds (payment.success)
    pending --> released : Payment fails (payment.failed)
    pending --> released : Timeout (expires_at < NOW()) -- cleanup job

    confirmed --> [*]
    released --> [*]
```

---

## Transition Table

| # | From | To | Trigger | Actor | Business Rule | Use Case |
|---|------|-----|---------|-------|---------------|----------|
| 1 | `[*]` | `pending` | `order.created` event received; reservation inserted with `expires_at = NOW() + 15 min` | System | BR-PRODUCT-007 | UC-PRODUCT-007 |
| 2 | `pending` | `confirmed` | `payment.success` event received (payment success) | System (Order Service) | -- | UC-PRODUCT-007 |
| 3 | `pending` | `released` | `payment.failed` event received (payment failure) | System (Order Service) | BR-PRODUCT-007 | UC-PRODUCT-007 |
| 4 | `pending` | `released` | `expires_at < NOW()` -- cleanup job (runs every 1-5 min) | System (Scheduler) | BR-PRODUCT-007 | -- |
| 5 | `confirmed` | `[*]` | Terminal state | -- | -- | -- |
| 6 | `released` | `[*]` | Terminal state | -- | -- | -- |

---

## Lifecycle Timeline

```
  t=0      Customer clicks "Dat hang"
           -> INSERT reservation (pending, expires_at = NOW()+15min)
           -> Redis DECRBY
           -> DB UPDATE stock_quantity (optimistic lock)

  t=0..15  Payment processing window

  t < 15   Payment succeeds
           -> UPDATE reservation SET status = 'confirmed'
           -> Stock already deducted, no restore needed

  t < 15   Payment fails
           -> UPDATE reservation SET status = 'released'
           -> Redis INCR
           -> DB UPDATE stock_quantity = stock_quantity + qty

  t > 15   (Cleanup job catches it)
           -> UPDATE reservation SET status = 'released'
           -> Redis INCR
           -> DB UPDATE stock_quantity = stock_quantity + qty
```

---

## Side Effects Per Transition

| Transition | Redis Action | DB Action | Kafka Event |
|------------|--------------|-----------|-------------|
| `[*]` -> `pending` | DECRBY stock:{vid} {qty} | stock_quantity -= qty | -- |
| `pending` -> `confirmed` | (none) | (none, already deducted) | -- |
| `pending` -> `released` | INCR stock:{vid} {qty} | stock_quantity += qty | -- |

---

## Constraints

| Rule | Detail |
|------|--------|
| TTL | 15 minutes from creation |
| Cleanup interval | 1-5 minutes |
| Idempotency | Cleanup checks `status = 'pending'` before releasing |
| Cascading statuses | Product and variant statuses recomputed after stock restore |

---

## Cross-References

| Ref ID | Type |
|--------|------|
| ENTITY-PRODUCT-005 | STOCK_RESERVATION |
| BR-PRODUCT-007 | Reservation expiry (15 min TTL) |
| BR-PRODUCT-008 | Optimistic lock for concurrent reservations |
| FR-PRODUCT-014 | Reserve stock during checkout |
| FR-PRODUCT-015 | Release expired reservations |
| UC-PRODUCT-007 | Reserve stock (system) |
