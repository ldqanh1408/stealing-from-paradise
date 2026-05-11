# Traceability Matrix: Payment Service

**Domain**: Payment Service  
**Version**: v5.4  
**Generated**: 2026-05-09  
**References**: All payment-service micro-documentation

---

## Entity to Business Rules

| Entity ID | Entity Name | Business Rules |
|-----------|-------------|----------------|
| ENTITY-PAYMENT-001 | Seller Stripe Account | BR-PAYMENT-001, BR-PAYMENT-002, BR-PAYMENT-003, BR-PAYMENT-004, BR-PAYMENT-005, BR-PAYMENT-006 |
| ENTITY-PAYMENT-002 | Transaction | BR-PAYMENT-007, BR-PAYMENT-008, BR-PAYMENT-011, BR-PAYMENT-015, BR-PAYMENT-016 |
| ENTITY-PAYMENT-003 | Seller Transfer | BR-PAYMENT-001, BR-PAYMENT-009, BR-PAYMENT-010, BR-PAYMENT-012, BR-PAYMENT-013, BR-PAYMENT-014, BR-PAYMENT-015 |
| ENTITY-PAYMENT-004 | Refund | BR-PAYMENT-016, BR-PAYMENT-017, BR-PAYMENT-018, BR-PAYMENT-019, BR-PAYMENT-020, BR-PAYMENT-021, BR-PAYMENT-022, BR-PAYMENT-023, BR-PAYMENT-024 |
| ENTITY-PAYMENT-005 | Refund Item | BR-PAYMENT-022, BR-PAYMENT-023 |

---

## Business Rules to Functional Requirements

| BR ID | Description | FR ID(s) |
|-------|-------------|----------|
| BR-PAYMENT-001 | Charges enabled requirement | FR-PAYMENT-011 |
| BR-PAYMENT-002 | Onboarding URL expiry (24h) | FR-PAYMENT-001 |
| BR-PAYMENT-003 | Duplicate account prevention | FR-PAYMENT-001 |
| BR-PAYMENT-004 | Webhook account sync | FR-PAYMENT-004 |
| BR-PAYMENT-005 | KYC requirements | FR-PAYMENT-001 |
| BR-PAYMENT-006 | Refresh link guard | FR-PAYMENT-001 |
| BR-PAYMENT-007 | Single transaction per parent order | FR-PAYMENT-002, FR-PAYMENT-003 |
| BR-PAYMENT-008 | Destination charges mode | FR-PAYMENT-002 |
| BR-PAYMENT-009 | Platform commission (5%) | FR-PAYMENT-010 |
| BR-PAYMENT-010 | Delayed payout after delivery | FR-PAYMENT-011 |
| BR-PAYMENT-011 | Payment intent idempotency | FR-PAYMENT-002, FR-PAYMENT-003 |
| BR-PAYMENT-012 | Payment timeout (30min) | FR-PAYMENT-007 |
| BR-PAYMENT-013 | Webhook signature verification | FR-PAYMENT-009 |
| BR-PAYMENT-014 | Seller charges check | FR-PAYMENT-011 |
| BR-PAYMENT-015 | Kafka event publishing (payment) | FR-PAYMENT-005, FR-PAYMENT-006 |
| BR-PAYMENT-016 | Transaction status propagation | FR-PAYMENT-012 |
| BR-PAYMENT-017 | Return window eligibility | FR-PAYMENT-013 |
| BR-PAYMENT-018 | Evidence requirement | FR-PAYMENT-014 |
| BR-PAYMENT-019 | Admin approval gate | FR-PAYMENT-015 |
| BR-PAYMENT-020 | Pre-payout vs post-payout refund | FR-PAYMENT-016 |
| BR-PAYMENT-021 | RTS auto-refund | FR-PAYMENT-008 |
| BR-PAYMENT-022 | Refund amount validation | FR-PAYMENT-013 |
| BR-PAYMENT-023 | Refund grouping by UUID | (entity-level) |
| BR-PAYMENT-024 | Kafka event publishing (refund) | FR-PAYMENT-015 |

---

## Functional Requirements to Use Cases

