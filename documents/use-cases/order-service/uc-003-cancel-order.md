# UC-ORDER-003: Cancel Order (Buyer)

**Stable ID:** UC-ORDER-003
**Actor:** BUYER (or SELLER)
**Priority:** P0 (Critical)
**API:** POST /orders/{id}/cancel
**Last Updated:** 2026-05-09

---

## Brief Description

Buyer cancels an order that is still in PENDING status. Seller may also cancel. System releases reserved stock and notifies relevant parties.

---

## Preconditions

| # | Condition |
|---|-----------|
| P1 | User is authenticated (JWT with role=BUYER or SELLER) |
| P2 | `orders.status` = PENDING |
| P3 | User is the order owner (buyer = customer_id, or seller = seller_id) |

---

## Main Flow

| Step | Actor | Action |
|------|-------|--------|
| 1 | Buyer | Requests POST /orders/{id}/cancel with {reason, note} |
| 2 | System | Verifies order exists (404 if not) |
| 3 | System | Verifies user is order owner (403 if not) |
| 4 | System | Checks order.status == PENDING (409 if not) |
| 5 | System | Updates ORDERS: status=CANCELLED, cancelled_by=BUYER, cancel_reason=reason |
| 6 | System | Releases reserved stock via `inventory.adjusted` Kafka event |
| 7 | System | Produces `order.cancelled` Kafka event |
| 8 | System | Returns 200 with order_id, status, cancelled_by, cancel_reason |

---

## Request Body

```json
{
  "reason": "I want to cancel this order",
  "note": "Ordered by mistake"
}
```

| Field | Type | Required | Max Length |
|-------|------|----------|------------|
| reason | string | Yes | 1000 chars |
| note | string | No | 500 chars |

---

## Alternative Flows

### A1: Order Not in PENDING Status

| Step | Action |
|------|--------|
| A1.1 | order.status is PAID, SHIPPING, etc. |
| A1.2 | System returns 409 "Order cannot be cancelled in current status" |

### A2: Seller Cancels

| Step | Action |
|------|--------|
| A2.1 | Seller requests cancel with reason |
| A2.2 | Same validation: status=PENDING, seller is owner |
| A2.3 | cancelled_by set to SELLER |
| A2.4 | Produces `seller.order_cancelled` (in addition to `order.cancelled`) |

### A3: Unauthorized

| Step | Action |
|------|--------|
| A3.1 | User is neither buyer nor seller of order |
| A3.2 | System returns 403 |

---

## Postconditions

| # | Condition |
|---|-----------|
| Q1 | orders.status = CANCELLED |
| Q2 | orders.cancelled_by = BUYER (or SELLER) |
| Q3 | Stock released back to product_variant.stock_quantity |
| Q4 | `order.cancelled` Kafka event published |
| Q5 | Buyer notified via Notification Service |

---

## Kafka Events

| Topic | Payload Key Fields |
|-------|-------------------|
| `order.cancelled` | order_id, parent_order_id, user_id, seller_id, cancelled_by, cancel_reason, total_amount |
| `inventory.adjusted` | variant_id, quantity_delta (+N, release) |

---

## Related

| Type | IDs |
|------|-----|
| Business Rules | BR-ORDER-011, BR-ORDER-021, BR-ORDER-025 |
| Functional Requirements | FR-ORDER-008 |
| Entities | ENTITY-ORDER-002 |
| State | state-order.md (PENDING → CANCELLED transition) |
