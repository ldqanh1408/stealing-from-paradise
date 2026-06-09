# Business Flow: Order Cancellation
Scope: Cross-service (`order-service`, `product-service`, `refund-service`, `payment-service`, `notification-service`)

### Use Case Coverage

| Use case | Status against current code | Evidence | Notes |
|----------|-----------------------------|----------|-------|
| UC-ORDER-003: Cancel Order (Buyer) | Implemented | `OrderController.cancelOrder` line 120, `OrderService.cancelOrder` line 282 | Buyer can cancel `PENDING` or unshipped `PAID` orders. Paid cancellation starts an automatic full-refund request. |
| UC-ORDER-008: Cancel Order (Seller) | Implemented | `OrderService.cancelOrder` line 282, `OrderService.publishAutoFullRefundRequested` line 353, `OrderProcessingSaga` line 190 | Seller can cancel an unshipped `PAID` order with a reason of at least 10 characters. |

### Sequence Diagram

```mermaid
sequenceDiagram
    actor User as Buyer or Seller
    participant GW as API Gateway
    participant OS as Order Service
    participant Saga as Order Processing Saga
    participant Kafka as Kafka
    participant Product as Product Service
    participant Refund as Refund Service
    participant Payment as Payment Service
    participant Notif as Notification Service

    User->>GW: POST /api/v1/orders/{orderId}/cancel
    GW->>OS: Route to /v1/orders/{orderId}/cancel
    OS->>OS: Load order and verify user is buyer or seller
    OS->>OS: Validate cancellable state
    alt Valid cancellation
        OS->>OS: Set status = CANCELLED and save cancel reason
        OS->>Saga: Publish OrderCancelledEvent
        Saga->>Kafka: order.cancelled
        opt Cancelled by seller
            Saga->>Kafka: seller.order_cancelled
        end
        opt Cancelled order was PAID
            OS->>Kafka: refund.full_requested auto_process=true
            Kafka->>Refund: Create and execute full Stripe refund
        end
        par Release product-side state
            Kafka->>Product: Consume order.cancelled
            Product->>Product: Release reservation and restore stock
        and Cancel pending payment intent when applicable
            Kafka->>Payment: Consume order.cancelled
            Payment->>Payment: Cancel Stripe PaymentIntent when still pending
        and Notify users
            Kafka->>Notif: Consume order.cancelled / seller.order_cancelled / refund events
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
| Buyer state rule | Buyer can cancel `PENDING` or `PAID`; `PAID` must not have tracking. |
| Seller state rule | Seller can cancel only `PAID` before shipping and must provide a reason of at least 10 characters. |
| Event bridge | `OrderProcessingSaga` publishes `order.cancelled`; seller cancellations also publish enriched `seller.order_cancelled`. |
| Paid refund side effect | `order-service` publishes `refund.full_requested` with `auto_process=true`; `refund-service` creates and attempts the Stripe refund. |
| Pending payment side effect | `payment-service` still consumes `order.cancelled` for pending PaymentIntent cancellation. |
