# Kafka Events -- Order Service

> Service: order-service (Port 8083)
> Source: Backend code `com.flashsale.orderservice`
> Generated: 2026-05-10

---

## Events Consumed

### payment.success (from Payment Service)

| Field | Value |
|-------|-------|
| **Consumer** | order-service (PaymentKafkaEventBridge) |
| **GroupId** | order-service-group |
| **Topic** | `payment.success` |
| **Action** | Publish Axon `ParentOrderPaymentSucceededEvent` → Saga transitions sub-orders to PAID |

**Payload:**
```json
{ "parent_order_id": 1 }
```

### payment.failed (from Payment Service)

| Field | Value |
|-------|-------|
| **Consumer** | order-service (PaymentKafkaEventBridge) |
| **GroupId** | order-service-group |
| **Topic** | `payment.failed` |
| **Action** | Publish Axon `ParentOrderPaymentFailedEvent` → Saga cancels sub-orders, releases stock |

**Payload:**
```json
{ "parent_order_id": 1, "reason": "Thanh toan that bai" }
```

### refund.admin_approved (from Payment Service)

| Field | Value |
|-------|-------|
| **Consumer** | order-service (PaymentKafkaEventBridge) |
| **GroupId** | order-service-group |
| **Topic** | `refund.admin_approved` |
| **Action** | PARTIAL refund → order.status = PARTIALLY_REFUNDED; FULL refund → order.status = REFUNDED |

**Payload:**
```json
{ "order_id": 5, "type": "FULL", "tracking_number": "RN123456789VN" }
```

### refund.rts_completed (from Payment Service)

| Field | Value |
|-------|-------|
| **Consumer** | order-service (PaymentKafkaEventBridge) |
| **GroupId** | order-service-group |
| **Topic** | `refund.rts_completed` |
| **Action** | Log confirmation that Stripe refund executed (order already at RETURNED status) |

### stock.reservation.expired (from Product Service)

| Field | Value |
|-------|-------|
| **Consumer** | order-service (StockKafkaEventBridge) |
| **GroupId** | order-service-group |
| **Topic** | `stock.reservation.expired` |
| **Action** | Auto-cancel parent order when stock reservation expires (reservation TTL exceeded) |

---

## Events Produced

All order events are produced via Axon Saga (OrderProcessingSaga, ParentOrderPaymentSaga):

| Event | Axon Event Class | Kafka Topic | Consumers |
|-------|-----------------|-------------|-----------|
| Order Created | `OrderCreatedEvent` | `order.created` | Product (stock lock), Search (sold count) |
| Order Cancelled | `OrderCancelledEvent` | `order.cancelled` | Product (unlock stock), Identity (audit), Notification |
| Seller Order Cancelled | `SellerOrderCancelledEvent` | `seller.order_cancelled` | Payment (auto-refund), Notification (buyer apology), Product (idempotent stock release) |
| Order Shipped | `OrderShippedEvent` | `order.shipped` | Notification (tracking update) |
| Order Delivered | `OrderDeliveredEvent` | `order.delivered` | Notification (delivery), Identity (unlock seller) |
| Order Returned | `OrderReturnedEvent` | `order.returned` | Product (restore stock), Payment (auto-refund) |
| Order Paid | `OrderPaidEvent` | `order.paid` | Product (confirm reservation) |
| Parent Checkout Created | `ParentOrderCheckoutCreatedEvent` | `order.checkout_created` | Payment (create intent) |
| Parent Payment Succeeded | `ParentOrderPaymentSucceededEvent` | `order.parent_paid` | Internal |
| Parent Payment Failed | `ParentOrderPaymentFailedEvent` | `order.parent_failed` | Internal |

### order.created

**Payload:**
```json
{
  "order_id": 5,
  "parent_order_id": 1,
  "customer_id": 42,
  "seller_id": 99,
  "items": [
    { "variant_id": "uuid", "quantity": 2, "price": 150000 }
  ],
  "total_amount": 300000,
  "timestamp": "2026-05-10T08:00:00Z"
}
```

### order.cancelled

| Field | Value |
|-------|-------|
| **Producer** | order-service (Saga) |
| **Consumers** | product-service (release stock), identity-service (audit), notification-service (notify buyer) |
| **Partition Key** | `parent_order_id` |
| **Retention** | 30 days |

**Payload:**
```json
{
  "order_id": 5,
  "parent_order_id": 1,
  "cancel_reason": "BUYER_REQUEST",
  "cancelled_by": "BUYER",
  "timestamp": "2026-05-10T08:30:00Z"
}
```

### seller.order_cancelled

