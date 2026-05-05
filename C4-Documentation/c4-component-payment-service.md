# C4 Component Level: Payment Service

## Overview

- **Name**: Payment Service
- **Description**: Payment processing component handling Stripe Connect payments, multi-vendor fund transfers, refund management (partial, full, RTS auto-refund), seller Stripe Connect Express onboarding, and payment lifecycle event coordination. Uses PostgreSQL for JPA persistence, Kafka for async event-driven communication with order-service and other components, and the Stripe Java SDK for payment gateway integration. Implements CQRS patterns via Axon Framework dependency (used transactionally, sagas reside in order-service) with Stripe webhook-driven event processing.
- **Type**: Service (CQRS)
- **Technology**: Java 25, Spring Boot 4.0.4, Axon Framework 4.13.0, PostgreSQL, Stripe Connect (Express), Kafka

## Purpose

The Payment Service is the payment orchestration and Stripe integration component for the FlashSale marketplace. It manages:

- **Payment Processing**: Receives `payment.requested` Kafka events from order-service's `ParentOrderPaymentSaga`, creates Stripe PaymentIntents, persists Transaction records, and creates per-seller SellerTransfer records for multi-vendor fund distribution. Stripe webhooks drive the actual payment state transitions (succeeded, failed, canceled).
- **Multi-Vendor Transfers**: After a successful payment, creates Stripe Connect transfers from the platform account to each seller's Express connected account. Transfers are proportional to each seller's sub-order amount minus the platform fee.
- **Stripe Webhook Processing**: The central webhook endpoint (`/v1/stripe/webhooks`) handles 20+ Stripe event types including PaymentIntent lifecycle, charge events, refund events, transfer events, payout events, account updates, and dispute events. All webhook payloads are verified using Stripe-Signature headers.
- **Seller Stripe Connect Onboarding**: Full Express account onboarding lifecycle: account creation (with card_payments and transfers capabilities), AccountLink generation (24-hour validity), onboarding status derivation from Stripe account state, link refresh, and Express dashboard single-use login links.
- **Refund Management**: Comprehensive refund workflows covering partial refunds (per sub-order, per item), full parent-order refunds (shared `group_ref` UUID across sub-orders), admin approval/rejection workflow, and automatic RTS-triggered refunds (no admin approval needed). Executes Stripe refunds and proportional seller transfer reversals.
- **Kafka Request-Reply**: Responds to order-service queries for refund history (by order, by buyer, filtered/paginated) and payment status (by parent order ID) via correlation-ID-based Kafka request-reply.
- **Payment Cancellation**: Listens to `order.cancelled` and `order.auto_cancelled` events from order-service to cancel Stripe PaymentIntents that are still in a pending state, ensuring funds are not captured for cancelled orders.
- **Dispute Management**: Monitors Stripe dispute events (`charge.dispute.created`, `charge.dispute.closed`) and publishes alert events to Kafka for downstream notification and admin action.
- **Seller Earnings**: Aggregates seller transfer records to compute total earnings, available balance, and pending balance with platform fee deduction.

The service uses Axon Framework as a dependency but does NOT use Axon's aggregate/saga annotations for its own sagas. The `ParentOrderPaymentSaga` concept is realized via event-driven coordination through Kafka consumers/producers and Stripe webhook handlers, while the Axon dependency primarily supports transactional infrastructure patterns.

## Software Features

