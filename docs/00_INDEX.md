# Documentation Index

**Project**: stealing-from-paradise — Flash Sale E-Commerce Platform
**Version**: v5.5
**Last Updated**: 2026-05-05
**Status**: Production-Ready

---

## Start Here

This is the entry point for all project documentation. Read the documents in the order that matches your role and goal.

### Quick Path by Role

| Role | Start With | Then Read |
|------|-----------|-----------|
| **New Developer** | README.md (root) | 01_OVERVIEW.md |
| **Backend Developer** | 01_OVERVIEW.md | api/README.md | 03_BUSINESS.md |
| **Frontend Developer** | 01_OVERVIEW.md (Frontend section) | api/README.md |
| **DevOps/Operations** | 10_ENVIRONMENT_VARIABLES.md | 09_RUNNING.md → 05_OPERATIONS.md |
| **Product Manager** | 03_BUSINESS.md | 07_BUSINESS_FLOWS.md |
| **Security/Audit** | 03_BUSINESS.md | KAFKA_EVENTS.md |

### Quick Path by Goal

| Goal | Read |
|------|------|
| **Load full context (AI/LLM)** | **ARCHITECTURE_MAP.md** ← start here |
| **Run the project** | README.md (root) → 09_RUNNING.md |
| **Understand architecture** | ARCHITECTURE_MAP.md → ARCHITECTURE.md |
| **Build new feature** | ARCHITECTURE_MAP.md → relevant service API doc |
| **Debug Kafka event flow** | ARCHITECTURE_MAP.md → KAFKA_EVENTS.md |
| **Deploy / configure env** | 10_ENVIRONMENT_VARIABLES.md |
| **Understand Kafka request-reply** | 11_KAFKA_REQUEST_REPLY.md |
| **Understand business logic** | 03_BUSINESS.md → 07_BUSINESS_FLOWS.md |
| **Database schema** | database-entities.md → ERD_FULL_SYSTEM.md |

---

## Documentation Map

### Core Documents

```
docs/
├── 00_INDEX.md              ← You are here
├── README.md                This documentation guide
├── overview/
│   ├── 01_OVERVIEW.md       Architecture, services, tech stack, setup
│   ├── ARCHITECTURE.md      Service architecture, Kafka flows, diagrams
│   └── ARCHITECTURE_MAP.md  Single-file context primer (AI/LLM optimized)
├── business/
│   ├── 03_BUSINESS.md       Business logic, workflows, policies
│   ├── 06_PAYMENT_SAGA_FLOW.md  Axon Saga payment orchestration
│   ├── 07_BUSINESS_FLOWS.md     Visual flows (Mermaid diagrams)
│   └── 08_PAYMENT_ORDER_INTEGRATION.md Order-Payment integration
├── operations/
│   ├── 05_OPERATIONS.md     17 cronjobs, data retention, cleanup
│   ├── 09_RUNNING.md        How to run, build, deploy
│   ├── 10_ENVIRONMENT_VARIABLES.md  All env vars, .env template, security notes
│   └── API_URLS_COMPACT.md  API URL reference
├── messaging/
│   ├── KAFKA_EVENTS.md      Kafka index catalog → per-service event docs
│   └── 11_KAFKA_REQUEST_REPLY.md  Request-Reply over Kafka pattern (6 pairs)
├── database/
│   ├── database-entities.md Full database schema reference
│   └── ERD_FULL_SYSTEM.md   Entity-Relationship Diagram
├── api/
│   └── README.md            API overview and navigation
├── architecture/
│   ├── C4-Documentation/    C4 architecture documentation
│   └── diagrams/
│       ├── 00_service_architecture.md  C4 Container diagrams
│       └── 01_erd_compact.md           Compact ERD overview
└── services/
    ├── ai-chat-service/
    │   ├── KAFKA_EVENTS.md      AI Chat Kafka events
    │   ├── 02_API_ai_chat.md    AI Chat API specification
    │   ├── 01_technical_module.md  AI Chat technical module docs
    │   └── 03_database_tables.md   AI Chat database tables
    ├── worker-service/
    │   └── 03_database_tables.md   Worker service database tables
    ├── flashsale-service/
    │   ├── 02_API_flash_sale_service.md  Flash sale session API
    │   ├── 03_database_tables.md         Flash sale database tables
    │   └── KAFKA_EVENTS.md              Flash sale Kafka events
    ├── identity-service/
    │   ├── 02_API_identity_service.md   Auth, users, admin (45 endpoints)
    │   ├── 03_database_tables.md        Identity database tables
    │   └── KAFKA_EVENTS.md              Identity Kafka events (admin events merged)
    ├── notification-service/
    │   ├── 02_API_notification_service.md  SSE notifications (5 endpoints)
    │   ├── 03_database_tables.md           Notification database tables
    │   └── KAFKA_EVENTS.md                Notification Kafka events
    ├── order-service/
    │   ├── 02_API_order_service.md  Orders, checkout, RTS (18 endpoints)
    │   ├── 03_database_tables.md    Order database tables
    │   └── KAFKA_EVENTS.md          Order Kafka events
    ├── payment-service/
    │   ├── 02_API_payment_service.md  Stripe, payments, refunds (15 endpoints)
    │   ├── 03_database_tables.md      Payment database tables
    │   └── KAFKA_EVENTS.md            Payment Kafka events
    ├── product-service/
    │   ├── 02_API_product_service.md  Products, variants, cart (24 endpoints)
    │   ├── product_service_flow.md    Product service workflows
    │   ├── product_service_ui_logic.md  Product UI display logic
    │   ├── 03_database_tables.md      Product MongoDB tables
    │   ├── database_tables.md         Product PostgreSQL schema (legacy)
    │   └── KAFKA_EVENTS.md            Product Kafka events
    └── search-service/
        ├── 02_API_search_service.md  Search (2 endpoints)
        ├── 03_database_tables.md     Search database tables
        └── KAFKA_EVENTS.md           Search Kafka events
```

