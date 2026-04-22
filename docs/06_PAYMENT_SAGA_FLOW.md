# Payment Saga Flow Architecture

**Date**: 2026-04-18  
**Status**: Implemented  
**Components**: order-service (saga orchestration), payment-service (payment initialization), Axon Framework, Kafka

---

## Overview

Implemented **Axon Saga-driven payment orchestration** to close the gap between order checkout and payment completion. The saga ensures exactly-once payment initialization and proper order status transitions across parent and sub-orders in a multi-vendor checkout scenario.

**Key Design Decision**: Use Axon Saga (not Kafka-only) to maintain strong ordering guarantees and enable deadline-based payment timeouts via the existing `OrderProcessingSaga`.

---

## Event Flow Diagram

```
┌─────────────────────┐
│    Checkout API     │
│   (order-service)   │
└──────────┬──────────┘
           │
           │ 1. Emit ParentOrderCheckoutCreatedEvent
           ▼
┌─────────────────────────────────┐
│ ParentOrderPaymentSaga (START)  │
│                                 │
│ • Capture parent_order_id       │
│ • Capture userId, amount        │
│ • Publish payment.requested     │
└──────────┬──────────────────────┘
           │
           │ 2. Kafka: payment.requested
           ▼
┌─────────────────────────────────┐
│   PaymentService Consumer       │
│ (payment-service)               │
│                                 │
│ • Receive payment.requested     │
│ • Create Stripe PaymentIntent   │
│ • Save Transaction(PENDING)     │
│ • On error → publish FAILED     │
└──────────┬──────────────────────┘
           │
           │ 3. Stripe Webhook: payment_intent.succeeded or .payment_failed
           ▼
┌─────────────────────────────────┐
│  PaymentService Webhook Handler │
│                                 │
│ • Update Transaction.status     │
│ • Publish payment.success or    │
│   payment.failed to Kafka       │
└──────────┬──────────────────────┘
           │
           │ 4. Kafka: payment.success / payment.failed
           ▼
┌─────────────────────────────────┐
│  PaymentKafkaEventBridge        │
│ (order-service)                 │
│                                 │
│ • Convert Kafka → Axon event    │
│ • Publish ParentOrderPayment    │
│   SucceededEvent / FailedEvent  │
└──────────┬──────────────────────┘
           │
           │ 5. Axon Event
           ▼
┌─────────────────────────────────┐
│ ParentOrderPaymentSaga (END)    │
│                                 │
│ SUCCESS:                        │
│ • Update all sub-orders → PAID  │
│ • Emit OrderPaidEvent per order │
│ • @EndSaga                      │
│                                 │
│ FAILURE:                        │
│ • Cancel all sub-orders         │
│ • Emit OrderCancelledEvent      │
│ • @EndSaga                      │
└──────────┬──────────────────────┘
           │
           │ 6. Axon Events: OrderPaidEvent / OrderCancelledEvent
           ▼
┌─────────────────────────────────┐
│ OrderProcessingSaga             │
│                                 │
│ • Handle OrderPaidEvent         │
│   - Cancel payment timeout      │
│ • Handle OrderCancelledEvent    │
│   - End saga (already @EndSaga) │
│                                 │
│ (Shipping saga continues ...)   │
└─────────────────────────────────┘
```

---

## Saga Events

### 1. **ParentOrderCheckoutCreatedEvent** (Triggered by OrderService.checkout)

**Source**: `backend/order-service/src/main/java/com/flashsale/orderdomain/axon/event/ParentOrderCheckoutCreatedEvent.java`

```java
@Getter
@AllArgsConstructor
public class ParentOrderCheckoutCreatedEvent {
    private Long parentOrderId;
    private Long userId;
    private BigDecimal totalAmount;
    private LocalDateTime timeoutAt;
}
```

**When**: Emitted at the end of `OrderService.checkout()` after all sub-orders are created.

**Association**: Axon associates saga by `parentOrderId`.

---

### 2. **ParentOrderPaymentSucceededEvent** (Triggered by PaymentKafkaEventBridge)

**Source**: `backend/order-service/src/main/java/com/flashsale/orderdomain/axon/event/ParentOrderPaymentSucceededEvent.java`

```java
@Getter
@AllArgsConstructor
public class ParentOrderPaymentSucceededEvent {
    private Long parentOrderId;
}
```

**When**: Bridge receives Kafka `payment.success` event, converts to Axon event.

**Saga Action**: 
- Update all pending sub-orders → **PAID**
- Emit `OrderPaidEvent` for each sub-order
- **@EndSaga** (saga completes)

---

