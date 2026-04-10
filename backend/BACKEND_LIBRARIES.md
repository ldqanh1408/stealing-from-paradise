# Backend Libraries Documentation

Tài liệu này trình bày các thư viện (dependencies) mà mỗi Microservice trong hệ thống Flash Sale sử dụng.

**Tech Stack:**
- **Java Version:** 25
- **Spring Boot:** 4.0.4
- **Spring Cloud:** 2025.1.1
- **Axon Framework:** 4.13.0
- **gRPC:** 1.70.0
- **Netty:** 4.1.112.Final
- **Lombok:** 1.18.40

---

## 📋 Danh Sách Chi Tiết Các Services

### 1. **Discovery Service** (Service Registry)
**Vai trò:** Trung tâm đăng ký/khám phá các Microservices (Eureka Server)

| Thư viện | Phiên bản | Mục đích |
|---------|----------|---------|
| `spring-cloud-starter-netflix-eureka-server` | Cloud | Máy chủ Eureka để Service Discovery |
| `spring-boot-starter-actuator` | Spring Boot | Giám sát health check của Eureka |

---

### 2. **API Gateway** (Reactive Model - WebFlux)
**Vai trò:** Cổng vào duy nhất cho tất cả request từ Frontend

| Thư viện | Phiên bản | Mục đích |
|---------|----------|---------|
| `spring-cloud-starter-gateway-server-webflux` | Cloud | Routing request tới các microservice theo pattern |
| `spring-cloud-starter-netflix-eureka-client` | Cloud | Client Eureka để tìm địa chỉ các service |
| `spring-boot-starter-data-redis-reactive` | Spring Boot | Redis Reactive cho Rate Limiting (chặn bot/spam) |
| `spring-boot-starter-actuator` | Spring Boot | Giám sát tình trạng gateway |
| `spring-boot-starter-security` | Spring Boot | JWT validation tại gateway |
| `reactor-test` | Project Reactor | Testing cho WebFlux |

---

### 3. **Cart Service** (Traditional Servlet Model)
**Vai trò:** Quản lý giỏ hàng của người dùng với cache Redis

| Thư viện | Phiên bản | Mục đích |
|---------|----------|---------|
| `spring-boot-starter-web` | Spring Boot | Web Controller & REST API |
| `spring-boot-starter-data-redis` | Spring Boot | Lưu trữ giỏ hàng trên Redis (tốc độ cao) |
| `spring-boot-starter-validation` | Spring Boot | Validate dữ liệu đầu vào (số lượng, SKU) |
| `grpc-client-spring-boot-starter` | Net.devh | gRPC client để call Identity/Product Service |
| `spring-cloud-starter-netflix-eureka-client` | Cloud | Đăng ký với Eureka |
| `spring-boot-starter-actuator` | Spring Boot | Giám sát hiệu năng |
| `micrometer-registry-prometheus` | Micrometer | Metrics export cho Prometheus |
| `lombok` | Lombok | Annotation processor (provided scope) |

---

### 4. **Flash Sale Service** (Core Business Logic)
**Vai trò:** Xử lý logic Flash Sale chính, Lua Script Redis

| Thư viện | Phiên bản | Mục đích |
|---------|----------|---------|
| `spring-boot-starter-web` | Spring Boot | Web Controller & REST API |
| `spring-boot-starter-data-redis-reactive` | Spring Boot | Redis Reactive để chạy Lua Script |
| `spring-kafka` | Spring Kafka | Đẩy đơn hàng sang Order Service |
| `grpc-client-spring-boot-starter` | Net.devh | gRPC client để check Voucher ở Promotion Service |
| `spring-cloud-starter-netflix-eureka-client` | Cloud | Đăng ký với Eureka |

---

### 5. **Search Service** (Elasticsearch Integration)
**Vai trò:** Full-text search sản phẩm bằng Elasticsearch

