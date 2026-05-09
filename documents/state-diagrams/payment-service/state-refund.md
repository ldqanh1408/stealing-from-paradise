# State Diagram: Refund

**Entity**: REFUNDS (ENTITY-PAYMENT-004)  
**Domain**: Payment Service  
**References**: [entity-refund.md](../../data-models/payment-service/entity-refund.md), [KAFKA_EVENTS.md](../../../docs/services/payment-service/KAFKA_EVENTS.md)

---

## State Machine

```
                        [*]
                         |
                         | buyer submits refund request
                         v
                   PENDING_REVIEW
                      /       \
                     /         \
          admin     /           \    admin
          approve  /             \   reject
                  v               v
             APPROVED          REJECTED
                  |               |
                  | Stripe refund | no Stripe call
                  v               |
             PROCESSING           |
                  |               |
                  | successful    |
                  v               |
             COMPLETED            |
                  |               |
                  v               v
                 [*]             [*]
              (terminal)      (terminal)
```

---

## State Transition Table

| From | To | Trigger | Actor | Cites |
|------|----|---------|-------|-------|
| `[*]` | `PENDING_REVIEW` | Buyer submits POST /refunds or `order.returned` for RTS | Buyer / System | UC-PAYMENT-004 |
| `PENDING_REVIEW` | `APPROVED` | Admin calls PUT /refunds/{id}/approve | Admin | UC-PAYMENT-005 |
| `PENDING_REVIEW` | `REJECTED` | Admin calls PUT /refunds/{id}/reject | Admin | UC-PAYMENT-006 |
| `APPROVED` | `PROCESSING` | System initiates Stripe refund API call | System | UC-PAYMENT-005 |
| `PROCESSING` | `COMPLETED` | Stripe refund succeeds (refund_ref populated) | Stripe / System | UC-PAYMENT-005 |
| `PROCESSING` | `FAILED` | Stripe refund API error | Stripe | UC-PAYMENT-005 |
| `COMPLETED` | `[*]` | Terminal state | -- | -- |
| `REJECTED` | `[*]` | Terminal state | -- | -- |
| `FAILED` | `[*]` | Terminal state (manual intervention) | -- | -- |

---

## Guard Conditions

| Transition | Guard |
|------------|-------|
| [*] -> PENDING_REVIEW | `order.return_window_end` not passed; evidence images provided (BUYER_REQUEST); amount <= remaining balance |
| PENDING_REVIEW -> APPROVED | REFUNDS.status = PENDING_REVIEW; admin has ADMIN role |
| PENDING_REVIEW -> REJECTED | REFUNDS.status = PENDING_REVIEW; `reject_reason` provided |
| APPROVED -> PROCESSING | Auto-transition after approval |
| PROCESSING -> COMPLETED | Stripe refund API returns success; `refund_ref` populated |

---

## RTS Fast Path (No Admin Review)

| From | To | Trigger | Actor |
|------|----|---------|-------|
| `[*]` | `PROCESSING` | `order.returned` Kafka event (RETURN_TO_SENDER) | System |
| `PROCESSING` | `COMPLETED` | Stripe refund succeeds | Stripe / System |

RTS refunds skip PENDING_REVIEW -> APPROVED because they are auto-approved.

---

## Kafka Events per Transition

| Transition | Kafka Topic |
|------------|-------------|
| [*] -> PENDING_REVIEW | `refund.requested` |
| PENDING_REVIEW -> APPROVED | `refund.admin_approved` |
| PENDING_REVIEW -> REJECTED | `refund.rejected` |
| PROCESSING -> COMPLETED | `refund.completed` (or `refund.rts_completed` for RTS) |
| PROCESSING -> COMPLETED (chargeback) | `refund.stripe_auto` |

---

## Related States in Other Entities

| Entity | Related State | Relationship |
|--------|--------------|-------------|
| TRANSACTIONS.status | REFUNDED / PARTIALLY_REFUNDED | Updated when REFUNDS reaches COMPLETED |
| SELLER_TRANSFERS.status | REFUNDED / REVERSED | Updated based on pre/post-payout |
