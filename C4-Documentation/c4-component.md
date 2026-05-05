# C4 Component Level: System Overview

## Infrastructure Components

### API Gateway
- **Name**: API Gateway
- **Description**: Spring Cloud Gateway (WebFlux/Reactive) serving as the single entry point for all client requests. Handles JWT validation, token blacklist checking via Redis, CORS, security headers, and routes to all downstream microservices.
- **Type**: Infrastructure / API Gateway
- **Documentation**: [c4-component-api-gateway.md](./c4-component-api-gateway.md)

### Service Discovery
- **Name**: Service Discovery
- **Description**: Netflix Eureka service registry for microservice discovery. All backend services register here on startup and use it to discover peers via logical service names.
- **Type**: Infrastructure / Service Registry
- **Documentation**: [c4-component-service-discovery.md](./c4-component-service-discovery.md)

### Common Library
- **Name**: Common Library
- **Description**: Shared Java library (JAR) providing DTOs, Kafka topic definitions, JWT utilities, security configurations, exception handling, and base classes consumed by all FlashSale microservices.
- **Type**: Library
- **Documentation**: [c4-component-common-lib.md](./c4-component-common-lib.md)

## Business Services

### Identity Service
- **Name**: Identity Service
- **Description**: Central authentication and user management component handling JWT token generation/validation, user registration/login, role-based access control (BUYER/SELLER/ADMIN), address management, token blacklisting, and session management.
- **Type**: Service
- **Documentation**: [c4-component-identity-service.md](./c4-component-identity-service.md)

### Product Service
- **Name**: Product Service
- **Description**: Product catalog and cart management component handling products, product variants (SKUs), hierarchical categories, shopping cart operations, atomic inventory tracking, presigned image uploads to MinIO, and admin product approval workflow.
- **Type**: Service
- **Documentation**: [c4-component-product-service.md](./c4-component-product-service.md)

### Order Service
- **Name**: Order Service
- **Description**: Axon CQRS-based order lifecycle orchestrator managing multi-vendor checkout, dual-saga orchestration (OrderProcessingSaga per sub-order + ParentOrderPaymentSaga per checkout), order status state machine, refund workflows, Return To Sender, and seller dashboard.
- **Type**: Service (CQRS)
- **Documentation**: [c4-component-order-service.md](./c4-component-order-service.md)

### Payment Service
- **Name**: Payment Service
- **Description**: Stripe Connect payment processing component handling PaymentIntent creation, Stripe webhook event processing (20+ event types), multi-vendor fund transfers, seller Stripe Express onboarding, partial/full/RTS refund management with admin approval workflow, and seller earnings aggregation.
- **Type**: Service (CQRS)
- **Documentation**: [c4-component-payment-service.md](./c4-component-payment-service.md)

### Flash Sale Service
- **Name**: Flash Sale Service
- **Description**: Core service managing flash sale sessions, item lifecycle (submission, approval, rejection), buyer purchase flow with anti-oversell protection via Redis Lua scripts, and reminder notifications.
- **Type**: Service
- **Documentation**: [c4-component-flashsale-service.md](./c4-component-flashsale-service.md)

### Search Service
- **Name**: Search Service
- **Description**: Full-text product search using Elasticsearch, consuming Kafka events from Product Service to maintain a denormalized read model. Consumer-only Kafka pattern.
- **Type**: Service (Consumer)
- **Documentation**: [c4-component-search-service.md](./c4-component-search-service.md)

### Notification Service
- **Name**: Notification Service
- **Description**: Real-time notification delivery via Server-Sent Events (SSE), plus email/SMS dispatching. Consumes domain events from Kafka (order lifecycle, Stripe compliance) and persists notifications in MongoDB with 90-day TTL auto-expiry.
- **Type**: Service
- **Documentation**: [c4-component-notification-service.md](./c4-component-notification-service.md)

### Worker Service
- **Name**: Worker Service
- **Description**: Background worker for reliable event publishing via the transactional outbox pattern, Dead Letter Queue (DLQ) handling with retry logic, and scheduled cron jobs (order auto-cancellation, cart cleanup). Uses ShedLock for distributed locking.
- **Type**: Service (Background Worker)
- **Documentation**: [c4-component-worker-service.md](./c4-component-worker-service.md)

## Frontend Components

### Frontend Shared Library
- **Name**: Frontend Shared Library
- **Description**: Shared React/TypeScript library providing 13 API client modules, 11 Zustand state stores, 5 UI shell components (Layout, Navbar, Footer, ErrorBoundary, PrivateRoute), mock backend infrastructure, and core utilities (Axios instance with JWT auth, TanStack Query client, TypeScript types). Consumed by all three frontend apps.
- **Type**: Library / Shared UI
- **Documentation**: [c4-component-frontend-shared.md](./c4-component-frontend-shared.md)

