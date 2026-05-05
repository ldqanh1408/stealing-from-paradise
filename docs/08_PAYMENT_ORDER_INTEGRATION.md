# Order-Payment Integration Reference

**Project**: stealing-from-paradise
**Version**: v5.4
**Last Updated**: 2026-05-01

> This document details every integration point between the Order Service and Payment Service — REST endpoints, Kafka topics, request-reply patterns, and Stripe webhook flows.

---

## 1. REST Endpoints (API Gateway)

### 1.1 Order Service → Payment Service (Client-Side)

After checkout, the frontend calls the Payment Service directly to get the Stripe client secret:

| Method | Endpoint | Auth | Purpose |
|--------|----------|------|---------|
| GET | `/api/v1/payments/by-order/{parentOrderId}` | JWT | Get payment status and client secret |
| GET | `/api/v1/payments/by-intent/{stripePaymentIntentId}` | JWT | Lookup by Stripe PI ID |

### 1.2 Checkout Flow (Frontend Side)

```
1. Frontend: POST /orders/checkout
   └─→ OrderController.checkout()
       ├─ Creates parent_order + sub-orders
       ├─ Returns { parentOrderId, paymentClientSecret? }
       └─ paymentClientSecret = null initially (created async by Payment Service)

2. Frontend: GET /payments/by-order/{parentOrderId}  (poll until clientSecret available)
   └─→ PaymentService.getClientSecret()
       └─ Returns { clientSecret: "pi_xxx_secret_xxx", status, transactionId }

3. Frontend: Stripe PaymentModal.open({ clientSecret })
   └─→ Buyer enters card details → Stripe processes payment

4. Stripe: webhook → PaymentService.handleStripeWebhook()
   ├─ payment_intent.succeeded → payment.success
   └─ payment_intent.payment_failed → payment.failed

5. Frontend: poll GET /orders/{parentOrderId} until status = PAID
```

---

## 2. Kafka Topics (Internal Communication)

### 2.1 Topics from Order Service to Payment Service

| Topic | Trigger | Payload | Consumer |
|-------|---------|---------|----------|
| `payment.requested` | ParentOrderPaymentSaga starts | `{parent_order_id, user_id, total_amount, orders[], currency, timeout_at}` | PaymentService.onPaymentRequested() |
| `order.returned` | OrderProcessingSaga (RTS) | `{order_id, parent_order_id, user_id, seller_id, refund_reason_type, return_tracking_number, total_amount, evidence_count}` | PaymentService.onOrderReturnedRts() |

### 2.2 Topics from Payment Service to Order Service

| Topic | Trigger | Payload | Consumer |
|-------|---------|---------|----------|
| `payment.success` | Stripe webhook (payment_intent.succeeded) | `{parent_order_id, transaction_id, stripe_pi_id, amount}` | OrderService → ParentOrderPaymentSaga |
| `payment.failed` | Stripe webhook (payment_intent.payment_failed) | `{parent_order_id, transaction_id, stripe_pi_id}` | OrderService → ParentOrderPaymentSaga |

### 2.3 Topics from Order Service to Other Services

| Topic | Trigger | Payload | Consumers |
|-------|---------|---------|-----------|
| `order.created` | OrderProcessingSaga | `{parent_order_id, order_id, user_id, seller_id, order_code, total_amount, is_flash_sale}` | NotificationService, WorkerService |
| `order.shipped` | OrderProcessingSaga | `{order_id, user_id, seller_id, tracking_number, carrier, shipped_at}` | NotificationService |
| `order.delivered` | OrderProcessingSaga | `{order_id, user_id, seller_id, total_amount, delivered_by, delivered_at}` | IdentityService, NotificationService |
| `order.cancelled` | OrderProcessingSaga | `{order_id, parent_order_id, user_id, seller_id, cancelled_by, cancel_reason, total_amount}` | CartService, NotificationService |
| `order.auto_cancelled` | Axon Deadline (payment timeout) | `{order_id, parent_order_id, user_id, seller_id, cancelled_by=SYSTEM, cancel_reason, total_amount}` | NotificationService |
| `order.checkout_completed` | OrderService.publishCheckoutCompleted() | `{user_id, item_ids[], parent_order_id}` | CartService (remove purchased items) |
| `seller.order_cancelled` | OrderProcessingSaga (seller cancels) | `{order_id, seller_id, buyer_id}` | IdentityService |

---

## 3. Stripe Webhook Integration

### 3.1 Webhook Endpoint

```
POST /api/v1/webhooks/stripe
Headers: Stripe-Signature: t=xxx,v1=xxx
Body: raw Stripe event JSON
```

Handled by: `PaymentService.handleStripeWebhook()`

### 3.2 Webhook Events Processed