### Service-Specific API Docs

```
docs/
├── api/
│   └── README.md            API overview and navigation
├── services/identity-service/
│   └── 02_API_identity_service.md  Auth, users (31 endpoints)
├── services/product-service/
│   ├── 02_API_product_service.md   Products, variants, cart (24 endpoints)
│   ├── product_service_flow.md     Product service workflows
│   ├── product_service_ui_logic.md Product UI display logic
│   ├── 03_database_tables.md       Product MongoDB tables
│   └── database_tables.md          Product PostgreSQL schema (legacy)
├── services/search-service/
│   └── 02_API_search_service.md    Search (2 endpoints)
├── services/order-service/
│   └── 02_API_order_service.md    Orders, checkout, RTS (18 endpoints)
├── services/payment-service/
│   └── 02_API_payment_service.md  Stripe, payments, refunds (15 endpoints)
├── services/flashsale-service/
│   └── 02_API_flash_sale_service.md  Flash sale (12 endpoints)
├── services/notification-service/
│   └── 02_API_notification_service.md  SSE notifications (5 endpoints)
├── services/ai-chat-service/
│   ├── 02_API_ai_chat.md           AI Chat API specification
│   ├── 01_technical_module.md      AI Chat technical module
│   ├── 03_database_tables.md       AI Chat database tables
│   └── KAFKA_EVENTS.md            AI Chat Kafka events (7 topics)
├── services/worker-service/
│   ├── KAFKA_EVENTS.md            Worker Kafka events
│   └── 03_database_tables.md      Worker database tables

#### Kafka Event Docs (Per-Service)

```
docs/
├── messaging/KAFKA_EVENTS.md                        Index catalog (47 topics total)
├── services/identity-service/KAFKA_EVENTS.md        account.*, seller.*
├── services/product-service/KAFKA_EVENTS.md         product.*, inventory.*, cart.*
├── services/search-service/KAFKA_EVENTS.md          consumer-only (10 topics)
├── services/order-service/KAFKA_EVENTS.md           order.*, seller.order_cancelled
├── services/payment-service/KAFKA_EVENTS.md         payment.*, refund.*, stripe.*
├── services/flashsale-service/KAFKA_EVENTS.md       flash_sale.*
├── services/notification-service/KAFKA_EVENTS.md    consumer-only (20+ topics)
├── services/worker-service/KAFKA_EVENTS.md          flash_sale.reminder, outbox pattern
├── services/ai-chat-service/KAFKA_EVENTS.md         ai_chat.*, tool_call.*
└── messaging/11_KAFKA_REQUEST_REPLY.md             6 request-reply pairs
```

## Document Summary

| # | File | Lines | Purpose |
|---|------|-------|---------|
| 1 | 00_INDEX.md | — | Documentation navigation |
| 2 | overview/01_OVERVIEW.md | ~1200 | Project architecture, tech stack, setup |
| 3 | overview/ARCHITECTURE.md | ~600 | Service architecture & Kafka flows |
| 4 | overview/ARCHITECTURE_MAP.md | — | Single-file context primer |
| 5 | business/03_BUSINESS.md | ~700 | Business logic & workflows |
| 6 | business/06_PAYMENT_SAGA_FLOW.md | — | Payment Saga pattern |
| 7 | business/07_BUSINESS_FLOWS.md | ~900 | Visual flows (Mermaid) |
| 8 | business/08_PAYMENT_ORDER_INTEGRATION.md | — | Order-Payment integration |
| 9 | operations/05_OPERATIONS.md | ~800 | 17 cronjobs, data retention |
| 10 | operations/09_RUNNING.md | ~600 | Running & deployment guide |
| 11 | operations/10_ENVIRONMENT_VARIABLES.md | — | Environment variables reference |
| 12 | messaging/KAFKA_EVENTS.md | ~220 | Kafka index → per-service event docs |
| 13 | messaging/11_KAFKA_REQUEST_REPLY.md | ~320 | Kafka request-reply (6 pairs) |
| 14 | database/database-entities.md | ~800 | Database schema reference |
| 15 | database/ERD_FULL_SYSTEM.md | — | Entity-Relationship Diagram |
| 16 | api/README.md | ~400 | API overview |
| — | services/identity-service/02_API_identity_service.md | ~750 | Identity API |
| — | services/product-service/02_API_product_service.md | ~650 | Product API |
| — | services/product-service/product_service_api.md | ~300 | Product API deep-dive |
| — | services/product-service/product_service_flow.md | ~200 | Product service workflows |
| — | services/product-service/product_service_ui_logic.md | ~150 | Product UI logic |
| — | services/product-service/03_database_tables.md | ~130 | Product MongoDB tables |
| — | services/product-service/database_tables.md | ~370 | Product PostgreSQL schema (legacy) |
| — | services/search-service/02_API_search_service.md | ~100 | Search API |
| — | services/order-service/02_API_order_service.md | ~600 | Order API |
| — | services/payment-service/02_API_payment_service.md | ~280 | Payment API |
| — | services/flashsale-service/02_API_flash_sale_service.md | ~330 | Flash Sale API |
| — | services/notification-service/02_API_notification_service.md | ~170 | Notification API |
| — | services/ai-chat-service/02_API_ai_chat.md | ~150 | AI Chat API spec |
| — | services/ai-chat-service/01_technical_module.md | ~200 | AI Chat technical module |
| — | services/ai-chat-service/03_database_tables.md | ~80 | AI Chat DB tables |
| — | services/worker-service/03_database_tables.md | ~30 | Worker DB tables |
| — | architecture/diagrams/00_service_architecture.md | ~200 | C4 Container diagrams |
| — | architecture/diagrams/01_erd_compact.md | ~150 | Compact ERD overview |

---

## Service Port Reference

| Service | Port | Database | Notes |
|---------|------|----------|-------|
| API Gateway | 8080 | — | Entry point, JWT validation, routing |
| Discovery Service | 8761 | — | Eureka service registry |
| Identity Service | 8081 | PostgreSQL | Auth, users |
| Payment Service | 8082 | PostgreSQL + Axon | Stripe, payments |
| Order Service | 8083 | PostgreSQL + Axon | Orders, checkout, RTS |
| Flashsale Service | 8085 | PostgreSQL + Axon | Flash sales, Redis |
| Worker Service | 8086 | PostgreSQL + Axon | Outbox, failed events, DLQ |
| Product Service | 8090 | MongoDB | Products, cart, variants |
| Search Service | 8091 | Elasticsearch | Full-text search |
| Notification Service | 8092 | MongoDB | SSE, real-time notifications |
| AI Chat Service | 8093 | PostgreSQL | AI chat, tool calls, human-in-the-loop |

### Frontend Apps

| App | Port | Purpose |
|-----|------|---------|
| Customer App | 3000 | Shopping, checkout |
| Seller App | 3001 | Shop management |
| Admin App | 3002 | Platform administration |

### Infrastructure

| Component | Port | Purpose |
|-----------|------|---------|
| PostgreSQL | 5432 | Primary SQL database |
| MongoDB | 27017 | NoSQL database |
| Redis | 6379 | Cache, pub/sub |
| Elasticsearch | 9200 | Search engine |
| MinIO | 9000 / 9001 | Object storage |
| Kafka | 9092 | Message queue |
| Axon Server | 8024 / 8124 | Event store |
| Nginx | 80 | Reverse proxy |

---

## Technology Stack

### Backend
- **Java**: 25 (LTS)
- **Spring Boot**: 4.0.4
- **Spring Cloud**: 2025.1.1
- **Axon Framework**: 4.13.0
- **Databases**: PostgreSQL 15.4, MongoDB 6.0
- **Cache**: Redis 7.2
- **Search**: Elasticsearch 8.10
- **Message Queue**: Kafka 7.4.0
- **Object Storage**: MinIO

### Frontend
- **React**: 19
- **Vite**: 6.0
- **TypeScript**: Latest
- **Tailwind CSS**: Latest
- **Zustand**: State management
- **React Query**: Server state
- **Stripe**: Payments

### Infrastructure
- **Docker**: Containerization
- **Docker Compose**: Orchestration
- **Nginx**: Reverse proxy
- **Eureka**: Service discovery

---

## Key Features

- Multi-vendor marketplace with 3 roles (Customer, Seller, Admin)
- Flash sales with Redis Lua scripts for 50k+ req/s concurrency
- Stripe Connect for multi-vendor payments with automatic transfers
- Real-time SSE notifications via Notification Service
- Full-text search with Elasticsearch
- Return To Sender (RTS) refund workflow
- AI Chat Support (multi-turn conversation with Tool calls, human-in-the-loop)
- 17 scheduled cronjobs for data retention and cleanup
- 47 Kafka topics for event-driven architecture
- Axon Framework for Order, Payment, Flashsale, Worker services

---

## External Links (Running Services)

| Service | URL |
|---------|-----|
| API Gateway | http://localhost:8080 |
| Eureka Dashboard | http://localhost:8761 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Customer App | http://localhost:3000 |
| Seller App | http://localhost:3001 |
| Admin App | http://localhost:3002 |
| Elasticsearch | http://localhost:9200 |
| MinIO Console | http://localhost:9001 |

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| v5.5 | 2026-05-05 | Documentation refactor: ports unified, Redis 7.2, AI Chat docs, 47 Kafka topics |
| v5.4 | 2026-05-01 | Documentation consolidation, index created, service docs organized |
| v5.3 RTS | 2026-04-30 | Return To Sender, tracking number for refunds |
| v5.0 | 2026-04-22 | Distributed cronjobs per service |
| v4.0 | 2026-04-15 | Tracking number for refunds, 23 cronjobs |
| v3.0 | — | Dynamic delta config (policy system removed in later versions) |

---

**Last Updated**: 2026-05-05
**Documentation Version**: v5.5
**Total Documents**: 42
