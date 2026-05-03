# Kafka Events — Worker Service

**Service**: worker-service — Port 8086  
**Pattern**: Axon CQRS/ES  
**Last Updated**: 2026-05-04

> Worker Service chịu trách nhiệm outbox relay, DLQ retry, deadline timeouts  
> và các scheduled jobs. Nó vừa produce event (từ outbox), vừa consume event  
> để quản lý deadlines.

---

## 📤 Events Produced

### 1. `flash_sale.reminder`
| Field | Value |
|-------|-------|
| **Consumers** | 🔗 [Notification Service](../notification-service/KAFKA_EVENTS.md) |
| **Trigger** | JOB-02 sends reminders 1 hour before flash sale session |

**Consumer Actions**:
- **Notification Service**: Send SSE reminder to subscribed users

---

## 📥 Events Consumed

### 1. `order.created` ← 🔗 [Order Service](../order-service/KAFKA_EVENTS.md)
| Field | Value |
|-------|-------|
| **Action** | Start Axon Deadline for payment timeout (30 min) |
| **Producer** | Order Service (checkout initiated) |

### 2. `payment.success` ← 🔗 [Payment Service](../payment-service/KAFKA_EVENTS.md)
| Field | Value |
|-------|-------|
| **Action** | Cancel payment deadline timer |
| **Producer** | Payment Service (Stripe webhook) |

---

## 🔄 Outbox Pattern

Worker Service polls the **outbox** table and publishes events to Kafka:

```
Service Transaction ──→ outbox table (same DB transaction)
                            │
                    Worker Service polls every N seconds
                            │
                    ┌───────┴───────┐
                    ▼               ▼
              Kafka Topic      FAILED → DLQ table
                                    │
                            Admin retry via
                        POST /admin/failed-events/{id}/retry
```

**Related**:
- All services produce events via outbox (ensured by `@Transactional`)
- Worker relays: `order.*`, `payment.*`, `refund.*`, `flash_sale.*`, `account.*`, `product.*`
- DLQ table supports manual retry via Admin API

---

## 🔗 Related Kafka Docs

| Service | Link |
|---------|------|
| Notification Service | 🔗 [notification-service/KAFKA_EVENTS.md](../notification-service/KAFKA_EVENTS.md) |
| Order Service | 🔗 [order-service/KAFKA_EVENTS.md](../order-service/KAFKA_EVENTS.md) |
| Payment Service | 🔗 [payment-service/KAFKA_EVENTS.md](../payment-service/KAFKA_EVENTS.md) |
| Full Index | 🔗 [KAFKA_EVENTS.md](../KAFKA_EVENTS.md) |