### 3. **ParentOrderPaymentFailedEvent** (Triggered by PaymentKafkaEventBridge)

**Source**: `backend/order-service/src/main/java/com/flashsale/orderdomain/axon/event/ParentOrderPaymentFailedEvent.java`

```java
@Getter
@AllArgsConstructor
public class ParentOrderPaymentFailedEvent {
    private Long parentOrderId;
    private String reason;
}
```

**When**: Bridge receives Kafka `payment.failed` event OR payment-service init fails.

**Saga Action**:
- Update all pending sub-orders → **CANCELLED** (status = "CANCELLED", cancelledBy = "SYSTEM")
- Emit `OrderCancelledEvent` for each sub-order with reason "Thanh toan that bai"
- **@EndSaga** (saga completes)

---

## Saga Implementations

### ParentOrderPaymentSaga

**Location**: `backend/order-service/src/main/java/com/flashsale/orderdomain/axon/saga/ParentOrderPaymentSaga.java`

**Lifecycle**:

1. **@StartSaga** `on(ParentOrderCheckoutCreatedEvent)`
   - Publish **payment.requested** to Kafka topic
   - Log: `[ParentPaymentSaga][${parentOrderId}] Payment requested`

2. **@EndSaga** `on(ParentOrderPaymentSucceededEvent)`
   - Load all pending sub-orders for parent_order_id
   - Set status → "PAID" for each
   - Emit `OrderPaidEvent` with OrderPaidEvent(orderId, parentOrderId, userId, sellerId, finalAmt)
   - Log: `[ParentPaymentSaga][${parentOrderId}] Payment succeeded, updated ${count} sub-orders`

3. **@EndSaga** `on(ParentOrderPaymentFailedEvent)`
   - Load all pending sub-orders
   - Set status → "CANCELLED", cancelledBy = "SYSTEM", cancelReason = reason
   - Emit `OrderCancelledEvent` with reason
   - Log: `[ParentPaymentSaga][${parentOrderId}] Payment failed, cancelled ${count} sub-orders`

---

### PaymentKafkaEventBridge

**Location**: `backend/order-service/src/main/java/com/flashsale/orderdomain/service/PaymentKafkaEventBridge.java`

**Responsibility**: Bridge Kafka `payment.success` / `payment.failed` to Axon events.

**Listeners**:

```java
@KafkaListener(topics = KafkaTopics.PAYMENT_SUCCESS, groupId = "order-service-group")
public void onPaymentSuccess(String message) {
    // Extract parent_order_id from Kafka message
    // Emit ParentOrderPaymentSucceededEvent to EventGateway
}

@KafkaListener(topics = KafkaTopics.PAYMENT_FAILED, groupId = "order-service-group")
public void onPaymentFailed(String message) {
    // Extract parent_order_id + reason from Kafka message
    // Emit ParentOrderPaymentFailedEvent to EventGateway
}
```

---

## Payment Initialization

### PaymentService.onPaymentRequested

**Location**: `backend/payment-service/src/main/java/com/flashsale/paymentdomain/service/PaymentService.java`

**Triggered by**: Kafka `payment.requested` topic (published by ParentOrderPaymentSaga).

**Payload** (Kafka message):
```json
{
  "parent_order_id": 55,
  "user_id": 42,
  "total_amount": 1200000,
  "currency": "VND",
  "timeout_at": "2026-04-18T15:00:00",
  "timestamp": "2026-04-18T10:00:00Z"
}
```

**Actions**:

1. **Idempotency Check**: If transaction already exists with status PENDING or SUCCESS, skip.

2. **Create Stripe PaymentIntent**:
   ```java
   PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
       .setAmount(toStripeAmount(totalAmount))     // Convert to long (smallest currency unit)
       .setCurrency("vnd")
       .setAutomaticPaymentMethods(
           PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
               .setEnabled(true)
               .build()
       )
       .putMetadata("parent_order_id", String.valueOf(parentOrderId))
       .putMetadata("user_id", String.valueOf(userId))
       .build();
   
   PaymentIntent pi = PaymentIntent.create(params);
   ```

3. **Save Transaction**:
   ```java
   Transaction tx = new Transaction();
   tx.setParentOrderId(parentOrderId);
   tx.setAmount(totalAmount);
   tx.setMethod("STRIPE");
   tx.setStatus("PENDING");
   tx.setStripePiId(pi.getId());
   tx.setTransRef(buildTransRef(parentOrderId));  // TXN-20260418101234-55
   tx.setRawResponse(pi.toJson());
   transactionRepository.save(tx);
   ```

