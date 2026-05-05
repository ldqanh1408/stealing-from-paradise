# C4 Component Level: Order Service

## Overview

- **Name**: Order Service
- **Description**: Axon CQRS-based order management component handling the full order lifecycle from multi-vendor checkout through delivery and returns. Implements a dual-saga orchestration pattern with `OrderProcessingSaga` (per sub-order lifecycle) and `ParentOrderPaymentSaga` (per checkout payment coordination). Uses PostgreSQL for JPA persistence, Axon Framework for event sourcing and saga orchestration, and Kafka for inter-service communication including payment coordination, refund management, stock validation, and notification publishing.
- **Type**: Service (CQRS)
- **Technology**: Java 25, Spring Boot 4.0.4, Axon Framework 4.13.0, PostgreSQL, Kafka

## Purpose

The Order Service is the central order lifecycle orchestrator for the multi-vendor FlashSale marketplace. It manages:

- **Multi-Vendor Checkout**: Single-cart checkout that creates a parent order grouping multiple sub-orders (one per seller). Uses Kafka request-reply to fetch cart items from the product-service and validate shipping addresses with the identity-service, then creates all entities in a single transaction and emits Axon events to start saga orchestration.
- **Dual-Saga Orchestration**: Two Axon sagas coordinate the distributed transaction. The `ParentOrderPaymentSaga` handles the payment phase (requests payment from payment-service, marks sub-orders as PAID on success, cancels on failure). The `OrderProcessingSaga` handles the per-sub-order lifecycle (payment timeout, shipping deadline, delivery, cancellation, return).
- **Order Lifecycle State Machine**: Orders transition through states: PENDING -> PAID -> SHIPPING -> DELIVERED (normal flow) with alternative paths for CANCELLED (by buyer, seller, payment failure, or timeout), RETURNED (via Return To Sender), and PARTIALLY_REFUNDED/REFUNDED (via refund workflows).
- **Payment Coordination**: Bridges Kafka events from payment-service back into Axon events via `PaymentKafkaEventBridge`, converting `payment.success`/`payment.failed` into Axon events that drive the `ParentOrderPaymentSaga`.
- **Refund Management**: Full refund workflows including partial refunds (per sub-order, per item), full parent-order refunds (all sub-orders within 7 days of delivery), and multi-seller partial refunds. Uses Kafka request-reply for refund history queries to payment-service.
- **Return To Sender (RTS)**: Seller-initiated return workflow for SHIPPING orders with evidence image uploads (1-5 images via presigned URLs). Triggers automatic refund creation in payment-service via Kafka.
- **Time-based Events**: Uses Axon `DeadlineManager` for payment timeout (30 minutes) and shipping deadline tracking. The payment timeout auto-cancels unpaid orders. The shipping deadline is monitored by the worker-service for auto-delivery.
- **Seller Dashboard**: Aggregated metrics for sellers including orders today, pending orders, and monthly revenue.

The service implements CQRS (Command Query Responsibility Segregation) with Axon Framework: commands create events, events are handled by sagas (write side) and update JPA entities (read side), and Kafka bridges connect to external services.

## Software Features

