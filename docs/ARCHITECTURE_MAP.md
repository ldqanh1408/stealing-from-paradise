# ARCHITECTURE MAP — stealing-from-paradise
> **Purpose**: Single-file context primer. Load this before any implementation task.  
> **Version**: v5.4 | **Date**: 2026-05-01 | **Status**: Production-Ready

---

## 1. System Identity

| Key | Value |
|-----|-------|
| **Name** | Flash Sale E-Commerce Platform |
| **Repo** | stealing-from-paradise |
| **Pattern** | Microservices + Event-Driven + CQRS (Axon) |
| **Scale Target** | 50k+ req/s on flash sale endpoints |
| **Roles** | Customer · Seller · Admin |

---

## 2. Service Registry

| Service | Port | DB | Pattern | Core Responsibility |
|---------|------|----|---------|---------------------|
| `api-gateway` | 8080 | — | Spring Cloud Gateway | JWT RS256 validation, routing, rate limiting |
| `discovery-service` | 8761 | — | Eureka | Service registry & health |
| `identity-service` | 8081 | PostgreSQL | Traditional JPA | Auth, JWT, users, loyalty points, trust score |
| `payment-service` | 8082 | PostgreSQL + Axon | CQRS/ES | Stripe Connect, multi-vendor splits, refunds |
| `order-service` | 8083 | PostgreSQL + Axon | CQRS/ES + Saga | Checkout, order lifecycle, multi-vendor split, RTS |
| `flashsale-service` | 8085 | PostgreSQL + Axon + Redis | CQRS/ES | Flash sale sessions, Redis Lua atomic buy. **No REST controllers** — Kafka consumer only |
| `worker-service` | 8086 | PostgreSQL + Axon | CQRS/ES | Outbox relay, DLQ retry, deadline timeouts |
| `product-service` | 8090 | MongoDB | Traditional | Catalog, SKU variants, cart, reviews, images (MinIO) |
| `search-service` | 8091 | Elasticsearch | Traditional | Full-text product search. **No REST controllers** — Kafka consumer only |
| `notification-service` | 8092 | MongoDB | Traditional | SSE real-time notifications. **No REST controllers** — Kafka consumer only |
| `common-lib` | — | — | Shared lib | DTOs, exceptions, utilities |

> **Admin Service**: Not a standalone process — admin routes handled inside relevant services, routed by API Gateway under `/admin/**`.

---

## 3. Infrastructure Map

| Component | Port | Used By | Purpose |
|-----------|------|---------|---------|
| PostgreSQL | 5432 | identity, payment, order, flashsale, worker | Primary relational store |
| MongoDB | 27017 | product, notification | Document store |
| Redis | 6379 | flashsale, identity, api-gateway | Session cache, Lua atomic ops, JWT blocklist |
| Elasticsearch | 9200 | search | Full-text product index |
| MinIO | 9000 / 9001 | product | Object storage (product images) |
| Kafka | 9092 | all services | Async event streaming (35+ topics) |
| Axon Server | 8024 / 8124 | payment, order, flashsale, worker | Event store + command bus |
| Nginx | 80 | frontend | Reverse proxy for 3 SPAs |

---

## 4. Frontend Apps

| App | Port | Stack | Key Pages |
|-----|------|-------|-----------|
| Customer | 3000 | React 19 + Vite + TypeScript | ProductList, Cart, Checkout (Stripe), FlashSale, OrderHistory |
| Seller | 3001 | React 19 + Vite + TypeScript | Dashboard, ProductManagement, Orders, StripeOnboarding |
| Admin | 3002 | React 19 + Vite + TypeScript | UserManagement, ProductModeration, Refunds, FlashSaleConfig, TrustScore |

**Shared frontend code**: `frontend/shared/` — axios client (JWT interceptor), React Query config, auth store (Zustand), LoginPage, RegisterPage.

**Data flow**: Component → Zustand/React Query → Axios → `Authorization: Bearer <JWT>` → API Gateway :8080 → Microservice.

