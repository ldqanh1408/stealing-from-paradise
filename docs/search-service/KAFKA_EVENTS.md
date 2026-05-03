# Kafka Events — Search Service

**Service**: search-service — Port 8091  
**Last Updated**: 2026-05-04

> Search Service **chỉ consumer** (không produce event nào).  
> Lắng nghe các event từ Product, Identity và Admin để đồng bộ Elasticsearch index.

---

## 📊 Overview

| Metric | Value |
|--------|-------|
| **Events Produced** | 0 |
| **Events Consumed** | 10 |
| **Database** | Elasticsearch |

---

## 📥 Events Consumed

### 1. `product.created` ← 🔗 [Product Service](../product-service/KAFKA_EVENTS.md)
| Field | Value |
|-------|-------|
| **Action** | Prepare indexing (wait for APPROVED status) |
| **Producer** | Product Service (seller creates product) |

### 2. `product.updated` ← 🔗 [Product Service](../product-service/KAFKA_EVENTS.md)
| Field | Value |
|-------|-------|
| **Action** | Update Elasticsearch index with new product data |
| **Producer** | Product Service (seller updates product) |

### 3. `product.deleted` ← 🔗 [Product Service](../product-service/KAFKA_EVENTS.md)
| Field | Value |
|-------|-------|
| **Action** | Remove from Elasticsearch index |
| **Producer** | Product Service (seller deletes product) |

### 4. `product.approved` ← 🔗 [Admin Service](../admin-service/KAFKA_EVENTS.md)
| Field | Value |
|-------|-------|
| **Action** | Index product in Elasticsearch (now visible in search) |
| **Producer** | Admin Service (admin approves product) |

### 5. `product.rejected` ← 🔗 [Admin Service](../admin-service/KAFKA_EVENTS.md)
| Field | Value |
|-------|-------|
| **Action** | Remove from Elasticsearch index |
| **Producer** | Admin Service (admin rejects product) |

### 6. `product.auto_hidden` ← 🔗 [Admin Service](../admin-service/KAFKA_EVENTS.md)
| Field | Value |
|-------|-------|
| **Action** | Remove from Elasticsearch index (90-day retention expired) |
| **Producer** | Admin Service (JOB-16 auto-hide) |

### 7. `category.updated` ← 🔗 [Product Service](../product-service/KAFKA_EVENTS.md)
| Field | Value |
|-------|-------|
| **Action** | Update category filters and facets in index |
| **Producer** | Product Service (admin updates category) |

### 8. `inventory.adjusted` ← 🔗 [Product Service](../product-service/KAFKA_EVENTS.md)
| Field | Value |
|-------|-------|
| **Action** | Update product stock status (in stock / out of stock) |
| **Producer** | Product Service (seller adjusts stock) |

### 9. `order.created` ← 🔗 [Order Service](../order-service/KAFKA_EVENTS.md)
| Field | Value |
|-------|-------|
| **Action** | Update sold count for products (optional) |
| **Producer** | Order Service (checkout initiated) |

### 10. `account.locked` ← 🔗 [Identity Service](../identity-service/KAFKA_EVENTS.md)
| Field | Value |
|-------|-------|
| **Action** | Hide seller's products from storefront |
| **Producer** | Identity Service (account locked) |

---

## 🔗 Related Kafka Docs

| Service | Link |
|---------|------|
| Product Service | 🔗 [product-service/KAFKA_EVENTS.md](../product-service/KAFKA_EVENTS.md) |
| Identity Service | 🔗 [identity-service/KAFKA_EVENTS.md](../identity-service/KAFKA_EVENTS.md) |
| Order Service | 🔗 [order-service/KAFKA_EVENTS.md](../order-service/KAFKA_EVENTS.md) |
| Admin Service | 🔗 [admin-service/KAFKA_EVENTS.md](../admin-service/KAFKA_EVENTS.md) |
| Full Index | 🔗 [KAFKA_EVENTS.md](../KAFKA_EVENTS.md) |
