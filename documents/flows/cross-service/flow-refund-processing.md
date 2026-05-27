# Business Flow: Refund Processing (Non-RTS)
Scope: Cross-Service (order-service · refund-service · payment-service · notification-service)

### Description
Documents the standard (non-Return-To-Sender) refund lifecycle. This includes the Buyer submitting a refund request with evidence, Admin review and approval gates, calling the Stripe Refund API, and initiating Seller transfer reversals in the Payment Service.

### Sequence Diagram

```mermaid
sequenceDiagram
    actor Buyer
    actor Admin
    participant GW as API Gateway
    participant OS as Order Service
    participant RefS as Refund Service
    participant Kafka as Kafka Broker
    participant PayS as Payment Service
    participant Stripe as Stripe API
    participant NotifS as Notification Service

    Buyer->>GW: POST /api/v1/orders/{orderId}/refunds (items, reason, images)
    GW->>OS: Route refund request
    OS->>OS: Verify order.status = DELIVERED & within return window
    OS->>RefS: Create Pending Refund (Feign Client)
    RefS->>RefS: Upload evidence to MinIO
    RefS->>RefS: INSERT INTO refunds (status=PENDING, type=PARTIAL/FULL)
    RefS-->>OS: Pending Refund Created (refundId)
    OS-->>Buyer: 200 OK (Refund Request Submitted)

    RefS->>Kafka: Publish event: refund.created
    Kafka->>NotifS: Consume event, notify admin dashboard

    Note over Admin, RefS: Admin Review Process

    Admin->>GW: POST /api/v1/admin/refunds/{refundId}/approve
    GW->>RefS: Route approval request
    RefS->>RefS: Verify refund.status = PENDING
    RefS->>Stripe: Execute refund (Stripe.Refund.create from Transaction.stripe_payment_intent_id)
    Stripe-->>RefS: Refund Success (Stripe ID: re_xxxx)
    RefS->>RefS: UPDATE refunds SET status=SUCCESS, refund_ref=re_xxxx
    RefS-->>Admin: 200 OK (Refund Approved)

    RefS->>Kafka: Publish event: refund.admin_approved (refundId, orderId, amount)

    par Reverse Seller Transfer
        Kafka->>PayS: Consume refund.admin_approved
        PayS->>Stripe: Reverse transfer (Stripe.Transfer.createReversal for seller's acct)
        Stripe-->>PayS: Reversal Confirmed
        PayS->>PayS: UPDATE transactions SET status=PARTIALLY_REFUNDED / REFUNDED
    and Update Order Status
        Kafka->>OS: Consume refund.admin_approved
        OS->>OS: UPDATE order_items SET status=REFUNDED, refunded_quantity = qty
        OS->>OS: Update order.status if all items refunded
    and Notify Buyer
        Kafka->>NotifS: Consume refund.admin_approved
        NotifS->>NotifS: Send SSE notification "Refund Approved & Processed"
        NotifS-->>Buyer: Push notification
    end
```

### Participant Directory

| Participant | Service Name | Role & Responsibility |
|-------------|--------------|-----------------------|
| **Buyer** | Client Browser | Requests refunds for defective or missing items within the return window. |
| **Admin** | Admin Dashboard | Reviews evidence images and details to approve or reject the request. |
| **Order Service** | `order-service` | Handles buyer-facing API endpoints, checks return windows, and tracks item-level refund quantities. |
| **Refund Service** | `refund-service` | Manages refund database records, integrates with MinIO for evidence storage, and executes Stripe refunds. |
| **Payment Service** | `payment-service` | Reverses Seller transfers on Stripe Connect to recoup the platform's paid-out funds. |
| **Stripe API** | External Service | Processes refunds and transfer reversals. |
| **Notification Service** | `notification-service` | Sends notifications to buyers and alert updates to administrators. |

### Message & Event Catalog

| Step | Source | Target | Trigger/Payload | Channel | Reference |
|------|--------|--------|-----------------|---------|-----------|
| 1    | Buyer | `order-service` | `POST /orders/{orderId}/refunds` | HTTP | API-POST-/orders/{orderId}/refunds |
| 2    | `order-service` | `refund-service` | Create Refund Request | Feign (HTTP) | Internal API |
| 3    | `refund-service` | Kafka | Event: `refund.created` | Kafka (topic: refund.events) | EV-refund.created |
| 4    | Admin | `refund-service` | `POST /admin/refunds/{id}/approve` | HTTP | API-POST-/admin/refunds/{id}/approve |
| 5    | `refund-service` | Stripe | Create Refund `re_xxx` | HTTPS API | External Stripe API |
| 6    | `refund-service` | Kafka | Event: `refund.admin_approved` | Kafka (topic: refund.events) | EV-refund.admin_approved |
| 7    | Kafka | `payment-service` | Event: `refund.admin_approved` | Kafka (topic: refund.events) | EV-refund.admin_approved |
| 8    | Kafka | `order-service` | Event: `refund.admin_approved` | Kafka (topic: refund.events) | EV-refund.admin_approved |
| 9    | Kafka | `notification-service` | Event: `refund.admin_approved` | Kafka (topic: refund.events) | EV-refund.admin_approved |
