# UC-PRODUCT-007: Reserve Stock (System, During Checkout)

| Attribute | Value |
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

### Phase 2: Reserve Stock (On order.checkout_submitted Event)
```
1. Order Service emits order.checkout_submitted event after creating order
   Payload includes:
   - session_id: unique checkout session ID
   - customer_id: buyer's user ID
   - items: [{ variantId, quantity, skuCode, priceSnapshot, sellerId, ... }]

2. Product Service: DB Transaction with Pessimistic Lock for each variant:
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
Payment Succeeds (order.paid event with session_id + customer_id):
  UPDATE stock_reservation SET status = 'confirmed'
  WHERE session_id = :sid
  -- Stock already deducted, no further action
  -- HARD DELETE cart_items WHERE (customer_id, variant_id) IN (...)
     via: CartItemRepository.deleteAllByCustomerIdAndVariantIds(userId, variantIds)

Payment Fails (order.payment_failed event with session_id + customer_id):
  UPDATE stock_reservation SET status = 'released'
  WHERE session_id = :sid
  -- DB: UPDATE product_variant SET stock_quantity += qty (pessimistic lock via SELECT FOR UPDATE)

Cleanup Job (runs every 1-5 min):
  SELECT * FROM stock_reservation
  WHERE status = 'pending' AND expires_at < NOW()
  -- For each: release (set released, restore DB stock via pessimistic lock)
```

---

## Cart Item Deletion Flow

When `order.paid` or `order.payment_failed` is received:
1. Extract `session_id` and `customer_id` (userId) from event payload
2. Find all `stock_reservation` records matching `session_id`
3. For each reservation: extract `variant_id`
4. Hard-delete: `DELETE FROM cart_items WHERE customer_id = :cid AND variant_id IN (:variantIds)`

No `cart_item_id` stored in `stock_reservation`. Composite key `(customer_id, variant_id)` used instead.

---

## Error Scenarios

| Scenario | Response |
|----------|----------|
| Insufficient stock | Pessimistic lock acquired, stock check fails -> 422 Insufficient stock |
| Concurrent reservation | Pessimistic lock serializes requests (second request waits for first to complete) |

---

## Related Requirements

| Ref ID | Description |
|--------|-------------|
| FR-PRODUCT-014 | Reserve stock during checkout |
| FR-PRODUCT-015 | Release expired reservations |
| BR-PRODUCT-005 | Pessimistic locking for concurrent reservations |
| BR-PRODUCT-007 | Reservation expiry (15 min TTL) |
| ENTITY-PRODUCT-005 | STOCK_RESERVATION |
| state-stock-reservation.md | pending -> confirmed / released |
