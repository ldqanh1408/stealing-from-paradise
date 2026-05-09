# State Diagram: Transaction

**Entity**: TRANSACTIONS (ENTITY-PAYMENT-002)  
**Domain**: Payment Service  
**References**: [entity-transaction.md](../../data-models/payment-service/entity-transaction.md), [06_PAYMENT_SAGA_FLOW.md](../../../docs/business/06_PAYMENT_SAGA_FLOW.md)

---

## State Machine

```
                        [*]
                         |
                         | payment.requested
                         v
                     PENDING
                      /   \
                     /     \
    payment.         /       \        payment.
    succeeded       /         \       failed
                   v           v
               COMPLETED    FAILED
                   |           |
                   | refunded  |
                   v           |
               REFUNDED        |
                   |           |
                   | partial   |
                   v           |
           PARTIALLY_REFUNDED  |
                               |
                    [*] <------+
                   (terminal)
```

---

## State Transition Table

| From | To | Trigger | Actor | Cites |
|------|----|---------|-------|-------|
| `[*]` | `PENDING` | `payment.requested` Kafka event | System (ParentOrderPaymentSaga) | UC-PAYMENT-002 |
| `PENDING` | `COMPLETED` | Stripe webhook `payment_intent.succeeded` | Stripe / System | UC-PAYMENT-003 |
| `PENDING` | `FAILED` | Stripe webhook `payment_intent.payment_failed` | Stripe / System | UC-PAYMENT-003 |
| `COMPLETED` | `REFUNDED` | All refunds processed; total refunded = amount | System (admin approve) | UC-PAYMENT-005 |
| `COMPLETED` | `PARTIALLY_REFUNDED` | Partial refund processed; amount > 0 refunded < amount | System (admin approve) | UC-PAYMENT-005 |
| `REFUNDED` | `[*]` | Terminal state | -- | -- |
| `PARTIALLY_REFUNDED` | `REFUNDED` | Remaining balance refunded | System | UC-PAYMENT-005 |
| `FAILED` | `[*]` | Terminal state | -- | -- |

---

## Guard Conditions

| Transition | Guard |
|------------|-------|
| PENDING -> COMPLETED | `event.id` not previously processed; TRANSACTIONS.status is PENDING |
| PENDING -> FAILED | `event.id` not previously processed; TRANSACTIONS.status is PENDING |
| COMPLETED -> REFUNDED | All SELLER_TRANSFERS.refunded_amount >= SELLER_TRANSFERS.transfer_amount for all sub-orders |
| COMPLETED -> PARTIALLY_REFUNDED | At least one refund processed but total refunded < amount |

---

## Related States in Other Entities

| Entity | Related State | Relationship |
|--------|--------------|-------------|
| PARENT_ORDERS.status | PAID | Set when TRANSACTIONS.status = COMPLETED |
| SELLER_TRANSFERS.status | AWAITING_DELIVERY | Set when TRANSACTIONS.status = COMPLETED |
| REFUNDS.status | SUCCESS | Triggers TRANSACTIONS.status -> REFUNDED / PARTIALLY_REFUNDED |
