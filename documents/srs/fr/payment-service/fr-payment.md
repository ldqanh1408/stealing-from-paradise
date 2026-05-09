# FR-PAYMENT: Functional Requirements

**Domain**: Payment Service  
**Version**: v5.4  
**References**: [02_API_payment_service.md](../../../docs/services/payment-service/02_API_payment_service.md), [06_PAYMENT_SAGA_FLOW.md](../../../docs/business/06_PAYMENT_SAGA_FLOW.md)

---

## FR-PAYMENT-001: Stripe Onboarding Start

| Property | Value |
|----------|-------|
| **Description** | Seller initiates Stripe Connect KYC onboarding |
| **Endpoint** | POST `/stripe/onboarding/start` |
| **Auth** | JWT (SELLER) |
| **Precondition** | Seller has no existing Stripe account with `details_submitted = true` |
| **Action** | Create/retrieve Stripe Express account; call Stripe `accountLinks.create()` |
| **Response** | `{ onboarding_url, expires_at }` (201 Created) |
| **Error** | 409 if already onboarded |
| **Cites** | UC-PAYMENT-001, BR-PAYMENT-002, BR-PAYMENT-003 |

---

## FR-PAYMENT-002: Payment Intent Creation

| Property | Value |
|----------|-------|
| **Description** | System creates a Stripe PaymentIntent when checkout completes |
| **Trigger** | Kafka `payment.requested` from ParentOrderPaymentSaga |
| **Action** | Create TRANSACTION (PENDING), create SELLER_TRANSFER records (PENDING), call Stripe `PaymentIntent.create()` |
| **Idempotency** | Skip if existing transaction with PENDING or SUCCESS status |
| **Response** | Returns `clientSecret` for frontend Stripe modal |
| **Cites** | UC-PAYMENT-002, BR-PAYMENT-007, BR-PAYMENT-008, BR-PAYMENT-011 |

---

## FR-PAYMENT-003: Payment Intent Idempotency

| Property | Value |
|----------|-------|
| **Description** | Duplicate `payment.requested` events must not create duplicate PaymentIntents |
| **Check** | `transactionRepository.findByParentOrderId(parentOrderId)` |
| **Guard** | If existing status is PENDING or SUCCESS -> skip creation |
| **Webhook Guard** | Check `event.id` dedup and transaction status before updating |
| **Cites** | UC-PAYMENT-002, BR-PAYMENT-011 |

---

## FR-PAYMENT-004: Stripe Webhook Processing

| Property | Value |
|----------|-------|
| **Description** | System receives and processes Stripe webhook events |
| **Endpoint** | POST `/stripe/webhook` |
| **Auth** | Stripe-Signature header verification |
| **Events** | `payment_intent.succeeded`, `payment_intent.payment_failed`, `charge.refunded`, `account.updated`, `transfer.created`, `transfer.reversed`, `payout.failed`, `charge.dispute.created`, `charge.dispute.closed` |
| **Cites** | UC-PAYMENT-003, BR-PAYMENT-013 |

---

## FR-PAYMENT-005: Payment Success Handling

| Property | Value |
|----------|-------|
| **Description** | When Stripe confirms payment, update local state and notify downstream |
| **Trigger** | Stripe webhook `payment_intent.succeeded` |
| **Actions** | TRANSACTIONS.status = SUCCESS; SELLER_TRANSFERS.status = AWAITING_DELIVERY; publish Kafka `payment.success` |
| **Downstream** | Order Service marks orders PAID; Notification Service sends confirmation |
| **Cites** | UC-PAYMENT-002, UC-PAYMENT-003, BR-PAYMENT-015 |

---

## FR-PAYMENT-006: Payment Failure Handling

| Property | Value |
|----------|-------|
| **Description** | When Stripe payment fails, update local state and trigger cancellation |
| **Trigger** | Stripe webhook `payment_intent.payment_failed` |
| **Actions** | TRANSACTIONS.status = FAILED; publish Kafka `payment.failed` |
| **Downstream** | Order Service cancels sub-orders; Notification Service alerts buyer |
| **Cites** | UC-PAYMENT-002, UC-PAYMENT-003, BR-PAYMENT-015 |

---

## FR-PAYMENT-007: Payment Timeout Auto-Cancel

| Property | Value |
|----------|-------|
| **Description** | Orders unpaid after 30 minutes are automatically cancelled |
| **Primary** | Axon Deadline fires `onPaymentTimeout()` in OrderProcessingSaga |
| **Safety Net** | JOB-13 polls every minute for expired PENDING orders |
| **Timeout** | `PAYMENT_TIMEOUT_MINUTES` config (default 30) |
| **Cites** | UC-PAYMENT-002, BR-PAYMENT-012 |

---

## FR-PAYMENT-008: RTS Auto-Refund

