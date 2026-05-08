# Kafka Events — Payment Service (incl. Refund)

**Service**: payment-service — Port 8082  
**Pattern**: Axon CQRS/ES  
**Last Updated**: 2026-05-04

> Payment Service xử lý Stripe payments, refunds, transfers và dispute management.  
> Refund logic được consolidate vào Payment Service.

---

## 📤 Events Produced

### 1. `payment.success`
| Field | Value |
|-------|-------|
| **Consumers** | 🔗 [Order Service](../order-service/KAFKA_EVENTS.md), 🔗 [Notification Service](../notification-service/KAFKA_EVENTS.md) |
| **Trigger** | Stripe webhook `payment_intent.succeeded` |

**Consumer Actions**:
- **Order Service**: Mark orders as PAID
- **Notification Service**: Send payment confirmation

---

### 2. `payment.failed`
| Field | Value |
|-------|-------|
| **Consumers** | 🔗 [Order Service](../order-service/KAFKA_EVENTS.md), 🔗 [Notification Service](../notification-service/KAFKA_EVENTS.md) |
| **Trigger** | Stripe webhook `payment_intent.payment_failed` |

**Consumer Actions**:
- **Order Service**: Keep orders PENDING; unlock stock if retry exhausted
- **Notification Service**: Send payment failure notification

---

### 3. `refund.requested`
| Field | Value |
|-------|-------|
| **Consumers** | 🔗 [Notification Service](../notification-service/KAFKA_EVENTS.md) |
| **Trigger** | Buyer requests refund via `POST /orders/{orderId}/refunds` |

**Consumer Actions**:
- **Notification Service**: Notify seller of refund request

---

### 4. `refund.created`
| Field | Value |
|-------|-------|
| **Consumers** | 🔗 [Notification Service](../notification-service/KAFKA_EVENTS.md) |
| **Trigger** | Refund record created in system (pending processing) |

---

### 5. `refund.full_requested`
| Field | Value |
|-------|-------|
| **Consumers** | 🔗 [Notification Service](../notification-service/KAFKA_EVENTS.md), Payment Service (internal) |
| **Trigger** | Buyer requests full refund (RTS flow) |

---

### 6. `refund.admin_approved`
| Field | Value |
|-------|-------|
| **Consumers** | 🔗 [Identity Service](../identity-service/KAFKA_EVENTS.md), 🔗 [Notification Service](../notification-service/KAFKA_EVENTS.md) |
| **Trigger** | Admin approves refund via `POST /admin/refunds/{refundId}/approve` |

**Consumer Actions**:
- **Notification Service**: Notify buyer and seller

---

### 7. `refund.rejected`
| Field | Value |
|-------|-------|
| **Consumers** | 🔗 [Notification Service](../notification-service/KAFKA_EVENTS.md) |
| **Trigger** | Admin rejects refund via `POST /admin/refunds/{refundId}/reject` |

---

### 8. `refund.stripe_auto`
| Field | Value |
|-------|-------|
| **Consumers** | 🔗 [Order Service](../order-service/KAFKA_EVENTS.md), 🔗 [Identity Service](../identity-service/KAFKA_EVENTS.md) |
| **Trigger** | Stripe webhook `charge.refunded` (chargeback/dispute) |

**Consumer Actions**:
- **Order Service**: Mark orders as refunded

---

### 9. `refund.rts_completed`
| Field | Value |
|-------|-------|
| **Consumers** | 🔗 [Order Service](../order-service/KAFKA_EVENTS.md), 🔗 [Notification Service](../notification-service/KAFKA_EVENTS.md) |
| **Trigger** | Return To Sender refund fully processed |

---

### 10. `stripe.account_suspended`
| Field | Value |
|-------|-------|
| **Consumers** | 🔗 [Notification Service](../notification-service/KAFKA_EVENTS.md), 🔗 [Identity Service](../identity-service/KAFKA_EVENTS.md) |
| **Trigger** | Stripe suspends seller's connected account |

---