### Customer Web App
- **Name**: Customer Web App
- **Description**: React 19 SPA (port 3000) for customers — product browsing, flash sale participation, cart management, Stripe checkout, order tracking, refund requests, loyalty points, trust score, and profile management.
- **Type**: Web Application (SPA)
- **Documentation**: [c4-component-customer-app.md](./c4-component-customer-app.md)

### Seller Web App
- **Name**: Seller Web App
- **Description**: React 19 SPA (port 3001) for sellers — product CRUD with image uploads, inventory management, order fulfillment, Stripe Connect onboarding, earnings dashboard, seller registration, and trust score.
- **Type**: Web Application (SPA)
- **Documentation**: [c4-component-seller-app.md](./c4-component-seller-app.md)

### Admin Web App
- **Name**: Admin Web App
- **Description**: React 19 SPA (port 3002) for platform administrators — user management (ban/unban), product moderation (approve/reject), refund processing (approve/reject), flash sale session configuration, and dashboard monitoring.
- **Type**: Web Application (SPA)
- **Documentation**: [c4-component-admin-app.md](./c4-component-admin-app.md)

## Utilities

### Dev Data Runner
- **Name**: Dev Data Runner
- **Description**: Standalone utility that coordinates development and demonstration data seeding across all microservices. Provides a centralized entry point with configuration-driven data generation parameters. Not deployed to production.
- **Type**: Utility
- **Documentation**: [c4-component-dev-data-runner.md](./c4-component-dev-data-runner.md)

## Code-Level Documentation Index

Each component is synthesized from these code-level documentation files:

| Component | Code-Level File |
|---|---|
| API Gateway | [c4-code-backend-api-gateway.md](./c4-code-backend-api-gateway.md) |
| Service Discovery | [c4-code-backend-discovery-service.md](./c4-code-backend-discovery-service.md) |
| Common Library | [c4-code-backend-common-lib.md](./c4-code-backend-common-lib.md) |
| Identity Service | [c4-code-backend-identity-service.md](./c4-code-backend-identity-service.md) |
| Product Service | [c4-code-backend-product-service.md](./c4-code-backend-product-service.md) |
| Order Service | [c4-code-backend-order-service.md](./c4-code-backend-order-service.md) |
| Payment Service | [c4-code-backend-payment-service.md](./c4-code-backend-payment-service.md) |
| Flash Sale Service | [c4-code-backend-flashsale-service.md](./c4-code-backend-flashsale-service.md) |
| Search Service | [c4-code-backend-search-service.md](./c4-code-backend-search-service.md) |
| Notification Service | [c4-code-backend-notification-service.md](./c4-code-backend-notification-service.md) |
| Worker Service | [c4-code-backend-worker-service.md](./c4-code-backend-worker-service.md) |
| Dev Data Runner | [c4-code-backend-dev-data-runner.md](./c4-code-backend-dev-data-runner.md) |
| Frontend Shared | [c4-code-frontend-shared.md](./c4-code-frontend-shared.md) |
| Customer App | [c4-code-frontend-customer.md](./c4-code-frontend-customer.md) |
| Seller App | [c4-code-frontend-seller.md](./c4-code-frontend-seller.md) |
| Admin App | [c4-code-frontend-admin.md](./c4-code-frontend-admin.md) |

## Component Relationships

