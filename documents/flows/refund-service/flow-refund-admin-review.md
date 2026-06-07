# Business Flow: Refund Admin Review
Scope: `refund-service`

### Use Case Coverage

| Use case | Status against current code | Evidence | Notes |
|----------|-----------------------------|----------|-------|
| UC-REFUND-001: Create Refund | Implemented through Kafka consumers | `RefundService.onRefundRequested` line 264, `onRefundFullRequested` line 355, `onOrderReturnedRts` line 419 | Creation is not exposed as a public refund-service REST endpoint. |
| UC-REFUND-002: Approve Refund | Implemented | `AdminRefundController.approveRefund` line 55, `RefundService.approveRefund` line 153 | Calls Stripe refund and publishes `refund.admin_approved`. |
| UC-REFUND-003: Reject Refund | Implemented | `AdminRefundController.rejectRefund` line 67, `RefundService.rejectRefund` line 223 | Publishes `refund.rejected`. |

### Sequence Diagram

```mermaid
sequenceDiagram
    actor Admin
    participant Kafka as Kafka
    participant Refund as Refund Service
    participant DB as PostgreSQL
    participant Stripe as Stripe API
    participant Order as Order Service
    participant Notif as Notification Service

    Kafka->>Refund: refund.requested or refund.full_requested
    Refund->>DB: Insert PENDING refund records
    Refund->>Kafka: refund.created

    Admin->>Refund: GET /v1/admin/refunds
    Refund->>DB: List with filters
    Admin->>Refund: GET /v1/admin/refunds/{refundId}
    Refund->>DB: Load refund and refund items

    alt Approve
        Admin->>Refund: POST /v1/admin/refunds/{refundId}/approve
        Refund->>DB: Validate PENDING refund
        Refund->>Stripe: Refund.create
        Refund->>DB: status = SUCCESS, refund_ref = Stripe refund id
        Refund->>Kafka: refund.admin_approved
        Kafka->>Order: Update order refund state
        Kafka->>Notif: Notify buyer
    else Reject
        Admin->>Refund: POST /v1/admin/refunds/{refundId}/reject
        Refund->>DB: status = REJECTED
        Refund->>Kafka: refund.rejected
        Kafka->>Notif: Notify buyer
    end
```

### Implementation Gaps

| Gap | Impact |
|-----|--------|
| Direct `POST /refunds` is not implemented in refund-service. | Client refund initiation should go through order-service endpoints. |
| `RefundService` owns transfer reversal logic; payment-service does not consume `refund.admin_approved` in current code. | Cross-service flow docs should avoid claiming payment-service performs this listener step. |