- **Stripe PaymentIntent Creation**: On `payment.requested`, creates a Stripe PaymentIntent with the parent order amount, persists a Transaction record in PENDING status, and creates SellerTransfer records for each sub-order seller with calculated transfer amounts (minus platform fee).
- **PaymentIntent Idempotency**: Skips payment creation if a transaction already exists in PENDING or SUCCESS state, preventing duplicate charges.
- **Stripe Webhook Processing**: Handles 20+ event types from Stripe, parsing JSON payloads, verifying Stripe-Signature headers, and dispatching to type-specific handlers:
  - `payment_intent.succeeded` -- Transaction -> SUCCESS, create seller transfers via Stripe Connect, publish `payment.success`
  - `payment_intent.failed` -- Transaction -> FAILED, publish `payment.failed`
  - `payment_intent.canceled` -- Transaction -> CANCELLED, publish `payment.failed`
  - `charge.succeeded` / `charge.failed` -- Idempotent fallback for charge events
  - `charge.refunded` -- Publish `refund.stripe_auto` for externally-triggered refunds
  - `charge.refund.updated` -- Sync refund status from Stripe
  - `transfer.created` / `transfer.updated` -- Record `stripe_transfer_id` on SellerTransfer
  - `transfer.reversed` -- SellerTransfer -> REVERSED, publish `stripe.transfer_reversed`
  - `payout.*` -- Payout lifecycle monitoring (created, updated, paid, failed)
  - `account.updated` -- Sync Stripe Connect account status (charges_enabled, payouts_enabled, details_submitted)
  - `charge.dispute.*` -- Publish dispute alerts to Kafka
- **Multi-Vendor Fund Transfers**: After payment confirmation, creates Stripe Connect transfers from the platform to each seller's Express account. Each seller receives their sub-order amount minus the platform fee percentage. Transfer IDs are recorded on SellerTransfer records.
- **Stripe Connect Express Onboarding**: Creates Express accounts with card_payments + transfers capabilities. Generates 24-hour AccountLinks for KYC flow. Derives onboarding status (PENDING -> IN_PROGRESS -> COMPLETE / SUSPENDED) from Stripe account state. Provides Express dashboard login links.
- **Partial Refund Workflow**: Buyer-initiated partial refunds are received via `refund.requested` Kafka event. Creates Refund + RefundItem records in PENDING status awaiting admin approval. Tracks refund reason type (DEFECTIVE, CHANGE_OF_MIND, BUYER_CANCEL, etc.) and evidence images.
- **Full Refund Workflow**: Buyer-initiated full parent-order refunds via `refund.full_requested` create N Refund records (one per sub-order) with a shared `group_ref` UUID for coordinated tracking.
- **Admin Refund Approval**: Admin approves refunds via REST endpoint. Executes the Stripe refund on the PaymentIntent, proportionally reverses the seller's Stripe Connect transfer, sets Refund status to SUCCESS, and publishes `refund.admin_approved` to Kafka for order-service status update.
- **Admin Refund Rejection**: Admin rejects refunds with a mandatory reason. Sets Refund status to REJECTED and publishes `refund.rejected` to Kafka.
- **RTS Auto-Refund**: Receives `order.returned_rts` from order-service. Auto-creates and executes the refund without admin approval, reverses the seller transfer, and publishes `refund.rts_completed`.
- **Seller Earnings Dashboard**: Aggregates all SellerTransfer records by seller, computing total earnings, available balance, and pending balance with platform fee deduction.
- **Kafka Request-Reply for Queries**: Responds to `order.refunds.request` (refund list by order/buyer with filters) and `order.payment_status.request` (transaction status by parent order ID) using correlation ID matching.
- **Payment Cancellation on Order Cancel**: Consumes `order.cancelled` and `order.auto_cancelled` from order-service. Cancels the Stripe PaymentIntent if still pending, sets Transaction to CANCELLED, and publishes `payment.failed` for saga cleanup.
- **Dev Data Seeding**: Seeds 5 active seller Stripe accounts (all charges/payouts enabled), 10 transactions in various states, corresponding seller transfers, and 4 refund scenarios (COMPLETED full, PENDING partial with items, REJECTED, RTS_COMPLETED).

## Code Elements

This component contains the following code-level elements:

- [c4-code-backend-payment-service.md](./c4-code-backend-payment-service.md) -- Full code-level documentation for the Payment Service

### Key Classes