| Stripe Event | Action in handleStripeWebhook() | Downstream Kafka |
|---|---|---|
| `payment_intent.succeeded` | Transaction.status = SUCCESS | payment.success |
| `payment_intent.payment_failed` | Transaction.status = FAILED | payment.failed |
| `payment_intent.canceled` | Transaction.status = CANCELLED | payment.failed |
| `charge.refunded` | Update refund status to SUCCESS | — |
| `charge.dispute.created` | Create dispute record, notify seller | stripe.dispute.created |
| `charge.dispute.closed` | Update dispute status | stripe.dispute.closed |
| `transfer.created` | Update SellerTransfer.status = SUCCEEDED | — |
| `transfer.reversed` | Mark transfer as reversed | stripe.transfer.reversed |
| `payout.failed` | Log warning | stripe.payout.failed |
| `account.updated` | Update SellerStripeAccount.chargesEnabled | — |

### 3.3 Webhook Signature Verification

```java
Event event = Webhook.constructEvent(payload, sigHeader, stripeConfig.getWebhookSecret());
// Throws SignatureVerificationException if invalid
```

The webhook secret is configured via `STRIPE_WEBHOOK_SECRET` environment variable.

---

## 4. Stripe Connect Integration

### 4.1 Seller Onboarding Flow

```
1. Seller POST /stripe/onboarding/start
   └─→ PaymentService.startStripeOnboarding()
       ├─ Check/creates Stripe connected account
       └─→ Stripe API: accountLinks.create()
           └─ Returns { url: "https://connect.stripe.com/..." }

2. Seller completes Stripe verification in browser
   └─→ Stripe sends account.updated webhook
       └─→ PaymentService.handleAccountUpdated()
           └─ Update SellerStripeAccount.chargesEnabled

3. Seller GET /stripe/onboarding/status
   └─→ PaymentService.getStripeOnboardingStatus()
       └─ Returns { charges_enabled, payouts_enabled, details_submitted }
```

### 4.2 Payment Intent Creation

When `PaymentService.onPaymentRequested()` receives `payment.requested`:

```java
// 1. Create Transaction record
Transaction tx = Transaction.builder()
    .parentOrderId(parentOrderId)
    .amount(totalAmount)
    .status("PENDING")
    .method("STRIPE")
    .build();
transactionRepository.save(tx);

// 2. Create SellerTransfer records (PENDING)
for (order : orders) {
    SellerTransfer st = SellerTransfer.builder()
        .parentOrderId(parentOrderId)
        .orderId(order.getOrderId())
        .sellerId(order.getSellerId())
        .transferAmount(order.getAmount())
        .feeAmount(computeFee(order.getAmount()))
        .netAmount(computeNet(order.getAmount()))
        .status("PENDING")
        .build();
    sellerTransferRepository.save(st);
}

// 3. Create Stripe PaymentIntent
PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
    .setAmount(toStripeAmount(totalAmount))
    .setCurrency("vnd")
    .putMetadata("parent_order_id", String.valueOf(parentOrderId))
    .build();
PaymentIntent pi = PaymentIntent.create(params);

// 4. Save clientSecret for frontend polling
tx.setClientSecret(pi.getClientSecret());
tx.setStripePiId(pi.getId());
transactionRepository.save(tx);
```

### 4.3 Multi-Vendor Transfer (After Payment)

When `payment_intent.succeeded` fires:

```java
// For each seller with PENDING transfer:
for (SellerTransfer st : sellerTransferRepository.findAllByParentOrderId(parentOrderId)) {
    // Skip sellers without active Stripe account
    if (sellerAccount == null || !chargesEnabled) {
        st.setStatus("SKIPPED");
        continue;
    }

    // Transfer net amount to seller
    TransferCreateParams params = TransferCreateParams.builder()
        .setAmount(toStripeAmount(st.getNetAmount()))
        .setCurrency("vnd")
        .setDestination(sellerAccount.getStripeAccountId())
        .setSourceTransaction(latestChargeId)  // guaranteed funds
        .build();
    Transfer transfer = Transfer.create(params);

    st.setStripeTransferId(transfer.getId());
    st.setStatus("SUCCEEDED");
}
```

### 4.4 Fee Calculation