- **Multi-Vendor Checkout**: Creates a ParentOrder (group) and per-seller sub-orders from cart items. Fetches address data (identity-service) and cart items (product-service) in parallel via Kafka request-reply. Emits `OrderCreatedEvent` per sub-order and `ParentOrderCheckoutCreatedEvent`.
- **Parent Order Grouping**: Groups all sub-orders from a single checkout under a parent order for unified payment, refund, and tracking. The parent order tracks total amount across all sellers.
- **OrderProcessingSaga**: Per-sub-order saga managing: PENDING -> (payment timeout auto-cancel at 30 min | PAID on payment success) -> SHIPPING (seller tracking update) -> (DELIVERED via buyer confirmation or auto-delivery | RETURNED via RTS | CANCELLED). Publishes corresponding Kafka events for notification-service.
- **ParentOrderPaymentSaga**: Per-parent-order saga managing the payment phase: publishes `payment.requested` to Kafka on checkout, updates all sub-orders to PAID on `ParentOrderPaymentSucceededEvent`, cancels all sub-orders on `ParentOrderPaymentFailedEvent`. Uses pessimistic locking to prevent concurrent saga conflicts.
- **Payment Timeout**: Orders in PENDING status are automatically cancelled after 30 minutes via Axon `DeadlineManager` in the `OrderProcessingSaga`. Publishes `order.auto_cancelled` to Kafka.
- **Shipping Management**: Sellers update tracking numbers for PAID orders (transitions to SHIPPING). Default 3-day shipping deadline is set. The worker-service handles auto-delivery via JOB-22.
- **Return To Sender (RTS)**: Sellers can confirm RTS for SHIPPING orders with 1-5 evidence images (uploaded via presigned URLs). Emits `OrderReturnedEvent` and publishes `order.returned_rts` to Kafka, triggering auto-refund in payment-service.
- **Partial Refunds**: Buyers can request partial refunds for specific order items with reasons and evidence. Validates refundable quantity (total - already refunded). Publishes `refund.requested` to Kafka.
- **Full Refunds**: Buyers can request full refunds for parent orders (all delivered sub-orders within 7-day window). Creates N refund records in payment-service with a shared `group_ref` UUID.
- **Multi-Seller Partial Refunds**: Buyers can request partial refunds across multiple sub-orders in a single request. Groups refund items by sub-order and publishes one `refund.requested` event per sub-order.
- **Refund History Queries**: Uses Kafka request-reply to payment-service for refund listing (by order, by buyer, with filters) and refund detail retrieval.
- **Order Cancellation**: PENDING orders can be cancelled by buyers or sellers with reason tracking. Cancelled-by field records the actor (BUYER/SELLER/SYSTEM/PAYMENT_FAILED).
- **Seller Dashboard**: Aggregated metrics: total products, orders today, pending orders, monthly revenue, active products.
- **Kafka Request-Reply Pattern**: `KafkaReplyService` replaces REST inter-service calls with correlation-ID-based Kafka request-reply for cart items, addresses, refund history, and payment status queries.
- **Kafka Event Bridge**: `PaymentKafkaEventBridge` translates payment-service Kafka events into Axon events and order status updates.
- **Pessimistic Locking**: `ParentOrderRepository.findByIdWithPessimisticLock()` and `OrderRepository.findAllByParentOrderIdAndStatusWithLock()` prevent `ObjectOptimisticLockingFailureException` during concurrent saga transactions.
- **Dev Data Seeding**: Seeds 10 parent orders with sub-orders covering all lifecycle states (COMPLETED, SHIPPED, CONFIRMED, DELIVERED, PENDING, CANCELLED, SHIPPING) for development and testing.

## Code Elements

This component contains the following code-level elements:

- [c4-code-backend-order-service.md](./c4-code-backend-order-service.md) -- Full code-level documentation for the Order Service

### Key Classes

| Category | Classes |
|----------|---------|
| **Entry Point** | `OrderServiceApplication` |
| **Controllers** | `OrderController` (10 endpoints), `RefundController` (8 endpoints) |
| **Services** | `OrderService`, `KafkaReplyService`, `PaymentKafkaEventBridge` |
| **Axon Commands** | `CreateOrderCommand`, `CancelOrderCommand` |
| **Axon Events** | `OrderCreatedEvent`, `OrderPaidEvent`, `OrderShippedEvent`, `OrderDeliveredEvent`, `OrderReturnedEvent`, `OrderCancelledEvent`, `ParentOrderCheckoutCreatedEvent`, `ParentOrderPaymentSucceededEvent`, `ParentOrderPaymentFailedEvent` |
| **Axon Sagas** | `OrderProcessingSaga`, `ParentOrderPaymentSaga` |
| **Domain Models** | `ParentOrder` (JPA entity), `Order` (JPA entity), `OrderItem` (JPA entity) |
| **Repositories** | `ParentOrderRepository`, `OrderRepository`, `OrderItemRepository` |
| **Client DTOs** | `AddressInfo`, `CartItemInfo` |
| **Configuration** | `AxonConfig`, `KafkaConfig`, `KafkaTopicConfig`, `SecurityConfig`, `SecurityFilterConfig`, `OrderDevDataLoader` |
| **DTOs** | 6 request DTOs, 13 response DTOs |

## Interfaces

### REST API (External -- via API Gateway)

