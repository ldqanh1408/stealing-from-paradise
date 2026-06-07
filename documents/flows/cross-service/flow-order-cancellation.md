# Business Flow: Order Cancellation
Scope: Cross-service (`order-service`, `product-service`, `payment-service`, `notification-service`)

### Use Case Coverage

| Use case | Status against current code | Evidence | Notes |
|----------|-----------------------------|----------|-------|
| UC-ORDER-003: Cancel Order (Buyer) | Implemented | `OrderController.cancelOrder` line 120, `OrderService.cancelOrder` line 279 | Code allows the customer who owns the order to cancel while status is `PENDING`. |
| UC-ORDER-008: Cancel Order (Seller) | Partial | `OrderService.cancelOrder` line 279 | Code allows the seller who owns the order to cancel, but only when status is `PENDING`. The use case says seller can cancel `PAID` before shipment, which is not implemented. |

### Sequence Diagram

```mermaid
sequenceDiagram
    actor User as Buyer or Seller
    participant GW as API Gateway
    participant OS as Order Service
    participant Saga as Order Processing Saga
    participant Kafka as Kafka
    participant Product as Product Service
    participant Payment as Payment Service
    participant Notif as Notification Service

    User->>GW: POST /api/v1/orders/{orderId}/cancel
    GW->>OS: Route to /v1/orders/{orderId}/cancel
    OS->>OS: Load order and verify user is buyer or seller
    OS->>OS: Require order.status = PENDING
    alt Valid cancellation
        OS->>OS: Set status = CANCELLED and save cancel reason
        OS->>Saga: Publish OrderCancelledEvent
        Saga->>Kafka: order.cancelled
        opt Cancelled by seller
            Saga->>Kafka: seller.order_cancelled
        end
        par Release or confirm product-side state
            Kafka->>Product: Consume order.cancelled
            Product->>Product: Release reservation and restore stock
        and Cancel pending payment intent
            Kafka->>Payment: Consume order.cancelled
            Payment->>Payment: Mark transaction cancelled
            Payment->>Payment: Cancel Stripe PaymentIntent when still pending
        and Notify users
            Kafka->>Notif: Consume order.cancelled / seller.order_cancelled
            Notif-->>User: SSE notification when connected
        end
        OS-->>User: 200 CancelOrderResponse
    else Invalid state or unauthorized
        OS-->>User: 403 or ORDER_NOT_CANCELLABLE
    end
```

### Implementation Notes

| Concern | Current behavior |
|---------|------------------|
| HTTP contract | `POST /v1/orders/{orderId}/cancel` receives `CancelOrderRequest` and reads `X-User-Role`. |
| State rule | `OrderService.cancelOrder` rejects every status except `PENDING`. |
| Event bridge | `OrderProcessingSaga` publishes `order.cancelled`; seller cancellations also publish `seller.order_cancelled`. |
| Payment side effect | `PaymentService.onOrderCancelled` consumes `order.cancelled` and cancels the Stripe PaymentIntent only when the transaction is still pending. |
| Product side effect | `product-service` consumes order events to release reservations on cancel and payment failure. |

### Gaps To Track

| Gap | Impact |
|-----|--------|
| UC-ORDER-008 documents seller cancellation from `PAID`, but code only accepts `PENDING`. | Seller cannot cancel a paid-but-unshipped order through the current implementation. |
| Existing docs mention `PENDING_PAYMENT`; code uses `PENDING`. | Contract text should use `PENDING` unless the domain model changes. |
