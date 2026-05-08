# Kafka Events — Order Service

**Service**: order-service — Port 8083  
**Pattern**: Axon CQRS/ES + Saga  
**Last Updated**: 2026-05-04

> Order Service dùng **Axon CQRS** — events được produce từ Aggregate/Saga handlers,  
> consume events từ Payment Service để update order status.

---

## 📤 Events Produced

### 1. `order.created`
| Field | Value |
|-------|-------|
| **Consumers** | 🔗 [Product Service](../product-service/KAFKA_EVENTS.md) (Inventory), 🔗 [Search Service](../search-service/KAFKA_EVENTS.md) |
| **Trigger** | Buyer checks out via `POST /orders/checkout` |

**Consumer Actions**:
- **Product Service (Inventory)**: Lock stock for each SKU
- **Search Service**: Update sold count

---

### 2. `order.cancelled`
| Field | Value |
|-------|-------|
| **Consumers** | 🔗 [Product Service](../product-service/KAFKA_EVENTS.md) (Cart + Inventory), 🔗 [Identity Service](../identity-service/KAFKA_EVENTS.md), 🔗 [Notification Service](../notification-service/KAFKA_EVENTS.md) |
| **Trigger** | Buyer cancels, seller cancels, or JOB-13 auto-cancel |

**Consumer Actions**:
- **Product Service**: Remove items from cart, unlock stock
- **Notification Service**: Send cancellation notification

---

### 3. `order.shipped`
| Field | Value |
|-------|-------|
| **Consumers** | 🔗 [Notification Service](../notification-service/KAFKA_EVENTS.md) |
| **Trigger** | Seller updates tracking via `PUT /orders/{orderId}/tracking` |

**Consumer Actions**:
- **Notification Service**: Send shipping notification with tracking number

---

### 4. `order.delivered`
| Field | Value |
|-------|-------|
| **Consumers** | 🔗 [Identity Service](../identity-service/KAFKA_EVENTS.md), 🔗 [Notification Service](../notification-service/KAFKA_EVENTS.md) |
| **Trigger** | Buyer confirms receipt via `POST /orders/{orderId}/confirm-received` |

**Consumer Actions**:
- **Notification Service**: Send delivery confirmation

---

### 5. `order.returned`
| Field | Value |
|-------|-------|
| **Consumers** | 🔗 [Payment Service](../payment-service/KAFKA_EVENTS.md) (Refund), 🔗 [Product Service](../product-service/KAFKA_EVENTS.md) (Inventory), 🔗 [Notification Service](../notification-service/KAFKA_EVENTS.md) |
| **Trigger** | Seller confirms return via `POST /orders/{orderId}/return-to-sender` |

**Consumer Actions**:
- **Payment Service**: Auto-create full refund, process Stripe refund
- **Product Service (Inventory)**: Restore stock
- **Notification Service**: Notify buyer of refund initiation

---

### 6. `order.checkout_completed`
| Field | Value |
|-------|-------|
| **Consumers** | 🔗 [Product Service](../product-service/KAFKA_EVENTS.md) (Cart) |
| **Trigger** | Order successfully created and payment initiated |

**Consumer Actions**:
- **Product Service (Cart)**: Remove checked-out items from user's cart

---

### 7. `order.auto_cancelled`
| Field | Value |
|-------|-------|
| **Consumers** | 🔗 [Product Service](../product-service/KAFKA_EVENTS.md) (Inventory), 🔗 [Identity Service](../identity-service/KAFKA_EVENTS.md), 🔗 [Notification Service](../notification-service/KAFKA_EVENTS.md) |
| **Trigger** | Payment timeout — Axon Deadline fires, JOB-13 as safety net |

**Consumer Actions**:
- **Product Service (Inventory)**: Unlock stock
- **Notification Service**: Send auto-cancellation notice

---

### 8. `seller.order_cancelled`
| Field | Value |
|-------|-------|
| **Consumers** | 🔗 [Identity Service](../identity-service/KAFKA_EVENTS.md) |
| **Trigger** | Seller proactively cancels order (separate from buyer cancellation) |

**Consumer Actions**:

---

## 📥 Events Consumed

### 1. `payment.success` ← 🔗 [Payment Service](../payment-service/KAFKA_EVENTS.md)
| Field | Value |
|-------|-------|
| **Action** | Mark orders as PAID |
| **Producer** | Payment Service (Stripe webhook `payment_intent.succeeded`) |

### 2. `payment.failed` ← 🔗 [Payment Service](../payment-service/KAFKA_EVENTS.md)
| Field | Value |
|-------|-------|
| **Action** | Keep orders as PENDING; unlock stock if retry exhausted |
| **Producer** | Payment Service (Stripe webhook `payment_intent.payment_failed`) |