| Category | Classes |
|----------|---------|
| **Entry Point** | `PaymentServiceApplication` |
| **Controllers** | `PaymentController`, `SellerPaymentsController`, `StripeOnboardingController`, `AdminRefundController` |
| **Services** | `PaymentService`, `SellerPaymentsService`, `StripeOnboardingService`, `RefundService` |
| **Domain Models** | `Transaction`, `Refund`, `RefundItem`, `SellerStripeAccount`, `SellerTransfer` |
| **JPA Converters** | `MapStringObjectConverter`, `ReturnEvidenceImagesConverter`, `StringListConverter`, `StringListJsonConverter`, `StringObjectMapJsonConverter` |
| **Repositories** | `TransactionRepository`, `RefundRepository`, `RefundItemRepository`, `SellerStripeAccountRepository`, `SellerTransferRepository` |
| **Configuration** | `StripeConfig`, `SecurityConfig`, `KafkaConsumerConfig`, `KafkaProducerConfig`, `KafkaTopicConfig`, `PaymentDevDataLoader` |
| **DTOs** | 2 request DTOs, 10 response DTOs |

## Interfaces

### REST API (External -- via API Gateway)

**Public / Authenticated Endpoints:**

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/v1/payments/parent-order/{parentOrderId}` | BUYER/SELLER/ADMIN | Get transaction detail with seller transfers and remaining payment window |
| `POST` | `/v1/stripe/webhooks` | None (Stripe-Signature) | Receive Stripe webhook events (20+ event types) |

**Seller Endpoints:**

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/v1/seller/payments/earnings` | SELLER | Get earnings overview (total/available/pending) |
| `GET` | `/v1/seller/payments/stripe-dashboard` | SELLER | Get Stripe Express dashboard login link |
| `POST` | `/v1/stripe/onboarding/start` | SELLER | Start Stripe Connect Express onboarding |
| `GET` | `/v1/stripe/onboarding/status` | SELLER | Check Stripe account onboarding status |
| `POST` | `/v1/stripe/onboarding/refresh-link` | SELLER | Refresh expired onboarding AccountLink |