| Property | Value |
|----------|-------|
| **Description** | Return-To-Sender orders automatically generate full refunds |
| **Trigger** | Kafka `order.returned` from Order Service |
| **Actions** | Create REFUND (type=FULL, refund_reason_type=RETURN_TO_SENDER), check pre/post payout, execute Stripe refund, publish `refund.rts_completed` |
| **Cites** | UC-PAYMENT-004, BR-PAYMENT-021 |

---

## FR-PAYMENT-009: Webhook Signature Verification

| Property | Value |
|----------|-------|
| **Description** | All Stripe webhook requests must pass cryptographic signature verification |
| **Method** | `Webhook.constructEvent(payload, sigHeader, STRIPE_WEBHOOK_SECRET)` |
| **On Failure** | HTTP 400, log security warning |
| **Config** | `STRIPE_WEBHOOK_SECRET` env variable |
| **Cites** | UC-PAYMENT-003, BR-PAYMENT-013 |

---

## FR-PAYMENT-010: Commission Calculation

| Property | Value |
|----------|-------|
| **Description** | Platform commission is calculated per seller transfer |
| **Formula** | `platform_commission_amt = transfer_amount * STRIPE_PLATFORM_FEE_PERCENTAGE / 100` |
| **Default Rate** | 5% |
| **Net Payout** | `net_payout_amount = transfer_amount - platform_commission_amt` |
| **Cites** | UC-PAYMENT-007, BR-PAYMENT-009 |

---

## FR-PAYMENT-011: Delayed Payout Execution

| Property | Value |
|----------|-------|
| **Description** | Seller payouts execute only after delivery + return window expiry |
| **Trigger** | Cron job (ShedLock) checks `status = RETURN_WINDOW AND payout_eligible_at <= NOW()` |
| **Transition** | RETURN_WINDOW -> READY_FOR_PAYOUT |
| **Stripe Call** | Create Stripe Transfer to seller's connected account |
| **On Success** | READY_FOR_PAYOUT -> PAID_OUT; set `payout_at` |
| **On Failure** | Increment `payout_retry_count`; set FAILED after max retries |
| **Cites** | UC-PAYMENT-007, BR-PAYMENT-010, BR-PAYMENT-014 |

---

## FR-PAYMENT-012: Transaction Status Aggregation

| Property | Value |
|----------|-------|
| **Description** | TRANSACTIONS.status reflects the aggregate refund state of the payment |
| **SUCCESS** | Payment completed, no refunds |
| **PARTIALLY_REFUNDED** | Some refunds processed |
| **REFUNDED** | All amounts refunded |
| **Cites** | UC-PAYMENT-002, BR-PAYMENT-016 |

---

## FR-PAYMENT-013: Refund Eligibility Validation

| Property | Value |
|----------|-------|
| **Description** | System validates refund requests before processing |
| **Checks** | (1) Order within return_window_end; (2) Refund amount <= remaining balance; (3) Evidence images provided for BUYER_REQUEST |
| **On Violation** | Return error with specific reason |
| **Cites** | UC-PAYMENT-004, BR-PAYMENT-017, BR-PAYMENT-022 |

---

## FR-PAYMENT-014: Refund Evidence Upload

| Property | Value |
|----------|-------|
| **Description** | Buyer must upload evidence images when requesting a refund |
| **Field** | `evidence_images` (JSONB array of MinIO URLs) |
| **Requirement** | At least 1 image for BUYER_REQUEST type |
| **Storage** | Images uploaded to MinIO, URLs stored in REFUNDS.evidence_images |
| **Cites** | UC-PAYMENT-004, BR-PAYMENT-018 |

---

## FR-PAYMENT-015: Admin Refund Review

| Property | Value |
|----------|-------|
| **Description** | Admin reviews and either approves or rejects refund requests |
| **Approve Endpoint** | PUT `/refunds/{id}/approve` |
| **Reject Endpoint** | PUT `/refunds/{id}/reject` |
| **Auth** | JWT (ADMIN) |
| **Approve Action** | Set status = APPROVED, trigger Stripe refund, publish `refund.admin_approved` |
| **Reject Action** | Set status = REJECTED, set `reject_reason`, publish `refund.rejected` |
| **Cites** | UC-PAYMENT-005, UC-PAYMENT-006, BR-PAYMENT-019 |

---

## FR-PAYMENT-016: Stripe Refund Execution

| Property | Value |
|----------|-------|
| **Description** | Execute the actual refund via Stripe API after admin approval |
| **Pre-Payout** | Stripe `Refund.create()` from platform balance; no reversal needed |
| **Post-Payout** | Stripe Transfer reversal; `SELLER_TRANSFERS.status = REVERSED` |
| **Response** | Set `refund_ref` (Stripe refund ID `re_xxx`), `raw_response` (Stripe payload) |
| **On Stripe Error** | Set status = FAILED, log error |
| **Cites** | UC-PAYMENT-005, BR-PAYMENT-020 |
