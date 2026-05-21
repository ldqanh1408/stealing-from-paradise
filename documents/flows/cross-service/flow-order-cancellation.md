# Business Flow: Order Cancellation
Scope: Cross-Service (order-service · product-service · payment-service · notification-service)

### Description
Documents the process of cancelling an order, either initiated by the Buyer (manual cancel) or triggered by the System (15-minute payment timeout Saga). It covers stock restoration in Redis/DB and Stripe payment intent cancellation.

### Sequence Diagram

```mermaid
sequenceDiagram
    actor Buyer
    participant GW as API Gateway
    participant OS as Order Service
    participant Kafka as Kafka Broker
    participant PS as Product Service
    participant Redis as Redis Cache
    participant PayS as Payment Service
    participant Stripe as Stripe API
    participant NotifS as Notification Service

    alt Buyer Manual Cancellation
        Buyer->>GW: POST /api/v1/orders/{orderId}/cancel
        GW->>OS: Route cancel request
        OS->>OS: Verify order status = PENDING_PAYMENT
        OS->>OS: Update orders.status = CANCELLED
        OS-->>Buyer: 200 OK (order cancelled)
    else System Timeout Cancellation (15-min)
        OS->>OS: Payout/Payment Cron detects unpaid order
        OS->>OS: Update orders.status = CANCELLED
    end

    OS->>Kafka: Publish event: order.cancelled (orderId, items[])
    
    par Release Reserved Stock
        Kafka->>PS: Consume order.cancelled
        PS->>Redis: INCRBY stock:{variant_id} {qty}
        PS->>PS: UPDATE product_variants SET stock = stock + qty
        PS->>PS: UPDATE stock_reservations SET status = RELEASED
    and Cancel Payment Intent
        Kafka->>PayS: Consume order.cancelled
        PayS->>PayS: Query TRANSACTIONS by parentOrderId
        PayS->>Stripe: Cancel Payment Intent (Stripe.PaymentIntent.cancel)
        Stripe-->>PayS: Cancelled Response
        PayS->>PayS: UPDATE TRANSACTIONS SET status = CANCELLED
    and Send Customer Alert
        Kafka->>NotifS: Consume order.cancelled
        NotifS->>NotifS: Create SSE message
        NotifS-->>Buyer: Push notification "Order Cancelled"
    end
```

### Participant Directory

| Participant | Service Name | Role & Responsibility |
|-------------|--------------|-----------------------|
| **Buyer** | Client Browser | Manually cancels an unpaid order before the 15-minute expiration window. |
| **Order Service** | `order-service` | Tracks order lifecycle, coordinates manual and scheduler cancellations, publishes `order.cancelled`. |
| **Product Service** | `product-service` | Handles stock quantities, tracks stock reservations, listens to order cancellations to release stock. |
| **Redis Cache** | Redis | Maintains the fast, in-memory stock counters for high-concurrency inventory protection. |
| **Payment Service** | `payment-service` | Interacts with Stripe Connect APIs to manage PaymentIntents, records transaction statuses. |
| **Stripe API** | External Service | Processes payment intent cancellations. |
| **Notification Service** | `notification-service` | Listens to Kafka event topics to dispatch SSE notifications to the customer browser. |

### Message & Event Catalog

| Step | Source | Target | Trigger/Payload | Channel | Reference |
|------|--------|--------|-----------------|---------|-----------|
| 1    | Buyer | `order-service` | `POST /orders/{id}/cancel` | HTTP | API-POST-/orders/{id}/cancel |
| 2    | `order-service` | Kafka | Event: `order.cancelled` (orderId, items[]) | Kafka (topic: order.events) | EV-order.cancelled |
| 3    | Kafka | `product-service` | Event: `order.cancelled` | Kafka (topic: order.events) | EV-order.cancelled |
| 4    | Kafka | `payment-service` | Event: `order.cancelled` | Kafka (topic: order.events) | EV-order.cancelled |
| 5    | Kafka | `notification-service` | Event: `order.cancelled` | Kafka (topic: order.events) | EV-order.cancelled |
