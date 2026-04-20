# 🔄 Payment & Order Services Integration Flow

**Hướng dẫn tương tác giữa Payment Service và Order Service**

**Date**: 2026-04-20  
**Version**: v1.0  
**Status**: Production ✅  
**Language**: Vietnamese (Tiếng Việt)

---

## 📚 Mục Lục

1. [Tổng Quan](#tổng-quan)
2. [Luồng Thanh Toán Đơn Hàng](#luồng-thanh-toán-đơn-hàng)
3. [Kafka Event Bridge](#kafka-event-bridge)
4. [Saga Coordination](#saga-coordination)
5. [Refund Integration](#refund-integration)
6. [Thực Hành Tốt](#thực-hành-tốt)

---

## Tổng Quan

### Services Involved

| Service | Role | Database | Port |
|---------|------|----------|------|
| **order-service** | Manage order lifecycle, checkout | PostgreSQL + Axon | 8083 |
| **payment-service** | Process payments, refunds, Stripe | PostgreSQL | 8082 |

### Architecture Pattern

```
Order Service (Axon CQRS + Event Sourcing)
    ├─ Aggregate: ParentOrder, Order
    ├─ Saga: ParentOrderPaymentSaga, OrderProcessingSaga
    └─ Event Bus: Axon Server (8124)
                     ↕
    Kafka Topics (Async Messaging)
                     ↕
Payment Service (Traditional MVC)
    ├─ Controller: PaymentController
    ├─ Service: PaymentService, RefundService
    └─ Repository: TransactionRepository, SellerTransferRepository
```

---

## Luồng Thanh Toán Đơn Hàng

### 📍 Step-by-Step Flow

```
T0: Buyer clicks Checkout
    │
    ├─ POST /api/v1/orders/checkout (order-service)
    │  Request:
    │  {
    │    "address_id": 7,
    │    "item_ids": ["item_1", "item_2"],
    │    "use_loyalty_points": false
    │  }
    │
    └─ Response 201:
       {
         "parent_order_id": 55,
         "orders": [
           { "order_id": 100, "seller_id": 5, "amount": 700000 },
           { "order_id": 101, "seller_id": 9, "amount": 500000 }
         ],
         "total_amount": 1200000
       }

T1: Order Service creates Saga
    │
    ├─ Database: INSERT parent_orders, orders, order_items
    ├─ Axon Event: ParentOrderCheckoutCreatedEvent
    └─ @StartSaga: ParentOrderPaymentSaga triggered

T2: Saga publishes Kafka event
    │
    ├─ Topic: payment.requested
    └─ Payload:
       {
         "parent_order_id": 55,
         "user_id": 42,
         "total_amount": 1200000,
         "orders": [...]
       }

T3: Payment Service processes
    │
    ├─ @KafkaListener: PaymentService.onPaymentRequested()
    ├─ Create Stripe PaymentIntent
    ├─ INSERT transactions table (status=PENDING)
    ├─ INSERT seller_transfers (status=PENDING)
    └─ Database: SAVE client_secret

T4: Frontend gets client secret
    │
    ├─ GET /api/v1/payments/parent-order/55/client-secret
    └─ Response:
       {
         "client_secret": "pi_1Qx...secret_xyz123",
         "status": "PENDING"
       }

T5: Buyer pays with Stripe
    │
    ├─ Frontend: Stripe.confirmPayment()
    ├─ Stripe charges: 1,200,000 VND
    └─ Stripe status: succeeded

T6: Stripe Webhook
    │
    ├─ POST /api/v1/stripe/webhooks
    ├─ Event: payment_intent.succeeded
    └─ PaymentService.handlePaymentIntentSucceeded():
       ├─ UPDATE transactions.status = SUCCESS
       ├─ For each seller_transfer:
       │  └─ CREATE Stripe Transfer (net_amount)
       └─ PUBLISH Kafka: payment.success

T7: Order Service receives payment.success
    │
    ├─ @KafkaListener: PaymentKafkaEventBridge
    ├─ Emit: ParentOrderPaymentSucceededEvent
    ├─ @SagaEventHandler triggers
    └─ UPDATE orders.status = PAID (for each sub-order)

T8: Seller ships + Buyer receives
    │
    ├─ PUT /orders/{id}/tracking (seller)
    │  └─ UPDATE orders.status = SHIPPING
    │
    └─ POST /orders/{id}/confirm-received (buyer)
       └─ UPDATE orders.status = DELIVERED

✅ Complete!
```

---

## Kafka Event Bridge

### Topics Used

| Topic | Direction | Sender | Receiver | Payload |
|-------|-----------|--------|----------|---------|
| `payment.requested` | Order → Payment | order-service | payment-service | parent_order_id, total_amount, orders[] |
| `payment.success` | Payment → Order | payment-service | order-service | parent_order_id, transaction_id, stripe_pi_id |
| `payment.failed` | Payment → Order | payment-service | order-service | parent_order_id, error_message |
| `refund.requested` | Order → Payment | order-service | payment-service | refund_id, order_id, amount |
| `refund.admin_approved` | Payment → Order | payment-service | order-service | refund_id, amount, stripe_refund_id |
| `refund.rejected` | Payment → Order | payment-service | order-service | refund_id, reject_reason |
| `order.paid` | Order → * | order-service | notification-service | order_id, parent_order_id, amount |

### Implementation

#### Order Service Publisher

```java
@Component
public class OrderEventPublisher {
    
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    
    @EventHandler
    public void on(ParentOrderCheckoutCreatedEvent event) {
        // Convert Axon event to Kafka message
        PaymentRequestedEvent kafkaEvent = new PaymentRequestedEvent(
            event.getParentOrderId(),
            event.getBuyerId(),
            event.getTotalAmount(),
            event.getOrders()
        );
        
        // Publish to Kafka
        kafkaTemplate.send(
            "payment.requested",
            String.valueOf(event.getParentOrderId()),
            objectMapper.writeValueAsString(kafkaEvent)
        );
    }
}
```

#### Payment Service Consumer

```java
@Component
public class PaymentKafkaEventBridge {
    
    @Autowired
    private PaymentService paymentService;
    
    @KafkaListener(
        topics = "payment.requested",
        groupId = "payment-service-consumer"
    )
    public void onPaymentRequested(String message) throws Exception {
        PaymentRequestedEvent event = objectMapper.readValue(
            message,
            PaymentRequestedEvent.class
        );
        
        // Process payment request
        paymentService.initiatePayment(event);
    }
    
    @KafkaListener(
        topics = "payment.success",
        groupId = "order-service-consumer"
    )
    public void onPaymentSuccess(String message) throws Exception {
        PaymentSuccessEvent event = objectMapper.readValue(
            message,
            PaymentSuccessEvent.class
        );
        
        // Publish to Axon EventBus (triggers saga)
        eventGateway.publishEvent(
            new ParentOrderPaymentSucceededEvent(
                event.getParentOrderId(),
                event.getTransactionId()
            )
        );
    }
}
```

---

## Saga Coordination

### ParentOrderPaymentSaga

Saga điều phối quá trình thanh toán cho toàn bộ parent order:

```java
@Saga
public class ParentOrderPaymentSaga {
    
    private String parentOrderId;
    private List<String> orderIds;
    
    @StartSaga
    @SagaEventHandler(associationProperty = "parentOrderId")
    public void on(ParentOrderCheckoutCreatedEvent event) {
        this.parentOrderId = event.getParentOrderId();
        this.orderIds = event.getOrders().stream()
            .map(OrderData::getOrderId)
            .collect(Collectors.toList());
        
        // Publish payment.requested to Kafka
        kafkaTemplate.send("payment.requested", event);
    }
    
    @SagaEventHandler(associationProperty = "parentOrderId")
    public void on(ParentOrderPaymentSucceededEvent event) {
        // Mark all sub-orders as PAID
        for (String orderId : orderIds) {
            commandGateway.sendAndWait(
                new MarkOrderAsPaidCommand(orderId)
            );
        }
        SagaLifecycle.end();  // Saga complete
    }
    
    @SagaEventHandler(associationProperty = "parentOrderId")
    public void on(ParentOrderPaymentFailedEvent event) {
        // Compensate: Cancel all orders
        for (String orderId : orderIds) {
            commandGateway.sendAndWait(
                new CancelOrderCommand(orderId)
            );
        }
        SagaLifecycle.end();
    }
}
```

### OrderProcessingSaga

Saga quản lý vòng đời của từng order (tracking, delivery):

```java
@Saga
public class OrderProcessingSaga {
    
    private String orderId;
    
    @StartSaga
    @SagaEventHandler(associationProperty = "orderId")
    public void on(OrderCreatedEvent event) {
        this.orderId = event.getOrderId();
        
        // Schedule payment timeout (600 seconds)
        SagaLifecycle.registerDeadlineManager(
            DeadlineManager.instance(),
            "payment-timeout",
            Instant.now().plusSeconds(600),
            (deadline) -> handlePaymentTimeout()
        );
    }
    
    @SagaEventHandler(associationProperty = "orderId")
    public void on(OrderPaidEvent event) {
        // Order is PAID, awaiting tracking
    }
    
    @SagaEventHandler(associationProperty = "orderId")
    public void on(OrderShippedEvent event) {
        // Order is SHIPPING, awaiting delivery confirmation
    }
    
    @SagaEventHandler(associationProperty = "orderId")
    public void on(OrderDeliveredEvent event) {
        SagaLifecycle.end();  // Order complete
    }
}
```

---

## Refund Integration

### Refund Request Flow

```
Buyer requests partial refund:
    │
    ├─ POST /orders/{orderId}/refunds
    ├─ OrderService creates Refund record (status=PENDING)
    ├─ Publish Kafka: refund.requested
    │
    └─ PaymentService receives:
       ├─ @KafkaListener: onRefundRequested()
       ├─ Store refund in database
       └─ Notify admin for review

Admin approves refund:
    │
    ├─ POST /admin/refunds/{refundId}/approve
    ├─ PaymentService.executeStripeRefund()
    │  └─ Stripe.refunds.create()
    ├─ UPDATE refunds.status = SUCCESS
    ├─ Publish Kafka: refund.admin_approved
    │
    └─ Order Service receives:
       ├─ Update order status as needed
       └─ Notify buyer (via notification-service)

Return-to-Sender (RTS):
    │
    ├─ Seller: POST /orders/{id}/return-to-sender
    ├─ OrderService:
    │  ├─ UPDATE orders.status = RETURNED
    │  ├─ Create Refund (type=FULL, initiated_by=SELLER)
    │  ├─ Publish Kafka: order.returned_rts
    │
    └─ PaymentService receives:
       ├─ AUTO refund (NO admin approval needed)
       ├─ Stripe.refunds.create()
       ├─ UPDATE refunds.status = SUCCESS
       └─ Publish Kafka: refund.rts_completed
```

### Implementation

```java
// OrderService: Request refund
@PostMapping("/{orderId}/refunds")
public ResponseEntity<?> requestRefund(
    @PathVariable String orderId,
    @RequestBody RefundRequest request
) {
    // Validate order status
    Order order = orderRepository.findById(orderId).orElseThrow();
    if (!List.of("PAID", "SHIPPING", "DELIVERED").contains(order.getStatus())) {
        throw new InvalidStateException("Cannot refund in " + order.getStatus());
    }
    
    // Create Refund record
    Refund refund = new Refund();
    refund.setOrderId(orderId);
    refund.setAmount(request.getAmount());
    refund.setStatus("PENDING");
    refundRepository.save(refund);
    
    // Publish to Kafka
    kafkaTemplate.send("refund.requested", refund);
    
    return ResponseEntity.created(null).body(refund);
}

// PaymentService: Process refund request
@KafkaListener(topics = "refund.requested", groupId = "payment-service")
public void onRefundRequested(String message) throws Exception {
    Refund refund = objectMapper.readValue(message, Refund.class);
    
    // Store in database
    refundRepository.save(refund);
    
    // Notify admin (admin dashboard will show pending refunds)
    notificationService.notifyAdminRefundPending(refund);
}

// PaymentService: Admin approves
@PostMapping("/admin/refunds/{refundId}/approve")
public ResponseEntity<?> approveRefund(
    @PathVariable String refundId,
    @RequestBody AdminRefundRequest request
) {
    Refund refund = refundRepository.findById(refundId).orElseThrow();
    
    // Call Stripe
    Stripe.apiKey = stripeSecretKey;
    RefundCreateParams params = RefundCreateParams.builder()
        .setPaymentIntent(refund.getStripePaymentIntentId())
        .setAmount(request.getAdjustAmount() != null 
            ? request.getAdjustAmount() 
            : refund.getAmount())
        .build();
    Refund stripeRefund = Refund.create(params);
    
    // Update database
    refund.setStatus("SUCCESS");
    refund.setStripeRefundId(stripeRefund.getId());
    refund.setReviewedBy(getCurrentAdminId());
    refundRepository.save(refund);
    
    // Publish Kafka event
    kafkaTemplate.send("refund.admin_approved", refund);
    
    return ResponseEntity.ok(refund);
}
```

---

## Thực Hành Tốt

### 1. **Idempotency**

Đảm bảo các request có thể được retry mà không có side effects:

```java
// Payment Service: Check if already processed
public void initiatePayment(PaymentRequestedEvent event) {
    // Check if transaction already exists
    Transaction existing = transactionRepository.findByParentOrderId(
        event.getParentOrderId()
    );
    if (existing != null) {
        logger.warn("Transaction already exists for parent_order_id: {}",
            event.getParentOrderId());
        return;  // Skip (idempotent)
    }
    
    // Create new transaction
    Transaction transaction = new Transaction();
    transaction.setParentOrderId(event.getParentOrderId());
    transactionRepository.save(transaction);
}
```

### 2. **Error Handling**

Handle tất cả các error cases:

```java
@KafkaListener(topics = "payment.requested")
public void onPaymentRequested(String message) {
    try {
        PaymentRequestedEvent event = objectMapper.readValue(message, PaymentRequestedEvent.class);
        paymentService.initiatePayment(event);
    } catch (Exception e) {
        logger.error("Error processing payment.requested", e);
        
        // Publish payment.failed to Kafka
        kafkaTemplate.send("payment.failed", new PaymentFailedEvent(
            e.getMessage()
        ));
        
        // Alert admin
        alertService.notifyAdminError("Payment processing failed", e);
    }
}
```

### 3. **Transactional Consistency**

Sử dụng database transactions:

```java
@Transactional
public void processPaymentSuccess(PaymentSuccessEvent event) {
    // All these operations succeed or all fail together
    Transaction transaction = transactionRepository.findByParentOrderId(
        event.getParentOrderId()
    );
    transaction.setStatus("SUCCESS");
    transaction.setPaidAt(Instant.now());
    transactionRepository.save(transaction);
    
    List<SellerTransfer> transfers = sellerTransferRepository.findByParentOrderId(
        event.getParentOrderId()
    );
    for (SellerTransfer transfer : transfers) {
        transfer.setStatus("SUCCEEDED");
        sellerTransferRepository.save(transfer);
    }
    
    // If any line fails, transaction rolls back
}
```

### 4. **Monitoring & Logging**

Log tất cả các state changes:

```java
@EventHandler
public void on(PaymentInitiatedEvent event) {
    logger.info("💳 Payment initiated: parent_order_id={}, amount={}, seller_count={}",
        event.getParentOrderId(),
        event.getAmount(),
        event.getOrders().size());
}

@EventHandler
public void on(PaymentSucceededEvent event) {
    logger.info("✅ Payment succeeded: parent_order_id={}, stripe_pi_id={}",
        event.getParentOrderId(),
        event.getStripePaymentIntentId());
}

@EventHandler
public void on(PaymentFailedEvent event) {
    logger.error("❌ Payment failed: parent_order_id={}, error={}",
        event.getParentOrderId(),
        event.getErrorMessage());
}
```

---

## 📚 Related Documents

- [PAYMENT_SERVICE_API_FLOW.md](PAYMENT_SERVICE_API_FLOW.md) - Payment service details
- [06_PAYMENT_SAGA_FLOW.md](06_PAYMENT_SAGA_FLOW.md) - Saga implementation
- [API_DETAILED_JSON_v5_3_RTS.md](API_DETAILED_JSON_v5_3_RTS.md) - API requests/responses
- [BUSINESS_DOC_v5_3_rts_unified.md](BUSINESS_DOC_v5_3_rts_unified.md) - Business workflows
- [CLAUDE.md](../CLAUDE.md) - Project setup

---

**Last Updated**: 2026-04-20  
**Status**: ✅ Production Ready  
**Language**: Vietnamese (Tiếng Việt)

