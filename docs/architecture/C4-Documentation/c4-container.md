# C4 Container Level: FlashSale Platform Deployment

## Overview

This document maps the logical components of the FlashSale platform to their physical deployment containers, following the [C4 Model Container Level](https://c4model.com/diagrams/container). A container in C4 terms is a deployable unit -- a process, service, database, or application that must be running for the system to work.

The FlashSale platform deploys as **22 containers** across three groups:

| Group | Count | Description |
|-------|-------|-------------|
| **Infrastructure Containers** | 8 | Data stores, message brokers, event buses, reverse proxy |
| **Backend Application Containers** | 11 | Java/Spring Boot microservices (including 1 utility) |
| **Frontend Application Containers** | 3 | React/Vite Single Page Applications |

All containers run on a shared Docker bridge network (`flashsale-net`).

---

## Infrastructure Containers

### 1. Nginx Reverse Proxy

- **Name**: Reverse Proxy
- **Description**: Entry point for all external HTTP traffic. Routes requests to the API Gateway and frontend SPAs. Generates configuration at container start via an entrypoint script that resolves upstream hostnames from environment variables.
- **Container Name**: `fs-reverse-proxy`
- **Type**: Reverse Proxy / Web Server
- **Technology**: nginx:alpine, custom entrypoint script
- **Deployment**: Docker (Dockerfile at `nginx/Dockerfile`), port 80 mapped to host

#### Purpose

The Nginx Reverse Proxy is the single ingress point for all traffic entering the FlashSale platform. It provides:

1. **Unified Entry Point**: All clients access the system through a single host and port. Nginx routes `/api/` paths to the API Gateway, `/` (root) to the Customer Web App, `/seller` to the Seller Web App, and `/admin` to the Admin Web App.
2. **URL-Based Frontend Routing**: Path-based routing eliminates the need for multiple exposed ports in production. Each SPA is served on its own path prefix.
3. **Health Check Endpoint**: Provides a `/health` endpoint used by container orchestration for readiness probes.
4. **Configuration Generation**: The Docker entrypoint script dynamically generates `nginx.conf` from environment variables, resolving upstream service hostnames at container startup.

#### Components

This container deploys no application components -- it is purely infrastructure. The routing logic is defined in the generated nginx configuration.

#### Interfaces

| Interfaces | Protocol | Description |
|------------|----------|-------------|
| Primary Ingress | HTTP/1.1 | Port 80, receives all external traffic |
| `/api/` routes | HTTP/1.1 | Proxied to API Gateway (`fs-gateway:8080`) |
| `/` (root) | HTTP/1.1 | Proxied to Customer Web App (`fs-customer-fe:3000`) |
| `/seller` | HTTP/1.1 | Proxied to Seller Web App (`fs-seller-fe:3001`) |
| `/admin` | HTTP/1.1 | Proxied to Admin Web App (`fs-admin-fe:3002`) |
| `/health` | HTTP/1.1 | Nginx health check endpoint |
| SSE passthrough | HTTP/1.1 | Forwards SSE streams from Notification Service |

#### Dependencies

**Containers Used:**
- **API Gateway** (fs-gateway:8080): Routes all `/api/` requests
- **Customer Web App** (fs-customer-fe:3000): Serves the customer SPA
- **Seller Web App** (fs-seller-fe:3001): Serves the seller SPA
- **Admin Web App** (fs-admin-fe:3002): Serves the admin SPA

**External Systems:**
- **Browser Client**: End-user browsers accessing the platform

#### Infrastructure

| Property | Value |
|----------|-------|
| Deployment Config | `nginx/Dockerfile`, `nginx/docker-entrypoint.sh` |
| Base Image | nginx:alpine |
| Exposed Port | 80 (configurable via `NGINX_PORT`) |
| Health Check | `wget --spider http://localhost:${NGINX_PORT}/health` |
| Scaling | Single instance (can be scaled behind a cloud load balancer) |

---

### 2. PostgreSQL

- **Name**: PostgreSQL Database
- **Description**: Primary relational database hosting schemas for Identity, Order, Payment, Flash Sale, Worker services, plus Axon Framework event/saga/token stores.
- **Container Name**: `fs-postgres`
- **Type**: Relational Database
- **Technology**: PostgreSQL 15.4-alpine
- **Deployment**: Docker, port 5432

#### Purpose

PostgreSQL is the primary relational data store for five backend microservices. It hosts separate database schemas per service and provides ACID-compliant transactional support for critical operations including user identity management, order processing, payment records, flash sale session management, and worker service scheduling.

**Database Schemas:**

| Schema | Service | Key Tables |
|--------|---------|------------|
| `identity` | Identity Service | users, roles, addresses |
| `order_service` | Order Service | parent_orders, orders, order_items, Axon saga_entry, token_entry, association_value_entry |
| `payment_service` | Payment Service | transactions, refunds, refund_items, seller_stripe_accounts, seller_transfers |
| `flashsale` | Flash Sale Service | fs_sessions, fs_items, fs_reminders, Axon saga tables |
| `worker` | Worker Service | outbox_events, failed_events, shedlock |

Schemas are initialized at container start via scripts in `backend/docker/postgres/init/`.

#### Components

No application components are deployed in this container. It is a pure data infrastructure service.

#### Interfaces

| Interface | Protocol | Description |
|-----------|----------|-------------|
| PostgreSQL wire protocol | TCP/5432 | Standard PostgreSQL connections from backend services |
| Health check | pg_isready | Docker health check verifying database availability |

#### Dependencies

**Containers That Use This Container:**
- Identity Service -- schema: `identity`
- Order Service -- schema: `order_service` (including Axon stores)
- Payment Service -- schema: `payment_service`
- Flash Sale Service -- schema: `flashsale` (including Axon stores)
- Worker Service -- schema: `worker`

#### Infrastructure

| Property | Value |
|----------|-------|
| Base Image | postgres:15.4-alpine |
| Port | 5432 |
| Authentication | `POSTGRES_USER` / `POSTGRES_PASSWORD` via .env |
| Persistence | Named volume `postgres_data` |
| Init Scripts | `backend/docker/postgres/init/` |
| Health Check | `pg_isready -U ${POSTGRES_USER}` (30s interval) |
| Scaling | Single instance (replication via external config) |

---

### 3. MongoDB

- **Name**: MongoDB Database
- **Description**: NoSQL document database hosting collections for Product and Notification services. Provides flexible schema for product catalogs with variants and notification documents with TTL indexes.
- **Container Name**: `fs-mongo`
- **Type**: Document Database
- **Technology**: MongoDB 6.0.8
- **Deployment**: Docker, port 27017

#### Purpose

MongoDB serves as the primary data store for two services that benefit from document-oriented storage:

1. **Product Service** (`fs_product` database): Stores products, product variants, categories, carts, cart items, inventories, and inventory logs. The document model suits the nested variant structure and flexible product attributes.
2. **Notification Service** (`fs_notification` database): Stores notification documents with a 90-day TTL index for automatic expiration and a compound index on `(user_id, is_read)` for efficient unread queries.

#### Components

No application components are deployed in this container.

#### Interfaces

| Interface | Protocol | Description |
|-----------|----------|-------------|
| MongoDB wire protocol | TCP/27017 | MongoDB driver connections from Product and Notification services |
| Health check | mongosh `db.adminCommand('ping')` | Docker health check |

#### Dependencies

**Containers That Use This Container:**
- Product Service -- database: `fs_product`
- Notification Service -- database: `fs_notification`

#### Infrastructure

| Property | Value |
|----------|-------|
| Base Image | mongo:6.0.8 |
| Port | 27017 |
| Authentication | `MONGO_INITDB_ROOT_USERNAME` / `MONGO_INITDB_ROOT_PASSWORD` |
| Persistence | Named volume `mongo_data` |
| Init Scripts | `backend/docker/mongo/init/` |
| Health Check | `mongosh --eval "db.adminCommand('ping')"` (30s interval) |
| Scaling | Single instance (replica set for production) |

---

### 4. Redis

- **Name**: Redis Cache
- **Description**: In-memory data structure store used for JWT token blacklisting, flash sale atomic inventory counters (Lua scripts), reactive session management for SSE connections, and general caching.
- **Container Name**: `fs-redis`
- **Type**: In-Memory Data Store / Cache
- **Technology**: Redis 7.2.1-alpine (AOF persistence enabled)
- **Deployment**: Docker, port 6379

#### Purpose

Redis provides low-latency, atomic operations for several critical platform functions:

1. **Token Blacklisting** (API Gateway + Identity Service): Stores revoked JWT identifiers (`token:blacklist:{jti}`) with TTL matching remaining token lifetime for immediate revocation.
2. **Flash Sale Anti-Oversell** (Flash Sale Service): Executes Lua scripts atomically to decrement inventory counters, guaranteeing stock never drops below zero during concurrent purchases.
3. **Reactive Session Management** (Notification Service): Manages SSE connection state for real-time notification push.
4. **Rate Limiting and Caching**: Available for service-level rate limiting and response caching (declared, not yet actively used in all services).

#### Components

No application components are deployed in this container.

#### Interfaces

| Interface | Protocol | Description |
|-----------|----------|-------------|
| Redis RESP protocol | TCP/6379 | Standard Redis commands from backend services |
| Health check | `redis-cli -a ${REDIS_PASSWORD} ping` | Docker health check |

#### Dependencies

**Containers That Use This Container:**
- API Gateway -- Token blacklist checks
- Identity Service -- Token blacklist management
- Flash Sale Service -- Atomic inventory operations via Lua scripts
- Notification Service -- Reactive SSE session management
- Product Service -- Dependency declared, available for caching

#### Infrastructure

| Property | Value |
|----------|-------|
| Base Image | redis:7.2.1-alpine |
| Port | 6379 |
| Authentication | Password via `--requirepass ${REDIS_PASSWORD}` |
| Persistence | AOF (appendonly yes), named volume `redis_data` |
| Health Check | `redis-cli -a ${REDIS_PASSWORD} ping` (30s interval) |
| Scaling | Single instance (Redis Cluster for production) |

---

### 5. Elasticsearch

- **Name**: Elasticsearch Search Engine
- **Description**: Full-text search and analytics engine storing a denormalized product index for the Search Service. Provides relevance-ranked multi-field search across product names, descriptions, and tags.
- **Container Name**: `fs-elasticsearch`
- **Type**: Search Engine
- **Technology**: Elasticsearch 8.10.2 (single-node, xpack security disabled)
- **Deployment**: Docker, port 9200

#### Purpose

Elasticsearch hosts the `products` index -- a denormalized read model of the product catalog optimized for full-text search. Documents include product name, description (boosted 2x), tags (boosted 1.5x), denormalized seller/category names, price range, stock status, and flash sale flags. The index is created with proper field mappings at service startup.

#### Components

No application components are deployed in this container.

#### Interfaces

| Interface | Protocol | Description |
|-----------|----------|-------------|
| Elasticsearch REST API | HTTP/9200 | Index creation, document indexing, search queries |
| Health check | `curl http://localhost:9200/_cluster/health` | Checks cluster status is not red |

#### Dependencies

**Containers That Use This Container:**
- Search Service -- Index management and full-text search queries

#### Infrastructure

| Property | Value |
|----------|-------|
| Base Image | elasticsearch:8.10.2 |
| Port | 9200 |
| Configuration | `discovery.type=single-node`, `xpack.security.enabled=false` |
| JVM Heap | `-Xms512m -Xmx512m` (configurable via `ES_JAVA_OPTS`) |
| Persistence | Named volume `elastic_data` |
| Health Check | Cluster health API, 30s interval |
| Scaling | Single node (cluster for production) |

---

### 6. MinIO

- **Name**: MinIO Object Storage
- **Description**: S3-compatible object storage for product images and user avatars. Supports presigned URL uploads from frontend clients through the Product Service and Identity Service.
- **Container Name**: `fs-minio`
- **Type**: Object Storage
- **Technology**: MinIO (latest), S3-compatible API
- **Deployment**: Docker, port 9000 (API) + 9001 (Console)

#### Purpose

MinIO provides S3-compatible object storage for binary assets:

1. **Product Images** (via Product Service): Stores product images in the `products-media` bucket organized by seller ID and product ID. The Product Service generates presigned PUT URLs with 15-minute TTL for direct browser-to-MinIO uploads.
2. **User Avatars** (via Identity Service): Stores user avatar images organized by seller ID and user ID. The Identity Service generates presigned PUT URLs for direct upload.
3. **Return Evidence Images** (via Order Service): Stores evidence images for Return To Sender (RTS) workflows.

#### Components

No application components are deployed in this container.

#### Interfaces

| Interface | Protocol | Description |
|-----------|----------|-------------|
| S3-compatible API | HTTP/9000 | Object storage operations (PUT, GET, presigned URLs) |
| MinIO Console | HTTP/9001 | Web-based management UI |
| Health check | `mc ready local` | Docker health check using MinIO Client |

#### Dependencies

**Containers That Use This Container:**
- Product Service -- Product image uploads via presigned URLs
- Identity Service -- User avatar uploads via presigned URLs
- Order Service -- RTS evidence image uploads

#### Infrastructure

| Property | Value |
|----------|-------|
| Base Image | minio/minio:latest |
| Ports | 9000 (API), 9001 (Console) |
| Persistence | Named volume `minio_data` |
| Startup | `server /data --console-address ":9001"` |
| Health Check | `mc ready local` (30s interval) |
| Scaling | Single instance (distributed mode for production) |

---

### 7. Kafka + Zookeeper

- **Name**: Kafka Event Bus
- **Description**: Distributed event streaming platform with Zookeeper coordination, hosting 52+ topics for asynchronous inter-service communication. Topics cover product lifecycle, order processing, payment coordination, refund management, flash sale events, and request-reply patterns.
- **Container Names**: `fs-zookeeper`, `fs-kafka`, `fs-kafka-init`
- **Type**: Message Broker / Event Bus
- **Technology**: Confluent Platform 7.4.0 (cp-kafka, cp-zookeeper), topic auto-creation enabled
- **Deployment**: Docker, port 9092 (Kafka), 2181 (Zookeeper)

#### Purpose

Kafka is the backbone of asynchronous inter-service communication for the FlashSale platform:

1. **Event-Driven Architecture**: Enables loose coupling between services. Services publish domain events (product approved, order created, payment succeeded) and consumers react independently.
2. **Saga Orchestration**: Carries payment requests, success/failure events, and refund commands that drive the `ParentOrderPaymentSaga` and `OrderProcessingSaga` in the Order Service.
3. **CQRS Event Sourcing**: Transports Axon events between services where direct Axon Server connectivity is not used.
4. **Request-Reply Pattern**: Provides temporary synchronous-style inter-service queries (cart item enrichment, address validation, refund history) via correlation-ID-based request-reply, substituting for planned gRPC communication.
5. **Notification Events**: Carries order lifecycle, payment status, and flash sale events consumed by the Notification Service for real-time user alerts.

The `fs-kafka-init` container runs a startup script (`create-topics.sh`) that creates all 52+ topics with appropriate partition and replication settings.

**Key Topic Groups (52+ topics):**

| Domain Group | Count | Example Topics |
|--------------|-------|---------------|
| Product | 8 | `product.created`, `product.updated`, `product.deleted`, `product.pending_review`, `product.approved`, `product.rejected`, `inventory.adjusted` |
| Order | 8 | `order.created`, `order.cancelled`, `order.auto_cancelled`, `order.shipped`, `order.delivered`, `order.returned_rts`, `order.checkout_completed`, `seller.order_cancelled` |
| Payment | 9 | `payment.requested`, `payment.success`, `payment.failed`, `stripe.account_suspended`, `stripe.dispute.created`, `stripe.dispute.closed`, `stripe.transfer_reversed`, `stripe.payout_failed` |
| Refund | 7 | `refund.requested`, `refund.full_requested`, `refund.admin_approved`, `refund.rejected`, `refund.created`, `refund.rts_completed`, `refund.stripe_auto` |
| Flash Sale | 6 | `flash_sale.session_started`, `flash_sale.session_ended`, `flash_sale.item_approved`, `flash_sale.item_rejected`, `flash_sale.item_sold` |
| Request-Reply | 14 | `order.cart_items.request`/`response`, `order.address.request`/`response`, `order.refunds.request`/`response`, `order.payment_status.request`/`response`, `order.stock_check.request`/`response`, etc. |

#### Components

No application components are deployed in these containers.

#### Interfaces

| Interface | Protocol | Description |
|-----------|----------|-------------|
| Kafka binary protocol | TCP/9092 | Producer and consumer connections from all backend services |
| Zookeeper | TCP/2181 | Kafka broker coordination |

#### Dependencies

**Containers That Use This Container:**
- Identity Service -- Kafka consumer (address requests) + producer (address responses)
- Product Service -- Kafka consumer (cart requests, checkout_completed) + producer (product events, cart responses)
- Order Service -- Kafka consumer (payment/refund events, request-reply responses) + producer (payment/refund requests, order events, request-reply requests)
- Payment Service -- Kafka consumer (payment/refund requests, order cancel events, request-reply requests) + producer (payment/refund events, request-reply responses)
- Flash Sale Service -- Kafka consumer (session started) + producer (item approved/rejected/sold)
- Notification Service -- Kafka consumer (order.delivered, seller.stripe_requirement)
- Search Service -- Kafka consumer (product.approved, product.created, product.updated, product.deleted)
- Worker Service -- Kafka producer (outbox events, auto-cancellation)

#### Infrastructure

| Property | Value |
|----------|-------|
| Base Images | confluentinc/cp-zookeeper:7.4.0, confluentinc/cp-kafka:7.4.0 |
| Kafka Port | 9092 (PLAINTEXT) |
| Zookeeper Port | 2181 |
| Broker Config | Single broker, auto-create topics enabled |
| Topic Init | `backend/docker/kafka/create-topics.sh` |
| Persistence | Named volumes `kafka_data`, `zookeeper_data` |
| Scaling | Single broker (multi-broker cluster for production) |

---

### 8. Axon Server

- **Name**: Axon Server
- **Description**: Dedicated event store and message bus for CQRS/Event Sourcing services. Provides event storage, command routing, and query handling for Axon Framework-based services (primarily Order Service and Payment Service).
- **Container Name**: `fs-axonserver`
- **Type**: Event Store / Message Bus
- **Technology**: AxonIQ Axon Server (latest), gRPC, dev mode enabled
- **Deployment**: Docker, port 8024 (GUI) + 8124 (gRPC)

#### Purpose

Axon Server provides the CQRS infrastructure for services using the Axon Framework:

1. **Event Store**: Persists all domain events emitted by Axon aggregates. Events are immutable and append-only, providing a complete audit trail.
2. **Event Bus**: Routes events from publishers to interested handlers within and across services.
3. **Command Bus**: Routes commands to their target aggregate handlers.
4. **Query Bus**: Routes queries to their handlers, supporting the CQRS read/write separation.
5. **Saga Support**: Persists saga state for long-running business transactions like `OrderProcessingSaga` and `ParentOrderPaymentSaga`.
6. **Deadline Manager**: Schedules and fires time-based events (payment timeout at 30 minutes, shipping deadline).

The GUI on port 8024 provides an overview of connected applications, event streams, and command/query handlers.

#### Components

No application components are deployed in this container.

#### Interfaces

| Interface | Protocol | Description |
|-----------|----------|-------------|
| Axon Server gRPC API | gRPC/8124 | Command, event, and query bus communication |
| Axon Server GUI | HTTP/8024 | Web-based monitoring dashboard |
| Health check | HTTP/8024/actuator/health | Docker health check |

#### Dependencies

**Containers That Use This Container:**
- Order Service -- Event sourcing, sagas, deadline management
- Payment Service -- Transactional Axon infrastructure
- Flash Sale Service -- Provisioned (infrastructure declared, not yet active in code)

#### Infrastructure

| Property | Value |
|----------|-------|
| Base Image | axoniq/axonserver:latest |
| gRPC Port | 8124 |
| GUI Port | 8024 |
| Configuration | `AXONIQ_AXONSERVER_STANDALONE=true`, `AXONIQ_AXONSERVER_DEVMODE_ENABLED=true` |
| Persistence | Named volume `axon_data` |
| Health Check | `curl http://localhost:8024/actuator/health` (30s interval, 120s start period) |
| Scaling | Single instance (Axon Server Enterprise for clustering) |

---

## Backend Application Containers

### 9. API Gateway

- **Name**: API Gateway
- **Description**: Spring Cloud Gateway (WebFlux/Reactive) serving as the single entry point for all client API requests. Handles JWT validation, token blacklist checking via Redis, CORS, security headers, and routes requests to all downstream microservices through Eureka service discovery.
- **Container Name**: `fs-gateway`
- **Type**: API Gateway / Reverse Proxy (Application)
- **Technology**: Java 25, Spring Boot 4.0.4, Spring Cloud Gateway (WebFlux/Reactive), Netty, Redis (Lettuce), JJWT 0.12.3
- **Deployment**: Docker (`backend/api-gateway/Dockerfile.dev`), no externally mapped port (behind nginx)

#### Purpose

The API Gateway centralizes cross-cutting concerns that would otherwise be duplicated across every microservice:

1. **Centralized Authentication**: All JWT validation happens once at the gateway. Validated user context (userId, email, role, JTI) is injected as `X-User-*` HTTP headers for downstream services.
2. **Token Revocation**: Checks Redis blacklist on every authenticated request for immediate token revocation.
3. **Routing Abstraction**: Routes 18 named path patterns to 9 downstream services using `lb://` Eureka URIs with `stripPrefix(1)`.
4. **Security Hardening**: Injects HSTS, X-Frame-Options, X-Content-Type-Options, Referrer-Policy, and Permissions-Policy headers.
5. **CORS Management**: Configures CORS for all microservices from a single location.

#### Components

This container deploys the API Gateway component:
- [API Gateway](./c4-component-api-gateway.md) -- Spring Cloud Gateway with JWT auth, routing, CORS, security headers

Full code-level documentation: [c4-code-backend-api-gateway.md](./c4-code-backend-api-gateway.md)

#### Interfaces

| Interface | Protocol | Description |
|-----------|----------|-------------|
| REST API (Primary) | HTTP/8080 | Unified API entry point for all client requests |
| Health Check | HTTP/8080/actuator/health | Docker health probe |
| Prometheus Metrics | HTTP/8080/actuator/metrics | Monitoring metrics |
| Gateway Routes | HTTP/8080/actuator/gateway | Route listing and status |
| Eureka Registration | HTTP/8761 | Service registration with Eureka |
| Redis Blacklist | TCP/6379 | Token blacklist key lookups |

**Route Table (18 named routes to 9 services):**

| Path Pattern | Target Service (Eureka `lb://`) | Auth |
|---|---|---|
| `/api/v1/auth/**` | identity-service | Public |
| `/api/v1/users/register` | identity-service | Public |
| `/api/v1/users/**` | identity-service | JWT |
| `/api/v1/products/**`, `/api/v1/categories/**`, `/api/v1/seller/**`, `/api/v1/inventory/**` | product-service | JWT (GET public) |
| `/api/v1/cart/**` | product-service | JWT |
| `/api/v1/orders/**`, `/api/v1/sellers/**` | order-service | JWT |
| `/api/v1/payments/**`, `/api/v1/refunds/**`, `/api/v1/stripe/**` | payment-service | JWT (webhooks public) |
| `/api/v1/flash-sales/**` | flashsale-service | JWT (GET public) |
| `/api/v1/workers/**`, `/api/v1/jobs/**` | worker-service | JWT |
| `/api/v1/search/**` | search-service | Public |
| `/api/v1/notifications/**` | notification-service | JWT |

#### Dependencies

**Containers Used:**
- Redis -- JWT token blacklist checking (`token:blacklist:{jti}`)
- Discovery Service (Eureka) -- Service registration and resolution of `lb://` URIs
- Identity Service -- Authentication endpoints
- Product Service -- Product, category, cart, inventory endpoints
- Order Service -- Order, seller, refund endpoints
- Payment Service -- Payment, Stripe, refund endpoints
- Flash Sale Service -- Flash sale session and purchase endpoints
- Worker Service -- Worker and job management endpoints
- Search Service -- Search endpoints
- Notification Service -- SSE notification endpoints

#### Infrastructure

| Property | Value |
|----------|-------|
| Dockerfile | `backend/api-gateway/Dockerfile.dev` |
| Internal Port | 8080 (configurable via `SERVER_PORT`) |
| Depends On | Redis |
| Environment Variables | `EUREKA_URI`, `REDIS_HOST`, `REDIS_PASSWORD`, `JWT_SECRET`, `JWT_EXPIRATION_MS`, `JVM_OPTS_GATEWAY` |
| Health Check | `curl http://localhost:8080/actuator/health` |
| Scaling | Horizontally scalable behind load balancer |

---

### 10. Discovery Service

- **Name**: Discovery Service (Eureka Server)
- **Description**: Netflix Eureka service registry for dynamic service discovery. All backend services register themselves on startup and discover peers via logical service names.
- **Container Name**: `fs-discovery`
- **Type**: Service Registry / Infrastructure
- **Technology**: Java 25, Spring Boot 4.0.4, Netflix Eureka (Spring Cloud Eureka Server), embedded Tomcat
- **Deployment**: Docker (`backend/discovery-service/Dockerfile.dev`), port 8761

#### Purpose

The Discovery Service enables dynamic, location-transparent inter-service communication:

1. **Service Registration**: All 10 backend application services register their network location on startup.
2. **Health-Aware Discovery**: Eureka tracks heartbeats and evicts unhealthy instances.
3. **Client-Side Load Balancing**: Spring Cloud LoadBalancer uses Eureka registry for `lb://` URI resolution.
4. **Operational Dashboard**: Web UI at port 8761 shows all registered services and their status.

#### Components

This container deploys the Service Discovery component:
- [Service Discovery](./c4-component-service-discovery.md) -- Eureka Server with dashboard, REST API, and health endpoints

Full code-level documentation: [c4-code-backend-discovery-service.md](./c4-code-backend-discovery-service.md)

#### Interfaces

| Interface | Protocol | Description |
|-----------|----------|-------------|
| Eureka REST API | HTTP/8761/eureka/ | Service registration, heartbeat, discovery |
| Eureka Dashboard | HTTP/8761/ | Web-based monitoring UI |
| Health Check | HTTP/8761/actuator/health | Docker health probe |

#### Dependencies

**Containers That Depend on This Container:**
- API Gateway -- Resolves `lb://` service URIs
- Identity Service, Product Service, Order Service, Payment Service, Flash Sale Service, Worker Service, Search Service, Notification Service -- All register and discover peers

#### Infrastructure

| Property | Value |
|----------|-------|
| Dockerfile | `backend/discovery-service/Dockerfile.dev` |
| Port | 8761 |
| Configuration | Self-registration disabled, self-preservation mode enabled |
| Health Check | `curl http://localhost:8761/actuator/health` (30s interval, 150s start period) |
| Scaling | Single instance (Eureka cluster for production) |

---

### 11. Identity Service

- **Name**: Identity Service
- **Description**: Central authentication and user management service handling JWT token lifecycle, user registration/login, role-based access control (BUYER/SELLER/ADMIN), address management, token blacklisting, and avatar presigned URLs.
- **Container Name**: `fs-identity`
- **Type**: Spring Boot MVC Application
- **Technology**: Java 25, Spring Boot 4.0.4, Spring MVC (Tomcat), PostgreSQL (identity schema), Redis, MinIO, Kafka
- **Deployment**: Docker (`backend/identity-service/Dockerfile.dev`), port 8081

#### Purpose

The Identity Service is the single source of truth for user identity:

1. **Authentication**: Multi-credential login (username/email/phone + password), JWT generation (RS256), token refresh, logout with blacklisting.
2. **User Management**: Profile CRUD, password change, avatar upload, seller role upgrade.
3. **Address Management**: Full CRUD with province/district data, default address logic.
4. **Admin Controls**: Account locking/unlocking with reason tracking.
5. **Internal APIs**: Inter-service user role/info/existence queries.
6. **Kafka Request-Reply**: Address lookups for order-service.

#### Components

This container deploys the Identity Service component:
- [Identity Service](./c4-component-identity-service.md) -- Authentication, user management, addresses, admin controls

Full code-level documentation: [c4-code-backend-identity-service.md](./c4-code-backend-identity-service.md)

#### Interfaces

| Interface | Protocol | Description |
|-----------|----------|-------------|
| Auth REST API | HTTP/8081/v1/auth/ | Login, register, refresh, logout (16 public endpoints) |
| User REST API | HTTP/8081/v1/users/ | Profile, avatar, addresses, password change (12 authenticated endpoints) |
| Admin REST API | HTTP/8081/v1/admin/ | Account locking/unlocking (2 admin endpoints) |
| Internal REST API | HTTP/8081/internal/ | User role/info/existence queries for inter-service use |
| Kafka Consumer | Kafka | `order.address.request` |
| Kafka Producer | Kafka | `order.address.response` |
| Health Check | HTTP/8081/actuator/health | Docker health probe |
| Eureka Registration | HTTP/8761 | Service registration |

Spec: [identity-service-api.yaml](./apis/identity-service-api.yaml)

#### Dependencies

**Containers Used:**
- PostgreSQL -- Persistent storage (identity schema: users, roles, addresses)
- Redis -- Token blacklist with TTL-based auto-expiry
- Kafka -- Address request-reply with Order Service
- MinIO -- Avatar presigned URL generation
- Discovery Service (Eureka) -- Service registration and discovery

#### Infrastructure

| Property | Value |
|----------|-------|
| Dockerfile | `backend/identity-service/Dockerfile.dev` |
| Port | 8081 |
| JVM Options | `JVM_OPTS_IDENTITY` |
| Depends On | PostgreSQL, Redis, Kafka |
| JWT Algorithm | RS256 (HMAC-SHA256 per common-lib) |
| Scaling | Horizontally scalable (stateless with Redis-backed blacklist) |

---

### 12. Product Service

- **Name**: Product Service
- **Description**: Product catalog and cart management service handling products, product variants, categories, shopping cart, inventory tracking, and image uploads. Uses MongoDB as primary data store, Redis for caching, and MinIO for image storage.
- **Container Name**: `fs-product`
- **Type**: Spring Boot MVC Application (Virtual Threads)
- **Technology**: Java 25, Spring Boot 4.0.4, Spring MVC (Virtual Threads), MongoDB, Redis, MinIO, Kafka
- **Deployment**: Docker (`backend/product-service/Dockerfile.dev`), port 8090

#### Purpose

The Product Service manages the complete product lifecycle and shopping cart:

1. **Product Lifecycle**: DRAFT -> PENDING -> APPROVED/REJECTED -> PUBLISHED/UNPUBLISHED. Full state machine with seller submission and admin moderation.
2. **Variant/SKU Management**: Per-product variants with unique SKU codes, tier names, and individual pricing. Auto-creates inventory on variant creation.
3. **Category Management**: Hierarchical categories with parent-child relationships and URL-friendly slugs.
4. **Shopping Cart**: Consolidated cart with multi-seller grouping, stock validation, and flash sale item linking.
5. **Atomic Inventory**: Thread-safe stock mutations using MongoDB `$inc` with four stock dimensions (total, locked, available, flash-reserved) and audit trail.
6. **Image Uploads**: Presigned MinIO URLs organized by seller/product with 15-minute TTL.
7. **Kafka Events**: Publishes product lifecycle events and handles cart request-reply for checkout.

#### Components

This container deploys the Product Service component:
- [Product Service](./c4-component-product-service.md) -- Products, variants, categories, cart, inventory, images

Full code-level documentation: [c4-code-backend-product-service.md](./c4-code-backend-product-service.md)

#### Interfaces

| Interface | Protocol | Description |
|-----------|----------|-------------|
| Product REST API | HTTP/8090/v1/products/ | Product CRUD, search (public and seller endpoints) |
| Category REST API | HTTP/8090/v1/categories/ | Category listing and admin CRUD |
| Cart REST API | HTTP/8090/v1/cart/ | Cart CRUD with multi-seller grouping |
| Inventory REST API | HTTP/8090/v1/inventory/ | Inventory lookup, restock, adjust |
| Kafka Consumer | Kafka | `product.approved`, `order.cart_items.request`, `order.checkout_completed` |
| Kafka Producer | Kafka | 8 product lifecycle topics + `order.cart_items.response` |
| Health Check | HTTP/8090/actuator/health | Docker health probe |
| Eureka Registration | HTTP/8761 | Service registration |

Spec: [product-service-api.yaml](./apis/product-service-api.yaml)

#### Dependencies

**Containers Used:**
- MongoDB -- Primary data store (fs_product database: products, variants, categories, carts, cart_items, inventories, inventory_logs)
- Redis -- Available for caching (dependency declared)
- Kafka -- Event publishing and cart request-reply
- MinIO -- Product image presigned URLs
- Discovery Service (Eureka) -- Service registration and discovery

#### Infrastructure

| Property | Value |
|----------|-------|
| Dockerfile | `backend/product-service/Dockerfile.dev` |
| Port | 8090 |
| JVM Options | `JVM_OPTS_PRODUCT` |
| Threading | Java 21+ Virtual Threads |
| Depends On | MongoDB, Kafka, MinIO |
| Scaling | Horizontally scalable (stateless with MongoDB backend) |

---

### 13. Order Service

- **Name**: Order Service
- **Description**: Axon CQRS-based order management service handling multi-vendor checkout, dual-saga orchestration, order lifecycle, shipping, returns (RTS), partial/full refunds, and seller dashboards. Uses PostgreSQL with Axon Framework for event sourcing and Kafka for inter-service coordination.
- **Container Name**: `fs-order`
- **Type**: Spring Boot MVC Application (CQRS/Event Sourcing)
- **Technology**: Java 25, Spring Boot 4.0.4, Axon Framework 4.13.0, PostgreSQL, Kafka
- **Deployment**: Docker (`backend/order-service/Dockerfile.dev`), port 8083

#### Purpose

The Order Service is the central order lifecycle orchestrator:

1. **Multi-Vendor Checkout**: Creates a parent order grouping sub-orders per seller from cart items fetched via Kafka request-reply.
2. **Dual-Saga Orchestration**: `ParentOrderPaymentSaga` handles payment coordination; `OrderProcessingSaga` manages per-sub-order lifecycle (payment timeout, shipping, delivery, return).
3. **Order State Machine**: PENDING -> PAID -> SHIPPING -> DELIVERED with alternative paths for CANCELLED, RETURNED, and REFUNDED.
4. **Refund Management**: Full and partial refunds across multiple sellers with evidence upload, admin approval workflow, and RTS auto-refunds.
5. **Payment Timeout**: Auto-cancels unpaid orders after 30 minutes via Axon `DeadlineManager`.

#### Components

This container deploys the Order Service component:
- [Order Service](./c4-component-order-service.md) -- CQRS order management with sagas, payment coordination, refunds

Full code-level documentation: [c4-code-backend-order-service.md](./c4-code-backend-order-service.md)

#### Interfaces

| Interface | Protocol | Description |
|-----------|----------|-------------|
| Order REST API | HTTP/8083/v1/orders/ | Checkout, listing, detail, cancel, tracking, delivery, RTS (14 buyer/seller endpoints) |
| Refund REST API | HTTP/8083/v1/orders/*/refunds* | Partial/full refunds, history, evidence upload (8 endpoints) |
| Axon Event Bus (internal) | Axon gRPC/8124 | Commands, events, sagas, deadline management |
| Kafka Consumer | Kafka | 8 topics (payment events, refund events, request-reply responses) |
| Kafka Producer | Kafka | 16 topics (payment/refund requests, order lifecycle events, request-reply requests) |
| Health Check | HTTP/8083/actuator/health | Docker health probe |
| Eureka Registration | HTTP/8761 | Service registration |

Spec: [order-service-api.yaml](./apis/order-service-api.yaml)

#### Dependencies

**Containers Used:**
- PostgreSQL -- Persistent storage (order_service schema: parent_orders, orders, order_items, Axon token/saga stores)
- Axon Server -- Event store, event bus, saga persistence, deadline management
- Kafka -- Payment/refund coordination, cart/address request-reply, lifecycle notifications
- Identity Service -- Address lookups (Kafka request-reply), user role queries (REST)
- Product Service -- Cart item enrichment (Kafka request-reply), checkout cleanup (Kafka events)
- Payment Service -- Payment and refund coordination (Kafka)
- Discovery Service (Eureka) -- Service registration and discovery

#### Infrastructure

| Property | Value |
|----------|-------|
| Dockerfile | `backend/order-service/Dockerfile.dev` |
| Port | 8083 |
| JVM Options | `JVM_OPTS_ORDER` |
| Depends On | PostgreSQL, Kafka, Axon Server |
| CQRS Pattern | Axon Framework: Commands -> Events -> Sagas + JPA read models |
| Saga Deadline | Payment timeout: 30 minutes |
| Scaling | Horizontally scalable (JPA token store handles event handler coordination) |

---

### 14. Payment Service

- **Name**: Payment Service
- **Description**: Payment processing service integrating Stripe Connect for multi-vendor payments, seller onboarding, refund management, and webhook handling. Uses PostgreSQL for persistence and Kafka for event-driven communication with Order Service.
- **Container Name**: `fs-payment`
- **Type**: Spring Boot MVC Application
- **Technology**: Java 25, Spring Boot 4.0.4, Spring MVC, PostgreSQL, Stripe Connect (Express), Axon Framework 4.13.0, Kafka
- **Deployment**: Docker (`backend/payment-service/Dockerfile.dev`), port 8082

#### Purpose

The Payment Service handles all monetary operations:

1. **Payment Processing**: Creates Stripe PaymentIntents on `payment.requested` Kafka events, persists Transactions, and creates per-seller SellerTransfer records.
2. **Stripe Webhooks**: Handles 20+ Stripe event types (PaymentIntent, charge, refund, transfer, payout, account, dispute) with Stripe-Signature verification.
3. **Multi-Vendor Transfers**: Creates Stripe Connect transfers from platform to each seller's Express account after successful payment, proportional to sub-order amount minus platform fee.
4. **Seller Stripe Connect Onboarding**: Full Express account lifecycle (account creation, AccountLink, status polling, refresh, dashboard login).
5. **Refund Management**: Partial/full/RTS refunds with Stripe refund execution, transfer reversal, and admin approval workflow.
6. **Payment Cancellation**: Cancels pending PaymentIntents when orders are cancelled.

#### Components

This container deploys the Payment Service component:
- [Payment Service](./c4-component-payment-service.md) -- Stripe payments, Connect onboarding, refunds, webhooks

Full code-level documentation: [c4-code-backend-payment-service.md](./c4-code-backend-payment-service.md)

#### Interfaces

| Interface | Protocol | Description |
|-----------|----------|-------------|
| Payment REST API | HTTP/8082/v1/payments/ | Transaction detail queries (1 endpoint) |
| Stripe Webhook | HTTP/8082/v1/stripe/webhooks | Stripe event receiver (public, Stripe-Signature verified, 20+ types) |
| Seller Payments REST API | HTTP/8082/v1/seller/payments/ | Earnings overview, Stripe dashboard link (2 endpoints) |
| Stripe Onboarding REST API | HTTP/8082/v1/stripe/onboarding/ | Onboarding start, status, refresh (3 endpoints) |
| Admin Refund REST API | HTTP/8082/v1/admin/refunds/ | Refund list, detail, approve, reject (4 endpoints) |
| Kafka Consumer | Kafka | 9 topics (payment/refund requests, order cancel, request-reply) |
| Kafka Producer | Kafka | 14 topics (payment/refund events, request-reply responses, stripe alerts) |
| Stripe API | HTTPS | PaymentIntent, Transfer, Account, Refund operations |
| Health Check | HTTP/8082/actuator/health | Docker health probe |
| Eureka Registration | HTTP/8761 | Service registration |

Spec: [payment-service-api.yaml](./apis/payment-service-api.yaml)

#### Dependencies

**Containers Used:**
- PostgreSQL -- Persistent storage (payment_service schema: transactions, refunds, refund_items, seller_stripe_accounts, seller_transfers)
- Kafka -- Payment/refund coordination with Order Service
- Axon Server -- Transactional infrastructure
- Stripe API (external) -- Payment processing gateway
- Discovery Service (Eureka) -- Service registration and discovery

#### Infrastructure

| Property | Value |
|----------|-------|
| Dockerfile | `backend/payment-service/Dockerfile.dev` |
| Port | 8082 |
| JVM Options | `JVM_OPTS_PAYMENT` |
| Depends On | PostgreSQL, Kafka, Axon Server |
| Platform Fee | Configurable percentage (default 5.0%) |
| Currency | VND (Vietnamese Dong, zero-decimal) |
| Health Check | `curl http://localhost:8082/actuator/health` (30s interval, 150s start period) |
| Scaling | Horizontally scalable |

---

### 15. Flash Sale Service

- **Name**: Flash Sale Service
- **Description**: Core service managing timed flash sale sessions, item lifecycle (submission, approval, rejection), buyer purchase flow with Redis-backed anti-oversell protection, and reminder notifications.
- **Container Name**: `fs-flashsale`
- **Type**: Spring Boot WebFlux Application (Reactive)
- **Technology**: Java 25, Spring Boot 4.0.4, Spring WebFlux, PostgreSQL (R2DBC), Redis (Lua scripts), Kafka, Axon Framework 4.13.0
- **Deployment**: Docker (`backend/flashsale-service/Dockerfile.dev`), port 8085

#### Purpose

The Flash Sale Service orchestrates timed discount events:

1. **Session Management**: CRUD for sessions with UPCOMING/ACTIVE/ENDED status and server-time synchronization.
2. **Item Approval Workflow**: Seller item submission -> admin approval/rejection.
3. **Anti-Oversell Protection**: Redis Lua scripts provide atomic stock decrement guaranteeing inventory never goes below zero.
4. **Buyer Purchase Flow**: Purchase during active sessions with total calculation and Kafka event publishing.
5. **Reminder Management**: Buyers set/remove session reminders for notifications.

#### Components

This container deploys the Flash Sale Service component:
- [Flash Sale Service](./c4-component-flashsale-service.md) -- Sessions, items, purchases, anti-oversell, reminders

Full code-level documentation: [c4-code-backend-flashsale-service.md](./c4-code-backend-flashsale-service.md)

#### Interfaces

| Interface | Protocol | Description |
|-----------|----------|-------------|
| Flash Sale REST API | HTTP/8085/v1/flash-sales/ | Session CRUD, item management, purchases, reminders (12 endpoints) |
| Kafka Consumer | Kafka | `flash_sale.session_started` |
| Kafka Producer | Kafka | `flash_sale.item_approved`, `flash_sale.item_rejected`, `flash_sale.item_sold` |
| Health Check | HTTP/8085/actuator/health | Docker health probe |
| Eureka Registration | HTTP/8761 | Service registration |

Spec: [flashsale-service-api.yaml](./apis/flashsale-service-api.yaml)

#### Dependencies

**Containers Used:**
- PostgreSQL -- Persistent storage (flashsale schema: fs_sessions, fs_items, fs_reminders, Axon saga tables) via R2DBC
- Redis -- Atomic inventory counters via Lua scripts for anti-oversell
- Kafka -- Event publishing and session lifecycle events
- Axon Server -- CQRS infrastructure (provisioned)
- Discovery Service (Eureka) -- Service registration and discovery

#### Infrastructure

| Property | Value |
|----------|-------|
| Dockerfile | `backend/flashsale-service/Dockerfile.dev` |
| Port | 8085 |
| JVM Options | `JVM_OPTS_FLASHSALE` |
| Database | PostgreSQL (R2DBC reactive driver) |
| Depends On | PostgreSQL, Redis, Kafka, Axon Server |
| Anti-Oversell | Lua scripts via Redis reactive client |
| Scaling | Horizontally scalable (Redis atomic counters handle concurrency) |

---

### 16. Worker Service

- **Name**: Worker Service
- **Description**: Background worker service implementing the transactional outbox pattern for reliable event publishing, Dead Letter Queue (DLQ) retry processing, and scheduled cron jobs (order auto-cancellation, cart cleanup). Uses ShedLock for distributed locking and Quartz for scheduling.
- **Container Name**: `fs-worker`
- **Type**: Spring Boot MVC Application (Background Worker)
- **Technology**: Java 25, Spring Boot 4.0.4, Spring MVC (Virtual Threads), PostgreSQL, ShedLock 5.13.0, Quartz Scheduler, Kafka
- **Deployment**: Docker (`backend/worker-service/Dockerfile.dev`), port 8086

#### Purpose

The Worker Service solves critical distributed system problems:

1. **Transactional Outbox Pattern**: All services write events to the shared `outbox_events` table within local DB transactions. The Worker Service polls this table, publishes to Kafka, and marks as processed, guaranteeing at-least-once delivery without distributed transactions.
2. **DLQ Retry Processing**: Reprocures failed events from `failed_events` table with configurable backoff.
3. **Order Auto-Cancellation**: Cron job cancels unpaid orders past their payment deadline.
4. **Cart Cleanup**: Cron job removes abandoned cart entries.
5. **Distributed Locking**: ShedLock ensures only one instance executes each job at a time.

#### Components

This container deploys the Worker Service component:
- [Worker Service](./c4-component-worker-service.md) -- Outbox publisher, DLQ retry, auto-cancel, cart cleanup

Full code-level documentation: [c4-code-backend-worker-service.md](./c4-code-backend-worker-service.md)

#### Interfaces

| Interface | Protocol | Description |
|-----------|----------|-------------|
| Admin REST API | HTTP/8086/v1/admin/ | Outbox status, DLQ management (4 admin endpoints) |
| Kafka Producer | Kafka | Outbox events, `order.auto_cancelled`, `flash_sale.session_ended` |
| Health Check | HTTP/8086/actuator/health | Docker health probe |
| Eureka Registration | HTTP/8761 | Service registration |

#### Dependencies

**Containers Used:**
- PostgreSQL -- Shared outbox database (worker schema: outbox_events, failed_events, shedlock)
- Kafka -- Event publishing for outbox and auto-cancellation

**Containers That Write to Shared Outbox:**
- Order Service, Payment Service, Product Service, Flash Sale Service

#### Infrastructure

| Property | Value |
|----------|-------|
| Dockerfile | `backend/worker-service/Dockerfile.dev` |
| Port | 8086 |
| JVM Options | `JVM_OPTS_WORKER` |
| Threading | Java 21+ Virtual Threads |
| Depends On | PostgreSQL, Kafka, Axon Server |
| Distributed Lock | ShedLock (PostgreSQL-backed) |
| Scheduling | Quartz Scheduler |
| Scaling | Multiple instances (ShedLock prevents duplicate execution) |

---

### 17. Search Service

- **Name**: Search Service
- **Description**: Full-text product search service consuming product lifecycle events from Kafka to maintain an Elasticsearch index. Provides relevance-ranked search across product names, descriptions, and tags with category and seller filtering.
- **Container Name**: `fs-search`
- **Type**: Spring Boot MVC Application (Virtual Threads, Kafka Consumer)
- **Technology**: Java 25, Spring Boot 4.0.4, Spring MVC (Virtual Threads), Elasticsearch 8.10, Spring Data Elasticsearch, Kafka
- **Deployment**: Docker (`backend/search-service/Dockerfile.dev`), port 8091

#### Purpose

The Search Service provides fast, relevance-ranked product discovery:

1. **Full-Text Search**: Multi-field search across name, description (2x boost), and tags (1.5x boost).
2. **Event-Driven Indexing**: Consumes `product.approved` events to index products into Elasticsearch.
3. **Denormalized Read Model**: Stores flattened product documents with seller/category names to avoid joins.
4. **Filtered Queries**: Category, seller, status, and flash sale filtering.

#### Components

This container deploys the Search Service component:
- [Search Service](./c4-component-search-service.md) -- Full-text search, Elasticsearch indexing, Kafka consumer

Full code-level documentation: [c4-code-backend-search-service.md](./c4-code-backend-search-service.md)

#### Interfaces

| Interface | Protocol | Description |
|-----------|----------|-------------|
| Search REST API | HTTP/8091/v1/search/ | Full-text search with filters (5 endpoints) |
| Kafka Consumer | Kafka | `product.approved`, `product.created`, `product.updated`, `product.deleted` |
| Health Check | HTTP/8091/actuator/health | Docker health probe |
| Eureka Registration | HTTP/8761 | Service registration |

Spec: [search-service-api.yaml](./apis/search-service-api.yaml)

#### Dependencies

**Containers Used:**
- Elasticsearch -- Index storage and full-text query execution
- Kafka -- Product lifecycle event consumption
- Discovery Service (Eureka) -- Service registration and discovery

#### Infrastructure

| Property | Value |
|----------|-------|
| Dockerfile | `backend/search-service/Dockerfile.dev` |
| Port | 8091 |
| JVM Options | `JVM_OPTS_SEARCH` |
| Threading | Java 21+ Virtual Threads |
| Depends On | Elasticsearch, Kafka |
| Kafka Consumer Group | `search-service-group` |
| Scaling | Horizontally scalable (Kafka partitions manage work distribution) |

---

### 18. Notification Service

- **Name**: Notification Service
- **Description**: Real-time notification service using Server-Sent Events (SSE) for browser push notifications, consuming domain events from Kafka (order delivery, Stripe compliance) and persisting notification records in MongoDB with 90-day TTL auto-expiry.
- **Container Name**: `fs-notification`
- **Type**: Spring Boot WebFlux Application (Reactive)
- **Technology**: Java 25, Spring Boot 4.0.4, Spring WebFlux (Reactive), MongoDB (Reactive driver), Redis (Reactive), Kafka, SSE
- **Deployment**: Docker (`backend/notification-service/Dockerfile.dev`), port 8092

#### Purpose

The Notification Service is the central hub for user-facing notifications:

1. **Real-Time SSE Push**: Delivers live notifications to connected browsers without polling.
2. **Event-Driven Generation**: Consumes Kafka events from Order Service (order.delivered) and Payment Service (seller.stripe_requirement).
3. **Notification Persistence**: MongoDB storage with unread tracking and 90-day auto-expiry TTL.
4. **Multi-Channel Architecture**: Designed for future email/SMS integration.

#### Components

This container deploys the Notification Service component:
- [Notification Service](./c4-component-notification-service.md) -- SSE push, Kafka consumers, notification storage

Full code-level documentation: [c4-code-backend-notification-service.md](./c4-code-backend-notification-service.md)

#### Interfaces

| Interface | Protocol | Description |
|-----------|----------|-------------|
| Notification REST API | HTTP/8092/v1/notifications/ | List, unread, mark read (5 endpoints) |
| SSE Stream | HTTP/8092/v1/notifications/stream | Server-Sent Events for real-time push |
| Kafka Consumer | Kafka | `order.delivered`, `seller.stripe_requirement` |
| Health Check | HTTP/8092/actuator/health | Docker health probe |
| Eureka Registration | HTTP/8761 | Service registration |

Spec: [notification-service-api.yaml](./apis/notification-service-api.yaml)

#### Dependencies

**Containers Used:**
- MongoDB -- Persistent storage (fs_notification database: notifications collection with TTL index)
- Redis -- Reactive session management for SSE connections
- Kafka -- Event consumption from Order and Payment Services
- Discovery Service (Eureka) -- Service registration and discovery

#### Infrastructure

| Property | Value |
|----------|-------|
| Dockerfile | `backend/notification-service/Dockerfile.dev` |
| Port | 8092 |
| JVM Options | `JVM_OPTS_NOTIFICATION` |
| Depends On | MongoDB, Redis, Kafka |
| Kafka Consumer Group | `notification-service-group` |
| TTL | 90 days on notification documents |
| Scaling | Horizontally scalable (Redis-backed SSE session management) |

---

### 19. Dev Data Runner

- **Name**: Dev Data Runner
- **Description**: Development-only utility service that coordinates data seeding across the microservice ecosystem. Provides a centralized CommandLineRunner with configuration-driven parameters, outputting instructions rather than directly writing data.
- **Container Name**: Not deployed as a persistent container (CLI utility)
- **Type**: Spring Boot CommandLineRunner (Utility)
- **Technology**: Java 25, Spring Boot 4.0.4, SLF4J/Logback
- **Deployment**: Standalone fat JAR (not part of Docker Compose)

#### Purpose

The Dev Data Runner orchestrates development data seeding:

1. **Centralized Configuration**: Provides shared `dev-data.*` properties for seller count, product count, order count, etc.
2. **Per-Service Delegation**: Outputs instructions for activating per-service `dev` profiles that trigger each service's own seeding logic.
3. **Non-Production Only**: Never deployed to staging or production environments.

#### Components

This container deploys the Dev Data Runner component:
- [Dev Data Runner](./c4-component-dev-data-runner.md) -- Configuration-driven seeding coordination

Full code-level documentation: [c4-code-backend-dev-data-runner.md](./c4-code-backend-dev-data-runner.md)

#### Interfaces

| Interface | Protocol | Description |
|-----------|----------|-------------|
| CLI | Terminal | `mvn spring-boot:run -Dspring-boot.run.profiles=dev` |

#### Dependencies

The Dev Data Runner has no runtime dependencies on other containers. It outputs instructions for activating seeding in downstream services:
- Payment Service, Order Service, Product Service (each reads `dev-data.*` properties from their own `application-dev.yml`)

#### Infrastructure

| Property | Value |
|----------|-------|
| Build | `spring-boot-maven-plugin` fat JAR |
| Profile | `dev` (required) |
| Config Properties | `dev-data.enabled`, `dev-data.reset`, `dev-data.seller-count`, `dev-data.product-count`, `dev-data.order-count`, `dev-data.transaction-count`, `dev-data.refund-count` |
| Scaling | Not applicable (CLI utility) |

---

## Frontend Application Containers

### 20. Customer Web App

- **Name**: Customer Web App
- **Description**: Customer-facing React Single Page Application for browsing products, managing a shopping cart, completing multi-step checkout with Stripe payments, tracking orders with refunds, and managing user profile and shipping addresses.
- **Container Name**: `fs-customer-fe`
- **Type**: React SPA (Vite Dev Server / Static Build)
- **Technology**: React 19, Vite 6, TypeScript 5.6, Tailwind CSS 3.4, TanStack React Query 5, Zustand 5, Stripe.js + Stripe Elements
- **Deployment**: Docker (`frontend/apps/customer/Dockerfile.dev`), port 3000 (internal, behind nginx)

#### Purpose

The Customer Web App is the primary shopping portal for end users:

1. **Product Discovery**: Paginated catalog with search, category filtering, and flash sale page with real-time countdowns.
2. **Multi-Seller Cart**: Grouped by seller with selective checkout and flash sale validation.
3. **Multi-Step Checkout**: Address selection -> order review -> Stripe payment with countdown. Resilient state recovery via sessionStorage.
4. **Order Tracking**: Full lifecycle with 9 status filters, auto-refresh, and action modals.
5. **Refund Management**: Full/partial refunds with evidence upload and status tracking.
6. **Account Management**: Profile, addresses (Vietnam province/district data), settings.

#### Components

This container deploys the Customer Web App component and consumes the Frontend Shared Library:
- [Customer Web App](./c4-component-customer-app.md) -- 17 pages: products, flash sales, cart, checkout, orders, refunds, profile, addresses, settings
- [Frontend Shared Library](./c4-component-frontend-shared.md) -- API clients, Zustand stores, UI shell components

Full code-level documentation: [c4-code-frontend-customer.md](./c4-code-frontend-customer.md)

#### Interfaces

| Interface | Protocol | Description |
|-----------|----------|-------------|
| Browser Routing | React Router | 17 routes (8 public, 9 authenticated) |
| REST API (via Shared Lib) | HTTP/8080 | Product, cart, order, payment, flash sale, refund, user, address APIs |
| Stripe.js | HTTPS | Stripe Elements for PaymentIntent confirmation |

#### Dependencies

**Containers Used (via Nginx Reverse Proxy):**
- API Gateway (fs-gateway:8080) -- All REST API calls
- Stripe API (external) -- Payment processing via Stripe.js

#### Infrastructure

| Property | Value |
|----------|-------|
| Dockerfile | `frontend/apps/customer/Dockerfile.dev` |
| Internal Port | 3000 |
| Build Tool | Vite 6 |
| Environment | `VITE_BACKEND_MODE=real`, `VITE_PROXY_TARGET=http://fs-gateway:8080` |
| Health Check | Disabled (dev mode Vite server) |
| Scaling | Horizontally scalable (static assets, stateless) |

---

### 21. Seller Web App

- **Name**: Seller Web App
- **Description**: Seller-facing React Single Page Application for product management, inventory control, order fulfillment, Stripe Connect onboarding, earnings tracking, and seller profile management. Enforces SELLER role on all routes.
- **Container Name**: `fs-seller-fe`
- **Type**: React SPA (Vite Dev Server / Static Build)
- **Technology**: React 19, Vite 6, TypeScript 5.6, Tailwind CSS 3.4, TanStack React Query 5, Zustand 5, Stripe Connect
- **Deployment**: Docker (`frontend/apps/seller/Dockerfile.dev`), port 3001 (internal, behind nginx)

#### Purpose

The Seller Web App is the shop management portal for sellers:

1. **Product Lifecycle**: Full CRUD with DRAFT/PENDING/APPROVED/REJECTED/PUBLISHED workflow, variant management, and image upload.
2. **Inventory Control**: Per-SKU management with adjustments and change log viewing.
3. **Order Fulfillment**: Status-filtered orders, tracking number updates, RTS confirmation.
4. **Stripe Connect Onboarding**: Full lifecycle wizard (PENDING -> IN_PROGRESS -> COMPLETE/SUSPENDED) with polling.
5. **Earnings Dashboard**: Transfer history, available/pending balance, Stripe Express Dashboard access.

#### Components

This container deploys the Seller Web App component and consumes the Frontend Shared Library:
- [Seller Web App](./c4-component-seller-app.md) -- 9 pages: dashboard, products, orders, payments, onboarding, settings
- [Frontend Shared Library](./c4-component-frontend-shared.md) -- API clients, auth store, UI shell components

Full code-level documentation: [c4-code-frontend-seller.md](./c4-code-frontend-seller.md)

#### Interfaces

| Interface | Protocol | Description |
|-----------|----------|-------------|
| Browser Routing | React Router | 12 routes (all SELLER role required except login/register) |
| REST API (via Shared Lib) | HTTP/8080 | Product, seller, order, payment, user, Stripe onboarding APIs |
| Stripe Connect | HTTPS | Account Link OAuth flow, Express Dashboard |

#### Dependencies

**Containers Used (via Nginx Reverse Proxy):**
- API Gateway (fs-gateway:8080) -- All REST API calls
- Stripe Connect (external) -- Seller Express account onboarding and dashboard

#### Infrastructure

| Property | Value |
|----------|-------|
| Dockerfile | `frontend/apps/seller/Dockerfile.dev` |
| Internal Port | 3001 |
| Build Tool | Vite 6 |
| Environment | `VITE_BACKEND_MODE=real`, `VITE_PROXY_TARGET=http://fs-gateway:8080` |
| Health Check | Disabled |
| Scaling | Horizontally scalable |

---

### 22. Admin Web App

- **Name**: Admin Web App
- **Description**: Admin-facing React Single Page Application for platform administration -- user management, product moderation, refund processing, flash sale configuration, and analytics dashboard. Enforces ADMIN role on all routes.
- **Container Name**: `fs-admin-fe`
- **Type**: React SPA (Vite Dev Server / Static Build)
- **Technology**: React 19, Vite 6, TypeScript 5.6, Tailwind CSS 3.4, TanStack React Query 5
- **Deployment**: Docker (`frontend/apps/admin/Dockerfile.dev`), port 3002 (internal, behind nginx)

#### Purpose

The Admin Web App is the platform administration portal:

1. **User Management**: Paginated user list with role/status filters, ban/unban.
2. **Product Moderation**: Approval queue with tabs, approve/reject with predefined reasons.
3. **Refund Processing**: Refund table with status filters, approve/reject with adjustable amounts and tracking.
4. **Flash Sale Configuration**: Session CRUD with start/end time scheduling.
5. **Platform Dashboard**: Stat cards and quick links (mock data placeholder).

#### Components

This container deploys the Admin Web App component and consumes the Frontend Shared Library:
- [Admin Web App](./c4-component-admin-app.md) -- 6 pages: dashboard, users, product moderation, refunds, flash sale config
- [Frontend Shared Library](./c4-component-frontend-shared.md) -- API clients, UI shell components

Full code-level documentation: [c4-code-frontend-admin.md](./c4-code-frontend-admin.md)

#### Interfaces

| Interface | Protocol | Description |
|-----------|----------|-------------|
| Browser Routing | React Router | 7 routes (all ADMIN role required except login) |
| REST API (via Shared Lib) | HTTP/8080 | Admin, refund, flash sale APIs |

#### Dependencies

**Containers Used (via Nginx Reverse Proxy):**
- API Gateway (fs-gateway:8080) -- All REST API calls

#### Infrastructure

| Property | Value |
|----------|-------|
| Dockerfile | `frontend/apps/admin/Dockerfile.dev` |
| Internal Port | 3002 |
| Build Tool | Vite 6 |
| Environment | `VITE_BACKEND_MODE=real`, `VITE_PROXY_TARGET=http://fs-gateway:8080` |
| Health Check | Disabled |
| Scaling | Horizontally scalable |

---

## Container Diagram

```mermaid
C4Container
    title Container Diagram for FlashSale Platform

    Person(customer, "Customer", "Buys products and participates in flash sales")
    Person(seller, "Seller", "Manages products, fulfills orders, views earnings")
    Person(admin, "Admin", "Moderates products, processes refunds, manages users")

    System_Boundary(flashsale_platform, "FlashSale Platform") {
        System_Boundary(infrastructure, "Infrastructure") {
            Container(reverse_proxy, "Nginx Reverse Proxy", "nginx:alpine", "Entry point for all HTTP traffic. Routes /api to Gateway, / to Customer, /seller to Seller, /admin to Admin SPA.")
            ContainerDb(postgresql, "PostgreSQL", "15.4-alpine", "Schemas: identity, order_service, payment_service, flashsale, worker. Axon token/saga stores.")
            ContainerDb(mongodb, "MongoDB", "6.0.8", "Databases: fs_product (products, variants, cart), fs_notification (notifications with TTL)")
            ContainerDb(redis, "Redis", "7.2.1-alpine", "Token blacklist, flash sale atomic counters (Lua), SSE sessions, caching")
            ContainerDb(elasticsearch, "Elasticsearch", "8.10.2", "Full-text product search index with denormalized documents")
            ContainerDb(minio, "MinIO", "S3-compatible", "Product images, user avatars, RTS evidence via presigned URLs")
            Container_Queue(kafka, "Kafka + Zookeeper", "Confluent 7.4.0", "Event bus with 52+ topics: product, order, payment, refund, flash sale, request-reply")
            Container(axonserver, "Axon Server", "AxonIQ", "CQRS event store and message bus for Order Service, Payment Service sagas, deadline management")
        }

        System_Boundary(backend_services, "Backend Services (Java 25 / Spring Boot 4)") {
            Container(api_gateway, "API Gateway", "Spring Cloud Gateway, Netty", "JWT validation, token blacklist, CORS, security headers. Routes to 9 downstream services via Eureka.")
            Container(discovery, "Discovery Service", "Netflix Eureka", "Service registration and discovery. Dashboard at :8761.")
            Container(identity_service, "Identity Service", "Spring MVC, PostgreSQL, Redis", "Authentication, user CRUD, addresses, token lifecycle. Port :8081.")
            Container(product_service, "Product Service", "Spring MVC, MongoDB, MinIO", "Products, variants, categories, cart, atomic inventory. Port :8090.")
            Container(order_service, "Order Service", "Spring MVC, Axon CQRS, PostgreSQL", "Multi-vendor checkout, dual-saga orchestration, refunds, RTS. Port :8083.")
            Container(payment_service, "Payment Service", "Spring MVC, Stripe, PostgreSQL", "Stripe Connect payments, seller onboarding, webhooks, refunds. Port :8082.")
            Container(flashsale_service, "Flash Sale Service", "Spring WebFlux, R2DBC, Redis", "Flash sessions, Lua anti-oversell, purchase flow. Port :8085.")
            Container(worker_service, "Worker Service", "Spring MVC, ShedLock, Quartz", "Transactional outbox, DLQ retry, auto-cancel, cart cleanup. Port :8086.")
            Container(search_service, "Search Service", "Spring MVC, Elasticsearch", "Full-text search, Kafka-driven indexing. Port :8091.")
            Container(notification_service, "Notification Service", "Spring WebFlux, MongoDB, SSE", "Real-time SSE push, Kafka event consumers. Port :8092.")
        }

        System_Boundary(frontend_apps, "Frontend SPAs (React 19 / Vite 6)") {
            Container(customer_app, "Customer Web App", "React, Vite, Stripe.js", "Shopping portal: products, cart, checkout, orders, refunds, profile. Port :3000.")
            Container(seller_app, "Seller Web App", "React, Vite, Stripe Connect", "Seller portal: product lifecycle, inventory, orders, earnings, Stripe onboarding. Port :3001.")
            Container(admin_app, "Admin Web App", "React, Vite", "Admin portal: user management, product moderation, refund processing, flash sale config. Port :3002.")
        }
    }

    System_Ext(stripe_api, "Stripe API", "Payment Gateway & Connect", "PaymentIntents, Express accounts, webhooks, refunds, transfers")
    System_Ext(email_svc, "Email Service", "SMTP/API (future)", "Async email notifications for offline users")

    Rel(customer, reverse_proxy, "Browses marketplace", "HTTPS")
    Rel(seller, reverse_proxy, "Manages shop", "HTTPS")
    Rel(admin, reverse_proxy, "Administers platform", "HTTPS")

    Rel(reverse_proxy, customer_app, "Routes / to", "HTTP")
    Rel(reverse_proxy, seller_app, "Routes /seller to", "HTTP")
    Rel(reverse_proxy, admin_app, "Routes /admin to", "HTTP")
    Rel(reverse_proxy, api_gateway, "Routes /api/ to", "HTTP")

    Rel(customer_app, api_gateway, "REST API calls via", "JSON/HTTPS, JWT Bearer")
    Rel(seller_app, api_gateway, "REST API calls via", "JSON/HTTPS, JWT Bearer")
    Rel(admin_app, api_gateway, "REST API calls via", "JSON/HTTPS, JWT Bearer")

    Rel(api_gateway, redis, "Checks token blacklist", "Redis HASKEY")
    Rel(api_gateway, identity_service, "Routes /auth/**, /users/** via", "HTTP, lb://identity-service")
    Rel(api_gateway, product_service, "Routes /products/**, /cart/**, /categories/** via", "HTTP, lb://product-service")
    Rel(api_gateway, order_service, "Routes /orders/**, /sellers/** via", "HTTP, lb://order-service")
    Rel(api_gateway, payment_service, "Routes /payments/**, /stripe/**, /refunds/** via", "HTTP, lb://payment-service")
    Rel(api_gateway, flashsale_service, "Routes /flash-sales/** via", "HTTP, lb://flashsale-service")
    Rel(api_gateway, worker_service, "Routes /workers/** via", "HTTP, lb://worker-service")
    Rel(api_gateway, search_service, "Routes /search/** via", "HTTP, lb://search-service")
    Rel(api_gateway, notification_service, "Routes /notifications/** via", "HTTP, lb://notification-service")

    Rel(identity_service, postgresql, "Reads/writes users, roles, addresses", "JDBC (identity schema)")
    Rel(identity_service, redis, "Blacklists tokens with TTL", "Redis SETEX")
    Rel(identity_service, minio, "Generates presigned URLs for avatars", "MinIO SDK")
    Rel(identity_service, kafka, "Address request-reply with Order Service", "Kafka")

    Rel(product_service, mongodb, "Reads/writes products, variants, cart, inventory", "MongoDB Driver (fs_product)")
    Rel(product_service, minio, "Generates presigned URLs for images", "MinIO SDK")
    Rel(product_service, kafka, "Publishes product lifecycle events; cart request-reply", "Kafka")

    Rel(order_service, postgresql, "Reads/writes orders, Axon token/saga stores", "JDBC (order_service schema)")
    Rel(order_service, axonserver, "CQRS: commands, events, sagas, deadlines", "gRPC")
    Rel(order_service, kafka, "Payment/refund coordination; cart/address/refund request-reply", "Kafka")

    Rel(payment_service, postgresql, "Reads/writes transactions, refunds, Stripe accounts, transfers", "JDBC (payment_service schema)")
    Rel(payment_service, kafka, "Payment/refund events; request-reply responses", "Kafka")
    Rel(payment_service, stripe_api, "Creates PaymentIntents, transfers, accounts, refunds", "Stripe Java SDK, HTTPS")

    Rel(flashsale_service, postgresql, "Reads/writes sessions, items, reminders", "R2DBC (flashsale schema)")
    Rel(flashsale_service, redis, "Atomic stock decrement via Lua scripts", "Redis Reactive")
    Rel(flashsale_service, kafka, "Publishes item approved/rejected/sold events", "Kafka")

    Rel(worker_service, postgresql, "Polls outbox_events, failed_events; ShedLock locks", "JDBC (worker schema)")
    Rel(worker_service, kafka, "Publishes outbox events and auto-cancellation notifications", "Kafka")

    Rel(search_service, elasticsearch, "Indexes and searches product documents", "REST/HTTP")
    Rel(search_service, kafka, "Consumes product lifecycle events", "Kafka")

    Rel(notification_service, mongodb, "Persists notifications with TTL", "Reactive MongoDB (fs_notification)")
    Rel(notification_service, redis, "Manages SSE session state", "Reactive Redis")
    Rel(notification_service, kafka, "Consumes order.delivered, seller.stripe_requirement", "Kafka")
    Rel(notification_service, customer_app, "Pushes real-time notifications via", "SSE")

    Rel(api_gateway, discovery, "Registers with and queries", "HTTP (Eureka REST API)")
    Rel(identity_service, discovery, "Registers with and queries", "HTTP (Eureka REST API)")
    Rel(product_service, discovery, "Registers with and queries", "HTTP (Eureka REST API)")
    Rel(order_service, discovery, "Registers with and queries", "HTTP (Eureka REST API)")
    Rel(payment_service, discovery, "Registers with and queries", "HTTP (Eureka REST API)")
    Rel(flashsale_service, discovery, "Registers with and queries", "HTTP (Eureka REST API)")
    Rel(worker_service, discovery, "Registers with and queries", "HTTP (Eureka REST API)")
    Rel(search_service, discovery, "Registers with and queries", "HTTP (Eureka REST API)")
    Rel(notification_service, discovery, "Registers with and queries", "HTTP (Eureka REST API)")
```

---

## Communication Matrix

### Inter-Container Communication Protocols

| From | To | Protocol | Purpose |
|------|----|----------|---------|
| Nginx Reverse Proxy | Customer/Seller/Admin Apps | HTTP | Frontend routing |
| Nginx Reverse Proxy | API Gateway | HTTP | API routing |
| Frontend SPAs | API Gateway | HTTP/REST + JWT | All API calls |
| API Gateway | All Backend Services | HTTP/REST (via lb://) | Request routing |
| API Gateway | Redis | TCP/RESP | Token blacklist checks |
| API Gateway | Discovery Service | HTTP/REST | Service resolution |
| All Backend Services | Discovery Service | HTTP/REST | Registration + discovery |
| Identity Service | PostgreSQL | JDBC | User/role/address persistence |
| Identity Service | Redis | TCP/RESP | Token blacklist management |
| Identity Service | MinIO | S3 API | Avatar presigned URLs |
| Identity Service | Kafka | Kafka | Address request-reply |
| Product Service | MongoDB | MongoDB Driver | Product/cart/inventory persistence |
| Product Service | MinIO | S3 API | Image presigned URLs |
| Product Service | Kafka | Kafka | Product lifecycle events + cart request-reply |
| Order Service | PostgreSQL | JDBC | Order + Axon store persistence |
| Order Service | Axon Server | gRPC | CQRS event/command/saga bus |
| Order Service | Kafka | Kafka | Payment/refund coordination, request-reply |
| Payment Service | PostgreSQL | JDBC | Transaction/refund/account persistence |
| Payment Service | Axon Server | gRPC | Transactional infrastructure |
| Payment Service | Kafka | Kafka | Payment/refund events, request-reply |
| Payment Service | Stripe API | HTTPS | Payment processing |
| Flash Sale Service | PostgreSQL | R2DBC | Session/item/reminder persistence |
| Flash Sale Service | Redis | Redis Reactive | Atomic inventory Lua scripts |
| Flash Sale Service | Axon Server | gRPC | CQRS infrastructure (provisioned) |
| Flash Sale Service | Kafka | Kafka | Item events |
| Worker Service | PostgreSQL | JDBC | Outbox/DLQ/shedlock persistence |
| Worker Service | Kafka | Kafka | Outbox event publishing |
| Search Service | Elasticsearch | REST/HTTP | Index management + search |
| Search Service | Kafka | Kafka | Product event consumption |
| Notification Service | MongoDB | Reactive MongoDB | Notification persistence |
| Notification Service | Redis | Reactive Redis | SSE session management |
| Notification Service | Kafka | Kafka | Event consumption |
| Notification Service | Browser (SSE) | HTTP/SSE | Real-time push |

### Kafka Event Flow Summary

```
Product Service ──produces──> [product.*] ──consumes──> Search Service
                                                      Notification Service
                                                      Worker Service (outbox)

Order Service ──produces──> [order.*] ──consumes──> Notification Service
                         [payment.requested] ──> Payment Service
                         [refund.*] ──> Payment Service
                         [*.request] ──> Product/Identity/Payment Service (request-reply)

Payment Service ──produces──> [payment.*] ──consumes──> Order Service
                          [refund.*] ──> Order Service
                          [stripe.*] ──> Notification Service

Flash Sale Service ──produces──> [flash_sale.*] ──consumes──> Worker Service (outbox)
                                                               Order Service
                                                               Notification Service

Identity Service ──produces──> [order.address.response] ──consumes──> Order Service
```

### Axon Event Flow Summary

```
OrderService.checkout()
  └── OrderCreatedEvent ──> OrderProcessingSaga (payment timeout 30min)
  └── ParentOrderCheckoutCreatedEvent ──> ParentOrderPaymentSaga
       └── Publishes payment.requested to Kafka ──> Payment Service

Payment Service (via Stripe webhook)
  └── Publishes payment.success to Kafka

PaymentKafkaEventBridge
  └── payment.success ──> ParentOrderPaymentSucceededEvent (Axon)
  └── payment.failed ──> ParentOrderPaymentFailedEvent (Axon)

ParentOrderPaymentSaga
  └── On ParentOrderPaymentSucceededEvent: marks all sub-orders PAID
  └── On ParentOrderPaymentFailedEvent: cancels all sub-orders

OrderProcessingSaga
  └── On OrderPaidEvent: cancels payment timeout
  └── On payment timeout (30 min): auto-cancels order
  └── On OrderDeliveredEvent: completes saga
```

---

## API Specifications

OpenAPI 3.1 specifications for the main REST APIs are available in the `apis/` directory:

| Service | Spec File |
|---------|-----------|
| Identity Service | [apis/identity-service-api.yaml](./apis/identity-service-api.yaml) |
| Product Service | [apis/product-service-api.yaml](./apis/product-service-api.yaml) |
| Order Service | [apis/order-service-api.yaml](./apis/order-service-api.yaml) |
| Payment Service | [apis/payment-service-api.yaml](./apis/payment-service-api.yaml) |
| Flash Sale Service | [apis/flashsale-service-api.yaml](./apis/flashsale-service-api.yaml) |
| Search Service | [apis/search-service-api.yaml](./apis/search-service-api.yaml) |
| Notification Service | [apis/notification-service-api.yaml](./apis/notification-service-api.yaml) |

---

## Deployment Architecture

### Docker Compose Structure

```
docker-compose.yml (root file)
├── Infrastructure (8 containers)
│   ├── PostgreSQL (fs-postgres)
│   ├── MongoDB (fs-mongo)
│   ├── Redis (fs-redis)
│   ├── Elasticsearch (fs-elasticsearch)
│   ├── MinIO (fs-minio)
│   ├── Zookeeper (fs-zookeeper)
│   ├── Kafka (fs-kafka, fs-kafka-init)
│   └── Axon Server (fs-axonserver)
├── Backend Services (10 containers)
│   ├── Discovery Service (fs-discovery)
│   ├── API Gateway (fs-gateway)
│   ├── Identity Service (fs-identity)
│   ├── Product Service (fs-product)
│   ├── Order Service (fs-order)
│   ├── Payment Service (fs-payment)
│   ├── Flash Sale Service (fs-flashsale)
│   ├── Worker Service (fs-worker)
│   ├── Search Service (fs-search)
│   └── Notification Service (fs-notification)
├── Frontend Apps (3 containers)
│   ├── Customer Web App (fs-customer-fe)
│   ├── Seller Web App (fs-seller-fe)
│   └── Admin Web App (fs-admin-fe)
└── Nginx Reverse Proxy (fs-reverse-proxy)
```

### Network

All containers communicate over a shared Docker bridge network named `flashsale-net`. Internal service discovery relies on Docker DNS (container name resolution) and Eureka (application-level service discovery).

### Deployment Modes

| Mode | Command | Description |
|------|---------|-------------|
| Full Stack | `docker compose -f docker-compose.yml up --build -d` | All 22 containers |
| Full Stack + Stripe Dev | `docker compose -f docker-compose.yml -f docker-compose.dev.yml up --build -d` | Includes Stripe CLI webhook listener |
| Infrastructure Only | `docker compose -f docker-compose-infrastructure.yml up --build -d` | 8 infra containers only |
| Infra + Backend | `docker compose -f docker-compose.yml -f docker-compose-backend.yml up --build -d` | Infrastructure + 10 backend services |
| Frontend Mock | `cd frontend && docker compose -f docker-compose.yml up --build -d` | 3 frontend apps with mock data |

### Port Map

| Port | Container | Service |
|------|-----------|---------|
| 80 | fs-reverse-proxy | Nginx (external entry) |
| 3000 | fs-customer-fe | Customer Web App (internal) |
| 3001 | fs-seller-fe | Seller Web App (internal) |
| 3002 | fs-admin-fe | Admin Web App (internal) |
| 5432 | fs-postgres | PostgreSQL |
| 27017 | fs-mongo | MongoDB |
| 6379 | fs-redis | Redis |
| 9000 | fs-minio | MinIO API |
| 9001 | fs-minio | MinIO Console |
| 9200 | fs-elasticsearch | Elasticsearch |
| 9092 | fs-kafka | Kafka |
| 2181 | fs-zookeeper | Zookeeper |
| 8024 | fs-axonserver | Axon Server GUI |
| 8124 | fs-axonserver | Axon Server gRPC |
| 8761 | fs-discovery | Eureka |
| 8080 | fs-gateway | API Gateway |
| 8081 | fs-identity | Identity Service |
| 8082 | fs-payment | Payment Service |
| 8083 | fs-order | Order Service |
| 8085 | fs-flashsale | Flash Sale Service |
| 8086 | fs-worker | Worker Service |
| 8090 | fs-product | Product Service |
| 8091 | fs-search | Search Service |
| 8092 | fs-notification | Notification Service |

---

## Component-to-Container Mapping

| Component | Container |
|-----------|-----------|
| [API Gateway](./c4-component-api-gateway.md) | API Gateway (fs-gateway) |
| [Service Discovery](./c4-component-service-discovery.md) | Discovery Service (fs-discovery) |
| [Common Library](./c4-component-common-lib.md) | (Shared JAR -- included in all backend containers) |
| [Identity Service](./c4-component-identity-service.md) | Identity Service (fs-identity) |
| [Product Service](./c4-component-product-service.md) | Product Service (fs-product) |
| [Order Service](./c4-component-order-service.md) | Order Service (fs-order) |
| [Payment Service](./c4-component-payment-service.md) | Payment Service (fs-payment) |
| [Flash Sale Service](./c4-component-flashsale-service.md) | Flash Sale Service (fs-flashsale) |
| [Worker Service](./c4-component-worker-service.md) | Worker Service (fs-worker) |
| [Search Service](./c4-component-search-service.md) | Search Service (fs-search) |
| [Notification Service](./c4-component-notification-service.md) | Notification Service (fs-notification) |
| [Dev Data Runner](./c4-component-dev-data-runner.md) | Dev Data Runner (standalone JAR) |
| [Customer Web App](./c4-component-customer-app.md) | Customer Web App (fs-customer-fe) |
| [Seller Web App](./c4-component-seller-app.md) | Seller Web App (fs-seller-fe) |
| [Admin Web App](./c4-component-admin-app.md) | Admin Web App (fs-admin-fe) |
| [Frontend Shared Library](./c4-component-frontend-shared.md) | (Shared npm package -- bundled in all frontend containers) |
