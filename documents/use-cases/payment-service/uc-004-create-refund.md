# UC-PAYMENT-004: Create Refund (Buyer)

**Domain**: Payment Service  
**Actor**: Buyer  
**Priority**: High  
**References**: [KAFKA_EVENTS.md](../../../docs/services/payment-service/KAFKA_EVENTS.md), [06_PAYMENT_SAGA_FLOW.md](../../../docs/business/06_PAYMENT_SAGA_FLOW.md)

---

## Preconditions

| # | Condition |
|---|-----------|
| P1 | Buyer is authenticated (JWT with BUYER role) |
| P2 | Order exists and belongs to the buyer |
| P3 | Order status is DELIVERED (or at least PAID) |
| P4 | Current time < `ORDERS.return_window_end` |
| P5 | No existing refund with PENDING or SUCCESS status for this order |

---

## Main Flow (BUYER_REQUEST)

| Step | Actor/System | Action |
|------|-------------|--------|
| 1 | Buyer | Submits refund request via `POST /refunds` |
| 1a | -- | Selects refund type: FULL or PARTIAL |
| 1b | -- | Provides reason text |
| 1c | -- | Uploads evidence images (MinIO) |
| 1d | -- | Selects items to refund (for PARTIAL) |
| 2 | System | Validates: return_window not expired, amount <= remaining balance |
| 3 | System | Generates `group_ref` UUID for this request |
| 4 | System | Creates REFUND row (type, amount, status=PENDING, reason, evidence_images) |
| 5 | System | Creates REFUND_ITEM rows for each selected item |
| 6 | System | Publishes Kafka `refund.requested` |
| 7 | Notification | Notifies seller of refund request |

---

## Main Flow (RTS Auto-Refund)

| Step | Actor/System | Action |
|------|-------------|--------|
| 1 | Order Svc | Publishes `order.returned` to Kafka |
| 2 | System | PaymentService consumes `order.returned` |
| 3 | System | Creates REFUND: type=FULL, refund_reason_type=RETURN_TO_SENDER, initiated_by=SYSTEM |
| 4 | System | Checks SELLER_TRANSFERS.status: pre-payout or post-payout |
| 5 | System | IF pre-payout: execute refund from platform balance |
| 5a | System | IF post-payout: execute Stripe Transfer reversal |
| 6 | System | Publishes Kafka `refund.rts_completed` |

---

## Alternate Flows

| Flow | Condition | Action |
|------|-----------|--------|
| A1 | Return window expired | Reject with "Return window expired" error |
| A2 | Amount exceeds remaining balance | Reject with validation error |
| A3 | No evidence images | Reject with "Evidence required" validation error |
| A4 | Duplicate refund request | Reject with "Refund already in progress" |

---

## Postconditions

| # | Condition |
|---|-----------|
| Q1 | REFUND record exists with status = PENDING |
| Q2 | REFUND_ITEM records created for each refunded item |
| Q3 | `refund.requested` published to Kafka |
| Q4 | Admin queue receives notification for review |

---

## Business Rules Cited

| Rule ID | Description |
|---------|-------------|
| BR-PAYMENT-017 | Return window eligibility check |
| BR-PAYMENT-018 | Evidence images required |
| BR-PAYMENT-020 | Pre-payout vs post-payout refund handling |
| BR-PAYMENT-021 | RTS auto-refund flow |
| BR-PAYMENT-022 | Refund amount validation |
| BR-PAYMENT-023 | Refund grouping by UUID |
| BR-PAYMENT-024 | Kafka event publishing |

---

## Related Use Cases

| Use Case | Relationship |
|----------|-------------|
| UC-PAYMENT-005 | Approve Refund (admin review follow-up) |
| UC-PAYMENT-006 | Reject Refund (admin review follow-up) |