---

## 5. Technology Stack

### Backend
```
Java 25 (LTS)  ·  Spring Boot 4.0.4  ·  Spring Cloud 2025.1.1
Axon Framework 4.13.0  ·  Spring Data JPA / MongoDB / Elasticsearch
Flyway (PostgreSQL migrations)  ·  Stripe Java SDK
```

### Frontend
```
React 19  ·  Vite 6.0  ·  TypeScript  ·  Tailwind CSS
Zustand (global state)  ·  React Query (server state)  ·  Stripe.js
```

### DevOps
```
Docker + Docker Compose  ·  Eclipse Temurin JRE 25 (Dockerfiles)
Nginx (SPA reverse proxy)  ·  Eureka (service discovery)
```

---

## 6. Axon CQRS Pattern (4 Services)

Services: `order-service`, `payment-service`, `flashsale-service`, `worker-service`

```
HTTP Request
    │
    ▼
Controller  →  CommandGateway.send(XxxCommand)
                    │
                    ▼
              @Aggregate  (domain/XxxAggregate.java)
              @CommandHandler applies XxxEvent
                    │
                    ▼ (stored in Axon Server event store)
              @EventSourcingHandler  rebuilds aggregate state
                    │
                    ▼
              @EventHandler in projection/  (read-model in PostgreSQL)
                    │
                    ▼
              @QueryHandler  serves read queries
```

**Axon Sagas (distributed transactions)**:
- `order-service`: `OrderProcessingSaga` + `ParentOrderPaymentSaga`
- `payment-service`: Handles Stripe webhook → fires payment events
- `flashsale-service`: Flash sale lifecycle saga
- `worker-service`: Deadline manager for order timeouts

---

## 7. Kafka Event Topology

### Topics by Domain (41 total: 29 event + 12 request-reply)

> Request-Reply topics (12) — xem chi tiết: `docs/11_KAFKA_REQUEST_REPLY.md`

**Account / Identity**
```
account.locked          → Notification Service
account.auto_locked     → Notification Service
account.unlocked        → Notification Service
seller.posting_suspended → Notification Service
seller.posting_resumed  → Notification Service
```

**Product**
```
product.created         → Search Service (index)
product.pending_review  → Notification Service (alert admin)
product.updated         → Search Service
product.deleted         → Search Service
product.approved        → Search Service + Notification
product.rejected        → Notification Service
product.auto_hidden     → Search Service + Notification
inventory.adjusted      → Search Service
```

**Review** (Producer: Product Service)
```
review.created          → Notification + Search
review.updated          → Search
review.deleted          → Search
review.summary_updated  → Search (update reviewCount, avgRating in ES)
```

**Stripe / Payment Extended** (Producer: Payment Service)
```
stripe.account_suspended → Notification + Identity
stripe.dispute.created   → Notification + Admin log
stripe.dispute.closed    → Notification
stripe.transfer.reversed → Order + Notification
stripe.payout.failed     → Notification
seller.stripe_requirement → Notification (KYC reminder)
payment.requested        → Payment (internal)
```

**Order**
```
order.created           → Inventory (lock stock) + Search
order.cancelled         → Cart + Loyalty + Notification
order.shipped           → Notification Service
order.delivered         → Identity (trust score +5) + Loyalty (confirm points) + Notification
order.returned          → Refund (auto) + Inventory + Notification
order.checkout_completed → Cart (clear)
order.auto_cancelled    → Notification Service
```

**Payment / Refund**
```
payment.success         → Order Service (status PAID) + Notification
payment.failed          → Order Service + Notification
refund.requested        → Notification (notify seller)
refund.full_requested   → Notification + Payment (RTS flow)
refund.created          → Notification (refund record created)
refund.admin_approved   → Loyalty (return points) + Notification
refund.rejected         → Notification
refund.rts_completed    → Order + Notification
refund.stripe_auto      → Order + Loyalty
```

