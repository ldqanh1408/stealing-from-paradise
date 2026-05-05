# Payment Saga Flow — Order & Payment Integration

**Project**: stealing-from-paradise
**Version**: v5.4
**Last Updated**: 2026-05-01

> This document describes the **end-to-end payment saga** — how orders are created, payment is orchestrated, and the distributed transaction completes (or rolls back) across the Order Service and Payment Service.

---

## 1. Architecture Overview

The payment saga is **distributed across two Axon Sagas** and the Payment Service, coordinated via Kafka events and Axon Deadlines:

```
┌──────────────────────────────────────────────────────────────────────────┐
│                          CHECKOUT FLOW                                    │
└──────────────────────────────────────────────────────────────────────────┘

  Buyer
    │
    ▼ POST /orders/checkout
  OrderController.checkout()
    │
    ▼ OrderService.checkout()
    │
    ├─ Kafka Request-Reply: fetchAddress()
    ├─ Kafka Request-Reply: fetchCartItems()
    ├─ Validate stock, compute totals
    ├─ Save PARENT_ORDER + N Order sub-records
    ├─ Save ORDER_ITEMS per sub-order
    ├─ Publish OrderCreatedEvent per sub-order → OrderProcessingSaga starts
    ├─ Publish ParentOrderCheckoutCreatedEvent → ParentOrderPaymentSaga starts
    └─ Return CheckoutResponse { parentOrderId, paymentClientSecret? }

  ParentOrderPaymentSaga (per parent order)
    │
    ▼ on(ParentOrderCheckoutCreatedEvent)
    └─ Kafka → PAYMENT_REQUESTED → Payment Service

  PaymentService.onPaymentRequested()
    │
    ├─ Create Transaction (PENDING)
    ├─ Create SellerTransfer records (PENDING)
    └─ Stripe PaymentIntent.create() → return clientSecret to buyer

  Buyer pays via Stripe modal
    │
    ▼ Stripe webhook
  PaymentService.handleStripeWebhook()
    │
    ├─ payment_intent.succeeded:
    │   ├─ Transaction.status = SUCCESS
    │   ├─ Kafka → PAYMENT_SUCCESS
    │   └─ Stripe Transfer to each seller
    │
    └─ payment_intent.payment_failed:
        └─ Kafka → PAYMENT_FAILED
```

```
┌──────────────────────────────────────────────────────────────────────────┐
│                    PAYMENT SUCCESS FLOW                                   │
└──────────────────────────────────────────────────────────────────────────┘

  Kafka: PAYMENT_SUCCESS
    │
    ├─→ OrderService (Kafka consumer) ──────────→
    │   │
    │   └─ ParentOrderPaymentSaga
    │       │
    │       ▼ on(ParentOrderPaymentSucceededEvent)
    │       ├─ Pessimistic lock ParentOrder
    │       ├─ For each sub-order: Order.status = PAID
    │       ├─ Publish OrderPaidEvent per sub-order
    │       └─ @EndSaga
    │
    ├─→ NotificationService ──────────────────→
    │   └─ Send "Payment confirmed" notification
    │
    └─ (Stripe Transfer → seller's connected account)

  OrderProcessingSaga (per sub-order)
    │
    ▼ on(OrderPaidEvent)
    ├─ Cancel payment deadline
    └─ (awaiting seller to ship)
```

```
┌──────────────────────────────────────────────────────────────────────────┐
│                    PAYMENT FAILURE FLOW                                  │
└──────────────────────────────────────────────────────────────────────────┘

  Stripe webhook: payment_intent.payment_failed
    │
    └─ Kafka → PAYMENT_FAILED

  Kafka: PAYMENT_FAILED
    │
    └─ ParentOrderPaymentSaga
        │
        ▼ on(ParentOrderPaymentFailedEvent)
        ├─ Pessimistic lock ParentOrder
        ├─ For each sub-order: Order.status = CANCELLED
        ├─ Publish OrderCancelledEvent per sub-order
        └─ @EndSaga

  OrderProcessingSaga
    │
    ▼ on(OrderCancelledEvent)
    ├─ Cancel payment deadline
    ├─ Kafka → ORDER_CANCELLED
    │   ├─→ CartService: restore cart items
    │   └─→ NotificationService: notify buyer
    └─ @EndSaga
```