| Component | Formula |
|-----------|---------|
| Platform fee | `orderAmount * PLATFORM_FEE_PERCENTAGE` (configured, e.g. 5%) |
| Stripe fee | ~1.9% + 8,000 VND per transaction (absorbed by platform in MVP) |
| Seller net | `orderAmount - platformFee` |
| Transfer amount | `sellerNet` (transferred to seller's connected account) |

---

## 5. Request-Reply Pattern (Kafka)

Order Service uses Kafka request-reply for inter-service calls:

| Request Topic | Response Topic | Purpose |
|---|---|---|
| `order.stock_check.request` | `order.stock_check.response` | Validate SKU stock before checkout |
| `order.cart_items.request` | `order.cart_items.response` | Fetch cart items during checkout |
| `order.address.request` | `order.address.response` | Fetch buyer address |
| `order.payment_status.request` | `order.payment_status.response` | Check if order is already paid (before refund) |
| `order.refunds.request` | `order.refunds.response` | Get refund info for order |
| `cart.product_info.request` | `cart.product_info.response` | Get product info from cart |

The Order Service uses `KafkaTemplate.sendAndReceive()` with correlation IDs to implement this pattern.

---

## 6. Error Handling & Idempotency

### 6.1 Payment Idempotency

```java
// onPaymentRequested — skip if already processed
Transaction existing = transactionRepository.findByParentOrderId(parentOrderId).orElse(null);
if (existing != null && ("PENDING".equals(existing.getStatus())
        || "SUCCESS".equals(existing.getStatus()))) {
    return; // Already created or succeeded
}

// handleStripeWebhook — skip if already updated
if ("SUCCESS".equals(tx.getStatus())) {
    return; // Already processed
}
```

### 6.2 Stripe Webhook Idempotency

Stripe webhooks are delivered at-least-once. The system handles duplicate events via:
- Transaction status check before update
- Event ID deduplication (Stripe provides `event.id`)

### 6.3 Saga Idempotency

- Axon Sagas are naturally idempotent — the same event can be received multiple times but state transitions are guarded by status checks
- `OrderProcessingSaga.onPaymentTimeout()` checks `if ("PENDING".equals(order.getStatus()))` before cancelling

---

## 7. Data Flow Summary

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ BUYER CHECKOUT                                                               │
│                                                                              │
│ POST /orders/checkout                                                         │
│   └─→ OrderService.checkout()                                                │
│       ├─ Kafka RR: fetchAddress()                                            │
│       ├─ Kafka RR: fetchCartItems()                                          │
│       ├─ Save ParentOrder + Orders                                           │
│       ├─ Publish ParentOrderCheckoutCreatedEvent → ParentOrderPaymentSaga    │
│       ├─ Publish OrderCreatedEvent → OrderProcessingSaga (per sub-order)      │
│       └─ Return { parentOrderId }                                            │
│                                                                              │
│ GET /payments/by-order/{parentOrderId}  (frontend polls)                     │
│   └─→ PaymentService.getClientSecret()                                       │
│       └─ Returns { clientSecret, status }                                    │
│                                                                              │
│ Stripe PaymentModal.open({ clientSecret })                                    │
│   └─→ Buyer completes payment                                                │
│                                                                              │
│ STRIPE WEBHOOK                                                               │
│   └─→ PaymentService.handleStripeWebhook()                                    │
│       ├─ payment_intent.succeeded:                                           │
│       │   ├─ Transaction.status = SUCCESS                                    │
│       │   ├─ Create Stripe Transfers to sellers                              │
│       │   └─ Kafka: payment.success                                          │
│       │       └─→ ParentOrderPaymentSaga.onPaymentSucceeded()                │
│       │           ├─ Update all sub-orders to PAID                           │
│       │           └─ Publish OrderPaidEvent (Axon) → sub-orders             │
│       │               └─→ OrderProcessingSaga.onOrderPaidEvent()            │
│       │                   └─ Cancel payment deadline                        │
│       │                                                                   │
│       └─ payment_intent.payment_failed:                                      │
│           ├─ Transaction.status = FAILED                                     │
│           └─ Kafka: payment.failed                                           │
│               └─→ ParentOrderPaymentSaga.onPaymentFailed()                  │
│                   ├─ Cancel all sub-orders                                   │
│                   └─ Publish OrderCancelledEvent → sub-orders               │
│                       └─→ OrderProcessingSaga.onOrderCancelledEvent()       │
│                           ├─ Cancel deadline                                 │
│                           ├─ Kafka: order.cancelled                          │
│                           │   ├─→ CartService: restore items                │
│                           │   ├─→ (LoyaltyService: removed in MVP)          │
│                           │   └─→ NotificationService: notify             │
│                           └─ @EndSaga                                       │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 8. Configuration

| Config Key | Service | Default | Description |
|-----------|---------|---------|-------------|
| `STRIPE_SECRET_KEY` | Payment | — | Stripe API secret key |
| `STRIPE_WEBHOOK_SECRET` | Payment | — | Stripe webhook signature secret |
| `STRIPE_PLATFORM_FEE_PERCENTAGE` | Payment | 5.0 | Platform commission % |
| `STRIPE_DEFAULT_COUNTRY` | Payment | US | Default seller country |
| `PAYMENT_TIMEOUT_MINUTES` | Order | 30 | Order payment timeout |

---

## 9. Related Documents

- [06_PAYMENT_SAGA_FLOW.md](06_PAYMENT_SAGA_FLOW.md) — Saga orchestration patterns
- [KAFKA_EVENTS.md](KAFKA_EVENTS.md) — Full Kafka event catalog
- [payment-service/02_API_payment_service.md](payment-service/02_API_payment_service.md) — Payment API reference
- [order-service/02_API_order_service.md](order-service/02_API_order_service.md) — Order API reference
- [03_BUSINESS.md](03_BUSINESS.md) — Business logic and refund workflows

---

**Last Updated**: 2026-05-01
**Version**: v5.4