**Order Extended**
```
seller.order_cancelled  → Identity (seller trust score adjustment)
```

**Flash Sale**
```
flash_sale.session_started → Notification (reminder users)
flash_sale.session_ended   → Notification
flash_sale.item_approved   → Notification (seller)
flash_sale.item_rejected   → Notification
flash_sale.item_sold       → Inventory (update sold_count)
flash_sale.reminder        → Notification (Worker cron)
```

**Loyalty**
```
loyalty.points_earned   → Notification
loyalty.points_used     → Identity
loyalty.points_refunded → Identity
loyalty.points_expired  → Notification
```

**Trust Score**
```
trust_score.warning     → Notification
appeal.resolved         → Notification
```

---

## 8. Key Business Flows (Concise)

### Checkout Flow
```
POST /orders/checkout
  → Order Service: validate stock, split by seller, create Stripe PaymentIntent
  → Emit: order.created (locks inventory)
  → Return: parent_order_id + Stripe payment URL
  → Stripe Modal: customer pays
  → Stripe Webhook → Payment Service: payment_intent.succeeded
  → Emit: payment.success → Order: status = PAID
```

### Flash Sale Buy (High Concurrency)
```
POST /flash-sale/sessions/{id}/buy
  → Redis Lua Script (ATOMIC): decrement stock, check limit_per_user
  → If success: add to cart, emit flash_sale.item_sold
  → If sold out: 409 SOLD_OUT
```

### Refund (Manual Admin Flow)
```
Buyer: POST /orders/{id}/refunds → status PENDING + emit refund.requested
Admin: POST /admin/refunds/{id}/approve
  → Stripe refund.create()
  → Adjust loyalty if needed
  → Adjust trust score if caused_by=SELLER
  → Emit: refund.admin_approved → Loyalty + Notification
```

### RTS (Return To Sender)
```
Seller: POST /orders/{id}/return-to-sender + evidence images
  → Status: RETURNED
  → Emit: order.returned → Auto-create full refund
  → Notification to buyer
```

### Order Auto-Cancel (Worker Service)
```
Worker DeadlineManager: if order not paid in X minutes
  → Emit: order.auto_cancelled
  → Inventory unlocked, Notification to buyer
```

---

## 9. Cross-Cutting Concerns

### Authentication (JWT RS256)
- **Issue**: Identity Service — `access_token` (15 min), `refresh_token` (7 days)
- **Validate**: API Gateway intercepts every request, verifies RS256 signature
- **Revoke**: JTI stored in Redis blocklist; account lock revokes all active JTIs
- **Cleanup**: Worker cron JOB-18 nightly purges expired Redis blocklist entries

### Multi-Vendor Payments (Stripe Connect)
- Seller onboarding: `POST /stripe/onboarding/start` → Stripe Express account
- On checkout: Payment Service creates PaymentIntent with transfer_data per seller
- Automatic split: platform fee deducted, remainder transferred to seller's Stripe account
- Webhook: `payment_intent.succeeded`, `charge.refunded` → processed by Payment Service

### Outbox Pattern (Worker Service)
- Critical events are written to an outbox table before committing the transaction
- Worker Service polls outbox → publishes to Kafka
- Failed events stored in DLQ table → Admin retries via `POST /admin/failed-events/{id}/retry`

### Trust Score System (6 Tiers)
```
BRONZE (0-49) → SILVER (50-149) → GOLD (150-299)
  → PLATINUM (300-499) → DIAMOND (500-799) → ELITE (800+)
```
- Seller +5 on delivery confirmed, -5 on admin-approved refund caused by seller
- Warnings sent at tier boundary thresholds

### Loyalty Points
- Merged into Identity Service (no separate loyalty-service)
- Points earned on delivery, can be used at checkout
- Points in PENDING state until buyer confirms receipt
- Refund reverses points

---

## 10. Directory Structure (Navigating the Repo)

