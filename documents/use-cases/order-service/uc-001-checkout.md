# UC-ORDER-001: Checkout (Create Order from Cart)

**Stable ID:** UC-ORDER-001
**Actor:** BUYER
**Priority:** P0 (Critical)
**API:** POST /orders/checkout
**Last Updated:** 2026-05-09

---

## Brief Description

Buyer selects items from their cart and completes checkout. The system splits items by seller, creates one parent order and N sub-orders, validates stock and address, and initiates payment.

---

## Preconditions

| # | Condition |
|---|-----------|
| P1 | Buyer is authenticated (JWT with role=BUYER) |
| P2 | Buyer has at least 1 item in cart |
| P3 | Buyer has a valid shipping address on file |

---

## Main Flow

| Step | Actor | Action |
|------|-------|--------|
| 1 | Buyer | Selects items from cart and shipping address |
| 2 | Buyer | Submits POST /orders/checkout with {address_id, item_ids[]} |
| 3 | System | Validates item_ids: 1-50 items, no duplicates |
| 4 | System | Fetches cart items via `order.cart_items.request` → Product Service |
| 5 | System | Validates address via `order.address.request` → Identity Service |
| 6 | System | Validates stock via `order.stock_check.request` → Product Service |
| 7 | System | Groups items by seller_id |
| 8 | System | Creates 1 PARENT_ORDER + N ORDERS in single transaction |
| 9 | System | Creates ORDER_ITEMS with price/image/name snapshots |
| 10 | System | Reserves stock (stock_reservation entries) |
| 11 | System | Emits Axon ParentOrderCheckoutCreatedEvent → starts Saga |
| 12 | System | Produces `order.created` and `order.checkout_created` Kafka events |
| 13 | System | Returns 201 with parent_order_id, orders[], shipping_address, totals |

---

## Alternative Flows

### A1: Some Items Out of Stock

| Step | Action |
|------|--------|
| A1.1 | Product Service returns allAvailable=false |
| A1.2 | System returns 422 with per-item stock status |

### A2: Invalid Address

| Step | Action |
|------|--------|
| A2.1 | Identity Service returns address not found or not owned |
| A2.2 | System returns 409 "Address not valid" |

### A3: Duplicate Checkout Session

| Step | Action |
|------|--------|
| A3.1 | session_id already exists in parent_orders |
| A3.2 | System returns 409 "Checkout already processed" |

---

## Postconditions

| # | Condition |
|---|-----------|
| Q1 | 1 parent_order created with status=PENDING_PAYMENT |
| Q2 | N sub-orders created with status=PENDING |
| Q3 | All ORDER_ITEMS snapshots captured |
| Q4 | Stock reserved with 15-min TTL |
| Q5 | `order.created` Kafka event published |
| Q6 | ParentOrderPaymentSaga started in Axon |
| Q7 | Cart items marked for removal |

---

## Error Responses

| Status | Condition |
|--------|-----------|
| 201 | Success |
| 422 | Some items out of stock or invalid item_ids |
| 409 | Address invalid or session duplicate |
| 401 | Not authenticated |

---

## Related

| Type | IDs |
|------|-----|
| Business Rules | BR-ORDER-001 through BR-ORDER-009 |
| Functional Requirements | FR-ORDER-001, FR-ORDER-002, FR-ORDER-003, FR-ORDER-004 |
| API Contract | api-post-orders-checkout.yaml |
| Kafka Events | order.created, order.checkout_created |
| Entities | ENTITY-ORDER-001, ENTITY-ORDER-002, ENTITY-ORDER-003 |