| Field | Value |
|-------|-------|
| **Producer** | order-service (Saga) — chỉ emit khi `cancelled_by = SELLER` |
| **Consumers** | payment-service (trigger full refund), notification-service (notify buyer + apology), product-service (release stock — nếu chưa nhận từ `order.cancelled`) |
| **Partition Key** | `parent_order_id` |
| **Retention** | 30 days |
| **Status** | NEW — re-activated 2026-05-10 (xem `MVP_ANALYSIS.md` đính chính v3 và BR-ORDER-026) |
| **Note** | Phát SONG SONG với `order.cancelled` (không thay thế). Subscribers cần idempotent dedupe theo `event_id`. |

**Payload:**
```json
{
  "topic": "seller.order_cancelled",
  "event_id": "evt_20260510_seller_cancel_001",
  "event_type": "seller.order_cancelled",
  "timestamp": "2026-05-10T08:30:00Z",
  "source_service": "order-service",
  "version": 1,
  "data": {
    "order_id": 5,
    "parent_order_id": 1,
    "seller_id": 99,
    "customer_id": 42,
    "cancel_reason": "Het hang, khong the fulfill",
    "transaction_id": 1234,
    "refund_amount": 300000,
    "currency": "VND",
    "cancelled_at": "2026-05-10T08:30:00Z"
  }
}
```

**Downstream effects:**
- Payment Service: tạo refund (type=FULL, reason=SELLER_CANCEL) tự động không cần admin duyệt. Khi Stripe refund thành công, Payment Service emit `refund.rts_completed` (hoặc tương đương) để Order Service cập nhật trạng thái.
- Notification Service: gửi notification `NOTIF-ORDER-CANCELLED-BY-SELLER` cho buyer kèm reason + thông tin refund.
- Product Service: idempotent — nếu đã release stock từ `order.cancelled` thì bỏ qua.

### order.shipped

**Payload:**
```json
{
  "order_id": 5,
  "tracking_number": "SPX123456789",
  "carrier": "SPX",
  "timestamp": "2026-05-10T10:00:00Z"
}
```

### order.returned

**Payload:**
```json
{
  "order_id": 5,
  "return_tracking_number": "RN987654321",
  "evidence_images": ["https://cdn.marketplace.vn/evidence/img1.jpg"],
  "timestamp": "2026-05-15T14:00:00Z"
}
```

### order.payment_timeout

| Field | Value |
|-------|-------|
| **Consumers** | order-service (self-consume → auto-cancel saga), Notification Service |
| **Trigger** | JOB-22 quét parent_orders ở `PENDING_PAYMENT` quá 10 phút |
| **Status** | NEW — bổ sung 2026-05-10 (MVP MUST-HAVE, xem `MVP_ANALYSIS.md` §3.1) |
| **Retention** | 30 days |
| **Partition Key** | `parent_order_id` |

**Payload:**
```json
{
  "topic": "order.payment_timeout",
  "event_id": "evt_20260510_payment_to_001",
  "event_type": "order.payment_timeout",
  "timestamp": "2026-05-10T10:10:00Z",
  "source_service": "order-service",
  "version": 1,
  "data": {
    "parent_order_id": 1,
    "order_ids": [5, 6],
    "session_id": "chk_2026_05_10_abc123",
    "timeout_threshold_minutes": 10,
    "timeout_reason": "PAYMENT_NOT_COMPLETED",
    "auto_cancelled_at": "2026-05-10T10:10:00Z"
  }
}
```

**Downstream effects:**
- Order saga: ParentOrderPaymentTimeoutEvent → cancel sub-orders → emit `order.cancelled` cho mỗi sub-order.
- Product service: nhận `order.cancelled` → release stock reservations.
- Notification: thông báo buyer "Đơn hàng đã bị hủy do hết thời gian thanh toán".

---

### order.checkout_completed

| Field | Value |
|-------|-------|
| **Trigger** | Checkout successful — parent order and all sub-orders created |
| **Consumers** | Product Service (Cart — remove purchased items) |

**Payload:**
```json
{
  "parent_order_id": 1,
  "customer_id": 42,
  "orders": [{"order_id": 5, "seller_id": 99}],
  "item_ids": ["uuid1", "uuid2"],
  "timestamp": "2026-05-12T10:00:00Z"
}
```

---

## Request-Reply (Order Service is Requester)

| Request Topic | Response Topic | Responder | Purpose |
|--------------|----------------|-----------|---------|
| `order.stock_check.request` | `order.stock_check.response` | Product Service | Validate stock before checkout |
| `order.cart_items.request` | `order.cart_items.response` | Product Service | Fetch cart items for checkout |
| `order.address.request` | `order.address.response` | Identity Service | Validate shipping address |
| `order.payment_status.request` | `order.payment_status.response` | Payment Service | Query payment status |
| `order.refunds.request` | `order.refunds.response` | Payment Service | Query refund history/detail |
| `order.refund_presigned_url.request` | `order.refund_presigned_url.response` | Payment Service | Get presigned URL for evidence upload |