### 3. `refund.stripe_auto` ← 🔗 [Payment Service](../payment-service/KAFKA_EVENTS.md)
| Field | Value |
|-------|-------|
| **Action** | Mark orders as refunded (chargeback) |
| **Producer** | Payment Service (Stripe chargeback/dispute) |

### 4. `refund.rts_completed` ← 🔗 [Payment Service](../payment-service/KAFKA_EVENTS.md)
| Field | Value |
|-------|-------|
| **Action** | Update order with RTS completion status |
| **Producer** | Payment Service (RTS refund processed) |

### 5. `stripe.transfer.reversed` ← 🔗 [Payment Service](../payment-service/KAFKA_EVENTS.md)
| Field | Value |
|-------|-------|
| **Action** | Log transfer reversal for order reconciliation |
| **Producer** | Payment Service (Stripe reverses transfer) |

---

## 🔄 Request-Reply

Order Service là **Requester** cho 5 cặp request-reply sau:

### 1. `order.stock_check` — Order ↔ Product (Inventory)
| Role | Service | Topic |
|------|---------|-------|
| **Requester** | **Order Service** | `order.stock_check.request` |
| **Responder** | 🔗 [Product Service](../product-service/KAFKA_EVENTS.md) | `order.stock_check.response` |

**Cycle**:
```
Order Service (Requester) ──order.stock_check.request──→ Product Service (Responder)
    │                       (items[{skuId, qty}])           │
    │                                                        │ check stock
    │◀──order.stock_check.response──────────────────────────│
    │     ({allAvailable, results})                          │
    │                                                        │
    └──→ if allAvailable → proceed with checkout             ┘
```

### 2. `order.payment_status` — Order ↔ Payment
| Role | Service | Topic |
|------|---------|-------|
| **Requester** | **Order Service** | `order.payment_status.request` |
| **Responder** | 🔗 [Payment Service](../payment-service/KAFKA_EVENTS.md) | `order.payment_status.response` |

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

### 3. `order.cart_items` — Order ↔ Product (Cart)
| Role | Service | Topic |
|------|---------|-------|
| **Requester** | **Order Service** | `order.cart_items.request` |
| **Responder** | 🔗 [Product Service](../product-service/KAFKA_EVENTS.md) | `order.cart_items.response` |

**Cycle**:
```
Order Service (Requester) ──order.cart_items.request──→ Product Service (Responder)
    │                       (userId, selectedItemIds)       │
    │                                                        │ get cart items
    │◀──order.cart_items.response──────────────────────────│
    │     (items[{cartItemId, skuId, price, qty, ...}])     │
    │                                                        │
    └──→ create order from cart items                        ┘
```

### 4. `order.address` — Order ↔ Identity
| Role | Service | Topic |
|------|---------|-------|
| **Requester** | **Order Service** | `order.address.request` |
| **Responder** | 🔗 [Identity Service](../identity-service/KAFKA_EVENTS.md) | `order.address.response` |

**Cycle**:
```
Order Service (Requester) ──order.address.request──→ Identity Service (Responder)
    │                       (userId, addressId)           │
    │                                                      │ look up address
    │◀──order.address.response───────────────────────────│
    │     (recipientName, phone, street, ward, city)      │
    │                                                      │
    └──→ use address for order shipping                    ┘
```

### 5. `order.refunds` — Order ↔ Payment
| Role | Service | Topic |
|------|---------|-------|
| **Requester** | **Order Service** | `order.refunds.request` |
| **Responder** | 🔗 [Payment Service](../payment-service/KAFKA_EVENTS.md) | `order.refunds.response` |

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

Xem chi tiết: 🔗 [11_KAFKA_REQUEST_REPLY.md](../../messaging/11_KAFKA_REQUEST_REPLY.md)

---

## 🔗 Related Kafka Docs

| Service | Link |
|---------|------|
| Product Service | 🔗 [product-service/KAFKA_EVENTS.md](../product-service/KAFKA_EVENTS.md) |
| Payment Service | 🔗 [payment-service/KAFKA_EVENTS.md](../payment-service/KAFKA_EVENTS.md) |
| Identity Service | 🔗 [identity-service/KAFKA_EVENTS.md](../identity-service/KAFKA_EVENTS.md) |
| Search Service | 🔗 [search-service/KAFKA_EVENTS.md](../search-service/KAFKA_EVENTS.md) |
| Notification Service | 🔗 [notification-service/KAFKA_EVENTS.md](../notification-service/KAFKA_EVENTS.md) |
| Full Index | 🔗 [KAFKA_EVENTS.md](../../messaging/KAFKA_EVENTS.md) |
