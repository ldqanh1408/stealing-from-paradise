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
| **Backend Developer** | 01_OVERVIEW.md | api/README.md | 03_BUSINESS.md | 04_POLICIES.md |
| **Frontend Developer** | 01_OVERVIEW.md (Frontend section) | api/README.md |
| **DevOps/Operations** | 10_ENVIRONMENT_VARIABLES.md | 09_RUNNING.md → 05_OPERATIONS.md |
| **Product Manager** | 03_BUSINESS.md | 07_BUSINESS_FLOWS.md |
| **Security/Audit** | 04_POLICIES.md | KAFKA_EVENTS.md |

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
├── ARCHITECTURE_MAP.md      Single-file context primer (AI/LLM optimized)
├── 01_OVERVIEW.md           Architecture, services, tech stack, setup
├── 03_BUSINESS.md           Business logic, workflows, policies
├── 04_POLICIES.md          System rules, trust score, flash sale, loyalty
├── 05_OPERATIONS.md         17 cronjobs, data retention, cleanup
├── 06_PAYMENT_SAGA_FLOW.md Axon Saga payment orchestration
├── 07_BUSINESS_FLOWS.md     Visual flows (Mermaid diagrams)
├── 08_PAYMENT_ORDER_INTEGRATION.md Order-Payment integration
├── 09_RUNNING.md            How to run, build, deploy
├── ARCHITECTURE.md          Service architecture, Kafka flows, diagrams
├── KAFKA_EVENTS.md          Kafka index catalog → per-service event docs
├── database-entities.md     Full database schema documentation
├── 10_ENVIRONMENT_VARIABLES.md  All env vars, .env template, security notes
├── 11_KAFKA_REQUEST_REPLY.md    Request-Reply over Kafka pattern (6 pairs)
├── ai-chat-service/
│   └── KAFKA_EVENTS.md      AI Chat Kafka events
└── README.md                This documentation guide
```

### Service-Specific API Docs

```
docs/
├── api/
│   └── README.md            API overview and navigation
├── identity-service/
│   └── 02_API_identity_service.md  Auth, users, loyalty (31 endpoints)
├── product-service/
│   └── 02_API_product_service.md  Products, variants, cart (24 endpoints)
├── search-service/
│   └── 02_API_search_service.md    Search (routes configured, WIP)
├── order-service/
│   └── 02_API_order_service.md    Orders, checkout, RTS (18 endpoints)
├── payment-service/
│   └── 02_API_payment_service.md  Stripe, payments, refunds (15 endpoints)
├── flashsale-service/
│   └── 02_API_flash_sale_service.md  Flash sale (routes configured, WIP)
├── notification-service/
│   └── 02_API_notification_service.md  SSE notifications (routes configured, WIP)
├── admin-service/
│   ├── 02_API_admin.md            Admin APIs (14 endpoints)
│   └── KAFKA_EVENTS.md            Kafka events for Admin domain
├── ai-chat-service/
│   └── KAFKA_EVENTS.md            AI Chat Kafka events (7 topics)
└── worker-service/
    └── KAFKA_EVENTS.md            Worker Kafka events

#### Kafka Event Docs (Per-Service)

