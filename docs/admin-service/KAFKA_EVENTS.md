# Kafka Events — Admin Service

**Service**: Admin Service (không phải standalone process — admin routes handled trong các services)  
**Last Updated**: 2026-05-04

> Admin Service không phải một process riêng. Các admin endpoints được route từ API Gateway  
> vào services tương ứng dưới prefix `/admin/**`. Tuy nhiên, các event dưới đây thuộc về  
> **admin domain** (moderation, system jobs) và được document riêng.

---

## 📤 Events Produced

### 1. `product.approved`
| Field | Value |
|-------|-------|
| **Consumers** | 🔗 [Search Service](../search-service/KAFKA_EVENTS.md), 🔗 [Notification Service](../notification-service/KAFKA_EVENTS.md) |
| **Trigger** | Admin approves product via `/admin/products/{productId}/approve` |

**Consumer Actions**:
- **Search Service**: Index product in Elasticsearch (visible in search)
- **Notification Service**: Notify seller of approval

---

### 2. `product.rejected`
| Field | Value |
|-------|-------|
| **Consumers** | 🔗 [Search Service](../search-service/KAFKA_EVENTS.md), 🔗 [Notification Service](../notification-service/KAFKA_EVENTS.md) |
| **Trigger** | Admin rejects product via `/admin/products/{productId}/reject` |

**Consumer Actions**:
- **Search Service**: Remove from Elasticsearch index (if previously indexed)
- **Notification Service**: Notify seller with rejection reason

---

### 3. `product.auto_hidden`
| Field | Value |
|-------|-------|
| **Consumers** | 🔗 [Search Service](../search-service/KAFKA_EVENTS.md), 🔗 [Notification Service](../notification-service/KAFKA_EVENTS.md) |
| **Trigger** | JOB-16 auto-hides rejected products after 90-day retention period |

**Consumer Actions**:
- **Search Service**: Remove from Elasticsearch index
- **Notification Service**: Notify seller product was auto-hidden

---

## 📥 Events Consumed

Admin Service **không consume Kafka events** (admin actions are triggered by REST requests,  
system jobs produce events for other services to consume).

---

## 🔗 Related Kafka Docs

| Service | Link |
|---------|------|
| Search Service | 🔗 [search-service/KAFKA_EVENTS.md](../search-service/KAFKA_EVENTS.md) |
| Notification Service | 🔗 [notification-service/KAFKA_EVENTS.md](../notification-service/KAFKA_EVENTS.md) |
| Full Index | 🔗 [KAFKA_EVENTS.md](../KAFKA_EVENTS.md) |
