# Kafka Events — Identity Service

**Service**: identity-service — Port 8081  
**Last Updated**: 2026-05-04

Liệt kê đầy đủ Kafka topics mà Identity Service **produces** và **consumes**, kèm cross-link đến các service khác.

---

## 📤 Events Produced

### 1. `account.locked`
| Field | Value |
|-------|-------|
| **Consumers** | 🔗 [Notification Service](../notification-service/KAFKA_EVENTS.md), 🔗 [Search Service](../search-service/KAFKA_EVENTS.md) |
| **Trigger** | Admin locks account via `/admin/users/{userId}/lock` (auto-lock by trust score removed in MVP) |

**Payload & Consumer Actions**: Xem chi tiết tại [Notification → account.locked](../notification-service/KAFKA_EVENTS.md) | [Search → account.locked](../search-service/KAFKA_EVENTS.md)

---

### 2. `account.unlocked`
| Field | Value |
|-------|-------|
| **Consumers** | 🔗 [Notification Service](../notification-service/KAFKA_EVENTS.md) |
| **Trigger** | Admin unlocks account via `/admin/users/{userId}/unlock` |

---

### 3. `account.auto_locked`
| Field | Value |
|-------|-------|
| **Consumers** | 🔗 [Notification Service](../notification-service/KAFKA_EVENTS.md) |
| **Trigger** | JOB-17 auto-locks accounts (JOB-17 removed in MVP) |

---

### 4. `appeal.resolved` (removed in MVP)

---

### 9. `seller.posting_suspended`
| Field | Value |
|-------|-------|
| **Consumers** | 🔗 [Notification Service](../notification-service/KAFKA_EVENTS.md) |
| **Trigger** | Admin suspends seller posting via `/admin/users/{userId}/suspend-posting` |

---

### 10. `seller.posting_resumed`
| Field | Value |
|-------|-------|
| **Consumers** | 🔗 [Notification Service](../notification-service/KAFKA_EVENTS.md) |
| **Trigger** | Admin resumes seller posting after remediation |

---

### 11. `product.approved`
| Field | Value |
|-------|-------|
| **Consumers** | 🔗 [Search Service](../search-service/KAFKA_EVENTS.md), 🔗 [Notification Service](../notification-service/KAFKA_EVENTS.md) |
| **Trigger** | Admin approves product via `/admin/products/{productId}/approve` |

**Consumer Actions**:
- **Search Service**: Index product in Elasticsearch (visible in search)
- **Notification Service**: Notify seller of approval

---

### 12. `product.rejected`
| Field | Value |
|-------|-------|
| **Consumers** | 🔗 [Search Service](../search-service/KAFKA_EVENTS.md), 🔗 [Notification Service](../notification-service/KAFKA_EVENTS.md) |
| **Trigger** | Admin rejects product via `/admin/products/{productId}/reject` |

**Consumer Actions**:
- **Search Service**: Remove from Elasticsearch index (if previously indexed)
- **Notification Service**: Notify seller with rejection reason

---

### 13. `product.auto_hidden`
| Field | Value |
|-------|-------|
| **Consumers** | 🔗 [Search Service](../search-service/KAFKA_EVENTS.md), 🔗 [Notification Service](../notification-service/KAFKA_EVENTS.md) |
| **Trigger** | JOB-16 auto-hides rejected products after 90-day retention period |

**Consumer Actions**:
- **Search Service**: Remove from Elasticsearch index
- **Notification Service**: Notify seller product was auto-hidden

---

## 📥 Events Consumed

### 1. `order.delivered` ← 🔗 [Order Service](../order-service/KAFKA_EVENTS.md)
| Field | Value |
|-------|-------|
| **Action** | Update order delivery state for analytics |
| **Producer** | Order Service (buyer confirms receipt) |

### 2. `order.cancelled` ← 🔗 [Order Service](../order-service/KAFKA_EVENTS.md)
| Field | Value |
|-------|-------|
| **Action** | Update cancellation state for analytics |
| **Producer** | Order Service (buyer/seller/system cancels) |

### 3. `refund.admin_approved` ← 🔗 [Payment Service](../payment-service/KAFKA_EVENTS.md)
| Field | Value |
|-------|-------|
| **Action** | Record refund approval for audit |
| **Producer** | Payment Service (admin approves refund) |

### 4. `seller.order_cancelled` ← 🔗 [Order Service](../order-service/KAFKA_EVENTS.md)
| Field | Value |
|-------|-------|
| **Action** | Record seller-initiated cancellation |
| **Producer** | Order Service |

### 5. `stripe.account_suspended` ← 🔗 [Payment Service](../payment-service/KAFKA_EVENTS.md)
| Field | Value |
|-------|-------|
| **Action** | Flag seller account, potentially lock posting |
| **Producer** | Payment Service (Stripe suspends seller account) |

---

## 🔄 Request-Reply

### `order.address`
| Role | Service | Topic |
|------|---------|-------|
| **Requester** | 🔗 [Order Service](../order-service/KAFKA_EVENTS.md) | `order.address.request` |
| **Responder** | **Identity Service** | `order.address.response` |

**Cycle**:
```
Order Service (Requester) ──order.address.request──→ Identity Service (Responder)
    │                       (userId, addressId)          │
    │                                                     │ look up address
    │◀──order.address.response───────────────────────────│
    │     (recipientName, phone, street, ward, ...)       │
    │                                                     │
    └──→ match correlationId → use address for checkout  ┘
```

Xem chi tiết tại: 🔗 [11_KAFKA_REQUEST_REPLY.md](../../messaging/11_KAFKA_REQUEST_REPLY.md) (#5 Order ↔ Identity)

---

## 🔗 Related Kafka Docs

| Service | Link |
|---------|------|
| Order Service | 🔗 [order-service/KAFKA_EVENTS.md](../order-service/KAFKA_EVENTS.md) |
| Payment Service | 🔗 [payment-service/KAFKA_EVENTS.md](../payment-service/KAFKA_EVENTS.md) |
| Notification Service | 🔗 [notification-service/KAFKA_EVENTS.md](../notification-service/KAFKA_EVENTS.md) |
| Search Service | 🔗 [search-service/KAFKA_EVENTS.md](../search-service/KAFKA_EVENTS.md) |
| Admin Service (merged into Identity) | 🔗 [identity-service/KAFKA_EVENTS.md](../identity-service/KAFKA_EVENTS.md) |
| Full Index | 🔗 [KAFKA_EVENTS.md](../../messaging/KAFKA_EVENTS.md) |
