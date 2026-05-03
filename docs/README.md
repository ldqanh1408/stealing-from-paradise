# Stealing From Paradise — Documentation Index

**Project**: stealing-from-paradise (Flash Sale E-Commerce Platform)
**Version**: v5.4
**Last Updated**: 2026-05-04

---

## Start Here

The primary entry point is **[00_INDEX.md](00_INDEX.md)** which contains the complete documentation map with role-based reading paths.

---

## Documentation Overview

### Core Documents

| File | Purpose |
|------|---------|
| [00_INDEX.md](00_INDEX.md) | **START HERE** — Complete index, service ports, navigation |
| [01_OVERVIEW.md](01_OVERVIEW.md) | Architecture, services, tech stack, project structure |
| [09_RUNNING.md](09_RUNNING.md) | How to run, build, and deploy |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Service interactions, Kafka flows, ASCII diagrams |
| [KAFKA_EVENTS.md](KAFKA_EVENTS.md) | Kafka index → per-service event docs (41 topics) |
| [11_KAFKA_REQUEST_REPLY.md](11_KAFKA_REQUEST_REPLY.md) | Kafka request-reply pattern (6 pairs) |
| [database-entities.md](database-entities.md) | Database schema reference |
| [ERD_FULL_SYSTEM.md](ERD_FULL_SYSTEM.md) | Entity-Relationship Diagram |

### Business & Policy

| File | Purpose |
|------|---------|
| [03_BUSINESS.md](03_BUSINESS.md) | Business logic, 9 workflows, trust score, loyalty |
| [04_POLICIES.md](04_POLICIES.md) | System rules, trust score tiers, flash sale policies |
| [05_OPERATIONS.md](05_OPERATIONS.md) | 23 cronjobs, data retention, cleanup |

### API Documentation

| File | Purpose |
|------|---------|
| [api/README.md](api/README.md) | API documentation summary (all services) |
| [identity-service/02_API_identity_service.md](identity-service/02_API_identity_service.md) | Auth, users, loyalty (31 endpoints) |
| [product-service/02_API_product_service.md](product-service/02_API_product_service.md) | Products, variants, cart (24 endpoints) |
| [search-service/02_API_search_service.md](search-service/02_API_search_service.md) | Elasticsearch search |
| [order-service/02_API_order_service.md](order-service/02_API_order_service.md) | Orders, checkout, RTS (18 endpoints) |
| [payment-service/02_API_payment_service.md](payment-service/02_API_payment_service.md) | Stripe, payments, refunds (15 endpoints) |
| [flashsale-service/02_API_flash_sale_service.md](flashsale-service/02_API_flash_sale_service.md) | Flash sale sessions |
| [notification-service/02_API_notification_service.md](notification-service/02_API_notification_service.md) | SSE notifications |
| [admin-service/02_API_admin.md](admin-service/02_API_admin.md) | Admin APIs (14 endpoints) |

### Integration Deep Dives

| File | Purpose |
|------|---------|
| [06_PAYMENT_SAGA_FLOW.md](06_PAYMENT_SAGA_FLOW.md) | Axon Saga payment orchestration (OrderProcessingSaga, ParentOrderPaymentSaga) |
| [08_PAYMENT_ORDER_INTEGRATION.md](08_PAYMENT_ORDER_INTEGRATION.md) | Order-Payment integration, Stripe webhooks, multi-vendor transfers |
| [07_BUSINESS_FLOWS.md](07_BUSINESS_FLOWS.md) | Visual flows (Mermaid diagrams) |

### Kafka Event Docs (Per-Service)

| File | Purpose |
|------|---------|
| [KAFKA_EVENTS.md](KAFKA_EVENTS.md) | **Index catalog** — all topics, flow chains, config |
| [identity-service/KAFKA_EVENTS.md](identity-service/KAFKA_EVENTS.md) | account.*, loyalty.*, seller.* events |
| [product-service/KAFKA_EVENTS.md](product-service/KAFKA_EVENTS.md) | product.*, inventory.*, cart.* events |
| [search-service/KAFKA_EVENTS.md](search-service/KAFKA_EVENTS.md) | Consumer-only (10 topics from Product, Identity, Order) |
| [order-service/KAFKA_EVENTS.md](order-service/KAFKA_EVENTS.md) | order.*, seller.order_cancelled + 5 request-reply |
| [payment-service/KAFKA_EVENTS.md](payment-service/KAFKA_EVENTS.md) | payment.*, refund.*, stripe.* events |
| [flashsale-service/KAFKA_EVENTS.md](flashsale-service/KAFKA_EVENTS.md) | flash_sale.* session & item events |
| [notification-service/KAFKA_EVENTS.md](notification-service/KAFKA_EVENTS.md) | Consumer-only (20+ topics, SSE output) |
| [admin-service/KAFKA_EVENTS.md](admin-service/KAFKA_EVENTS.md) | product.approved/rejected/auto_hidden |
| [worker-service/KAFKA_EVENTS.md](worker-service/KAFKA_EVENTS.md) | flash_sale.reminder, outbox pattern |
| [11_KAFKA_REQUEST_REPLY.md](11_KAFKA_REQUEST_REPLY.md) | 6 request-reply pairs with full cycle diagrams |

