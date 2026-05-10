# Kafka Events -- Payment Service

> Service: payment-service (Port 8082)
> Source: Backend code `com.flashsale.paymentservice`
> Generated: 2026-05-10

---

## Events Consumed

### order.returned (from Order Service)

| Field | Value |
|-------|-------|
| **Consumer** | payment-service |
| **Action** | Auto-create full refund (RTS flow), process Stripe refund |

### order.checkout_created (from Order Service)

| Field | Value |
|-------|-------|
| **Consumer** | payment-service |
| **Action** | Create Stripe PaymentIntent, create TRANSACTIONS record |

---

## Events Produced

### payment.success

| Field | Value |
|-------|-------|
| **Trigger** | Stripe webhook `payment_intent.succeeded` |
| **Consumers** | Order Service |

**Payload:**
```json
{
  "parent_order_id": 1,
  "transaction_id": "txn_abc123",
  "stripe_payment_intent_id": "pi_3NqX...",
  "amount": 450000,
  "currency": "vnd",
  "payment_method": "card",
  "timestamp": "2026-05-10T08:05:00Z"
}
```

### payment.failed

| Field | Value |
|-------|-------|
| **Trigger** | Stripe webhook `payment_intent.payment_failed` |
| **Consumers** | Order Service |

**Payload:**
```json
{
  "parent_order_id": 1,
  "reason": "card_declined",
  "timestamp": "2026-05-10T08:05:00Z"
}
```

### refund.requested

| Field | Value |
|-------|-------|
| **Trigger** | Order Service publishes `REFUND_REQUESTED` or `REFUND_FULL_REQUESTED` |
| **Consumers** | Notification Service |

**Payload:**
```json
{
  "refund_type": "PARTIAL",
  "order_id": 5,
  "parent_order_id": 1,
  "user_id": 42,
  "seller_id": 99,
  "reason": "San pham bi loi",
  "amount": 150000,
  "group_ref": "uuid",
  "items": [{ "order_item_id": 10, "quantity": 1, "refund_amount": 150000 }],
  "evidence_images": [],
  "timestamp": "2026-05-12T10:00:00Z"
}
```

### refund.admin_approved

| Field | Value |
|-------|-------|
| **Trigger** | Admin approves refund via `POST /admin/refunds/{id}/approve` |
| **Consumers** | Order Service, Identity Service, Notification Service |

**Payload:**
```json
{
  "refund_id": 7,
  "order_id": 5,
  "type": "FULL",
  "amount": 150000,
  "adjust_amount": 140000,
  "tracking_number": "RN123456789VN",
  "approved_by": 1,
  "timestamp": "2026-05-12T14:00:00Z"
}
```

### refund.rejected

| Field | Value |
|-------|-------|
| **Trigger** | Admin rejects refund via `POST /admin/refunds/{id}/reject` |
| **Consumers** | Identity Service, Notification Service |

**Payload:**
```json
{
  "refund_id": 7,
  "order_id": 5,
  "rejected_by": 1,
  "reason": "Khong du dieu kien hoan tien",
  "timestamp": "2026-05-12T14:00:00Z"
}
```

### refund.stripe_auto

| Field | Value |
|-------|-------|
| **Trigger** | Stripe webhook `charge.refunded` (automatic RTS refund) |
| **Consumers** | Order Service |

### refund.rts_completed

| Field | Value |
|-------|-------|
| **Trigger** | Stripe refund for RTS flow completes |
| **Consumers** | Order Service |

### seller.transfer.eligible

| Field | Value |
|-------|-------|
| **Trigger** | JOB-23 PayoutScheduler phát hiện `orders.delivered_at + 30 days <= NOW()` và chưa có refund pending |
| **Consumers** | Notification Service |
| **Status** | NEW — bổ sung 2026-05-10 (MVP SHOULD-HAVE) |
| **Retention** | 90 days |
| **Partition Key** | `seller_id` |

**Payload:**
```json
{
  "topic": "seller.transfer.eligible",
  "event_id": "evt_20260610_payout_001",
  "data": {
    "seller_transfer_id": 88,
    "order_id": 5,
    "seller_id": 99,
    "transfer_amount": 651000,
    "platform_commission_amount": 49000,
    "currency": "vnd",
    "eligible_at": "2026-06-10T00:00:00Z"
  }
}
```

### seller.transfer.paid_out

| Field | Value |
|-------|-------|
| **Trigger** | Stripe webhook `payout.paid` hoặc `transfer.created` thành công |
| **Consumers** | Notification Service |
| **Status** | NEW — MVP SHOULD-HAVE |
| **Retention** | 90 days |

**Payload:**
```json
{
  "topic": "seller.transfer.paid_out",
  "data": {
    "seller_transfer_id": 88,
    "seller_id": 99,
    "amount": 651000,
    "stripe_transfer_id": "tr_1Ab...",
    "stripe_payout_id": "po_1Cd...",
    "paid_at": "2026-06-10T03:00:00Z"
  }
}
```

### seller.transfer.failed

| Field | Value |
|-------|-------|
| **Trigger** | Stripe webhook `payout.failed` hoặc retry exhausted (PayoutScheduler `payout_retry_count > 3`) |
| **Consumers** | Notification Service, audit |
| **Status** | NEW — MVP SHOULD-HAVE |
| **Retention** | 365 days (compliance) |

**Payload:**
```json
{
  "topic": "seller.transfer.failed",
  "data": {
    "seller_transfer_id": 88,
    "seller_id": 99,
    "amount": 651000,
    "failure_code": "account_closed",
    "failure_reason": "The destination account has been closed",
    "retry_count": 3,
    "failed_at": "2026-06-10T03:05:00Z"
  }
}
```

---

## Request-Reply (Payment Service is Responder)

| Request Topic | Response Topic | Requester | Purpose |
|--------------|----------------|-----------|---------|
| `order.payment_status.request` | `order.payment_status.response` | Order Service | Query payment/transaction status |
| `order.refunds.request` | `order.refunds.response` | Order Service | Query refund history/detail |
| `order.refund_presigned_url.request` | `order.refund_presigned_url.response` | Order Service | Generate presigned URL for evidence upload |

---

## Direct Kafka Publishing (via KafkaTemplate)

Payment Service directly publishes to these topics via `KafkaTemplate`:

| Topic | Published From | Purpose |
|-------|---------------|---------|
| `refund.requested` | RefundController (order-service) via `REFUND_REQUESTED` | Partial refund request |
| `refund.full_requested` | RefundController (order-service) via `REFUND_FULL_REQUESTED` | Full refund request |