```mermaid
C4Component
    title Infrastructure Components and Their Consumers

    Container_Boundary(infrastructure, "Infrastructure Components") {
        Component(common_lib, "Common Library", "JAR Library", "Shared DTOs, JWT utils, Kafka topics, error codes, security configs, event payloads")
        Component(api_gateway, "API Gateway", "Spring Cloud Gateway", "Single entry point: JWT auth, token blacklist, CORS, routing to 11 services")
        Component(service_discovery, "Service Discovery", "Netflix Eureka", "Service registration, discovery, and health checking")
    }

    Container_Boundary(services, "Business Microservices") {
        Component(identity, "Identity Service", "Spring Boot Servlet", "Authentication, user/role management, address book, token blacklist (PostgreSQL + Redis)")
        Component(product, "Product Service", "Spring Boot Servlet", "Product CRUD, variants, categories, cart, inventory (MongoDB $inc), image uploads (MinIO)")
        Component(order, "Order Service", "Spring Boot Servlet (CQRS)", "Checkout, OrderProcessingSaga, ParentOrderPaymentSaga, refunds, RTS (Axon + PostgreSQL)")
        Component(payment, "Payment Service", "Spring Boot Servlet (CQRS)", "Stripe PaymentIntent, Connect transfers, webhooks, refunds, seller onboarding (Stripe SDK + PostgreSQL)")
        Component(flashsale, "FlashSale Service", "Spring Boot Reactive", "Flash sale sessions and buy operations")
        Component(notification, "Notification Service", "Spring Boot Reactive", "User notifications, email, push")
        Component(search, "Search Service", "Spring Boot Servlet", "Product and content search")
        Component(worker, "Worker Service", "Spring Boot Servlet", "Background jobs and scheduled tasks")
    }

    System_Ext(redis, "Redis", "Token Blacklist / Cache")
    System_Ext(kafka, "Apache Kafka", "Event Bus")
    SystemDb_Ext(postgresql_identity, "PostgreSQL", "identity schema")
    SystemDb_Ext(postgresql_order, "PostgreSQL", "order_service / Axon stores")
    SystemDb_Ext(postgresql_payment, "PostgreSQL", "payment_service schema")
    SystemDb_Ext(mongodb, "MongoDB", "fs_product database")
    Container_Ext(minio, "MinIO / S3", "Object Storage")
    Container_Ext(stripe, "Stripe API", "Payment Gateway")

    Rel(api_gateway, common_lib, "Uses JwtUtils", "compile dependency")
    Rel(api_gateway, service_discovery, "Registers with and queries", "HTTP (Eureka REST API)")
    Rel(api_gateway, redis, "Checks token blacklist", "Redis HASKEY")

    Rel(identity, common_lib, "Uses", "DTOs, JwtUtils, security, exceptions")
    Rel(product, common_lib, "Uses", "DTOs, exceptions, Kafka topics")
    Rel(order, common_lib, "Uses", "DTOs, exceptions, Kafka topics, event payloads")
    Rel(payment, common_lib, "Uses", "DTOs, exceptions, Kafka topics, event payloads")
    Rel(flashsale, common_lib, "Uses", "DTOs, exceptions, Kafka topics")
    Rel(notification, common_lib, "Uses", "DTOs, exceptions, Kafka topics, event payloads")
    Rel(search, common_lib, "Uses", "DTOs, exceptions")
    Rel(worker, common_lib, "Uses", "DTOs, exceptions, Kafka topics")

    Rel(identity, service_discovery, "Registers with", "HTTP")
    Rel(product, service_discovery, "Registers with", "HTTP")
    Rel(order, service_discovery, "Registers with", "HTTP")
    Rel(payment, service_discovery, "Registers with", "HTTP")
    Rel(flashsale, service_discovery, "Registers with", "HTTP")
    Rel(notification, service_discovery, "Registers with", "HTTP")
    Rel(search, service_discovery, "Registers with", "HTTP")
    Rel(worker, service_discovery, "Registers with", "HTTP")

    Rel(api_gateway, identity, "Routes to", "lb://identity-service")
    Rel(api_gateway, product, "Routes to", "lb://product-service")
    Rel(api_gateway, order, "Routes to", "lb://order-service")
    Rel(api_gateway, payment, "Routes to", "lb://payment-service")
    Rel(api_gateway, flashsale, "Routes to", "lb://flashsale-service")
    Rel(api_gateway, notification, "Routes to", "lb://notification-service")
    Rel(api_gateway, search, "Routes to", "lb://search-service")
    Rel(api_gateway, worker, "Routes to", "lb://worker-service")

    Rel(identity, postgresql_identity, "Persists users, roles, addresses", "JDBC")
    Rel(identity, redis, "Blacklists JWT tokens with TTL", "Redis SETEX")
    Rel(product, mongodb, "Persists product catalog and cart", "MongoDB Driver")
    Rel(product, minio, "Generates presigned PUT URLs", "MinIO SDK")
    Rel(order, postgresql_order, "Persists orders and Axon stores", "JDBC")
    Rel(payment, postgresql_payment, "Persists transactions, refunds, accounts", "JDBC")
    Rel(payment, stripe, "PaymentIntent, Transfer, Refund, Webhook", "Stripe Java SDK")

    Rel(identity, kafka, "Address request-reply with order-service", "Kafka")
    Rel(product, kafka, "Product lifecycle events, cart request-reply", "Kafka")
    Rel(order, kafka, "Payment/refund coordination, request-reply", "Kafka")
    Rel(payment, kafka, "Payment events, refund events, Stripe alerts", "Kafka")
    Rel(flashsale, kafka, "Publishes/consumes", "Kafka")
    Rel(notification, kafka, "Consumes domain events", "Kafka")
    Rel(search, kafka, "Consumes product events", "Kafka")
```