4. **On Error**: Publish **payment.failed** to Kafka:
   ```json
   {
     "parent_order_id": 55,
     "reason": "Khoi tao thanh toan that bai",
     "timestamp": "2026-04-18T10:00:00Z"
   }
   ```

---

## Kafka Topics

### Topics Added

| Topic | Producer | Consumer | Purpose |
|-------|----------|----------|---------|
| `payment.requested` | ParentOrderPaymentSaga | PaymentService | Initiate payment & create Stripe PaymentIntent |
| `payment.success` | PaymentService (webhook handler) | PaymentKafkaEventBridge | Signal successful payment |
| `payment.failed` | PaymentService (webhook handler) / init error | PaymentKafkaEventBridge | Signal failed payment |

### Topic Registration

**order-service** (`KafkaTopicConfig`):
```java
@Bean public NewTopic paymentRequested() { return topic(KafkaTopics.PAYMENT_REQUESTED); }
```

**payment-service** (`KafkaTopicConfig`):
```java
@Bean public NewTopic paymentRequested() { return topic(KafkaTopics.PAYMENT_REQUESTED); }  // consume
```

**common-lib** (`KafkaTopics.java`):
```java
public static final String PAYMENT_REQUESTED = "payment.requested";
```

---

## Integration with OrderProcessingSaga

### OrderCreatedEvent → OrderProcessingSaga

The existing **OrderProcessingSaga** (per sub-order) continues to handle:
- Payment timeout scheduling (30 minutes)
- Shipping deadline scheduling
- Publishing Kafka events for downstream services

### OrderPaidEvent → OrderProcessingSaga

When `ParentOrderPaymentSaga` emits **OrderPaidEvent**, the per-order saga:
```java
@SagaEventHandler(associationProperty = "orderId")
public void on(OrderPaidEvent event) {
    cancelDeadline(PAYMENT_TIMEOUT, paymentDeadlineId);
    paymentDeadlineId = null;
    log.info("[Saga][{}] PAID", orderId);
    // payment.success already published by payment-service — no Kafka publish needed here
}
```

This cancels the payment timeout deadline (since payment succeeded) and allows the order to move to SHIPPING state.

---

## Files Modified / Created

### New Files
- `backend/order-service/src/main/java/com/flashsale/orderdomain/axon/event/ParentOrderCheckoutCreatedEvent.java`
- `backend/order-service/src/main/java/com/flashsale/orderdomain/axon/event/ParentOrderPaymentSucceededEvent.java`
- `backend/order-service/src/main/java/com/flashsale/orderdomain/axon/event/ParentOrderPaymentFailedEvent.java`
- `backend/order-service/src/main/java/com/flashsale/orderdomain/axon/saga/ParentOrderPaymentSaga.java`
- `backend/order-service/src/main/java/com/flashsale/orderdomain/service/PaymentKafkaEventBridge.java`

### Modified Files

**order-service**:
- `src/main/java/com/flashsale/orderdomain/service/OrderService.java`
  - `checkout()`: Emit `ParentOrderCheckoutCreatedEvent` instead of handling payment directly
  - Removed `@KafkaListener` methods for `PAYMENT_SUCCESS` / `PAYMENT_FAILED` (moved to bridge)
- `src/main/java/com/flashsale/orderdomain/config/KafkaTopicConfig.java`
  - Added `paymentRequested()` bean

**payment-service**:
- `src/main/java/com/flashsale/paymentdomain/service/PaymentService.java`
  - Added `@KafkaListener onPaymentRequested(String message)` method
  - Creates Stripe PaymentIntent on receipt of `payment.requested`
- `src/main/java/com/flashsale/paymentdomain/config/KafkaTopicConfig.java`
  - Added `paymentRequested()` bean (consumer)

**common-lib**:
- `src/main/java/com/flashsale/commonlib/event/KafkaTopics.java`
  - Added `PAYMENT_REQUESTED` constant

---

## State Transitions

### Sub-Order State Flow

```
PENDING
  ├─ [ParentOrderPaymentSucceededEvent] → PAID (OrderPaidEvent emitted)
  │   └─ [OrderShippedEvent] → SHIPPING (seller updates tracking)
  │       └─ [OrderDeliveredEvent] → DELIVERED (buyer confirms receipt)
  │
  └─ [ParentOrderPaymentFailedEvent] → CANCELLED (OrderCancelledEvent emitted)
      └─ @EndSaga (OrderProcessingSaga ends)
```

### Parent-Order Level

