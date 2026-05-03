# Service Architecture — C4 Container Diagrams

> Mô tả kiến trúc hệ thống ở mức Container, luồng giao tiếp giữa các service và database.

---

## 1. Tổng quan hệ thống

```mermaid
C4Context
    title System Context — stealing-from-paradise

    Person(buyer, "Buyer", "Người mua hàng trên platform")
    Person(seller, "Seller", "Người bán hàng")
    Person(admin, "Admin", "Quản trị viên hệ thống")

    System_Boundary(platform, "E-Commerce Platform") {
        System(api_gateway, "API Gateway", "Spring Cloud Gateway\nPort: 8080")
        System(identity_svc, "Identity Service", "Auth, Users,\nLoyalty, Trust")
        System(product_svc, "Product Service", "Catalog, Cart,\nInventory")
        System(order_svc, "Order Service", "Orders, Reviews")
        System(payment_svc, "Payment Service", "Stripe, VNPAY,\nRefunds")
        System(flashsale_svc, "Flash Sale Service", "Flash sessions,\nitems, reminders")
        System(notification_svc, "Notification Service", "Push, in-app\nnotifications")
        System(search_svc, "Search Service", "ES indexing,\nfull-text search")
        System(ai_chat_svc, "AI Chat Service", "Spring AI,\nChat support")
    }

    System_Ext(stripe, "Stripe", "Payment processor")
    System_Ext(vnpay, "VNPAY", "VN payment gateway")
    System_Ext(minio, "MinIO", "Object storage")
    System_Ext(pageindex, "PageIndex", "Vector search API")
    System_Ext(openai, "OpenAI", "LLM provider")

    Rel(buyer, api_gateway, "HTTPS", "REST/SSE")
    Rel(seller, api_gateway, "HTTPS", "REST")
    Rel(admin, api_gateway, "HTTPS", "REST")

    Rel(payment_svc, stripe, "HTTPS", "Stripe API")
    Rel(payment_svc, vnpay, "HTTPS", "VNPAY API")
    Rel(product_svc, minio, "HTTPS", "MinIO API")
    Rel(ai_chat_svc, pageindex, "HTTPS", "Vector search")
    Rel(ai_chat_svc, openai, "HTTPS", "LLM chat")
```

---

## 2. Microservices & Databases

```mermaid
C4Container
    title Container Diagram — Services & Data Stores

    System_Boundary(gateway, "API Gateway") {
        Container(gw, "Spring Cloud Gateway", "Java 21", "Routing, JWT validation, Rate limiting")
    }

    System_Boundary(identity, "Identity Service\nPort: 8081") {
        Container(identity_app, "Spring Boot App", "Java 21", "Auth, Users, Loyalty, Trust")
        ContainerDb(identity_db, "PostgreSQL", "identity_db", "Users, Customers, Sellers,\nLoyalty, Trust, Appeals")
    }

    System_Boundary(product, "Product Service\nPort: 8082") {
        Container(product_app, "Spring Boot App", "Java 21", "Catalog, Cart, Inventory")
        ContainerDb(product_db, "MongoDB", "product_db", "Products, Categories,\nVariants, Inventories, Carts")
        ContainerDb(image_db, "PostgreSQL", "media_db", "IMAGES table")
    }

    System_Boundary(order, "Order Service\nPort: 8083") {
        Container(order_app, "Spring Boot App", "Java 21", "Orders, Reviews")
        ContainerDb(order_db, "PostgreSQL", "order_db", "Orders, Order Items,\nReviews, Outbox")
    }

    System_Boundary(payment, "Payment Service\nPort: 8084") {
        Container(payment_app, "Spring Boot App", "Java 21", "Stripe/VNPAY, Transfers")
        ContainerDb(payment_db, "PostgreSQL", "payment_db", "Transactions, Refunds,\nStripe Accounts")
    }

    System_Boundary(flashsale, "Flash Sale Service\nPort: 8085") {
        Container(flashsale_app, "Spring Boot App", "Java 21", "Flash sessions, Items")
        ContainerDb(flashsale_db, "PostgreSQL", "flashsale_db", "Sessions, Items, Reminders")
    }

    System_Boundary(search, "Search Service\nPort: 8086") {
        Container(search_app, "Spring Boot App", "Java 21", "ES indexing & search")
        ContainerDb(search_db, "Elasticsearch", "search_db", "Product index")
    }

    System_Boundary(notification, "Notification Service\nPort: 8087") {
        Container(notification_app, "Spring Boot App", "Java 21", "Push notifications")
        ContainerDb(notification_db, "MongoDB", "notification_db", "Notifications")
    }

    System_Boundary(ai_chat, "AI Chat Service\nPort: 8088") {
        Container(ai_chat_app, "Spring Boot App", "Java 21 + Spring AI", "Chat, Tools, Sessions")
        ContainerDb(ai_chat_db, "PostgreSQL", "ai_chat_db", "Sessions, Messages,\nConfirmations, Tool Logs")
        ContainerDb(ai_chat_cache, "Redis", "ai_chat_cache", "Rate limit, Session cache,\nConfirm fast-lookup")
    }

    Rel(gw, identity_app, "route /api/identity/**")
    Rel(gw, product_app, "route /api/product/**")
    Rel(gw, order_app, "route /api/order/**")
    Rel(gw, payment_app, "route /api/payment/**")
    Rel(gw, flashsale_app, "route /api/flashsale/**")
    Rel(gw, search_app, "route /api/search/**")
    Rel(gw, notification_app, "route /api/notification/**")
    Rel(gw, ai_chat_app, "route /api/ai/**")
```