```
┌──────────────────────────────────────────────────────────────────────────┐
│                PAYMENT TIMEOUT (AUTO-CANCEL)                             │
└──────────────────────────────────────────────────────────────────────────┘

  Axon Deadline: payment-timeout fires (30 min after checkout)
    │
    └─ OrderProcessingSaga.onPaymentTimeout()
        ├─ Order.status = CANCELLED, cancelledBy = SYSTEM
        ├─ Kafka → ORDER_AUTO_CANCELLED
        └─ SagaLifecycle.end()

  Kafka: ORDER_AUTO_CANCELLED
    │
    ├─ ParentOrderPaymentSaga (if still alive)
    │   └─ on(ParentOrderPaymentFailedEvent): cancel sub-orders, @EndSaga
    │
    ├─ OrderService: JOB-13 backup check
    ├─ NotificationService: notify buyer
```

---

## 2. Sagas in Detail

### 2.1 OrderProcessingSaga

**Association key**: `orderId` (one saga per sub-order)

**File**: `backend/order-service/src/main/java/com/flashsale/orderdomain/axon/saga/OrderProcessingSaga.java`

**Responsibilities**:
- Start when `OrderCreatedEvent` fires (after checkout)
- Schedule payment timeout deadline (30 min)
- Publish `order.created` Kafka event (downstream consumers)
- Handle `OrderPaidEvent` → cancel deadline
- Handle `OrderShippedEvent` → schedule shipping deadline, publish `order.shipped`
- Handle `OrderDeliveredEvent` → publish `order.delivered`, @EndSaga
- Handle `OrderCancelledEvent` → publish `order.cancelled`, @EndSaga
- Handle `OrderReturnedEvent` → publish `order.returned`, @EndSaga
- `onPaymentTimeout()` → auto-cancel, @EndSaga

**Saga state fields**:
```java
private Long orderId;
private Long parentOrderId;
private Long userId;
private Long sellerId;
private String sellerName;
private BigDecimal totalAmount;
private boolean isFlashSale;
private String paymentDeadlineId;  // from DeadlineManager
private String shippingDeadlineId;
```

**Secondary association**: `parentOrderId` (via `SagaLifecycle.associateWith`) so payment events can also route to this saga.

### 2.2 ParentOrderPaymentSaga

**Association key**: `parentOrderId` (one saga per checkout)

**File**: `backend/order-service/src/main/java/com/flashsale/orderdomain/axon/saga/ParentOrderPaymentSaga.java`

**Responsibilities**:
- Start when `ParentOrderCheckoutCreatedEvent` fires
- Publish `payment.requested` → Payment Service (with sub-order list)
- Handle `ParentOrderPaymentSucceededEvent` → update all sub-orders to PAID, publish `OrderPaidEvent` per sub-order, @EndSaga
- Handle `ParentOrderPaymentFailedEvent` → update all sub-orders to CANCELLED, publish `OrderCancelledEvent` per sub-order, @EndSaga

**Critical fix**: Uses **pessimistic locking** on ParentOrder to prevent `ObjectOptimisticLockingFailureException` when concurrent transactions try to update the same parent order. Previously used `findById` (no lock) which caused race conditions.

### 2.3 Payment Service (Non-Saga)

The Payment Service is **not an Axon Saga**. It is a traditional Spring `@Service` that:
- Listens to `payment.requested` → creates Stripe PaymentIntent
- Handles Stripe webhooks (`payment_intent.succeeded`, `payment_intent.payment_failed`, etc.)
- Creates `SellerTransfer` records and executes Stripe Connect transfers
- Publishes `payment.success` / `payment.failed` Kafka events

---

## 3. Kafka Topics in Payment Flow

| Topic | Producer | Consumers | Purpose |
|-------|----------|-----------|---------|
| `payment.requested` | ParentOrderPaymentSaga | PaymentService | Trigger Stripe PaymentIntent |
| `payment.success` | PaymentService | OrderService (ParentOrderPaymentSaga) | Mark orders PAID |
| `payment.failed` | PaymentService | OrderService (ParentOrderPaymentSaga) | Cancel orders |
| `order.created` | OrderProcessingSaga | NotificationService, WorkerService | Notify order created |
| `order.shipped` | OrderProcessingSaga | NotificationService | Notify shipped |
| `order.delivered` | OrderProcessingSaga | IdentityService, NotificationService | Notify |
| `order.cancelled` | OrderProcessingSaga | CartService, NotificationService | Restore items |
| `order.auto_cancelled` | OrderProcessingSaga (deadline) | NotificationService | Auto-cancel notification |
| `order.returned` | OrderProcessingSaga | PaymentService, NotificationService | RTS → auto refund |

---

## 4. Order Status Transitions

