# TECH STACK & FRAMEWORKS

> Tài liệu liệt kê đầy đủ các công nghệ, framework, thư viện và hạ tầng được sử dụng trong dự án **Flash Sale E-Commerce Platform** (`stealing-from-paradise`).
>
> **Kiến trúc tổng quan:** Microservices + Event-Driven (CQRS/Event Sourcing) + Reactive
>
> Cập nhật: 2026-05-21

---

## Mục lục

1. [Tóm tắt nhanh](#1-tóm-tắt-nhanh)
2. [Backend — Ngôn ngữ & Runtime](#2-backend--ngôn-ngữ--runtime)
3. [Backend — Framework chính](#3-backend--framework-chính)
4. [Backend — Theo từng service](#4-backend--theo-từng-service)
5. [Frontend](#5-frontend)
6. [Cơ sở dữ liệu & Lưu trữ](#6-cơ-sở-dữ-liệu--lưu-trữ)
7. [Messaging & Event Backbone](#7-messaging--event-backbone)
8. [Service Discovery, Gateway & Cấu hình](#8-service-discovery-gateway--cấu-hình)
9. [Bảo mật & Identity](#9-bảo-mật--identity)
10. [AI / LLM](#10-ai--llm)
11. [Thanh toán & Tích hợp bên thứ ba](#11-thanh-toán--tích-hợp-bên-thứ-ba)
12. [DevOps / Hạ tầng vận hành](#12-devops--hạ-tầng-vận-hành)
13. [Quan sát & Giám sát](#13-quan-sát--giám-sát)
14. [Phụ lục: Phiên bản chốt](#14-phụ-lục-phiên-bản-chốt)

---

## 1. Tóm tắt nhanh

| Lớp | Công nghệ chính |
|---|---|
| Ngôn ngữ backend | **Java 25** |
| Framework backend | **Spring Boot 4.0.4**, **Spring Cloud 2025.1.1**, **Axon Framework 4.13.0** |
| Reactive stack | **Spring WebFlux**, **R2DBC**, **Project Reactor**, **Virtual Threads (Loom)** |
| Frontend | **React 19**, **Vite 6**, **TypeScript 5.6**, **TanStack Query v5**, **Zustand 5**, **Tailwind 3.4** |
| Database | **PostgreSQL 15**, **MongoDB 6**, **Redis 7**, **Elasticsearch 8** |
| Messaging | **Apache Kafka (Confluent 7.4)**, **Axon Server** (event store + command/query bus) |
| AI | **Spring AI 2.0.0-M6** (OpenAI / DeepSeek compatible) |
| Thanh toán | **Stripe** |
| Storage | **MinIO** (S3 compatible) |
| Gateway / Discovery | **Spring Cloud Gateway (WebFlux)**, **Netflix Eureka** |
| Container | **Docker Compose** (multi-stack: infrastructure / backend / frontend) |
| Quan sát | **Spring Actuator**, **Micrometer + Prometheus** |

---

## 2. Backend — Ngôn ngữ & Runtime

| Hạng mục | Chi tiết |
|---|---|
| Ngôn ngữ | **Java 25** (LTS, dùng tính năng records, sealed types, pattern matching) |
| Build tool | **Maven** (multi-module, parent `flashsale-parent` quản lý toàn bộ version) |
| Virtual Threads | Bật `spring.threads.virtual.enabled=true` ở các service blocking (Project Loom) để xử lý nhiều request đồng thời mà không cần thread pool lớn |
| Code generation | **Lombok 1.18.40** (giảm boilerplate getter/setter/builder) |
| Validation | **Jakarta Validation API + Hibernate Validator** |

**Vì sao chọn Java 25 + Spring Boot 4:**
- Java 25 = LTS gần nhất, có virtual threads ổn định → phù hợp với traffic cao kiểu flash-sale (hàng nghìn request đồng thời).
- Spring Boot 4 = sinh thái lớn, auto-config nhanh, tương thích Spring Cloud 2025.1.

---

## 3. Backend — Framework chính

### 3.1 Spring Boot 4.0.4

Nền tảng autoconfigure cho mọi service. Starters chính được dùng:

| Starter | Mục đích |
|---|---|
| `spring-boot-starter-web` | REST API blocking (Tomcat servlet) |
| `spring-boot-starter-webflux` | REST API reactive / SSE (Netty) |
| `spring-boot-starter-data-jpa` | ORM cho PostgreSQL |
| `spring-boot-starter-data-r2dbc` | Reactive driver cho PostgreSQL |
| `spring-boot-starter-data-mongodb` / `-reactive` | Document store |
| `spring-boot-starter-data-redis` / `-reactive` | Cache, rate limiter, distributed lock |
| `spring-boot-starter-data-elasticsearch` | Full-text search |
| `spring-boot-starter-security` | Auth (JWT) |
| `spring-boot-starter-validation` | Bean validation |
| `spring-boot-starter-actuator` | Health, metrics, info endpoints |

### 3.2 Spring Cloud 2025.1.1

| Module | Mục đích |
|---|---|
| `spring-cloud-starter-netflix-eureka-server` | Discovery server (port 8761) |
| `spring-cloud-starter-netflix-eureka-client` | Client tự đăng ký với Eureka |
| `spring-cloud-starter-gateway-server-webflux` | API Gateway reactive (port 8080), routing + filter |

### 3.3 Axon Framework 4.13.0 — CQRS / Event Sourcing

Dùng cho các domain **trạng thái nghiệp vụ phức tạp** (order, payment, refund, flashsale).

- **Command side** (`@CommandHandler`): nhận lệnh, validate invariant trong aggregate root.
- **Event side** (`@EventSourcingHandler`): tái dựng aggregate từ event store.
- **Saga** (`@Saga`): điều phối flow đa service (ví dụ: Order → Payment → Inventory deduction → Notification).
- **Axon Server**: event store + command/query bus (chạy như container riêng, port 8024 UI, 8124 gRPC).

### 3.4 Spring WebFlux + Project Reactor

Dùng ở **api-gateway**, **flashsale-service**, **chat-service**, **notification-service** — nơi cần xử lý nhiều kết nối đồng thời (SSE, long-polling, downstream call chain).

- Non-blocking I/O trên Netty
- `Mono<T>` / `Flux<T>` cho stream/single async
- Tích hợp tốt với R2DBC (PostgreSQL reactive) và Mongo reactive driver

### 3.5 Spring Data

- **JPA + Hibernate**: blocking PostgreSQL (identity, order, payment, refund)
- **R2DBC**: reactive PostgreSQL (flashsale — yêu cầu throughput cực cao)
- **Mongo (blocking & reactive)**: product, notification, chat
- **Redis (blocking & reactive)**: cache, rate-limiter, Redis stream

### 3.6 Flyway

Database migration cho mọi service dùng PostgreSQL (`identity`, `order`, `payment`, `refund`, `flashsale`). File SQL nằm trong `src/main/resources/db/migration`.

---

## 4. Backend — Theo từng service

| Service | Port | DB | Pattern | Đặc thù công nghệ |
|---|---|---|---|---|
| **discovery-service** | 8761 | — | Eureka Server | Spring Cloud Netflix |
| **api-gateway** | 8080 | Redis (rate-limit) | WebFlux Gateway | Reactive, JWT validate, route theo Eureka |
| **identity-service** | 8081 | PostgreSQL + Redis | Layered (CRUD) | Spring Security, JWT issuance, Flyway |
| **product-service** | 8082 | MongoDB + Redis + MinIO | Layered | Upload ảnh MinIO, cache Redis |
| **order-service** | 8083 | PostgreSQL (JPA) + Kafka | **Axon CQRS** | Aggregate, Saga điều phối Payment |
| **payment-service** | 8084 | PostgreSQL (JPA) + Kafka | **Axon CQRS** | Stripe SDK, webhook handler |
| **refund-service** | 8085 | PostgreSQL (JPA) + Kafka | **Axon CQRS** | Stripe refund, Saga rollback |
| **flashsale-service** | 8086 | PostgreSQL (R2DBC) + Redis (reactive) | **Axon + WebFlux** | High-throughput reactive, atomic decrement Redis |
| **search-service** | 8087 | Elasticsearch | CQRS read-side | Consume Kafka event để index sản phẩm |
| **notification-service** | 8092 | MongoDB (reactive) + Redis | WebFlux | SSE push, consume sự kiện đơn hàng |
| **chat-service** | 8093 | MongoDB (reactive) | WebFlux + **Spring AI** | LLM tool calling, SSE streaming chat |
| **common-lib** | — | — | Shared library | DTOs, exceptions, JWT util, Kafka topic constants |
| **dev-data-runner** | — | — | Helper | Seed dữ liệu dev |

---

## 5. Frontend

3 ứng dụng riêng biệt trong `frontend/apps/`: **customer**, **seller**, **admin**.

### 5.1 Stack chung

| Công nghệ | Version | Mục đích |
|---|---|---|
| **React** | 19.0 | UI library |
| **TypeScript** | 5.6 | Type safety |
| **Vite** | 6.0 | Dev server + bundler (HMR cực nhanh, ESM native) |
| **React Router** | 6.26 | Client-side routing |
| **TanStack Query (React Query)** | 5.62 | Server state, cache, retry, optimistic update |
| **Zustand** | 5.0 | Client state (auth, cart) — siêu nhẹ thay Redux |
| **Axios** | 1.7 | HTTP client với interceptor |
| **Tailwind CSS** | 3.4 | Utility-first styling |
| **js-cookie** | 3.0 | Lưu access/refresh token |

### 5.2 Riêng customer + seller

- **`@stripe/stripe-js` 5.5** + **`@stripe/react-stripe-js` 3.2** — Stripe Elements để nhập thẻ.

### 5.3 Vì sao không Next.js?

Dự án là 3 SPA độc lập (B2C / B2B / Admin). Không cần SSR/SEO sâu → Vite + React SPA nhẹ và build nhanh hơn Next.js.

---

## 6. Cơ sở dữ liệu & Lưu trữ

| DB | Version | Service sử dụng | Lý do chọn |
|---|---|---|---|
| **PostgreSQL** | 15.4-alpine | identity, order, payment, refund, flashsale | OLTP ACID, transactional, JPA/R2DBC ổn định |
| **MongoDB** | 6.0.8 | product, notification, chat | Schema linh hoạt (sản phẩm có attribute động), document model phù hợp chat history |
| **Redis** | 7.2.1-alpine | gateway, identity, product, flashsale, notification | Cache, rate-limit, atomic counter (flash-sale inventory), pub/sub |
| **Elasticsearch** | 8.10.2 | search | Full-text search, fuzzy, aggregation |
| **MinIO** | latest | product | S3-compatible object storage cho ảnh sản phẩm |
| **Axon Server** | latest | order, payment, refund, flashsale | Event store + command/query bus |

---

## 7. Messaging & Event Backbone

### 7.1 Apache Kafka (Confluent 7.4.0)

- Broker port `9092` (internal), `9094` (external)
- Zookeeper port `2181` (legacy mode)
- Mọi service đều có `spring-kafka` dependency
- Pattern dùng: **Event Notification** + **Event-Carried State Transfer** (search-service index từ event)
- Topic management: container `kafka-init` chạy script tạo topic khi compose up
- Catalog topic: xem `documents/messaging/KAFKA_CATALOG.md`

### 7.2 Axon Server

- Event store cho các CQRS aggregate
- Command/Query bus (gRPC)
- Có UI quản trị ở port `8024`

### 7.3 Khi nào dùng Kafka vs Axon?

| Use case | Bus dùng |
|---|---|
| Domain event nội bộ aggregate, replay event | **Axon** |
| Cross-service integration event (Order created → Search index, → Notification) | **Kafka** |
| Saga điều phối nhiều service | **Axon Saga** publish event ra Kafka cho service ngoài |

---

## 8. Service Discovery, Gateway & Cấu hình

- **Discovery**: `discovery-service` (Eureka, port 8761) — mọi microservice tự đăng ký.
- **API Gateway**: `api-gateway` (Spring Cloud Gateway WebFlux, port 8080) — route theo `serviceId` (Eureka), filter JWT, rate-limit qua Redis.
- **Cấu hình**: file `application.yaml` mỗi service + biến môi trường (qua `.env` và docker-compose).

---

## 9. Bảo mật & Identity

| Hạng mục | Công nghệ |
|---|---|
| Auth framework | **Spring Security 6** |
| Token | **JWT** (HS256), issue bởi `identity-service`, validate ở gateway |
| Password | BCrypt (Spring Security) |
| Refresh token | Lưu Redis với TTL |
| CORS | Cấu hình ở API Gateway |
| Role | USER / SELLER / ADMIN |

---

## 10. AI / LLM

### 10.1 Spring AI 2.0.0-M6 (chat-service)

- Starter: `spring-ai-starter-model-openai` — chuẩn OpenAI-compatible nên có thể swap sang **DeepSeek**, **Groq**, **Together AI**, etc. bằng cách override `SPRING_AI_OPENAI_BASE_URL`.
- Tính năng dùng trong `ChatService`:
  - **Tool calling** (`@Tool`): `OrderQueryTool`, `ProductSearchTool`, `SystemActionTool` — LLM tự quyết định gọi tool nào.
  - **Human-in-the-loop confirmation**: lưu `PendingConfirmation` trước khi thực thi action có ảnh hưởng (hủy đơn, refund).
  - **Streaming SSE**: trả lời từng token qua `Flux<ServerSentEvent<String>>`.
- Lưu trữ session/message: MongoDB reactive.

### 10.2 Cấu hình env (xem `.env`)

```
SPRING_AI_OPENAI_API_KEY=<key>
SPRING_AI_OPENAI_BASE_URL=https://api.deepseek.com   # hoặc https://api.openai.com
SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL=deepseek-chat    # hoặc gpt-4o-mini
```

---

## 11. Thanh toán & Tích hợp bên thứ ba

| Tích hợp | Thư viện | Service |
|---|---|---|
| **Stripe** | `stripe-java` (SDK chính thức) + Stripe Elements (frontend) | payment-service, refund-service |
| **Webhook** | Stripe webhook → payment-service xử lý sự kiện `payment_intent.succeeded`, `charge.refunded`, etc. | payment-service |

---

## 12. DevOps / Hạ tầng vận hành

### 12.1 Docker Compose stacks

| File | Mục đích |
|---|---|
| `docker-compose-infrastructure.yml` | Postgres, Mongo, Redis, Elasticsearch, Kafka, Zookeeper, MinIO, Axon Server |
| `docker-compose-backend.yml` | 12 microservices Java |
| `docker-compose.yml` | All-in-one (infra + backend) |
| `docker-compose.dev.yml` | Overlay dev |
| `docker-compose.prod-pulled.yml` | Production (image từ registry) |
| `frontend/docker compose.yml` | 3 frontend apps |

### 12.2 Build & deploy

- **Dockerfile.dev** mỗi service (multi-stage build với Maven + JRE)
- **PowerShell script** `flashsale-build.ps1` để build hàng loạt
- **Nginx** reverse proxy cho frontend (folder `nginx/`)

### 12.3 GitHub Actions

Có workflow `.github/workflows/copilot-setup-steps.yml` để setup môi trường cho Copilot/CI.

---

## 13. Quan sát & Giám sát

| Hạng mục | Công cụ |
|---|---|
| Health check | `spring-boot-starter-actuator` → `/actuator/health` |
| Metrics | **Micrometer** + `micrometer-registry-prometheus` (product-service đã có, mở rộng cho service khác) |
| Logging | Slf4j + Logback (mặc định Spring Boot) |
| Tracing | _Chưa cấu hình (gợi ý thêm OpenTelemetry / Zipkin trong tương lai)_ |

---

## 14. Phụ lục: Phiên bản chốt

| Component | Version |
|---|---|
| Java | 25 |
| Spring Boot | 4.0.4 |
| Spring Cloud | 2025.1.1 |
| Axon Framework | 4.13.0 |
| Spring AI | 2.0.0-M6 |
| Lombok | 1.18.40 |
| PostgreSQL | 15.4-alpine |
| MongoDB | 6.0.8 |
| Redis | 7.2.1-alpine |
| Elasticsearch | 8.10.2 |
| Kafka (Confluent) | 7.4.0 |
| Node (frontend) | 22 (types) |
| React | 19.0.0 |
| TypeScript | 5.6.2 |
| Vite | 6.0.0 |
| Tailwind CSS | 3.4.1 |
| TanStack Query | 5.62 |
| Zustand | 5.0.2 |
| Axios | 1.7.9 |
| Stripe Java SDK | mới nhất (managed by Spring Boot BOM) |
| Stripe JS | 5.5.0 |

---

## Tài liệu liên quan

- `README.md` — quick start
- `documents/PROJECT_OVERVIEW.md` — kiến trúc tổng quan
- `documents/ERD_DIAGRAMS.md` — sơ đồ ERD
- `documents/messaging/KAFKA_CATALOG.md` — danh mục Kafka topic
- `documents/UC_FULL_SYSTEM.md` — use case toàn hệ thống