**Buyer Endpoints:**

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/v1/orders/checkout` | BUYER | Multi-vendor checkout from cart (addressId + itemIds) |
| `GET` | `/v1/orders` | BUYER | List buyer's orders with status/date filters and pagination |
| `GET` | `/v1/orders/{orderId}` | Authenticated | Get sub-order detail (buyer or seller owner) |
| `GET` | `/v1/orders/parent/{parentOrderId}` | BUYER | Get parent order detail with all sub-orders |
| `POST` | `/v1/orders/{orderId}/cancel` | Authenticated | Cancel PENDING order (buyer or seller) |
| `POST` | `/v1/orders/{orderId}/confirm-received` | BUYER | Confirm delivery (SHIPPING -> DELIVERED) |
| `POST` | `/v1/orders/{orderId}/refunds` | BUYER | Request partial refund for one sub-order |
| `POST` | `/v1/orders/parent/{parentOrderId}/refund` | BUYER | Request full refund of parent order |
| `POST` | `/v1/orders/parent/{parentOrderId}/refunds/partial` | BUYER | Multi-seller partial refund |
| `GET` | `/v1/orders/{orderId}/refunds` | BUYER/SELLER/ADMIN | List refund history for sub-order |
| `GET` | `/v1/orders/{orderId}/refunds/{refundId}` | BUYER/ADMIN | Get refund detail |
| `GET` | `/v1/orders/{orderId}/refunds/presigned-url` | BUYER | Get presigned URL for evidence upload |
| `GET` | `/v1/orders/refunds` | BUYER | List all refunds for buyer |
| `GET` | `/v1/orders/parent/{parentOrderId}/refund` | BUYER/ADMIN | Get full refund status |

**Seller Endpoints:**

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `PUT` | `/v1/orders/{orderId}/tracking` | SELLER | Update tracking number (PAID -> SHIPPING) |
| `POST` | `/v1/orders/{orderId}/return-to-sender` | SELLER | RTS with evidence images (SHIPPING -> RETURNED) |
| `GET` | `/v1/sellers/me/orders` | SELLER | List seller's orders with filters |
| `GET` | `/v1/sellers/me/dashboard` | SELLER | Seller dashboard stats |

### Kafka Topics

**Consumed (from payment-service, product-service, identity-service):**

| Topic | Purpose |
|-------|---------|
| `payment.success` | Payment succeeded -- bridged to `ParentOrderPaymentSucceededEvent` (Axon) |
| `payment.failed` | Payment failed -- bridged to `ParentOrderPaymentFailedEvent` (Axon) |
| `refund.admin_approved` | Admin approved refund -- updates order status to PARTIALLY_REFUNDED/REFUNDED |
| `refund.rts_completed` | RTS auto-refund completed -- logged for confirmation |
| `order.cart_items.response` | Cart item enrichment reply (request-reply) |
| `order.address.response` | Address validation reply (request-reply) |
| `order.refunds.response` | Refund history reply (request-reply) |
| `order.payment_status.response` | Payment status reply (request-reply) |

**Produced (to payment-service, product-service, notification-service):**

| Topic | Purpose |
|-------|---------|
| `payment.requested` | Request payment from payment-service (ParentOrderPaymentSaga) |
| `refund.requested` | Request partial refund from payment-service |
| `refund.full_requested` | Request full parent-order refund from payment-service |
| `order.created` | Sub-order created (notification-service) |
| `order.cancelled` | Sub-order cancelled by buyer/seller (notification-service) |
| `order.auto_cancelled` | Sub-order auto-cancelled by payment timeout (notification-service) |
| `seller.order_cancelled` | Seller-specific order cancellation notification |
| `order.shipped` | Tracking number updated, order shipped (notification-service) |
| `order.delivered` | Order delivered/confirmed (notification-service) |
| `order.returned_rts` | Return to sender confirmed (payment-service for auto-refund) |
| `order.checkout_completed` | Checkout successful (product-service to clean cart) |
| `order.cart_items.request` | Request-reply: fetch cart items from product-service |
| `order.address.request` | Request-reply: fetch/validate address from identity-service |
| `order.refunds.request` | Request-reply: fetch refund history from payment-service |
| `order.payment_status.request` | Request-reply: fetch payment status from payment-service |
| `order.stock_check.request` | Request-reply: validate stock with product-service (declared) |

### Axon Event Bus (Internal)

| Event | Publisher | Consumer | Purpose |
|-------|-----------|----------|---------|
| `OrderCreatedEvent` | `OrderService.checkout()` | `OrderProcessingSaga` | Start sub-order saga, schedule payment timeout |
| `OrderCancelledEvent` | `OrderService.cancelOrder()` | `OrderProcessingSaga` | End sub-order saga, cancel deadlines |
| `OrderPaidEvent` | `ParentOrderPaymentSaga` | `OrderProcessingSaga` | Cancel payment timeout, transition to PAID |
| `OrderShippedEvent` | `OrderService.updateTracking()` | `OrderProcessingSaga` | Schedule shipping deadline |
| `OrderDeliveredEvent` | `OrderService.confirmReceived()` | `OrderProcessingSaga` | End sub-order saga |
| `OrderReturnedEvent` | `OrderService.returnToSender()` | `OrderProcessingSaga` | End sub-order saga |
| `ParentOrderCheckoutCreatedEvent` | `OrderService.checkout()` | `ParentOrderPaymentSaga` | Start payment saga, publish payment.requested |
| `ParentOrderPaymentSucceededEvent` | `PaymentKafkaEventBridge` | `ParentOrderPaymentSaga` | Mark sub-orders PAID, end payment saga |
| `ParentOrderPaymentFailedEvent` | `PaymentKafkaEventBridge` | `ParentOrderPaymentSaga` | Cancel sub-orders, end payment saga |

### Order Status State Machine

```
[*] --> PENDING (checkout)
PENDING --> PAID (payment.success via ParentOrderPaymentSaga)
PENDING --> CANCELLED (cancel by BUYER/SELLER)
PENDING --> CANCELLED (payment timeout, 30 min, OrderProcessingSaga)
PAID --> SHIPPING (update tracking by SELLER)
SHIPPING --> DELIVERED (confirm received by BUYER)
SHIPPING --> DELIVERED (auto-delivery by worker-service JOB-22)
SHIPPING --> RETURNED (return to sender by SELLER)
DELIVERED --> PARTIALLY_REFUNDED (partial refund approved)
DELIVERED --> REFUNDED (full refund approved)
PARTIALLY_REFUNDED --> PARTIALLY_REFUNDED (additional partial refund)
PARTIALLY_REFUNDED --> REFUNDED (remaining full refund)
CANCELLED --> [*]
RETURNED --> [*]
DELIVERED --> [*]
REFUNDED --> [*]
```

## Dependencies

### Other Components

| Component | Interaction | Protocol |
|-----------|-------------|----------|
| **Payment Service** | Sends payment requests and refund requests; receives payment status and refund history via Kafka request-reply; receives payment lifecycle events | Kafka (events + request-reply) |
| **Product Service** | Fetches cart items and validates stock during checkout via Kafka request-reply; sends checkout_completed to clean cart | Kafka (request-reply + events) |
| **Identity Service** | Validates and fetches shipping addresses via Kafka request-reply; uses internal REST for user role queries | Kafka + REST |
| **Notification Service** | Publishes order lifecycle events for buyer/seller notification delivery (indirect via Kafka) | Kafka |

### External Systems

| System | Purpose | Configuration |
|--------|---------|---------------|
| **PostgreSQL** | Persistent storage for `parent_orders`, `orders`, `order_items`, Axon token store, Axon saga store | Spring Data JPA + Flyway migrations |
| **Kafka** | Inter-service communication: payment coordination, cart/address fetch, refund queries, lifecycle notifications | Spring Kafka, idempotent producer, manual consumer commit |
| **Axon Server** (embedded) | CQRS event bus for internal command/event/saga routing within the service | Axon Framework embedded |
| **Eureka** | Service discovery registration | Spring Cloud Netflix Eureka Client |
| **API Gateway** | JWT decoding, request routing, `X-User-*` header injection | Stateless, header-based |

## Component Diagram

```mermaid
C4Component
    title Component Diagram for Order Service Container

    Container_Boundary(order_service, "Order Service") {
        Component(order_controller, "Order Controller", "Spring REST Controller", "Multi-vendor checkout, order listing, cancellation, tracking, delivery confirmation, RTS, seller dashboard.")
        Component(refund_controller, "Refund Controller", "Spring REST Controller", "Partial/full refund requests, refund history queries, evidence presigned URLs.")
        Component(order_service_svc, "Order Service", "Spring Service", "Core business logic: checkout orchestration, order queries, cancellations, tracking, delivery, RTS, saga event emission.")
        Component(kafka_reply_service, "Kafka Reply Service", "Spring Service", "Synchronous Kafka request-reply: correlation ID matching, 5s timeout, pending request map.")
        Component(payment_kafka_bridge, "Payment Kafka Event Bridge", "Kafka Listener", "Bridges payment.success/failed to Axon events; updates order status on refund approval.")
        Component(axon_event_gateway, "Axon Event Gateway", "Axon Framework", "Publishes Axon events (OrderCreatedEvent, OrderCancelledEvent, etc.) to the internal Axon event bus.")
        Component(order_processing_saga, "OrderProcessingSaga", "Axon Saga", "Per-sub-order lifecycle: PENDING -> PAID -> SHIPPING -> DELIVERED. Payment timeout (30 min), shipping deadline, Kafka event publishing.")
        Component(parent_order_payment_saga, "ParentOrderPaymentSaga", "Axon Saga", "Per-parent-order payment coordination: publishes payment.requested, marks sub-orders PAID on success, cancels on failure. Pessimistic locking.")
        Component(axon_deadline_manager, "Axon DeadlineManager", "Axon Framework", "Time-based events: payment timeout and shipping deadline scheduling/cancellation.")
        ComponentDb(parent_order_entity, "ParentOrder", "JPA Entity", "parent_orders table: customerId, totalAmt, finalAmt, version (optimistic lock), sub-orders collection.")
        ComponentDb(order_entity, "Order", "JPA Entity", "orders table: sellerId, orderCode (unique), status, shippingAddress (JSONB), trackingNumber, shippingDeadline, version.")
        ComponentDb(order_item_entity, "OrderItem", "JPA Entity", "order_items table: skuCode, nameSnapshot, priceSnapshot, quantity, refundedQuantity, fsItemId.")
        ComponentDb(axon_token_store, "Axon Token Store", "JPA Token Store", "Tracks event processing position for event handlers. Uses XStream serializer with BYTEA columns.")
        ComponentDb(axon_saga_store, "Axon Saga Store", "JPA Saga Store", "Persists saga state for OrderProcessingSaga and ParentOrderPaymentSaga instances.")
    }

    Container_Ext(api_gateway, "API Gateway", "JWT decoding, request routing")
    Container_Ext(payment_service, "Payment Service", "Stripe payment processing")
    Container_Ext(product_service, "Product Service", "Product catalog and cart")
    Container_Ext(identity_service, "Identity Service", "User authentication, addresses")
    Container_Ext(postgresql, "PostgreSQL", "order_service schema + Axon stores")
    Container_Ext(kafka, "Apache Kafka", "Event Bus")
    Container_Ext(eureka, "Eureka", "Service Discovery")

    Rel(api_gateway, order_controller, "Routes HTTP requests", "REST")
    Rel(api_gateway, refund_controller, "Routes HTTP requests", "REST")
    Rel(order_controller, order_service_svc, "Calls", "Java method")
    Rel(refund_controller, order_service_svc, "Calls", "Java method")
    Rel(refund_controller, kafka_reply_service, "Queries refunds", "Java method")
    Rel(order_service_svc, kafka_reply_service, "Fetches cart/address", "Java method")
    Rel(order_service_svc, axon_event_gateway, "Publishes Axon events", "Axon EventBus")
    Rel(order_service_svc, order_entity, "Manages", "JPA")
    Rel(order_service_svc, parent_order_entity, "Manages", "JPA")
    Rel(order_service_svc, order_item_entity, "Manages", "JPA")

    Rel(axon_event_gateway, order_processing_saga, "Dispatches OrderCreatedEvent, OrderCancelledEvent, etc.", "Axon EventBus")
    Rel(axon_event_gateway, parent_order_payment_saga, "Dispatches ParentOrderCheckoutCreatedEvent", "Axon EventBus")
    Rel(order_processing_saga, axon_deadline_manager, "Schedules/cancels deadlines", "Axon DeadlineManager")
    Rel(parent_order_payment_saga, parent_order_entity, "Reads/writes with pessimistic lock", "JPA")
    Rel(parent_order_payment_saga, order_entity, "Updates sub-order status", "JPA")
    Rel(parent_order_payment_saga, axon_event_gateway, "Publishes OrderPaidEvent/OrderCancelledEvent", "Axon EventBus")

    Rel(order_processing_saga, kafka, "Publishes order lifecycle events", "Kafka")
    Rel(parent_order_payment_saga, kafka, "Publishes payment.requested", "Kafka")
    Rel(order_service_svc, kafka, "Publishes checkout_completed, refund events", "Kafka")

    Rel(payment_kafka_bridge, kafka, "Consumes payment.success/failed, refund events", "Kafka")
    Rel(payment_kafka_bridge, axon_event_gateway, "Publishes ParentOrderPayment*Events", "Axon EventBus")
    Rel(payment_kafka_bridge, order_entity, "Updates order status on refund approval", "JPA")

    Rel(kafka, payment_service, "Payment and refund coordination", "Kafka")
    Rel(kafka_reply_service, kafka, "Cart items request-reply", "Kafka")
    Rel(kafka, product_service, "Cart data and checkout cleanup", "Kafka")
    Rel(kafka_reply_service, kafka, "Address request-reply", "Kafka")
    Rel(kafka, identity_service, "Address lookups", "Kafka")

    Rel(order_entity, postgresql, "Persisted to", "JDBC")
    Rel(parent_order_entity, postgresql, "Persisted to", "JDBC")
    Rel(order_item_entity, postgresql, "Persisted to", "JDBC")
    Rel(axon_token_store, postgresql, "Persisted to", "JDBC")
    Rel(axon_saga_store, postgresql, "Persisted to", "JDBC")

    Rel(order_service, eureka, "Registers with", "Eureka Client")
```