```
CHECKOUT
  │
  ├─→ PENDING (order created, awaiting payment)
  │     │
  │     ├─→ PAID (payment_intent.succeeded)
  │     │     │
  │     │     ├─→ SHIPPING (seller updates tracking)
  │     │     │     │
  │     │     │     ├─→ DELIVERED (buyer confirms)
  │     │     │     │     │
  │     │     │     │     └─ Terminal
  │     │     │     │
  │     │     │     └─→ RETURNED (RTS — goods returned to seller)
  │     │     │           │
  │     │     │           └─ Terminal (auto-refund)
  │     │     │
  │     │     └─→ DELIVERED (auto-delivered by JOB-22 after deadline)
  │     │
  │     ├─→ CANCELLED (buyer cancels before payment)
  │     │     │
  │     │     └─ Terminal
  │     │
  │     └─→ CANCELLED (SYSTEM — payment timeout)
  │           │
  │           └─ Terminal (ORDER_AUTO_CANCELLED published)
  │
  └─ (order never existed if checkout validation fails)
```

---

## 5. Multi-Vendor Payment Flow

When a buyer checks out items from multiple sellers in one checkout:

```
1. OrderService.checkout()
   ├─ Creates 1 PARENT_ORDER
   ├─ Creates N sub-orders (one per seller)
   ├─ Publishes N OrderCreatedEvents → N OrderProcessingSagas start
   └─ Publishes 1 ParentOrderCheckoutCreatedEvent → ParentOrderPaymentSaga starts

2. ParentOrderPaymentSaga.onPaymentRequested()
   ├─ Publishes 1 payment.requested with { parentOrderId, totalAmount, orders[] }
   └─ orders[] contains: [{order_id, seller_id, seller_name, amount}, ...]

3. PaymentService.onPaymentRequested()
   ├─ Creates 1 Transaction (PENDING, total amount)
   ├─ Creates N SellerTransfer records (one per seller, PENDING)
   └─ Creates 1 Stripe PaymentIntent for total amount

4. Buyer pays once via Stripe modal

5. PaymentService.handlePaymentIntentSucceeded()
   ├─ Transaction.status = SUCCESS
   ├─ Kafka → PAYMENT_SUCCESS
   └─ For each SellerTransfer:
       ├─ If seller chargesEnabled: Stripe Transfer to seller account
       └─ If not: SellerTransfer.status = SKIPPED

6. ParentOrderPaymentSaga.onPaymentSucceeded()
   └─ Updates all N sub-orders to PAID
```

---

## 6. Key Implementation Notes

### 6.1 Pessimistic Locking for Parent Order

The `ParentOrderPaymentSaga` uses pessimistic locking to prevent race conditions when updating multiple sub-orders concurrently:

```java
// Before: caused ObjectOptimisticLockingFailureException
parentOrderRepository.findById(event.getParentOrderId());

// After: pessimistic lock prevents concurrent modification
parentOrderRepository.findByIdWithPessimisticLock(event.getParentOrderId());
```

### 6.2 Axon Deadline vs Database Job

The Axon `DeadlineManager` fires `onPaymentTimeout()` first (Axon Server-side deadline). The worker-service JOB-13 serves as a **safety net** for cases where Axon Server is unavailable or the saga didn't start properly:

```
Deadline fires (Axon Server)
    │
    ├─→ OrderProcessingSaga.onPaymentTimeout()  ← Primary
    │
    └─→ JOB-13 (every minute)                  ← Safety net
          └─ SELECT * FROM orders WHERE status = 'PENDING'
              AND created_at < NOW() - INTERVAL '30 minutes'
```

### 6.3 No Duplicate Payment Intents

`PaymentService.onPaymentRequested()` is idempotent:

```java
Transaction existing = transactionRepository.findByParentOrderId(parentOrderId).orElse(null);
if (existing != null && ("PENDING".equals(existing.getStatus())
        || "SUCCESS".equals(existing.getStatus()))) {
    log.info("Skip payment.requested — transaction already exists");
    return;
}
```

### 6.4 Stripe Connect Destination Charges

Payments use Stripe **Destination Charges** pattern:
- Platform collects full payment
- Platform deducts commission fee
- Platform transfers net amount to each seller's connected account
- Each transfer uses `source_transaction` (the charge ID) for guaranteed funds

---

## 7. Related Documents

- [08_PAYMENT_ORDER_INTEGRATION.md](08_PAYMENT_ORDER_INTEGRATION.md) — Detailed API integration points
- [KAFKA_EVENTS.md](KAFKA_EVENTS.md) — Full event catalog with payloads
- [03_BUSINESS.md](03_BUSINESS.md) — Business logic and refund workflows
- [payment-service/02_API_payment_service.md](payment-service/02_API_payment_service.md) — Payment API reference
- [order-service/02_API_order_service.md](order-service/02_API_order_service.md) — Order API reference

---

**Last Updated**: 2026-05-01
**Version**: v5.4
