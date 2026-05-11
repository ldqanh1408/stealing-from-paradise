# UC-PAYMENT-006: Reject Refund (Admin)

**Domain**: Payment Service  
**Actor**: Admin  
**Priority**: High  
**References**: [02_API_payment_service.md](../../../docs/services/payment-service/02_API_payment_service.md), [KAFKA_EVENTS.md](../../../docs/services/payment-service/KAFKA_EVENTS.md)

---

## Preconditions

| # | Condition |
|---|-----------|
| P1 | Admin is authenticated (JWT with ADMIN role) |
| P2 | REFUND exists with status = PENDING |
| P3 | Admin has reviewed evidence and determined refund is not warranted |

---

## Main Flow

| Step | Actor/System | Action |
|------|-------------|--------|
| 1 | Admin | Reviews refund details and evidence |
| 2 | Admin | Determines refund does not meet criteria |
| 3 | Admin | Calls PUT `/refunds/{id}/reject` with `reason` in request body |
| 4 | System | Validates refund is in PENDING status (→ ENTITY-PAYMENT-004) |
| 5 | System | Sets REFUNDS.status = REJECTED (→ ENTITY-PAYMENT-004) |
| 6 | System | Sets `reject_reason`, `reviewed_by = admin.id`, `reviewed_at = NOW()` (→ ENTITY-PAYMENT-004) |
| 7 | System | Publishes Kafka `refund.rejected` |
| 8 | Notification | Notifies buyer with rejection reason |

---

## Alternate Flows

| Flow | Condition | Action |
|------|-----------|--------|
| A1 | Refund not PENDING | Return 409 "Refund already processed" |
| A2 | Missing reject reason | Return 400 "Reject reason required" |

---

## Postconditions

| # | Condition |
|---|-----------|
| Q1 | REFUNDS.status = REJECTED |
| Q2 | `reject_reason` populated |
| Q3 | `reviewed_by` and `reviewed_at` set |
| Q4 | `refund.rejected` published to Kafka |
| Q5 | Buyer notified of rejection |

---

## Business Rules Cited

| Rule ID | Description |
|---------|-------------|
| BR-PAYMENT-019 | Admin approval gate (rejection path) |
| BR-PAYMENT-024 | Kafka event publishing |

---

## Related Use Cases

| Use Case | Relationship |
|----------|-------------|
| UC-PAYMENT-004 | Create Refund (precedes this) |
| UC-PAYMENT-005 | Approve Refund (alternative outcome) |

### Admin listing endpoints

| Endpoint | Usage |
|----------|-------|
| GET /admin/refunds | List all refunds (paginated, filterable by status/type/seller/date) |
| GET /admin/refunds/{refundId} | Full refund detail with items, evidence, review history, and Stripe ref |

> These admin endpoints support the review workflow in this UC (admin browses pending refunds before rejecting).
