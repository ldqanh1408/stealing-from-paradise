# Stealing From Paradise — Documentation Index

**Project**: stealing-from-paradise (Flash Sale E-Commerce Platform)
**Version**: v5.4
**Last Updated**: 2026-05-01

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
| [KAFKA_EVENTS.md](KAFKA_EVENTS.md) | 50+ Kafka topics with payloads |
| [database-entities.md](database-entities.md) | Database schema reference |
| [erd.mermaid](erd.mermaid) | Entity-Relationship Diagram |

### Business & Policy

| File | Purpose |
|------|---------|
| [03_BUSINESS.md](03_BUSINESS.md) | Business logic, 9 workflows, trust score, loyalty |
| [04_POLICIES.md](04_POLICIES.md) | System rules, trust score tiers, flash sale policies |
| [05_OPERATIONS.md](05_OPERATIONS.md) | 23 cronjobs, data retention, cleanup |

### API Documentation

| File | Purpose |
|------|---------|
| [02_API.md](02_API.md) | Unified API specification (all services, 100+ endpoints) |
| [identity-service/02_API_identity_service.md](identity-service/02_API_identity_service.md) | Auth, users, loyalty (31 endpoints) |
| [product-service/02_API_product_service.md](product-service/02_API_product_service.md) | Products, variants, cart (24 endpoints) |
| [search-service/02_API_search_service.md](search-service/02_API_search_service.md) | Elasticsearch search |
| [order-service/02_API_order_service.md](order-service/02_API_order_service.md) | Orders, checkout, RTS (16 endpoints) |
| [payment-service/02_API_payment_service.md](payment-service/02_API_payment_service.md) | Stripe, payments, refunds (12 endpoints) |
| [flashsale-service/02_API_flash_sale_service.md](flashsale-service/02_API_flash_sale_service.md) | Flash sale sessions |
| [notification-service/02_API_notification_service.md](notification-service/02_API_notification_service.md) | SSE notifications |
| [admin-service/02_API_admin.md](admin-service/02_API_admin.md) | Admin APIs (14 endpoints) |
| [api/README.md](api/README.md) | API documentation summary |

### Integration Deep Dives

| File | Purpose |
|------|---------|
| [06_PAYMENT_SAGA_FLOW.md](06_PAYMENT_SAGA_FLOW.md) | Axon Saga payment orchestration (OrderProcessingSaga, ParentOrderPaymentSaga) |
| [08_PAYMENT_ORDER_INTEGRATION.md](08_PAYMENT_ORDER_INTEGRATION.md) | Order-Payment integration, Stripe webhooks, multi-vendor transfers |
| [07_BUSINESS_FLOWS.md](07_BUSINESS_FLOWS.md) | Visual flows (Mermaid diagrams) |

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
| **API reference** | [02_API.md](02_API.md) or service-specific docs above |
| **Debug Kafka flows** | [KAFKA_EVENTS.md](KAFKA_EVENTS.md) → [ARCHITECTURE.md](ARCHITECTURE.md) |
| **Understand payment saga** | [06_PAYMENT_SAGA_FLOW.md](06_PAYMENT_SAGA_FLOW.md) |
| **Database schema** | [database-entities.md](database-entities.md) → [erd.mermaid](erd.mermaid) |
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
| **Kafka Topics** | 50+ |
| **Cronjobs** | 23 |
| **Documentation Files** | 25 |
| **Authentication** | JWT (RS256) |

---

## ✨ v5.4 Features

- Trust Score Tier system (6 levels: BRONZE → ELITE)
- Multi-vendor order split with Stripe Connect
- Real-time SSE notifications
- Return To Sender (RTS) refund workflow
- Cart merged into Product Service
- Loyalty merged into Identity Service
- 50+ Kafka topics for event-driven architecture
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
**Last Updated**: 2026-05-01