---

## Service Ports Reference

| Service | Port | Database | Notes |
|---------|------|----------|-------|
| API Gateway | 8080 | — | Entry point, JWT validation, routing |
| Discovery (Eureka) | 8761 | — | Service registry |
| Identity Service | 8081 | PostgreSQL | Auth, users, loyalty |
| Payment Service | 8082 | PostgreSQL + Axon | Stripe, payments |
| Order Service | 8083 | PostgreSQL + Axon | Orders, checkout, RTS |
| Flashsale Service | 8085 | PostgreSQL + Axon | Flash sales, Redis |
| Worker Service | 8086 | PostgreSQL + Axon | Outbox, failed events |
| Product Service | 8090 | MongoDB | Products, cart, variants |
| Search Service | 8091 | Elasticsearch | Full-text search |
| Notification Service | 8092 | MongoDB | SSE, real-time notifications |

### Frontend Apps

| App | Port |
|-----|------|
| Customer App | 3000 |
| Seller App | 3001 |
| Admin App | 3002 |

---

## Quick Navigation

| Goal | Go to |
|------|-------|
| **Understand the system** | [00_INDEX.md](00_INDEX.md) → [ARCHITECTURE.md](ARCHITECTURE.md) |
| **Run the project** | [09_RUNNING.md](09_RUNNING.md) |
| **API reference** | [api/README.md](api/README.md) or service-specific docs above |
| **Debug Kafka flows** | [KAFKA_EVENTS.md](KAFKA_EVENTS.md) → [ARCHITECTURE.md](ARCHITECTURE.md) |
| **Understand payment saga** | [06_PAYMENT_SAGA_FLOW.md](06_PAYMENT_SAGA_FLOW.md) |
| **Database schema** | [database-entities.md](database-entities.md) → [ERD_FULL_SYSTEM.md](ERD_FULL_SYSTEM.md) |
| **Business logic** | [03_BUSINESS.md](03_BUSINESS.md) → [07_BUSINESS_FLOWS.md](07_BUSINESS_FLOWS.md) |

---

## 🔐 Authentication

All API endpoints use **JWT (RS256)** for authentication except public endpoints:

```
Authorization: Bearer <jwt_token>
```

**Public Endpoints**:
- `GET /search/products` — Search products
- `GET /products/{id}` — Product details
- `GET /categories` — List categories
- `POST /auth/register` — Register new user
- `POST /auth/login` — Login

---

## 🛠️ Development

### Services Communication
- **Synchronous**: REST API (HTTP via API Gateway)
- **Asynchronous**: Kafka Events
- **Shared Cache**: Redis
- **Service Discovery**: Eureka
- **Event Store**: Axon Server (for order, payment, flashsale, worker services)

---

## 📊 Statistics

| Metric | Value |
|--------|-------|
| **Backend Services** | 11 (+ common-lib) |
| **Frontend Apps** | 3 |
| **API Endpoints** | 100+ |
| **Kafka Topics** | 41 (29 event + 12 request-reply) |
| **Cronjobs** | 23 |
| **Documentation Files** | 35 |
| **Authentication** | JWT (RS256) |

---

## ✨ v5.4 Features

- Trust Score Tier system (6 levels: BRONZE → ELITE)
- Multi-vendor order split with Stripe Connect
- Real-time SSE notifications
- Return To Sender (RTS) refund workflow
- Cart merged into Product Service
- Loyalty merged into Identity Service
- 41 Kafka topics for event-driven architecture
- High-concurrency Flash Sale (50k+ req/s with Redis Lua scripts)
- Axon Sagas: OrderProcessingSaga + ParentOrderPaymentSaga

---

## 📁 Project Structure

```
backend/
├── api-gateway/          (Spring Cloud Gateway — 8080)
├── discovery-service/     (Eureka — 8761)
├── identity-service/      (Auth, users, loyalty — 8081)
├── payment-service/       (Stripe Connect — 8082)
├── order-service/        (Orders, checkout, RTS — 8083)
├── flashsale-service/    (Flash sales — 8085)
├── worker-service/        (Outbox, DLQ — 8086)
├── product-service/       (Products, cart — 8090)
├── search-service/       (Elasticsearch — 8091)
├── notification-service/  (SSE — 8092)
└── common-lib/           (Shared DTOs, Kafka topics, security)

frontend/
├── apps/customer/         (Port 3000)
├── apps/seller/           (Port 3001)
├── apps/admin/            (Port 3002)
└── shared/                (API clients, stores, components)

docs/                     (25 documentation files)
```

---

**Version**: v5.4
**Status**: Production Ready
**Last Updated**: 2026-05-04
