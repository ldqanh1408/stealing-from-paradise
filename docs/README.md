# Stealing From Paradise — Documentation Index

**Project**: stealing-from-paradise (Flash Sale E-Commerce Platform)
**Version**: v5.5
**Last Updated**: 2026-05-05

---

## Start Here

The primary entry point is **[00_INDEX.md](00_INDEX.md)** which contains the complete documentation map with role-based reading paths.

---

## Documentation Overview

### Core Documents

| File | Purpose |
|------|---------|
| [00_INDEX.md](00_INDEX.md) | **START HERE** — Complete index, service ports, navigation |
| [overview/01_OVERVIEW.md](overview/01_OVERVIEW.md) | Architecture, services, tech stack, project structure |
| [operations/09_RUNNING.md](operations/09_RUNNING.md) | How to run, build, and deploy |
| [overview/ARCHITECTURE.md](overview/ARCHITECTURE.md) | Service interactions, Kafka flows, ASCII diagrams |
| [messaging/KAFKA_EVENTS.md](messaging/KAFKA_EVENTS.md) | Kafka index → per-service event docs (47 topics) |
| [messaging/11_KAFKA_REQUEST_REPLY.md](messaging/11_KAFKA_REQUEST_REPLY.md) | Kafka request-reply pattern (6 pairs) |
| [database/database-entities.md](database/database-entities.md) | Database schema reference |
| [database/ERD_FULL_SYSTEM.md](database/ERD_FULL_SYSTEM.md) | Entity-Relationship Diagram |

### Business & Policy

| File | Purpose |
|------|---------|
| [business/03_BUSINESS.md](business/03_BUSINESS.md) | Business logic, 9 workflows, refunds, RTS |
| [operations/05_OPERATIONS.md](operations/05_OPERATIONS.md) | 17 cronjobs, data retention, cleanup |

### API Documentation

| File | Purpose |
|------|---------|
| [api/README.md](api/README.md) | API documentation summary (all services) |
| [services/identity-service/02_API_identity_service.md](services/identity-service/02_API_identity_service.md) | Auth, users (31 endpoints) |
| [services/product-service/02_API_product_service.md](services/product-service/02_API_product_service.md) | Products, variants, cart (24 endpoints) |
| [services/search-service/02_API_search_service.md](services/search-service/02_API_search_service.md) | Elasticsearch search |
| [services/order-service/02_API_order_service.md](services/order-service/02_API_order_service.md) | Orders, checkout, RTS (18 endpoints) |
| [services/payment-service/02_API_payment_service.md](services/payment-service/02_API_payment_service.md) | Stripe, payments, refunds (15 endpoints) |
| [services/flashsale-service/02_API_flash_sale_service.md](services/flashsale-service/02_API_flash_sale_service.md) | Flash sale sessions |
| [services/notification-service/02_API_notification_service.md](services/notification-service/02_API_notification_service.md) | SSE notifications |

### Integration Deep Dives

| File | Purpose |
|------|---------|
| [business/06_PAYMENT_SAGA_FLOW.md](business/06_PAYMENT_SAGA_FLOW.md) | Axon Saga payment orchestration (OrderProcessingSaga, ParentOrderPaymentSaga) |
| [business/08_PAYMENT_ORDER_INTEGRATION.md](business/08_PAYMENT_ORDER_INTEGRATION.md) | Order-Payment integration, Stripe webhooks, multi-vendor transfers |
| [business/07_BUSINESS_FLOWS.md](business/07_BUSINESS_FLOWS.md) | Visual flows (Mermaid diagrams) |

### Kafka Event Docs (Per-Service)

| File | Purpose |
|------|---------|
| [messaging/KAFKA_EVENTS.md](messaging/KAFKA_EVENTS.md) | **Index catalog** — all topics, flow chains, config |
| [services/identity-service/KAFKA_EVENTS.md](services/identity-service/KAFKA_EVENTS.md) | account.*, seller.* events |
| [services/product-service/KAFKA_EVENTS.md](services/product-service/KAFKA_EVENTS.md) | product.*, inventory.*, cart.* events |
| [services/search-service/KAFKA_EVENTS.md](services/search-service/KAFKA_EVENTS.md) | Consumer-only (10 topics from Product, Identity, Order) |
| [services/order-service/KAFKA_EVENTS.md](services/order-service/KAFKA_EVENTS.md) | order.*, seller.order_cancelled + 5 request-reply |
| [services/payment-service/KAFKA_EVENTS.md](services/payment-service/KAFKA_EVENTS.md) | payment.*, refund.*, stripe.* events |
| [services/flashsale-service/KAFKA_EVENTS.md](services/flashsale-service/KAFKA_EVENTS.md) | flash_sale.* session & item events |
| [services/notification-service/KAFKA_EVENTS.md](services/notification-service/KAFKA_EVENTS.md) | Consumer-only (20+ topics, SSE output) |
| [services/ai-chat-service/KAFKA_EVENTS.md](services/ai-chat-service/KAFKA_EVENTS.md) | ai_chat.*, tool_call.* events |
| [messaging/11_KAFKA_REQUEST_REPLY.md](messaging/11_KAFKA_REQUEST_REPLY.md) | 6 request-reply pairs with full cycle diagrams |