| FR ID | Description | Use Cases |
|-------|-------------|-----------|
| FR-PAYMENT-001 | Stripe onboarding start | UC-PAYMENT-001 |
| FR-PAYMENT-002 | Payment intent creation | UC-PAYMENT-002 |
| FR-PAYMENT-003 | Payment intent idempotency | UC-PAYMENT-002 |
| FR-PAYMENT-004 | Stripe webhook processing | UC-PAYMENT-003 |
| FR-PAYMENT-005 | Payment success handling | UC-PAYMENT-002, UC-PAYMENT-003 |
| FR-PAYMENT-006 | Payment failure handling | UC-PAYMENT-002, UC-PAYMENT-003 |
| FR-PAYMENT-007 | Payment timeout auto-cancel | UC-PAYMENT-002 |
| FR-PAYMENT-008 | RTS auto-refund | UC-PAYMENT-004 |
| FR-PAYMENT-009 | Webhook signature verification | UC-PAYMENT-003 |
| FR-PAYMENT-010 | Commission calculation | UC-PAYMENT-007 |
| FR-PAYMENT-011 | Delayed payout execution | UC-PAYMENT-007 |
| FR-PAYMENT-012 | Transaction status aggregation | UC-PAYMENT-002 |
| FR-PAYMENT-013 | Refund eligibility validation | UC-PAYMENT-004 |
| FR-PAYMENT-014 | Refund evidence upload | UC-PAYMENT-004 |
| FR-PAYMENT-015 | Admin refund review | UC-PAYMENT-005, UC-PAYMENT-006 |
| FR-PAYMENT-016 | Stripe refund execution | UC-PAYMENT-005 |

---

## Use Case to State Diagram

| Use Case | State Diagrams Affected |
|----------|------------------------|
| UC-PAYMENT-001 | state-stripe-account (PENDING -> IN_PROGRESS -> COMPLETE) |
| UC-PAYMENT-002 | state-transaction ([*] -> PENDING -> COMPLETED / FAILED) |
| UC-PAYMENT-003 | state-transaction, state-refund, state-stripe-account |
| UC-PAYMENT-004 | state-refund ([*] -> PENDING_REVIEW) |
| UC-PAYMENT-005 | state-refund (PENDING_REVIEW -> APPROVED -> PROCESSING -> COMPLETED) |
| UC-PAYMENT-006 | state-refund (PENDING_REVIEW -> REJECTED) |
| UC-PAYMENT-007 | state-transaction (SELLER_TRANSFERS downstream states) |
| UC-PAYMENT-008 | (read-only, no state transitions) |

---

## Use Case to API Contracts

| Use Case | API Contract(s) |
|----------|-----------------|
| UC-PAYMENT-001 | api-post-stripe-onboarding-start.yaml |
| UC-PAYMENT-002 | api-post-stripe-webhook.yaml (payment_intent events) |
| UC-PAYMENT-003 | api-post-stripe-webhook.yaml |
| UC-PAYMENT-004 | api-post-refunds.yaml |
| UC-PAYMENT-005 | api-put-refunds-approve.yaml (approve endpoint) |
| UC-PAYMENT-006 | api-put-refunds-approve.yaml (reject endpoint) |
| UC-PAYMENT-007 | api-post-stripe-webhook.yaml (transfer.created events) |
| UC-PAYMENT-008 | (GET endpoints documented in API spec, no dedicated contract) |

---

## Kafka Event to Entity

| Kafka Topic | Producing Entity | Affected Entities |
|-------------|-----------------|-------------------|
| `payment.success` | TRANSACTIONS | PARENT_ORDERS, SELLER_TRANSFERS (-> AWAITING_DELIVERY) |
| `payment.failed` | TRANSACTIONS | PARENT_ORDERS (-> CANCELLED) |
| `refund.requested` | REFUNDS | (notification only) |
| `refund.admin_approved` | REFUNDS | REFUND_ITEMS, SELLER_TRANSFERS |
| `refund.rejected` | REFUNDS | (notification only) |
| `refund.rts_completed` | REFUNDS | (notification, order update) |
| `refund.stripe_auto` | REFUNDS | (order update) |
| `stripe.account_suspended` (post-MVP) | SELLER_STRIPE_ACCOUNTS | SELLERS, SELLER_TRANSFERS (future) |
| `stripe.transfer.reversed` | SELLER_TRANSFERS | (order update) |
| `stripe.payout.failed` | SELLER_TRANSFERS | (notification only) |
| `payment.requested` (consumed) | -- | TRANSACTIONS, SELLER_TRANSFERS |
| `order.returned` (consumed) | -- | REFUNDS, REFUND_ITEMS, SELLER_TRANSFERS |

---

## Full Coverage Matrix

```
                    ENTITY  BR    FR    UC    API   STATE
ENTITY-PAYMENT-001    X     X     X     X     X      X
ENTITY-PAYMENT-002    X     X     X     X     X      X
ENTITY-PAYMENT-003    X     X     X     X     -      -
ENTITY-PAYMENT-004    X     X     X     X     X      X
ENTITY-PAYMENT-005    X     X     -     X     -      -

BR-PAYMENT-001..006   -     X     X     X     X      X
BR-PAYMENT-007..016   -     X     X     X     X      X
BR-PAYMENT-017..024   -     X     X     X     X      X

FR-PAYMENT-001..016   -     -     X     X     X      X

UC-PAYMENT-001..008   -     -     -     X     X      X
```

**Key**: X = coverage exists, - = not applicable (e.g., API contracts only for POST/PUT endpoints)