### 11. `stripe.dispute.created`
| Field | Value |
|-------|-------|
| **Consumers** | 🔗 [Notification Service](../notification-service/KAFKA_EVENTS.md) |
| **Trigger** | Buyer opens dispute on Stripe |

---

### 12. `stripe.dispute.closed`
| Field | Value |
|-------|-------|
| **Consumers** | 🔗 [Notification Service](../notification-service/KAFKA_EVENTS.md) |
| **Trigger** | Dispute resolved (closed) |

---

### 13. `stripe.transfer.reversed`
| Field | Value |
|-------|-------|
| **Consumers** | 🔗 [Order Service](../order-service/KAFKA_EVENTS.md), 🔗 [Notification Service](../notification-service/KAFKA_EVENTS.md) |
| **Trigger** | Stripe reverses transfer to platform |

---

### 14. `stripe.payout.failed`
| Field | Value |
|-------|-------|
| **Consumers** | 🔗 [Notification Service](../notification-service/KAFKA_EVENTS.md) |
| **Trigger** | Seller payout failed |

---

### 15. `seller.stripe_requirement`
| Field | Value |
|-------|-------|
| **Consumers** | 🔗 [Notification Service](../notification-service/KAFKA_EVENTS.md) |
| **Trigger** | Stripe requires additional KYC info from seller |

---

### 16. `payment.requested`
| Field | Value |
|-------|-------|
| **Consumers** | Payment Service (internal) |
| **Trigger** | Order service requests payment intent creation |

---

## 📥 Events Consumed

### 1. `order.returned` ← 🔗 [Order Service](../order-service/KAFKA_EVENTS.md)
| Field | Value |
|-------|-------|
| **Action** | Auto-create full refund, process Stripe refund |
| **Producer** | Order Service (RTS flow) |

---

## 🔄 Request-Reply

Payment Service là **Responder** cho 2 cặp request-reply sau:

### 1. `order.payment_status` — Order ↔ Payment
| Role | Service | Topic |
|------|---------|-------|
| **Requester** | 🔗 [Order Service](../order-service/KAFKA_EVENTS.md) | `order.payment_status.request` |
| **Responder** | **Payment Service** | `order.payment_status.response` |

**Cycle**:
```
Order Service (Requester) ──order.payment_status.request──→ Payment Service (Responder)
    │                       (orderId, paymentIntentId)          │
    │                                                           │ check payment status
    │◀──order.payment_status.response─────────────────────────│
    │     (status, amount, paidAt)                              │
    │                                                           │
    └──→ sync order status with payment                        ┘
```

### 2. `order.refunds` — Order ↔ Payment
| Role | Service | Topic |
|------|---------|-------|
| **Requester** | 🔗 [Order Service](../order-service/KAFKA_EVENTS.md) | `order.refunds.request` |
| **Responder** | **Payment Service** | `order.refunds.response` |

**Cycle**:
```
Order Service (Requester) ──order.refunds.request──→ Payment Service (Responder)
    │                       (orderId)                     │
    │                                                      │ look up refunds
    │◀──order.refunds.response───────────────────────────│
    │     (refunds[{refundId, amount, status, reason}])   │
    │                                                      │
    └──→ display refund info in order detail              ┘
```

Xem chi tiết: 🔗 [11_KAFKA_REQUEST_REPLY.md](../../messaging/11_KAFKA_REQUEST_REPLY.md) (#3 Payment Status, #6 Refunds)

---

## 🔗 Related Kafka Docs

| Service | Link |
|---------|------|
| Order Service | 🔗 [order-service/KAFKA_EVENTS.md](../order-service/KAFKA_EVENTS.md) |
| Identity Service | 🔗 [identity-service/KAFKA_EVENTS.md](../identity-service/KAFKA_EVENTS.md) |
| Notification Service | 🔗 [notification-service/KAFKA_EVENTS.md](../notification-service/KAFKA_EVENTS.md) |
| Full Index | 🔗 [KAFKA_EVENTS.md](../../messaging/KAFKA_EVENTS.md) |