| Thư viện | Phiên bản | Mục đích |
|---------|----------|---------|
| `spring-boot-starter-web` | Spring Boot | Web Controller & REST API |
| `spring-boot-starter-data-elasticsearch` | Spring Boot | Elasticsearch repository & queries |
| `spring-kafka` | Spring Kafka | Consumer để nghe sự kiện từ Product Service |
| `spring-cloud-starter-netflix-eureka-client` | Cloud | Đăng ký với Eureka |

---

### 6. **Worker Service** (Async Jobs & Cronjobs)
**Vai trò:** Cronjobs, retry DLQ, hủy đơn quá hạn

| Thư viện | Phiên bản | Mục đích |
|---------|----------|---------|
| `spring-boot-starter-web` | Spring Boot | Web Controller (nếu cần) |
| `spring-boot-starter-data-jpa` | Spring Boot | JPA repository cho PostgreSQL |
| `postgresql` | Postgres JDBC | Driver PostgreSQL |
| `spring-boot-starter-quartz` | Spring Boot | Scheduling & cronjobs |
| `shedlock-spring` | ShedLock 5.13.0 | Khóa phân tán để tránh 2 instance chạy trùng job |
| `shedlock-provider-jdbc-template` | ShedLock 5.13.0 | Provider JDBC cho ShedLock |
| `spring-cloud-starter-netflix-eureka-client` | Cloud | Đăng ký với Eureka |

---

### 7. **Notification Service** (Reactive - WebFlux)
**Vai trò:** Push thông báo real-time cho người dùng

| Thư viện | Phiên bản | Mục đích |
|---------|----------|---------|
| `spring-boot-starter-webflux` | Spring Boot | Reactive WebFlux cho real-time notifications |
| `spring-boot-starter-data-mongodb-reactive` | Spring Boot | MongoDB Reactive để lưu notification |
| `spring-boot-starter-data-redis-reactive` | Spring Boot | Redis Reactive cho session/cache |
| `spring-kafka` | Spring Kafka | Consumer để hứng event từ Order/Payment |
| `spring-cloud-starter-netflix-eureka-client` | Cloud | Đăng ký với Eureka |
| `spring-boot-starter-actuator` | Spring Boot | Giám sát |
| `reactor-test` | Project Reactor | Testing cho WebFlux |

---

## 🔐 Identity Service (Auth & User Management)

### **identity-api** (REST Layer)
**Vai trò:** Mở cổng REST để client login/register

| Thư viện | Phiên bản | Mục đích |
|---------|----------|---------|
| `spring-boot-starter-web` | Spring Boot | REST API |
| `axon-spring-boot-starter` | Axon | Event Sourcing & CQRS |
| `spring-boot-starter-data-jpa` | Spring Boot | JPA repository |
| `postgresql` | Postgres JDBC | Driver PostgreSQL |
| `spring-cloud-starter-netflix-eureka-client` | Cloud | Service Discovery |

### **identity-domain** (Event Sourcing Layer)
**Vai trò:** Xử lý Event Sourcing cho User aggregate

| Thư viện | Phiên bản | Mục đích |
|---------|----------|---------|
| `identity-api` | 1.0.0-SNAPSHOT | Dependency từ API layer |
| `spring-boot-starter-web` | Spring Boot | Web support |
| `axon-spring-boot-starter` | Axon | Event Sourcing & CQRS |
| `spring-boot-starter-data-jpa` | Spring Boot | JPA repository |
| `postgresql` | Postgres JDBC | Driver PostgreSQL |
| `grpc-server-spring-boot-starter` | Net.devh | gRPC server (cung cấp service) |
| `grpc-client-spring-boot-starter` | Net.devh | gRPC client (gọi service khác) |
| `spring-cloud-starter-netflix-eureka-client` | Cloud | Service Discovery |

---

## 📦 Product Service (Catalog Management)

### **product-api** (REST Layer)
**Vai trò:** Mở cổng REST để Seller đăng sản phẩm