**Admin Endpoints:**

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/v1/admin/refunds` | ADMIN | List all refunds (filterable by status, type, date range) |
| `GET` | `/v1/admin/refunds/{refundId}` | ADMIN | Get full refund detail (items, tracking, evidence) |
| `POST` | `/v1/admin/refunds/{refundId}/approve` | ADMIN | Approve refund (Stripe refund + transfer reversal) |
| `POST` | `/v1/admin/refunds/{refundId}/reject` | ADMIN | Reject refund with mandatory reason |

### Kafka Topics

**Consumed (from order-service and event bus):**

| Topic | Purpose |
|-------|---------|
| `payment.requested` | Create Stripe PaymentIntent + Transaction + SellerTransfer records |
| `payment.success` | Idempotency check for already-processed payments |
| `order.cancelled` | Cancel pending Stripe PaymentIntent on buyer/seller cancellation |
| `order.auto_cancelled` | Cancel pending Stripe PaymentIntent on payment timeout |
| `refund.requested` | Create Refund + RefundItems from buyer partial refund request |
| `refund.full_requested` | Create N Refund records (one per sub-order) for full parent-order refund |
| `order.returned_rts` | Auto-create and execute RTS refund (no admin approval) |
| `order.refunds.request` | Request-reply: respond with refund list by order/buyer |
| `order.payment_status.request` | Request-reply: respond with transaction status by parent order |

**Produced (to order-service, notification-service, identity-service):**

| Topic | Purpose |
|-------|---------|
| `payment.failed` | Payment failed/cancelled -- consumed by order-service PaymentKafkaEventBridge |
| `payment.success` | Payment succeeded -- consumed by order-service PaymentKafkaEventBridge |
| `refund.admin_approved` | Admin approved refund -- consumed by order-service to update order status |
| `refund.rejected` | Admin rejected refund -- consumed by order-service |
| `refund.created` | New refund record created -- consumed by notification-service |
| `refund.rts_completed` | RTS auto-refund completed -- consumed by order-service for confirmation |
| `refund.stripe_auto` | Externally-triggered Stripe refund detected |
| `stripe.account_suspended` | Stripe Connect account suspended -- consumed by notification-service |
| `stripe.dispute.created` | Stripe dispute opened -- alert for admin action |
| `stripe.dispute.closed` | Stripe dispute resolved |
| `stripe.transfer_reversed` | Stripe transfer reversed -- alert for admin action |
| `stripe.payout_failed` | Stripe payout failed -- alert for admin action |
| `order.refunds.response` | Request-reply: refund list response to order-service |
| `order.payment_status.response` | Request-reply: transaction status response to order-service |

## Dependencies

### Other Components

| Component | Interaction | Protocol |
|-----------|-------------|----------|
| **Order Service** | Receives payment requests, refund requests, and cancellation events; sends payment success/failure/refund status events and responds to refund/payment-status queries via request-reply | Kafka (events + request-reply) |
| **Identity Service** | Seller identity verification (indirect, via seller ID from order-service events) | No direct calls |
| **Notification Service** | Publishes refund.created, stripe.account_suspended, dispute events for buyer/seller/admin notifications (indirect via Kafka) | Kafka |

### External Systems

| System | Purpose | Configuration |
|--------|---------|---------------|
| **Stripe API** | PaymentIntent creation, Connect Transfer, Account management, Refund execution, Webhook event delivery | `stripe.secret-key`, `stripe.webhook-secret`, `stripe.platform-fee-percentage` (default: 5.0%) |
| **PostgreSQL** | Persistent storage for `transactions`, `refunds`, `refund_items`, `seller_stripe_accounts`, `seller_transfers` | Spring Data JPA + Flyway migrations |
| **Kafka** | Async event-driven communication with order-service and notification-service | Spring Kafka, idempotent producer, manual consumer commit |
| **Eureka** | Service discovery registration | Spring Cloud Netflix Eureka Client |
| **API Gateway** | JWT decoding, request routing, `X-User-*` header injection | Stateless, header-based |

### Currency and Amounts

All monetary amounts in this service use VND (Vietnamese Dong), a zero-decimal currency. Stripe amounts are in the smallest unit (VND has 1:1 mapping, no multiplication by 100 needed). Platform fee is configured as a percentage (default: 5.0%).

## Component Diagram

```mermaid
C4Component
    title Component Diagram for Payment Service Container

    Container_Boundary(payment_service, "Payment Service") {
        Component(payment_controller, "Payment Controller", "Spring REST Controller", "Transaction detail queries and Stripe webhook endpoint (20+ event types, Stripe-Signature verified).")
        Component(seller_payments_controller, "Seller Payments Controller", "Spring REST Controller", "Seller earnings overview and Stripe Express dashboard login links.")
        Component(stripe_onboarding_controller, "Stripe Onboarding Controller", "Spring REST Controller", "Stripe Connect Express onboarding: start, status check, link refresh.")
        Component(admin_refund_controller, "Admin Refund Controller", "Spring REST Controller", "Admin refund management: listing, detail, approval, rejection.")
        Component(payment_service_svc, "Payment Service", "Spring Service / Kafka Listener", "Core payment orchestration: PaymentIntent creation, webhook dispatch (20+ handlers), Stripe Connect transfers, payment cancellation.")
        Component(refund_service, "Refund Service", "Spring Service / Kafka Listener", "Refund management: create, approve (Stripe refund + transfer reversal), reject, RTS auto-refund, request-reply responses.")
        Component(seller_payments_service, "Seller Payments Service", "Spring Service", "Seller earnings aggregation from transfer records and Stripe Express dashboard link generation.")
        Component(stripe_onboarding_service, "Stripe Onboarding Service", "Spring Service", "Express account creation, AccountLink generation, status derivation, link refresh.")
        Component(stripe_config, "Stripe Config", "Spring Configuration", "Stripe API key, webhook secret, platform fee %, onboarding URLs, default country.")
        ComponentDb(transaction_entity, "Transaction", "JPA Entity", "transactions table: parentOrderId, amount, transRef, status (PENDING/SUCCESS/FAILED/CANCELLED), rawResponse (JSONB).")
        ComponentDb(refund_entity, "Refund", "JPA Entity", "refunds table: orderId, groupRef (UUID), type (FULL/PARTIAL), status, amount, evidenceImages (JSONB), admin review fields.")
        ComponentDb(refund_item_entity, "RefundItem", "JPA Entity", "refund_items table: refundId, itemId, quantity, refundAmount, returnTrackingNumber, returnEvidenceImages (JSONB).")
        ComponentDb(seller_stripe_account_entity, "SellerStripeAccount", "JPA Entity", "seller_stripe_accounts table: sellerId (unique), stripeAccountId, chargesEnabled, payoutsEnabled, onboarding status field.")
        ComponentDb(seller_transfer_entity, "SellerTransfer", "JPA Entity", "seller_transfers table: orderId, sellerId, transferAmount, stripeTransferId, status (PENDING/SUCCEEDED/FAILED/REVERSED).")
    }

    Container_Ext(api_gateway, "API Gateway", "JWT decoding, request routing")
    Container_Ext(order_service, "Order Service", "Order lifecycle and saga orchestration")
    Container_Ext(stripe_api, "Stripe API", "Payment Gateway", "PaymentIntent, Transfer, Account, Refund, Webhook")
    Container_Ext(postgresql, "PostgreSQL", "payment_service schema")
    Container_Ext(kafka, "Apache Kafka", "Event Bus")
    Container_Ext(eureka, "Eureka", "Service Discovery")

    Rel(api_gateway, payment_controller, "Routes HTTP requests", "REST")
    Rel(api_gateway, seller_payments_controller, "Routes HTTP requests", "REST")
    Rel(api_gateway, stripe_onboarding_controller, "Routes HTTP requests", "REST")
    Rel(api_gateway, admin_refund_controller, "Routes HTTP requests", "REST")

    Rel(payment_controller, payment_service_svc, "Calls", "Java method")
    Rel(seller_payments_controller, seller_payments_service, "Calls", "Java method")
    Rel(stripe_onboarding_controller, stripe_onboarding_service, "Calls", "Java method")
    Rel(admin_refund_controller, refund_service, "Calls", "Java method")

    Rel(payment_service_svc, kafka, "Consumes payment.requested, order.cancelled, payment.success", "Kafka")
    Rel(payment_service_svc, kafka, "Produces payment.success, payment.failed, stripe.* alerts", "Kafka")
    Rel(refund_service, kafka, "Consumes refund.requested, order.returned_rts, request-reply topics", "Kafka")
    Rel(refund_service, kafka, "Produces refund.admin_approved, refund.rts_completed, request-reply responses", "Kafka")

    Rel(kafka, order_service, "Payment and refund coordination", "Kafka")

    Rel(payment_service_svc, stripe_api, "Creates PaymentIntents and Transfers", "Stripe Java SDK")
    Rel(refund_service, stripe_api, "Executes Stripe refunds and Transfer reversals", "Stripe Java SDK")
    Rel(stripe_onboarding_service, stripe_api, "Creates Express Accounts and AccountLinks", "Stripe Java SDK")
    Rel(seller_payments_service, stripe_api, "Generates Express dashboard login links", "Stripe Java SDK")
    Rel(stripe_api, payment_controller, "Sends webhook events (Stripe-Signature verified)", "HTTPS")

    Rel(payment_service_svc, transaction_entity, "Manages", "JPA")
    Rel(payment_service_svc, seller_stripe_account_entity, "Reads", "JPA")
    Rel(payment_service_svc, seller_transfer_entity, "Writes", "JPA")
    Rel(refund_service, refund_entity, "Manages", "JPA")
    Rel(refund_service, refund_item_entity, "Manages", "JPA")
    Rel(refund_service, transaction_entity, "Reads", "JPA")
    Rel(refund_service, seller_transfer_entity, "Reads/writes", "JPA")
    Rel(seller_payments_service, seller_transfer_entity, "Reads", "JPA")
    Rel(seller_payments_service, seller_stripe_account_entity, "Reads", "JPA")
    Rel(stripe_onboarding_service, seller_stripe_account_entity, "Manages", "JPA")

    Rel(transaction_entity, postgresql, "Persisted to", "JDBC")
    Rel(refund_entity, postgresql, "Persisted to", "JDBC")
    Rel(refund_item_entity, postgresql, "Persisted to", "JDBC")
    Rel(seller_stripe_account_entity, postgresql, "Persisted to", "JDBC")
    Rel(seller_transfer_entity, postgresql, "Persisted to", "JDBC")

    Rel(payment_service, eureka, "Registers with", "Eureka Client")
```
