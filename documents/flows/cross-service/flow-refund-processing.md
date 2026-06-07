# Business Flow: Refund Processing
Scope: Cross-service (`order-service`, `refund-service`, `payment-service`, `notification-service`)

### Use Case Coverage

| Use case | Status against current code | Evidence | Notes |
|----------|-----------------------------|----------|-------|
| UC-REFUND-001: Create Refund | Implemented via order-service events | `RefundController.createPartialRefund` line 64, `RefundService.onRefundRequested` line 264, `RefundService.onOrderReturnedRts` line 419 | There is no public `POST /refunds` in refund-service; buyer/system creation enters through order-service and Kafka. |
| UC-REFUND-002: Approve Refund | Implemented | `AdminRefundController.approveRefund` line 55, `RefundService.approveRefund` line 153 | Executes Stripe refund and publishes `refund.admin_approved`. |
| UC-REFUND-003: Reject Refund | Implemented | `AdminRefundController.rejectRefund` line 67, `RefundService.rejectRefund` line 223 | Marks refund rejected and publishes `refund.rejected`. |
| UC-ORDER-006: Request Return / RTS | Implemented | `OrderController.returnToSender` line 171, `RefundService.onOrderReturnedRts` line 419 | RTS refund bypasses admin approval and attempts Stripe refund immediately. |

### Sequence Diagram

```mermaid
sequenceDiagram
    actor Buyer
    actor Seller
    actor Admin
    participant Order as Order Service
    participant Kafka as Kafka
    participant Refund as Refund Service
    participant Stripe as Stripe API
    participant Notif as Notification Service

    alt Buyer refund request
        Buyer->>Order: POST /v1/orders/{orderId}/refunds
        Order->>Order: Validate delivered window and refundable quantities
        Order->>Kafka: refund.requested
        Kafka->>Refund: onRefundRequested
        Refund->>Refund: Insert REFUNDS and REFUND_ITEMS as PENDING
        Refund->>Kafka: refund.created
        Kafka->>Notif: Notify admin/buyer
    else Buyer full parent-order refund
        Buyer->>Order: POST /v1/orders/parent/{parentOrderId}/refund
        Order->>Kafka: refund.full_requested
        Kafka->>Refund: onRefundFullRequested
        Refund->>Refund: Create grouped refund records
    else Seller return-to-sender
        Seller->>Order: POST /v1/orders/{orderId}/return-to-sender
        Order->>Kafka: order.returned_rts
        Kafka->>Refund: onOrderReturnedRts
        Refund->>Stripe: Refund PaymentIntent amount
        Refund->>Kafka: refund.rts_completed
    end

    Admin->>Refund: POST /v1/admin/refunds/{refundId}/approve
    Refund->>Refund: Validate refund is PENDING
    Refund->>Stripe: Refund.create(payment_intent, amount)
    Refund->>Refund: Mark SUCCESS and store refund_ref
    Refund->>Kafka: refund.admin_approved
    par Downstream updates
        Kafka->>Order: PaymentKafkaEventBridge.onRefundApproved
        Order->>Order: Update order status to PARTIALLY_REFUNDED or REFUNDED
        Kafka->>Notif: PaymentEventConsumer.onRefundAdminApproved
        Notif-->>Buyer: SSE notification
    end
```

### Implementation Notes

| Concern | Current behavior |
|---------|------------------|
| Refund creation channel | `refund-service` consumes `refund.requested`, `refund.full_requested`, and `order.returned_rts`; it does not expose a buyer-facing create endpoint. |
| Admin review | `refund-service` owns admin listing, detail, approve, and reject endpoints under `/v1/admin/refunds`. |
| Stripe refund | `RefundService.executeStripeRefund` extracts the PaymentIntent from the stored transaction raw response. |
| Transfer reversal | Transfer reversal logic exists inside `RefundService`; there is no payment-service listener for `refund.admin_approved` in current code. |
| Order status update | `order-service` consumes `refund.admin_approved` and `refund.rts_completed`. |

### Gaps To Track

| Gap | Impact |
|-----|--------|
| Old flow text described a Feign call from order-service to refund-service. Current code uses Kafka. | Architecture docs must describe async creation, not direct HTTP creation. |
| UC-REFUND-001 mentions direct `POST /refunds`; current implementation has no such refund-service controller endpoint. | Client-facing contract should point to order-service refund endpoints. |