| Thư viện | Phiên bản | Mục đích |
|---------|----------|---------|
| `spring-boot-starter-web` | Spring Boot | REST API |
| `axon-spring-boot-starter` | Axon | Event Sourcing & CQRS |
| `spring-boot-starter-data-mongodb` | Spring Boot | MongoDB repository |
| `spring-cloud-starter-netflix-eureka-client` | Cloud | Service Discovery |

### **product-domain** (Event Sourcing Layer)
**Vai trò:** Xử lý Event Sourcing cho Product aggregate

| Thư viện | Phiên bản | Mục đích |
|---------|----------|---------|
| `product-api` | 1.0.0-SNAPSHOT | Dependency từ API layer |
| `axon-spring-boot-starter` | Axon | Event Sourcing & CQRS |
| `spring-boot-starter-data-mongodb` | Spring Boot | MongoDB lưu catalog |
| `minio` | 8.5.7 | Object Storage (ảnh sản phẩm) |
| `spring-kafka` | Spring Kafka | Kafka producer để đồng bộ Search |
| `spring-cloud-starter-netflix-eureka-client` | Cloud | Service Discovery |

---

## 📋 Order Service (Order Management)

### **order-api** (REST Layer)
**Vai trò:** Mở cổng HTTP nhận request tạo đơn từ Gateway

| Thư viện | Phiên bản | Mục đích |
|---------|----------|---------|
| `spring-boot-starter-web` | Spring Boot | REST API |
| `axon-spring-boot-starter` | Axon | Event Sourcing & CQRS |
| `spring-boot-starter-data-jpa` | Spring Boot | JPA repository để đọc Order View |
| `postgresql` | Postgres JDBC | Driver PostgreSQL |
| `spring-cloud-starter-netflix-eureka-client` | Cloud | Service Discovery |

### **order-domain** (Saga & Event Store)
**Vai trò:** Xử lý Saga orchestration cho quy trình đặt hàng

| Thư viện | Phiên bản | Mục đích |
|---------|----------|---------|
| `order-api` | 1.0.0-SNAPSHOT | Dependency từ API layer |
| `spring-boot-starter-web` | Spring Boot | Web support |
| `axon-spring-boot-starter` | Axon | Event Sourcing & Saga |
| `spring-kafka` | Spring Kafka | Consumer hứng đơn từ FlashSale Service |
| `spring-boot-starter-data-jpa` | Spring Boot | JPA repository |
| `postgresql` | Postgres JDBC | Driver PostgreSQL |
| `grpc-server-spring-boot-starter` | Net.devh | gRPC server (cung cấp dịch vụ thanh toán) |
| `grpc-client-spring-boot-starter` | Net.devh | gRPC client (gọi sang Payment) |
| `spring-cloud-starter-netflix-eureka-client` | Cloud | Service Discovery |

---

## 💳 Payment Service (Payment Processing)

### **payment-api** (REST Layer)
**Vai trò:** Mở cổng REST để xử lý thanh toán

| Thư viện | Phiên bản | Mục đích |
|---------|----------|---------|
| `spring-boot-starter-web` | Spring Boot | REST API |
| `axon-spring-boot-starter` | Axon | Event Sourcing & CQRS |
| `spring-boot-starter-data-jpa` | Spring Boot | JPA repository |
| `postgresql` | Postgres JDBC | Driver PostgreSQL |
| `spring-cloud-starter-netflix-eureka-client` | Cloud | Service Discovery |

### **payment-domain** (Event Store)
**Vai trò:** Xử lý logic thanh toán và lưu trữ sự kiện

| Thư viện | Phiên bản | Mục đích |
|---------|----------|---------|
| `payment-api` | 1.0.0-SNAPSHOT | Dependency từ API layer |
| `axon-spring-boot-starter` | Axon | Event Sourcing & CQRS |
| `spring-boot-starter-data-jpa` | Spring Boot | JPA repository |
| `postgresql` | Postgres JDBC | Driver PostgreSQL |
| `grpc-server-spring-boot-starter` | Net.devh | gRPC server (cung cấp dịch vụ thanh toán) |
| `grpc-client-spring-boot-starter` | Net.devh | gRPC client (gọi service khác) |
| `spring-cloud-starter-netflix-eureka-client` | Cloud | Service Discovery |

