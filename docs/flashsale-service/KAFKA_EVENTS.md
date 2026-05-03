# Kafka Events — Flash Sale Service

**Service**: flashsale-service — Port 8086  
**Pattern**: Axon CQRS/ES + Redis Lua  
**Last Updated**: 2026-05-04

> Flash Sale Service quản lý flash sale sessions, items, và xử lý mua hàng  
> tốc độ cao (50k+ req/s) dùng Redis Lua atomic operations.

---

## 📤 Events Produced

### 1. `flash_sale.session_started`
| Field | Value |
|-------|-------|
| **Consumers** | 🔗 [Notification Service](../notification-service/KAFKA_EVENTS.md) |
| **Trigger** | JOB-01 updates session status UPCOMING → ACTIVE |

**Consumer Actions**:
- **Notification Service**: Send SSE reminders to subscribed users

---

### 2. `flash_sale.session_ended`
| Field | Value |
|-------|-------|
| **Consumers** | 🔗 [Notification Service](../notification-service/KAFKA_EVENTS.md), 🔗 [Product Service](../product-service/KAFKA_EVENTS.md) |
| **Trigger** | JOB-01 updates session status ACTIVE → ENDED |

**Consumer Actions**:
- **Notification Service**: Send session end notification
- **Product Service (Cart)**: Remove expired flash sale items (JOB-07)

---

### 3. `flash_sale.item_approved`
| Field | Value |
|-------|-------|
| **Consumers** | 🔗 [Notification Service](../notification-service/KAFKA_EVENTS.md) |
| **Trigger** | Admin approves item via `POST /flash-sale/sessions/{id}/items/{id}/approve` |

**Consumer Actions**:
- **Notification Service**: Notify seller of approval

---

### 4. `flash_sale.item_rejected`
| Field | Value |
|-------|-------|
| **Consumers** | 🔗 [Notification Service](../notification-service/KAFKA_EVENTS.md) |
| **Trigger** | Admin rejects item via `POST /admin/flash-sale/items/{id}/reject` |

**Consumer Actions**:
- **Notification Service**: Notify seller of rejection with reason

---

### 5. `flash_sale.item_sold`
| Field | Value |
|-------|-------|
| **Consumers** | 🔗 [Product Service](../product-service/KAFKA_EVENTS.md) (Inventory) |
| **Trigger** | User buys via `POST /flash-sale/sessions/{id}/buy` (Redis Lua success) |

**Consumer Actions**:
- **Product Service (Inventory)**: Update sold count and remaining stock

---

## 📥 Events Consumed

Flash Sale Service **không consume event từ Kafka** (tất cả trigger đều từ REST API, cronjob JOB-01, hoặc nội bộ Axon).

---

## 🔗 Related Kafka Docs

| Service | Link |
|---------|------|
| Product Service | 🔗 [product-service/KAFKA_EVENTS.md](../product-service/KAFKA_EVENTS.md) |
| Notification Service | 🔗 [notification-service/KAFKA_EVENTS.md](../notification-service/KAFKA_EVENTS.md) |
| Full Index | 🔗 [KAFKA_EVENTS.md](../KAFKA_EVENTS.md) |