```
docs/
├── KAFKA_EVENTS.md                Index catalog (47 topics total)
├── identity-service/KAFKA_EVENTS.md   account.*, loyalty.*, seller.*
├── product-service/KAFKA_EVENTS.md    product.*, inventory.*, cart.*
├── search-service/KAFKA_EVENTS.md     consumer-only (10 topics)
├── order-service/KAFKA_EVENTS.md      order.*, seller.order_cancelled
├── payment-service/KAFKA_EVENTS.md    payment.*, refund.*, stripe.*
├── flashsale-service/KAFKA_EVENTS.md  flash_sale.*
├── notification-service/KAFKA_EVENTS.md  consumer-only (20+ topics)
├── admin-service/KAFKA_EVENTS.md      product.approved/rejected/auto_hidden
├── worker-service/KAFKA_EVENTS.md     flash_sale.reminder, outbox pattern
├── ai-chat-service/KAFKA_EVENTS.md    ai_chat.*, tool_call.*
└── 11_KAFKA_REQUEST_REPLY.md        6 request-reply pairs
```
```

## Document Summary

| # | File | Lines | Purpose |
|---|------|-------|---------|
| 1 | 00_INDEX.md | — | Documentation navigation |
| 2 | 01_OVERVIEW.md | ~1200 | Project architecture, tech stack, setup |
| 3 | 03_BUSINESS.md | ~700 | Business logic & workflows |
| 5 | 04_POLICIES.md | ~500 | System policies & rules |
| 6 | 05_OPERATIONS.md | ~800 | 17 cronjobs, data retention |
| 7 | 06_PAYMENT_SAGA_FLOW.md | — | Payment Saga pattern |
| 8 | 07_BUSINESS_FLOWS.md | ~900 | Visual flows (Mermaid) |
| 9 | 08_PAYMENT_ORDER_INTEGRATION.md | — | Order-Payment integration |
| 10 | 09_RUNNING.md | ~600 | Running & deployment guide |
| 11 | ARCHITECTURE.md | ~600 | Service architecture & Kafka flows |
| 12 | KAFKA_EVENTS.md | ~220 | Kafka index → 10 per-service event docs |
| 13 | database-entities.md | ~800 | Database schema reference |
| 14 | README.md | ~200 | Documentation guide |
| — | api/README.md | ~400 | API overview |
| — | identity-service/02_API_identity_service.md | ~750 | Identity API |
| — | identity-service/KAFKA_EVENTS.md | ~170 | Identity Kafka events |
| — | product-service/02_API_product_service.md | ~650 | Product API |
| — | product-service/KAFKA_EVENTS.md | ~200 | Product Kafka events |
| — | search-service/02_API_search_service.md | ~100 | Search API |
| — | search-service/KAFKA_EVENTS.md | ~95 | Search Kafka events |
| — | order-service/02_API_order_service.md | ~600 | Order API |
| — | order-service/KAFKA_EVENTS.md | ~250 | Order Kafka events |
| — | payment-service/02_API_payment_service.md | ~280 | Payment API |
| — | payment-service/KAFKA_EVENTS.md | ~220 | Payment Kafka events |
| — | flashsale-service/02_API_flash_sale_service.md | ~330 | Flash Sale API |
| — | flashsale-service/KAFKA_EVENTS.md | ~85 | Flash Sale Kafka events |
| — | notification-service/02_API_notification_service.md | ~170 | Notification API |
| — | notification-service/KAFKA_EVENTS.md | ~125 | Notification Kafka events |
| — | admin-service/02_API_admin.md | ~630 | Admin API |
| — | admin-service/KAFKA_EVENTS.md | ~65 | Admin Kafka events |
| — | worker-service/KAFKA_EVENTS.md | ~75 | Worker Kafka events |
| — | 11_KAFKA_REQUEST_REPLY.md | ~320 | Kafka request-reply (6 pairs) |

---

## Service Port Reference

| Service | Port | Database | Notes |
|---------|------|----------|-------|
| API Gateway | 8080 | — | Entry point, JWT validation, routing |
| Discovery Service | 8761 | — | Eureka service registry |
| Identity Service | 8081 | PostgreSQL | Auth, users, loyalty |
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
- Trust Score system (6 tiers: BRONZE → ELITE)
- Loyalty Points with configurable earning rates
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
| v5.0 | 2026-04-22 | Distributed cronjobs per service, Loyalty merged into Identity |
| v4.0 | 2026-04-15 | Tracking number for refunds, 23 cronjobs |
| v3.0 | — | Trust score, appeals, dynamic delta config |

---

**Last Updated**: 2026-05-05
**Documentation Version**: v5.5
**Total Documents**: 30
