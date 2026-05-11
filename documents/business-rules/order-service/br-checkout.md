# BR-ORDER-001 to BR-ORDER-009: Checkout Business Rules

**Stable IDs:** BR-ORDER-001 through BR-ORDER-009
**Domain:** Checkout (POST /orders/checkout)
**Last Updated:** 2026-05-09

---

## BR-ORDER-001: Cart Not Empty

**Rule:** Checkout requires at least 1 item selected from the cart.

| Condition | Outcome |
|-----------|---------|
| IF `item_ids` is empty | THEN reject with 422 "No items selected" |
| IF `item_ids` has items | THEN proceed to stock validation |

**Validation:** `item_ids` array length must be 1-50.

---

## BR-ORDER-002: Stock Availability Check

**Rule:** All selected items must have sufficient stock at checkout time.

| Condition | Outcome |
|-----------|---------|
| IF any item has `stock_quantity < requested_quantity` | THEN reject with 422 "Item [sku] out of stock" |
| IF all items have sufficient stock | THEN proceed to reservation |

**Mechanism:** 
1. Order Service sends `order.stock_check.request` to Product Service
2. Product Service responds with `order.stock_check.response` ({allAvailable: bool, results[]})
3. IF `allAvailable == false` THEN abort checkout

**Atomicity:** Stock is reserved atomically via `stock_reservation` table (status=pending, expires_at = NOW() + 15 min).

---

## BR-ORDER-003: Address Validation

**Rule:** The delivery address must exist and belong to the checkout user.

| Condition | Outcome |
|-----------|---------|
| IF `address_id` does not exist in `addresses` table | THEN reject with 409 "Address not found" |
| IF `address.user_id != current_user.id` | THEN reject with 409 "Address does not belong to user" |
| IF address is valid | THEN snapshot into `orders.shipping_address` (JSONB) |

**Mechanism:**
1. Order Service sends `order.address.request` (userId, addressId) to Identity Service
2. Identity Service responds with `order.address.response` (full address details)
3. Address snapshot stored in each sub-order

---

## BR-ORDER-004: Multi-Vendor Split Logic

**Rule:** Cart items are grouped by seller; one sub-order created per seller.

| Condition | Outcome |
|-----------|---------|
| IF cart items from 1 seller | THEN create 1 parent_order + 1 sub-order |
| IF cart items from N sellers | THEN create 1 parent_order + N sub-orders |

**Algorithm:**
```
1. Fetch cart items via order.cart_items.request → Product Service
2. Group items by seller_id
3. For each seller group:
   - Create one ORDERS row with status=PENDING
   - Create ORDER_ITEMS rows for each item in the group
   - Set sub_order.total_amt = SUM(item.price_snapshot * item.quantity)
4. Create one PARENT_ORDERS row with:
   - status = PENDING_PAYMENT
   - total_amt = SUM(all sub_orders.total_amt)
   - final_amt = total_amt (before any platform discounts)
   - session_id = generated checkout session
```

---

## BR-ORDER-005: Parent-Order Financial Integrity

**Rule:** Parent order amounts must equal the sum of sub-orders.

| Condition | Outcome |
|-----------|---------|
| IF `parent_order.final_amt != SUM(orders.final_amt)` | THEN data integrity error; abort transaction |

**Enforcement:** Single database transaction for all INSERTs. Rollback on mismatch.

---

## BR-ORDER-006: Order Code Generation

**Rule:** Each sub-order receives a unique human-readable `order_code`.

**Format:** `OR-YYYYMMDD-{id}`

| Component | Value |
|-----------|-------|
| Prefix | `OR` |
| Date | `YYYYMMDD` (checkout date) |
| Separator | `-` |
| ID | `orders.id` (BIGSERIAL) |

**Example:** `OR-20260509-100`

---

## BR-ORDER-007: Shipping Deadline Calculation

**Rule:** Seller must provide tracking within 3 days of order creation.

| Condition | Outcome |
|-----------|---------|
| IF `orders.status` = PAID and `NOW() > shipping_deadline` | THEN JOB-13 may auto-cancel |

**Formula:** `shipping_deadline = created_at + 3 days`

---

## BR-ORDER-008: Checkout Idempotency

**Rule:** A checkout session cannot be processed twice.

| Condition | Outcome |
|-----------|---------|
| IF `session_id` already exists in `parent_orders` | THEN reject with 409 "Checkout already processed" |

**Enforcement:** UNIQUE constraint on `parent_orders.session_id`.

---

## BR-ORDER-009: Kafka Event on Checkout

**Rule:** On successful checkout, produce `order.created` event.

| Field | Value |
|-------|-------|
| Topic | `order.created` |
| Payload | parent_order_id, user_id, orders[], total_amount, timestamp |
| Consumers | Product Service (inventory lock), Search Service (sold count) |

**Also produced:**
- `order.checkout_created` → Product Service (remove items from cart)

---

## Error Response Summary

| HTTP Status | Condition | Rule |
|-------------|-----------|------|
| 422 | Empty item_ids or items out of stock | BR-ORDER-001, BR-ORDER-002 |
| 409 | Address invalid or session duplicate | BR-ORDER-003, BR-ORDER-008 |
| 500 | Financial integrity mismatch | BR-ORDER-005 |

---

## Cross-References

- **ENTITY-ORDER-001:** [PARENT_ORDERS](../../data-models/order-service/entity-parent-order.md)
- **ENTITY-ORDER-002:** [ORDERS](../../data-models/order-service/entity-order.md)
- **ENTITY-ORDER-003:** [ORDER_ITEMS](../../data-models/order-service/entity-order-item.md)
- **BR:** [br-order-lifecycle.md](br-order-lifecycle.md)
- **UC:** [uc-001-checkout.md](../../use-cases/order-service/uc-001-checkout.md)
- **API:** [api-post-orders-checkout.yaml](../../api-contracts/order-service/api-post-orders-checkout.yaml)
- **FR:** [fr-order.md](../../srs/fr/order-service/fr-order.md)
- **State:** [state-order.md](../../state-diagrams/order-service/state-order.md)
- **Traceability:** [traceability-matrix.md](../../traceability/order-service/traceability-matrix.md)
