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
| **Trigger** | Admin locks account via `/admin/users/{userId}/lock`; Auto-lock when trust score < 10 |

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
| **Trigger** | JOB-17 auto-locks accounts when trust score < 10 |

---

### 4. `appeal.resolved`
| Field | Value |
|-------|-------|
| **Consumers** | 🔗 [Notification Service](../notification-service/KAFKA_EVENTS.md) |
| **Trigger** | Admin resolves appeal via `/admin/appeals/{appealId}/resolve` |

---

### 5. `loyalty.points_earned`
| Field | Value |
|-------|-------|
| **Consumers** | 🔗 [Notification Service](../notification-service/KAFKA_EVENTS.md) |
| **Trigger** | Order delivered, buyer confirms receipt → points confirmed |

---

### 6. `loyalty.points_used`
| Field | Value |
|-------|-------|
| **Consumers** | Identity Service (internal) |
| **Trigger** | User spends loyalty points at checkout |

---

### 7. `loyalty.points_refunded`
| Field | Value |
|-------|-------|
| **Consumers** | Identity Service (internal) |
| **Trigger** | Refund approved, points returned to user |

---

### 8. `loyalty.points_expired`
| Field | Value |
|-------|-------|
| **Consumers** | 🔗 [Notification Service](../notification-service/KAFKA_EVENTS.md) |
| **Trigger** | JOB-X expires points older than 365 days |

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

### 11. `trust_score.warning`
| Field | Value |
|-------|-------|
| **Consumers** | 🔗 [Notification Service](../notification-service/KAFKA_EVENTS.md) |
| **Trigger** | User reaches tier boundary threshold (e.g., GOLD→SILVER) |

---

## 📥 Events Consumed

### 1. `order.delivered` ← 🔗 [Order Service](../order-service/KAFKA_EVENTS.md)
| Field | Value |
|-------|-------|
| **Action** | Award seller trust_score +5; Confirm pending loyalty points |
| **Producer** | Order Service (buyer confirms receipt) |

### 2. `order.cancelled` ← 🔗 [Order Service](../order-service/KAFKA_EVENTS.md)
| Field | Value |
|-------|-------|
| **Action** | Adjust trust score (BUYER: -5 if excessive cancel); Refund pending loyalty points |
| **Producer** | Order Service (buyer/seller/system cancels) |

### 3. `refund.admin_approved` ← 🔗 [Payment Service](../payment-service/KAFKA_EVENTS.md)
| Field | Value |
|-------|-------|
| **Action** | If `caused_by=SELLER`: adjust seller trust_score -5; Return loyalty points |
| **Producer** | Payment Service (admin approves refund) |

### 4. `seller.order_cancelled` ← 🔗 [Order Service](../order-service/KAFKA_EVENTS.md)
| Field | Value |
|-------|-------|
| **Action** | Adjust seller trust score when seller proactively cancels |
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

Xem chi tiết tại: 🔗 [11_KAFKA_REQUEST_REPLY.md](../11_KAFKA_REQUEST_REPLY.md) (#5 Order ↔ Identity)

---

## 🔗 Related Kafka Docs

| Service | Link |
|---------|------|
| Order Service | 🔗 [order-service/KAFKA_EVENTS.md](../order-service/KAFKA_EVENTS.md) |
| Payment Service | 🔗 [payment-service/KAFKA_EVENTS.md](../payment-service/KAFKA_EVENTS.md) |
| Notification Service | 🔗 [notification-service/KAFKA_EVENTS.md](../notification-service/KAFKA_EVENTS.md) |
| Search Service | 🔗 [search-service/KAFKA_EVENTS.md](../search-service/KAFKA_EVENTS.md) |
| Admin Service | 🔗 [admin-service/KAFKA_EVENTS.md](../admin-service/KAFKA_EVENTS.md) |
| Full Index | 🔗 [KAFKA_EVENTS.md](../KAFKA_EVENTS.md) |