```
stealing-from-paradise/
├── backend/
│   ├── api-gateway/          Spring Cloud Gateway
│   ├── discovery-service/    Eureka
│   ├── identity-service/     Auth + Loyalty + Trust Score (PostgreSQL/JPA)
│   ├── payment-service/      Stripe + Axon CQRS
│   ├── order-service/        Checkout + Axon Sagas
│   ├── flashsale-service/    Flash Sale + Redis Lua + Axon
│   ├── worker-service/       Outbox + DLQ + Deadlines + Axon
│   ├── product-service/      Catalog + Cart (MongoDB)
│   ├── search-service/       Elasticsearch
│   ├── notification-service/ SSE (MongoDB)
│   ├── common-lib/           Shared DTOs, exceptions
│   ├── docker/               Init scripts (postgres, mongo, axon)
│   └── pom.xml               Parent Maven POM
│
├── frontend/
│   ├── shared/               Axios, React Query, Zustand auth store
│   └── apps/
│       ├── customer/         :3000 — Shopping + Checkout
│       ├── seller/           :3001 — Shop management
│       └── admin/            :3002 — Platform admin
│
├── docs/                     All documentation (see 00_INDEX.md)
├── docker-compose.yml        Full stack orchestration
├── docker-compose-infrastructure.yml   Infra only
└── nginx/                    Reverse proxy config
```

### Axon Service Internal Layout
```
{service}/src/main/java/com/flashsale/{service}/
  ├── domain/
  │   ├── {X}Aggregate.java           @Aggregate — state + command handlers
  │   ├── command/                    Commands (user intent)
  │   ├── event/                      Domain events (what happened)
  │   └── {X}Saga.java               @Saga — distributed transactions
  ├── projection/                     @EventHandler → read model in PostgreSQL
  ├── query/                          @QueryHandler → serves GET requests
  ├── api/                            REST controllers
  ├── service/                        Facade / orchestration
  └── infrastructure/config/          Axon Server config
```

### Traditional Service Internal Layout
```
{service}/src/main/java/com/flashsale/{service}/
  ├── entity/          JPA / MongoDB entities
  ├── repository/      Spring Data repositories
  ├── service/         Business logic
  ├── controller/      REST endpoints
  ├── dto/             Request/Response objects
  └── exception/       Service-specific exceptions
```

---

## 11. Database Schema Zones

| Zone | Tables/Collections | Owner Service |
|------|--------------------|---------------|
| **Identity** | USERS, USER_ROLES, ADDRESSES, REFRESH_TOKENS, TRUST_SCORES, TRUST_SCORE_APPEALS, LOYALTY_ACCOUNTS, LOYALTY_TRANSACTIONS | identity-service |
| **Order** | PARENT_ORDERS, ORDERS, ORDER_ITEMS, ORDER_STATUS_HISTORY | order-service (Axon projection) |
| **Payment** | TRANSACTIONS, STRIPE_ACCOUNTS | payment-service (Axon projection) |
| **Refund** | REFUNDS, REFUND_ITEMS | payment-service |
| **Flash Sale** | FLASH_SALE_SESSIONS, FLASH_SALE_ITEMS, FLASH_SALE_REMINDERS | flashsale-service |
| **Worker** | OUTBOX_EVENTS, FAILED_EVENTS | worker-service |
| **Product** | products, categories, cart_items, product_images (MongoDB) | product-service |
| **Notification** | notifications (MongoDB) | notification-service |
| **Search** | products index (Elasticsearch) | search-service |

> See `docs/database-entities.md` for full schema · `docs/erd.mermaid` for ERD diagram

---

## 12. Operations (23 Cronjobs)

Distributed per service — each service runs its own scheduled jobs.

