# BR-PAYMENT: Refund Business Rules

**Domain**: Payment Service  
**Feature**: Refund Processing (Buyer Request, Admin Approval, Stripe Execution)  
**References**: [KAFKA_EVENTS.md](../../../docs/services/payment-service/KAFKA_EVENTS.md), [06_PAYMENT_SAGA_FLOW.md](../../../docs/business/06_PAYMENT_SAGA_FLOW.md)

---

## BR-PAYMENT-017: Return Window Eligibility

| Property | Value |
|----------|-------|
| **Rule** | Refund requests MUST be submitted within the return window |
| **Check** | `NOW() < ORDERS.return_window_end` |
| **Window Duration** | `delivered_at` + 7 calendar days |
| **On Expired** | Refund request rejected with "Return window expired" |
| **Cites** | UC-PAYMENT-004, FR-PAYMENT-013 |

---

## BR-PAYMENT-018: Evidence Requirement for Buyer Refunds

| Property | Value |
|----------|-------|
| **Rule** | `BUYER_REQUEST` refund type MUST include evidence images |
| **Field** | `REFUNDS.evidence_images` (JSONB array of MinIO URLs) |
| **Min Images** | At least 1 image required |
| **On Missing** | Validation error: "Evidence images required for refund request" |
| **Cites** | UC-PAYMENT-004, FR-PAYMENT-014 |

---

## BR-PAYMENT-019: Admin Approval Gate

| Property | Value |
|----------|-------|
| **Rule** | All refunds (except RTS auto-refunds) MUST pass admin review before Stripe execution |
| **Decision States** | APPROVED -> proceed to Stripe refund; REJECTED -> notify buyer with `reject_reason` |
| **Review Fields** | `reviewed_by` (ADMIN FK), `reviewed_at` (timestamp), `admin_note` (optional) |
| **Cites** | UC-PAYMENT-005, UC-PAYMENT-006, FR-PAYMENT-015 |

---

## BR-PAYMENT-020: Pre-Payout vs Post-Payout Refund

| Property | Value |
|----------|-------|
| **Rule** | Refund processing differs based on whether payout has occurred |
| **Pre-Payout** | `SELLER_TRANSFERS.status` not yet `PAID_OUT` -> set to `REFUNDED`; no Stripe reversal |
| **Post-Payout** | `SELLER_TRANSFERS.status = PAID_OUT` -> set to `REVERSED`; execute Stripe Transfer reversal |
| **Partial Post-Payout** | Set to `PARTIALLY_REVERSED` |
| **Cites** | UC-PAYMENT-004, UC-PAYMENT-005, FR-PAYMENT-016 |

---

## BR-PAYMENT-021: RTS Auto-Refund

| Property | Value |
|----------|-------|
| **Rule** | Return-To-Sender (RTS) orders auto-generate a FULL refund |
| **Trigger** | Kafka event `order.returned` from Order Service |
| **Type** | `refund_reason_type = RETURN_TO_SENDER` |
| **Amount** | Full order `final_amt` |
| **Admin Review** | NOT required for RTS refunds (auto-approved) |
| **Kafka** | Publishes `refund.rts_completed` on success |
| **Cites** | UC-PAYMENT-004, FR-PAYMENT-008 |

---

## BR-PAYMENT-022: Refund Amount Validation

| Property | Value |
|----------|-------|
| **Rule** | Refund amount MUST NOT exceed the original transaction amount minus existing refunds |
| **Check** | `requested_refund_amount <= (TRANSACTIONS.amount - SUM(existing_refunds.amount))` |
| **On Violation** | Reject with "Refund amount exceeds remaining balance" |
| **FULL Refund** | `type = FULL` -> amount = remaining balance |
| **PARTIAL Refund** | `type = PARTIAL` -> amount specified by buyer |
| **Cites** | UC-PAYMENT-004, FR-PAYMENT-013 |

---

## BR-PAYMENT-023: Refund Grouping by UUID

| Property | Value |
|----------|-------|
| **Rule** | Multiple items refunded in one request share a common `group_ref` UUID |
| **Field** | `REFUNDS.group_ref` = UUID generated at request creation |
| **Purpose** | Enables tracking and bulk admin review of multi-item refunds |
| **REFUND_ITEMS** | All items within the same `group_ref` belong to the same refund request |
| **Cites** | UC-PAYMENT-004 |

---

## BR-PAYMENT-024: Kafka Event Publishing for Refund Lifecycle

| Property | Value |
|----------|-------|
| **Rule** | Each refund state transition publishes a Kafka event |
| **Events** | `refund.requested` (buyer submits), `refund.admin_approved` (admin approves), `refund.rejected` (admin rejects), `refund.rts_completed` (auto-refund done), `refund.stripe_auto` (chargeback) |
| **Consumers** | Notification Service, Order Service, Identity Service |
| **Cites** | UC-PAYMENT-004, UC-PAYMENT-005, UC-PAYMENT-006 |

---

## BR-PAYMENT-025: Return Tracking Number on Admin-Approved Refunds

| Property | Value |
|----------|-------|
| **Rule** | When admin approves a refund that involves physical goods return, a return tracking number MUST be captured |
| **Field** | `REFUND_ITEMS.return_tracking_number` (VARCHAR) |
| **Mandatory** | When refund reason involves failed delivery, defective product requiring return, or RTS |
| **Optional** | When refund does not involve physical goods return (e.g., admin error correction, dispute resolution without return) |
| **Audit** | `REFUND_ITEMS.carrier` stored alongside tracking number for full audit trail |
| **Buyer Notification** | Tracking number included in refund approval notification to buyer |
| **Cites** | UC-PAYMENT-005, FR-PAYMENT-015 |

**Tracking Number Scenarios:**

| Scenario | Tracking Required | Example |
|----------|-------------------|---------|
| Refund due to failed delivery | Mandatory | Return tracking from shipper: VT123456 |
| Refund due to defective product + return | Recommended | Shipper pickup return code |
| RTS (Return To Sender) | Mandatory | Seller-provided return tracking |
| Admin error correction (no return) | Optional | No physical return needed |
| Buyer/Seller dispute (no return) | Optional | Only if goods need return |

---

## Cross-References

| Ref ID | Target |
|--------|--------|
| STATE-PAYMENT-002 | [state-refund.md](../../state-diagrams/payment-service/state-refund.md) |
