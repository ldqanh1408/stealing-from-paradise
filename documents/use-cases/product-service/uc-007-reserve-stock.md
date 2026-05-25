# UC-PRODUCT-007: Reserve Stock (System, During Checkout)

|| Attribute | Value |
|-----------|-------|
| **ID** | UC-PRODUCT-007 |
| **Actor** | System (triggered by Order Service via Kafka or request-reply) |
| **Priority** | CRITICAL |
| **Precondition** | Customer has confirmed checkout; preview_token is valid |
| **Postcondition** | Stock reserved with 15-min TTL; stock_quantity deducted |

---

## Main Flow

### Phase 1: Stock Check (Request-Reply)
```
1. Order Service sends order.stock_check.request
   Payload: { items: [{ variantId, quantity }] }

2. Product Service (Inventory module) checks each variant:
   - stock_quantity >= requested quantity
   - status = 'active'

3. Product Service returns order.stock_check.response:
   { allAvailable: true/false,
     results: [{ variantId, available: bool, currentStock, reason? }] }

4. IF any unavailable: Order Service rejects checkout
```

### Phase 2: Reserve Stock (On order.created Event)
```
1. Order Service emits order.created event after creating order

2. DB Transaction with Pessimistic Lock:
   a) SELECT ... FOR UPDATE on product_variant (acquires row lock)

   b) IF stock_quantity < requested quantity:
      ROLLBACK lock, return error "out of stock"

   c) INSERT INTO stock_reservation (variant_id, session_id, quantity, status, expires_at)
      VALUES (:vid, :sid, :qty, 'pending', NOW() + INTERVAL '15 minutes')

   d) UPDATE product_variant
      SET stock_quantity = stock_quantity - :qty,
          status = CASE WHEN stock_quantity - :qty = 0 THEN 'out_of_stock' ELSE status END
      WHERE id = :vid

3. Product status recomputed in same transaction

4. Returns success -> Order Service proceeds to payment
```

### Phase 3: Confirm or Release

```
Payment Succeeds (payment.success event):
  UPDATE stock_reservation SET status = 'confirmed'
  WHERE session_id = :sid
  -- Stock already deducted, no further action

Payment Fails (payment.failed event):
  UPDATE stock_reservation SET status = 'released'
  WHERE session_id = :sid
  -- DB: UPDATE product_variant SET stock_quantity += qty (version field handles concurrency)

Cleanup Job (runs every 1-5 min):
  SELECT * FROM stock_reservation
  WHERE status = 'pending' AND expires_at < NOW()
  -- For each: release (set released, restore DB stock)
```

---

## Error Scenarios

|| Scenario | Response |
||----------|----------|
| Insufficient stock | Pessimistic lock acquired, stock check fails -> 422 Insufficient stock |
| Concurrent reservation | Pessimistic lock serializes requests (second request waits for first to complete) |

---

## Related Requirements

|| Ref ID | Description |
||--------|-------------|
| FR-PRODUCT-014 | Reserve stock during checkout |
| FR-PRODUCT-015 | Release expired reservations |
| BR-PRODUCT-005 | Pessimistic locking for concurrent reservations |
| BR-PRODUCT-007 | Reservation expiry (15 min TTL) |
| ENTITY-PRODUCT-005 | STOCK_RESERVATION |
| state-stock-reservation.md | pending -> confirmed / released |