---

## 🔄 Communication Protocols

### **gRPC (gRPC + Protobuf)**
- **Sử dụng bởi:** Identity Domain, Product Domain, Order Domain, Payment Domain, Cart Service, FlashSale Service
- **Mục đích:** Inter-service communication (high-performance, low-latency)
- **Phiên bản:** 
  - gRPC: 1.70.0
  - Protobuf: 4.33.1
  - Net.devh Spring Boot Starter: 3.1.0.RELEASE

### **Kafka (Message Queue)**
- **Sử dụng bởi:** FlashSale Service, Order Service, Notification Service, Search Service, Product Service
- **Mục đích:** Asynchronous event streaming
- **Thư viện:** `spring-kafka`

### **REST API (HTTP/WebFlux)**
- **API Gateway:** Spring Cloud Gateway (WebFlux)
- **Services:** Spring Web (Servlet) hoặc Spring WebFlux (Reactive)

---

## 💾 Database & Cache

### **PostgreSQL**
- **Services sử dụng:** 
  - Identity Service (API & Domain)
  - Order Service (API & Domain)
  - Payment Service (API & Domain)
  - Worker Service
- **Thư viện:** `postgresql` JDBC Driver + JPA
- **Mục đích:** Lưu trữ chính cho dữ liệu Order, Payment, User

### **MongoDB**
- **Services sử dụng:**
  - Product Service (API & Domain)
  - Notification Service
- **Thư viện:** `spring-boot-starter-data-mongodb` (Sync) & `spring-boot-starter-data-mongodb-reactive`
- **Mục đích:** Lưu Product Catalog, Notification History

### **Redis**
- **Services sử dụng:**
  - API Gateway (Reactive)
  - Cart Service
  - FlashSale Service (Reactive)
  - Notification Service (Reactive)
- **Thư viện:** `spring-boot-starter-data-redis` & `spring-boot-starter-data-redis-reactive`
- **Mục đích:** 
  - Rate Limiting (API Gateway)
  - Session/Cart caching (Cart Service)
  - Lua Script execution (FlashSale Service)
  - Real-time cache (Notification Service)

### **Elasticsearch**
- **Services sử dụng:** Search Service
- **Thư viện:** `spring-boot-starter-data-elasticsearch`
- **Mục đích:** Full-text search sản phẩm

### **MinIO (Object Storage)**
- **Services sử dụng:** Product Service (Domain)
- **Thư viện:** `minio` 8.5.7
- **Mục đích:** Lưu trữ ảnh sản phẩm

---

## 🏗️ Architecture Patterns

### **Event Sourcing + CQRS**
- **Framework:** Axon 4.13.0
- **Services:** Identity, Product, Order, Payment
- **Lợi ích:** 
  - Audit trail đầy đủ
  - Temporal queries
  - Event replay

### **Saga Pattern**
- **Framework:** Axon
- **Services:** Order Domain (Orchestrate Order → Payment)
- **Mục đích:** Distributed transaction management

### **Service Discovery**
- **Framework:** Spring Cloud Netflix Eureka
- **Mọi services** đều là Eureka Client
- **Discovery Service** là Eureka Server
- **Mục đích:** Dynamic service registration & discovery

### **Reactive Programming**
- **Frameworks:** Project Reactor, Spring WebFlux
- **Services:** 
  - API Gateway
  - Notification Service
- **Mục đích:** Handle high concurrency với ít resources

### **Scheduled Tasks**
- **Framework:** Quartz Scheduler
- **Services:** Worker Service
- **Add-on:** ShedLock (Distributed Lock) để tránh duplicate execution
- **Mục đích:** Cronjobs, retry logic, order cancellation

---