---

## Service Ports Reference

| Service | Port | Database | Notes |
|---------|------|----------|-------|
| API Gateway | 8080 | — | Entry point, JWT validation, routing |
| Discovery (Eureka) | 8761 | — | Service registry |
| Identity Service | 8081 | PostgreSQL | Auth, users |
| Payment Service | 8082 | PostgreSQL + Axon | Stripe, payments |
| Order Service | 8083 | PostgreSQL + Axon | Orders, checkout, RTS |
| Flashsale Service | 8085 | PostgreSQL + Axon | Flash sales, Redis |
| Product Service | 8090 | MongoDB | Products, cart, variants |
| Search Service | 8091 | Elasticsearch | Full-text search |
| Notification Service | 8092 | MongoDB | SSE, real-time notifications |
| AI Chat Service | 8093 | PostgreSQL | AI chat, tool calls, human-in-the-loop |

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
| **Understand the system** | [00_INDEX.md](00_INDEX.md) → [overview/ARCHITECTURE.md](overview/ARCHITECTURE.md) |
| **Run the project** | [operations/09_RUNNING.md](operations/09_RUNNING.md) |
| **API reference** | [api/README.md](api/README.md) or service-specific docs above |
| **Debug Kafka flows** | [messaging/KAFKA_EVENTS.md](messaging/KAFKA_EVENTS.md) → [overview/ARCHITECTURE.md](overview/ARCHITECTURE.md) |
| **Understand payment saga** | [business/06_PAYMENT_SAGA_FLOW.md](business/06_PAYMENT_SAGA_FLOW.md) |
| **Database schema** | [database/database-entities.md](database/database-entities.md) → [database/ERD_FULL_SYSTEM.md](database/ERD_FULL_SYSTEM.md) |
| **Business logic** | [business/03_BUSINESS.md](business/03_BUSINESS.md) → [business/07_BUSINESS_FLOWS.md](business/07_BUSINESS_FLOWS.md) |

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
- **Event Store**: Axon Server (for order, payment, flashsale services)

---

## 📊 Statistics

| Metric | Value |
|--------|-------|
| **Backend Services** | 10 (+ common-lib) |
| **Frontend Apps** | 3 |
| **API Endpoints** | 100+ |
| **Kafka Topics** | 47 (35 event + 12 request-reply) |
| **Cronjobs** | 17 |
| **Documentation Files** | 34 |
| **Authentication** | JWT (RS256) |

---

## ✨ v5.5 Features

- Multi-vendor order split with Stripe Connect
- Real-time SSE notifications
- Return To Sender (RTS) refund workflow
- Cart merged into Product Service
- AI Chat Support (multi-turn conversation with Tool calls, human-in-the-loop)
- 47 Kafka topics for event-driven architecture
- High-concurrency Flash Sale (50k+ req/s with Redis Lua scripts)
- Axon Sagas: OrderProcessingSaga + ParentOrderPaymentSaga

---

## 📁 Project Structure

```
backend/
├── api-gateway/          (Spring Cloud Gateway — 8080)
├── discovery-service/     (Eureka — 8761)
├── identity-service/      (Auth, users — 8081)
├── payment-service/       (Stripe Connect — 8082)
├── order-service/        (Orders, checkout, RTS — 8083)
├── flashsale-service/    (Flash sales — 8085)
├── product-service/       (Products, cart — 8090)
├── search-service/       (Elasticsearch — 8091)
├── notification-service/  (SSE — 8092)
├── ai-chat-service/     (AI Chat, Tool calls — 8093)
└── common-lib/           (Shared DTOs, Kafka topics, security)

frontend/
├── apps/customer/         (Port 3000)
├── apps/seller/           (Port 3001)
├── apps/admin/            (Port 3002)
└── shared/                (API clients, stores, components)

docs/                     (42 documentation files)
```

---

**Version**: v5.4
**Status**: Production Ready
**Last Updated**: 2026-05-04