---

## 3. Communication & Event Flow

```mermaid
flowchart TB
    subgraph "Clients"
        B[Buyer]
        S[Seller]
        A[Admin]
    end

    subgraph "API Gateway"
        GW[Spring Cloud Gateway\nPort 8080]
    end

    subgraph "Core Services"
        IS[Identity Service\nPort 8081]
        PS[Product Service\nPort 8082]
        OS[Order Service\nPort 8083]
        PM[Payment Service\nPort 8084]
        FS[Flash Sale Service\nPort 8085]
    end

    subgraph "Supporting Services"
        SS[Search Service\nPort 8086]
        NS[Notification Service\nPort 8087]
        AI[AI Chat Service\nPort 8088]
    end

    subgraph "Data Stores"
        PGSQL[(PostgreSQL\nidentity / order / payment\nflashsale / ai_chat)]
        MONGO[(MongoDB\nproduct / cart / notification)]
        ES[(Elasticsearch\nproduct search)]
        REDIS[(Redis\nrate / cache / session)]
        MINIO[(MinIO\nimages)]
    end

    subgraph "External"
        STRIPE[Stripe]
        VNPAY[VNPAY]
        PI[PageIndex\nVector Search]
        LLM[OpenAI GPT-4o]
    end

    subgraph "Messaging"
        KAFKA[Kafka\nproduct.changes]
    end

    B --> GW
    S --> GW
    A --> GW
    GW --> IS & PS & OS & PM & FS & SS & NS & AI

    IS --- PGSQL
    OS --- PGSQL
    PM --- PGSQL
    FS --- PGSQL
    AI --- PGSQL

    PS --- MONGO
    NS --- MONGO

    SS --- ES
    AI --- REDIS
    PS --- MINIO

    PM --> STRIPE
    PM --> VNPAY
    AI --> PI
    AI --> LLM

    PS -.->|publish| KAFKA
    KAFKA -.->|consume| SS

    AI -.->|tool call| OS
    AI -.->|tool call| PS
    AI -.->|tool call| IS
```

---

## 4. Service Responsibilities

```mermaid
flowchart LR
    subgraph "Identity Domain"
        IS[Identity Service]
        IS -->|Users| PG1[(PostgreSQL)]
        IS -->|Loyalty| PG1
        IS -->|Trust/Moderation| PG1
    end

    subgraph "Catalog Domain"
        PS[Product Service]
        PS -->|Products| MG1[(MongoDB)]
        PS -->|Categories| MG1
        PS -->|Variants| MG1
        PS -->|Inventory| MG1
        PS -->|Cart| MG1
        PS -->|Images| PG2[(PostgreSQL)]
    end

    subgraph "Order Domain"
        OS[Order Service]
        OS -->|Orders| PG3[(PostgreSQL)]
        OS -->|Reviews| PG3
    end

    subgraph "Payment Domain"
        PM[Payment Service]
        PM -->|Transactions| PG4[(PostgreSQL)]
        PM -->|Refunds| PG4
        PM -->|Stripe KYC| PG4
        PM -->|Transfers| PG4
    end

    subgraph "Flash Sale Domain"
        FS[Flash Sale Service]
        FS -->|Sessions| PG5[(PostgreSQL)]
    end

    subgraph "Search Domain"
        SS[Search Service]
        SS -->|Index| ES[(Elasticsearch)]
    end

    subgraph "Notification Domain"
        NS[Notification Service]
        NS -->|Notifications| MG2[(MongoDB)]
    end

    subgraph "AI Chat Domain"
        AI[AI Chat Service]
        AI -->|Sessions| PG6[(PostgreSQL)]
        AI -->|Messages| PG6
        AI -->|Cache| RS[(Redis)]
    end

    IS -.->|REST| OS
    OS -.->|REST| PM
    PS -.->|Kafka| SS
    PS -.->|Flink| FS
    AI -.->|Tool| OS
    AI -.->|Tool| PS
    AI -.->|Tool| IS
```