## 📊 Observability Stack

### **Health Checks & Metrics**
- **Thư viện:** `spring-boot-starter-actuator`
- **Sử dụng bởi:** Tất cả services
- **Endpoints:** `/actuator/health`, `/actuator/metrics`

### **Prometheus Metrics**
- **Thư viện:** `micrometer-registry-prometheus`
- **Sử dụng bởi:** Cart Service
- **Mục đích:** Export metrics cho Prometheus scraping

### **Testing**
- **Thư viện:** 
  - `spring-boot-starter-test` (Parent POM)
  - `reactor-test` (cho WebFlux services)
- **Frameworks:** JUnit 5, Mockito, Spring Test

---

## 📝 Dependency Management (Parent POM)

**File:** `backend/pom.xml`

**BOM (Bill of Materials) được import:**
1. `spring-boot-dependencies` (4.0.4)
2. `spring-cloud-dependencies` (2025.1.1)
3. `grpc-bom` (1.70.0)
4. `protobuf-bom` (4.33.1)
5. `netty-bom` (4.1.112.Final)
6. `axon-bom` (4.13.0)

**Build Plugins:**
- Maven Compiler Plugin (Java 25)
- Spring Boot Maven Plugin (4.0.4)
- Lombok Annotation Processor (1.18.40)

---

## 🚀 Key Technologies Summary

| Teknologi | Phiên bản | Mục đích |
|-----------|----------|---------|
| Java | 25 | Modern features & Virtual Threads |
| Spring Boot | 4.0.4 | Core framework |
| Spring Cloud | 2025.1.1 | Microservices infrastructure |
| Axon Framework | 4.13.0 | Event Sourcing & CQRS |
| gRPC | 1.70.0 | Inter-service communication |
| Kafka | (Spring Kafka) | Message streaming |
| PostgreSQL | (JDBC) | Primary relational DB |
| MongoDB | (Spring Data) | Document DB |
| Redis | (Spring Data) | Cache & Rate Limiting |
| Elasticsearch | (Spring Data) | Full-text search |
| MinIO | 8.5.7 | Object storage |
| Netty | 4.1.112 | Async I/O |
| Project Reactor | (Spring WebFlux) | Reactive programming |
| Quartz | (Spring Starter) | Job scheduling |
| ShedLock | 5.13.0 | Distributed locking |

---

## 📚 Thư viện Hỗ trợ

- **Lombok:** 1.18.40 (Reduce boilerplate code)
- **Spring Security:** (API Gateway & Services)
- **Spring Validation:** (Input validation)

---

## 🔗 Mối Quan Hệ Services

```
Frontend (React/Vue/TS)
    ↓
    └─→ API Gateway (WebFlux + Rate Limit)
            ├─→ gRPC Call → Identity Domain (Auth)
            ├─→ gRPC Call → Product Domain (Catalog)
            ├─→ REST Call → Cart Service
            ├─→ REST Call → FlashSale Service
            │       └─→ Kafka → Order Domain
            ├─→ REST Call → Search Service
            │       ← Kafka Event ← Product Domain
            ├─→ gRPC Call → Order Domain
            │       ├─→ gRPC Call → Payment Domain
            │       └─→ Kafka Produce
            └─→ WebSocket/Server-Sent Events → Notification Service
                    ← Kafka Event ← Order Domain
```

---

## 📖 Notes

- **Virtual Threads (Java 25):** Hỗ trợ từ Spring Boot 4.0.4, giúp handle hàng ngàn concurrent requests
- **Reactive Stack:** API Gateway & Notification Service sử dụng mô hình Reactive (Project Reactor)
- **Traditional Stack:** Hầu hết services sử dụng Spring Web (Servlet) vì tính đơn giản
- **Distributed Tracing:** Có thể tích hợp Spring Cloud Sleuth + Jaeger/Zipkin (không được cấu hình trong pom hiện tại)
- **Service Discovery:** Eureka được sử dụng cho service registration & discovery