**Key jobs by service**:
- `identity-service`: Cleanup expired tokens, auto-lock high-risk accounts, appeal timeout
- `order-service`: Auto-cancel unpaid orders (deadline), RTS timeout handling
- `payment-service`: Stripe webhook reconciliation, refund timeout
- `flashsale-service`: Session status transitions (UPCOMING→ACTIVE→ENDED), reminder dispatch
- `worker-service`: Outbox relay, DLQ retry, expired event cleanup
- `product-service`: Cart TTL cleanup, expired reservations, hidden product cleanup
- `notification-service`: Expired notification cleanup (TTL 90 days)

> Full cronjob catalog: `docs/05_OPERATIONS.md`

---

## 13. API Surface Summary

All routes prefixed: `http://localhost:8080/api/v1`

| Domain | Key Endpoints | Notes |
|--------|--------------|-------|
| **Auth** | POST /auth/login, /auth/register, /auth/refresh, /auth/logout | Identity Service |
| **Users** | GET/PUT /users/me, POST /users/me/roles/seller | Identity Service |
| **Products** | CRUD /products, /products/{id}/variants, /seller/products | Product Service |
| **Cart** | GET/POST/PUT/DELETE /cart/items | Product Service |
| **Search** | GET /search/products | Search Service |
| **Orders** | POST /orders/checkout, GET /orders, /orders/{id} | Order Service |
| **Tracking** | PUT /orders/{id}/tracking | Order Service |
| **Payments** | Stripe webhook /stripe/webhook, /stripe/onboarding/start | Payment Service |
| **Refunds** | POST /orders/{id}/refunds, GET/POST /admin/refunds/** | Payment Service |
| **Flash Sale** | CRUD /flash-sale/sessions, POST /flash-sale/sessions/{id}/buy | Flashsale Service |
| **Loyalty** | GET /loyalty/balance, GET /loyalty/transactions | Identity Service |
| **Notifications** | GET /notifications/stream (SSE) | Notification Service |
| **Admin** | /admin/users/**, /admin/products/**, /admin/refunds/**, /admin/failed-events/** | Per-service, admin-routed |
| **Support (Appeals)** | GET/POST /v1/support/trust-score-appeal, GET presigned-url | Identity Service — Seller/Buyer submit appeal |
| **Seller Payments** | GET /v1/seller/payments/earnings, GET /v1/seller/payments/stripe-dashboard | Payment Service |

**Internal APIs** (service-to-service, NOT exposed via API Gateway):

| Route | Service | Purpose |
|-------|---------|---------|
| `GET /internal/users/{userId}/role` | Identity | Lấy role của user (dùng bởi order/payment) |
| `GET /internal/users/{userId}` | Identity | Lấy thông tin user đầy đủ |
| `GET /internal/users/exists?username=&email=&phone=` | Identity | Kiểm tra user tồn tại |

> Full spec: `docs/02_API.md` · Per-service docs: `docs/{service}/02_API_{service}.md`

---

## 14. Quick Navigation Index

| Task | Read |
|------|------|
| Understand a service's API | `docs/{service-name}/02_API_{service}.md` |
| Trace a Kafka event | `docs/KAFKA_EVENTS.md` → `docs/ARCHITECTURE.md` |
| Understand request-reply Kafka pattern | `docs/11_KAFKA_REQUEST_REPLY.md` |
| Understand payment/order Saga | `docs/06_PAYMENT_SAGA_FLOW.md` |
| Database schema | `docs/database-entities.md` + `docs/erd.mermaid` |
| Business rules (trust, loyalty, flash sale) | `docs/04_POLICIES.md` |
| Business flows (diagrams) | `docs/07_BUSINESS_FLOWS.md` |
| Refund/RTS flow | `docs/08_PAYMENT_ORDER_INTEGRATION.md` |
| Cronjobs | `docs/05_OPERATIONS.md` |
| How to run | `docs/09_RUNNING.md` |
| Environment variables / deployment config | `docs/10_ENVIRONMENT_VARIABLES.md` |
| All docs index | `docs/00_INDEX.md` |

---

*Last Updated: 2026-05-01 · v5.4*