```
Checkout created
  ├─ PaymentRequested
  │   ├─ PaymentInitialized (Stripe PI created)
  │   └─ [Stripe Webhook: succeeded] → PaymentSucceeded
  │       └─ [All sub-orders → PAID] → ParentPaymentSaga ends
  │
  └─ [Stripe Webhook: failed] or [init error] → PaymentFailed
      └─ [All sub-orders → CANCELLED] → ParentPaymentSaga ends
```

---

## Validation & Testing

### Build Verification
```bash
cd backend
mvn -pl common-lib,order-service,payment-service -am -DskipTests compile
# BUILD SUCCESS
```

### Unit Test
```bash
cd backend/payment-service
mvn test -Dtest=PaymentDomainApplicationTests
# Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
```

### Manual Test Flow (Local)

1. **Start services**:
   ```bash
   docker-compose up -d  # Start Postgres, Kafka, Axon Server, Stripe mock
   cd backend/order-service && mvn spring-boot:run
   cd backend/payment-service && mvn spring-boot:run
   ```

2. **Call checkout API**:
   ```bash
   POST http://localhost:8080/api/v1/orders/checkout
   {
     "address_id": 1,
     "item_ids": ["item_1", "item_2"],
     "use_loyalty_points": false,
     "loyalty_points_to_use": 0
   }
   ```
   Response: `parentOrderId`, `orders[]`, `payment_status: PENDING`

3. **Verify Axon saga started**:
   - Check Axon Server UI: http://localhost:8124
   - Saga instance created for `ParentOrderPaymentSaga` with association `parentOrderId`

4. **Verify payment.requested published**:
   - Kafka topic `payment.requested` has message
   - `parent_order_id`, `total_amount` are correct

5. **PaymentService processes payment.requested**:
   - Transaction created with status PENDING
   - Stripe PaymentIntent created
   - Log: `Payment initialized: parentOrderId=..., txId=..., piId=...`

6. **Simulate Stripe webhook success**:
   - POST webhook to `http://localhost:8082/api/v1/stripe/webhooks`
   - Transaction updated to SUCCESS
   - Kafka `payment.success` published

7. **Verify order status updated**:
   - All sub-orders: status = PAID
   - OrderPaidEvent emitted for each
   - OrderProcessingSaga cancelled payment timeout

8. **Seller can now ship**:
   - Call `POST /api/v1/orders/{orderId}/tracking` (seller)
   - Order transitions → SHIPPING

---

## Error Handling

### Timeout Scenarios

**Payment Timeout (30 min)**: If no `payment.success` received within 30 minutes:
- `OrderProcessingSaga.onPaymentTimeout()` fires (Axon deadline)
- Updates order status → CANCELLED
- Publishes `order.auto_cancelled`
- Worker service (JOB-13) is a safety net fallback

### Idempotency

**Payment Initialization**: If `payment.requested` is received twice (Kafka retry):
- Check: existing Transaction with status PENDING or SUCCESS?
- If yes: **skip** (don't create duplicate Stripe PaymentIntent)
- Log: `Skip payment.requested because transaction already exists`

### Init Failure

**Stripe API Error**: If PaymentIntent creation fails:
- Catch exception
- Publish `payment.failed` to Kafka with reason `"Khoi tao thanh toan that bai"`
- `PaymentKafkaEventBridge` converts to `ParentOrderPaymentFailedEvent`
- Orders automatically cancelled

---

## Advantages of Saga Pattern

1. **Exactly-Once Semantics**: Axon saga prevents duplicate payment initialization even with Kafka retries
2. **Atomic Order Status Update**: All sub-orders transition together (PENDING → PAID or CANCELLED)
3. **Deadline Support**: Payment timeout deadline handled by OrderProcessingSaga (existing mechanism)
4. **Decoupling**: order-service and payment-service remain loosely coupled via events
5. **Observability**: Full saga execution path visible in Axon Server UI
6. **Compensation**: If payment fails, saga-driven compensation (order cancellation) is automatic

---

## Future Improvements

1. **Add payment.requested timeout handler**: If PaymentService doesn't respond within X seconds, trigger timeout compensation
2. **Implement retry logic**: Exponential backoff for failed payment initialization
3. **Support partial refunds**: Allow refund of individual order items post-delivery
4. **Add analytics events**: Publish domain events to analytics platform for payment funnel tracking
5. **Multi-currency support**: Extend `ParentOrderPaymentSaga` to handle multiple currencies per basket

---

## References

- **Axon Framework Saga**: https://docs.axoniq.io/reference-guide/axon-framework/advanced-concepts/saga
- **Project Architecture**: `docs/01_OVERVIEW.md`
- **API Contracts**: `docs/02_API.md`
- **Business Logic**: `docs/03_BUSINESS.md`

