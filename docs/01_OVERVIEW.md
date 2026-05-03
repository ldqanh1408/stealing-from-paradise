# Flash Sale E-Commerce Platform - Project Overview

**Project**: stealing-from-paradise (Flash Sale E-Commerce Platform)
**Date**: 2026-05-01  
**Status**: Production-Ready  

---

## 📋 Table of Contents

1. [Project Overview](#-project-overview)
2. [Tech Stack](#-tech-stack)
3. [Backend Architecture](#-backend-architecture)
4. [Frontend Architecture](#-frontend-architecture)
5. [Project Structure](#-project-structure)
6. [Setup & Installation](#-setup--installation)
7. [Running Services](#-running-services)
8. [Development Workflow](#-development-workflow)
9. [Deployment](#-deployment)

---

## 🎯 Project Overview

**Flash Sale E-Commerce Platform** is a high-scale e-commerce system supporting flash sales, multi-seller marketplace, and admin management. The architecture follows **microservices pattern** with **event-driven architecture** using Axon Framework for critical services.

### Key Features

- 🛒 **Customer**: Browse products, add to cart, checkout with Stripe
- 🏪 **Seller**: Manage products, view orders, Stripe onboarding
- 🔧 **Admin**: User management, product moderation, flash sale configuration
- 📊 **Event-Driven**: Order, payment, and flash sale processing via Axon Framework
- 🔍 **Search**: Elasticsearch for fast product search
- 💬 **Notifications**: Email/SMS via notification service
- 🎯 **Flash Sales**: Time-limited promotional sales

### User Roles

| Role | Portal | Port | Purpose |
|------|--------|------|---------|
| **Customer** | Customer App | 3000 | Shopping, checkout, order tracking |
| **Seller** | Seller App | 3001 | Shop management, product listing |
| **Admin** | Admin App | 3002 | Platform management, moderation |

---

## 🛠️ Tech Stack

### Backend

| Component | Version | Purpose |
|-----------|---------|---------|
| **Java** | 25 (LTS) | Programming language |
| **Spring Boot** | 4.0.4 | Web framework |
| **Spring Cloud** | 2025.1.1 | Microservices |
| **Axon Framework** | 4.13.0 | Event sourcing & CQRS |
| **PostgreSQL** | 15.4 | SQL database |
| **MongoDB** | 6.0 | NoSQL database |
| **Redis** | 7.0 | Cache |
| **Kafka** | 7.4.0 | Message queue |
| **Elasticsearch** | 8.10 | Search engine |
| **MinIO** | Latest | Object storage |

### Frontend

| Component | Version | Purpose |
|-----------|---------|---------|
| **React** | 19 | UI library |
| **Vite** | 6.0 | Build tool |
| **TypeScript** | Latest | Type safety |
| **Zustand** | Latest | State management |
| **React Query** | Latest | Server state |
| **React Router** | Latest | Routing |
| **Tailwind CSS** | Latest | Styling |
| **Stripe** | Latest | Payments |

### DevOps

| Tool | Purpose |
|------|---------|
| **Docker** | Containerization |
| **Docker Compose** | Orchestration |
| **Nginx** | Reverse proxy |
| **Eclipse Temurin JRE 25** | Java runtime |

---

## 🏗️ Backend Architecture

### Services Overview

```
┌────────────────────────────────────────────────────────────────────────────────┐
│                      API GATEWAY (8080)                                       │
│                   Spring Cloud Gateway                                          │
└─────────────────────────┬──────────────────────────────────────────────────────┘
                          │
     ┌────────────────────┴──────────────────────────────────────────────────┐
     │              SERVICE DISCOVERY (8761 - Eureka)                          │
     └────────────────────┬───────────────────────────────────────────────────┘
                          │
    ┌─────────┬─────────┬─────────┬─────────┬─────────┬─────────┬──────────┐
    ▼         ▼         ▼         ▼         ▼         ▼         ▼          ▼
  Identity  Payment   Order    Flashsale Product  Search  Notification Worker
  (8081)   (8082)   (8083)    (8085)   (8090)  (8091)   (8092)    (8086)
    │         │         │         │         │         │         │          │
  PostgreSQL PgSQL+Axon PgSQL+Axon PgSQL+Axon MongoDB  Elastic   MongoDB    PostgreSQL
                   OrderSaga   OrderSaga  +Redis
                   PaymentSaga FlashSaleSaga
```

### Backend Services (11 microservices + common-lib)

#### **Infrastructure Services**

1. **discovery-service** (Port 8761)
   - Service registry (Eureka)
   - Service discovery
   - Health checks

2. **api-gateway** (Port 8080)
   - Request routing
   - Load balancing
   - Authentication validation

#### **Axon Framework Services (Event-Driven)**

3. **order-service** (Port 8083)
   - Order creation and management
   - Axon Sagas: OrderProcessingSaga + ParentOrderPaymentSaga
   - Handles checkout, multi-vendor order split, shipping, RTS

4. **payment-service** (Port 8082)
   - Stripe Connect payment processing
   - Multi-vendor payment split with automatic transfers
   - Refund management (manual + auto RTS)
   - Stripe webhook handling

5. **flashsale-service** (Port 8085)
   - Flash sale session management
   - Redis Lua scripts for 50k+ req/s concurrency
   - Anti-oversell with atomic operations
   - Reminder notifications

6. **worker-service** (Port 8086)
   - Outbox pattern: reliable Kafka event publishing
   - Failed event retry management (DLQ)
   - Background job infrastructure (Axon DeadlineManager for timeouts)

#### **Traditional Database Services**

7. **identity-service** (Port 8081)
   - User authentication & authorization
   - JWT token management (RS256)
   - User profiles, addresses
   - Loyalty points management (merged from Loyalty Service)
   - Trust Score and appeals
   - Database: PostgreSQL + JPA

8. **product-service** (Port 8090)
   - Product catalog (MongoDB)
   - SKU variants, inventory management
   - Shopping cart (merged from Cart Service)
   - Product images (MinIO)
   - Reviews and ratings

9. **search-service** (Port 8091)
   - Product search
   - Full-text search
   - Elasticsearch integration

10. **notification-service** (Port 8092)
    - SSE real-time notifications
    - Order/payment alerts
    - Database: MongoDB

#### **Shared Library**

11. **common-lib**
    - Shared DTOs
    - Common exceptions
    - Utility functions
    - Shared entities

---

## 📁 Backend Directory Structure

### Axon Framework Service (Example: order-service)

Services using **Event Sourcing** and **CQRS** pattern:

```
order-service/
├── src/main/java/com/flashsale/order/
│   │
│   ├── domain/                        ← Business Logic (Axon)
│   │   ├── OrderAggregate.java       (@Aggregate - Main entity)
│   │   ├── OrderId.java              (Value object)
│   │   │
│   │   ├── command/                  (User actions - @CommandHandler)
│   │   │   ├── CreateOrderCommand.java
│   │   │   ├── PayOrderCommand.java
│   │   │   ├── ShipOrderCommand.java
│   │   │   └── CancelOrderCommand.java
│   │   │
│   │   ├── event/                    (Domain events)
│   │   │   ├── OrderCreatedEvent.java
│   │   │   ├── OrderPaidEvent.java
│   │   │   ├── OrderShippedEvent.java
│   │   │   └── OrderCancelledEvent.java
│   │   │
│   │   └── OrderSaga.java            (@Saga - Distributed transactions)
│   │
│   ├── projection/                   (CQRS Read Model)
│   │   ├── OrderProjection.java      (@EventHandler - Listen to events)
│   │   ├── OrderItemProjection.java
│   │   └── OrderViewRepository.java  (Spring Data JPA - read model)
│   │
│   ├── query/                        (Query handlers)
│   │   ├── GetOrderByIdQuery.java
│   │   ├── GetUserOrdersQuery.java
│   │   └── OrderQueryHandler.java    (@QueryHandler)
│   │
│   ├── infrastructure/
│   │   ├── OrderRepository.java      (Repository for persistence)
│   │   ├── OrderEventStore.java      (Custom event store logic)
│   │   └── config/
│   │       └── AxonConfiguration.java (Axon Server config)
│   │
│   ├── api/
│   │   ├── OrderController.java      (REST endpoints)
│   │   ├── request/
│   │   │   ├── CreateOrderRequest.java
│   │   │   └── PayOrderRequest.java
│   │   └── response/
│   │       └── OrderResponse.java
│   │
│   ├── service/
│   │   └── OrderService.java         (Business logic)
│   │
│   └── OrderServiceApplication.java
│
├── src/main/resources/
│   ├── application.yml               (General config)
│   ├── application-prod.yml          (Production config)
│   └── schema.sql                    (Database schema)
│
├── pom.xml                           (Dependencies)
├── Dockerfile                        (Eclipse Temurin JRE 25)
└── HELP.md

### Axon Services List:
- order-service
- payment-service
- flashsale-service
- worker-service
```

### Traditional Database Service (Example: product-service)

Services using **traditional database approach**:

```
product-service/
├── src/main/java/com/flashsale/product/
│   │
│   ├── entity/
│   │   ├── Product.java              (@Entity - MongoDB)
│   │   ├── Category.java
│   │   └── ProductImage.java
│   │
│   ├── repository/
│   │   ├── ProductRepository.java    (Spring Data MongoDB)
│   │   └── CategoryRepository.java
│   │
│   ├── service/
│   │   ├── ProductService.java       (Business logic)
│   │   ├── ProductSearchService.java
│   │   └── CategoryService.java
│   │
│   ├── controller/
│   │   ├── ProductController.java
│   │   └── CategoryController.java
│   │
│   ├── dto/
│   │   ├── ProductDTO.java
│   │   └── ProductResponse.java
│   │
│   ├── exception/
│   │   └── ProductNotFoundException.java
│   │
│   └── ProductServiceApplication.java
│
├── src/main/resources/
│   ├── application.yml
│   └── application-prod.yml
│
├── pom.xml
├── Dockerfile
└── HELP.md

### Traditional Database Services:
- identity-service (PostgreSQL + JPA)
- product-service (MongoDB + Redis for Cart)
- search-service (Elasticsearch)
- notification-service (MongoDB)
- common-lib (Shared code)
```

### Axon Framework vs Traditional Structure

| Aspect | Axon Services | Traditional Services |
|--------|---------------|---------------------|
| **Command Handling** | `domain/command/` + `@CommandHandler` | N/A |
| **Events** | `domain/event/` + Event classes | N/A |
| **Saga** | `domain/OrderSaga.java` | N/A |
| **Projection** | `projection/` + `@EventHandler` | N/A |
| **Service Layer** | `service/` (facade only) | `service/` (main logic) |
| **Repository** | For read model (projections) | For persistence |
| **Event Store** | Axon Server (external) | N/A |
| **Data Flow** | Command → Event → Projection | Request → Service → DB |

---

## 🎨 Frontend Architecture

### Frontend Apps

```
frontend/
├── shared/                         (Code shared by all 3 apps)
│   ├── api/
│   │   └── auth.api.ts            (Authentication API)
│   ├── lib/
│   │   ├── axios.ts               (HTTP client with interceptors)
│   │   └── queryClient.ts         (React Query config)
│   ├── pages/
│   │   ├── LoginPage.tsx
│   │   └── RegisterPage.tsx
│   ├── store/
│   │   └── authStore.ts           (Zustand auth state)
│   └── types/
│       └── api.ts                 (TypeScript interfaces)
│
└── apps/
    ├── customer/                  (🛒 Customer App - Port 3000)
    │   ├── src/
    │   │   ├── pages/
    │   │   │   ├── ProductListPage.tsx
    │   │   │   ├── CartPage.tsx
    │   │   │   ├── CheckoutPage.tsx
    │   │   │   ├── CheckoutResultPage.tsx
    │   │   │   ├── FlashSalePage.tsx
    │   │   │   └── OrderHistoryPage.tsx
    │   │   ├── components/
    │   │   │   └── checkout/
    │   │   │       └── StripeCheckout.tsx
    │   │   ├── lib/
    │   │   │   └── stripe.ts      (Stripe integration)
    │   │   ├── App.tsx
    │   │   ├── main.tsx
    │   │   └── index.css
    │   ├── vite.config.ts          (Alias: @ = src, @shared = ../../shared)
    │   ├── package.json
    │   ├── Dockerfile
    │   └── nginx.conf
    │
    ├── seller/                    (🏪 Seller App - Port 3001)
    │   ├── src/
    │   │   ├── pages/
    │   │   │   ├── SellerDashboard.tsx
    │   │   │   ├── ProductManagementPage.tsx
    │   │   │   ├── SellerOrdersPage.tsx
    │   │   │   ├── OrdersPage.tsx
    │   │   │   ├── StripeOnboardingPage.tsx
    │   │   │   └── DashboardPage.tsx
    │   │   ├── App.tsx
    │   │   ├── main.tsx
    │   │   └── index.css
    │   ├── vite.config.ts
    │   ├── package.json
    │   ├── Dockerfile
    │   └── nginx.conf
    │
    └── admin/                     (🔧 Admin App - Port 3002)
        ├── src/
        │   ├── pages/
        │   │   ├── AdminDashboard.tsx
        │   │   ├── UserManagementPage.tsx
        │   │   ├── ProductModerationPage.tsx
        │   │   ├── RefundsPage.tsx
        │   │   ├── FlashSaleConfigPage.tsx
        │   │   └── TrustScorePage.tsx
        │   ├── App.tsx
        │   ├── main.tsx
        │   └── index.css
        ├── vite.config.ts
        ├── package.json
        ├── Dockerfile
        └── nginx.conf
```

### Frontend Data Flow

```
User Action
    ↓
Component (React)
    ↓
Custom Hook / Store (Zustand)
    ↓
API Call (Axios)
    ↓
API Interceptor (Add JWT token)
    ↓
Backend API Gateway (Port 8080)
    ↓
Microservice
    ↓
Response → React Query Cache → UI Update
```

---

## 📁 Project Structure (Root Level)

```
stealing-from-paradise/
├── backend/                       (Microservices)
│   ├── discovery-service/
│   ├── api-gateway/
│   ├── identity-service/
│   ├── product-service/
│   ├── order-service/
│   ├── payment-service/
│   ├── flashsale-service/
│   ├── search-service/
│   ├── notification-service/
│   ├── worker-service/
│   ├── common-lib/
│   ├── docker/                    (Init scripts)
│   │   ├── postgres/
│   │   ├── mongo/
│   │   └── axon/
│   ├── pom.xml                    (Parent POM)
│   └── docker-compose.yml
│
├── frontend/                      (React Vite Apps)
│   ├── shared/                    (Shared code)
│   ├── apps/
│   │   ├── customer/
│   │   ├── seller/
│   │   └── admin/
│   └── docker-compose.yml
│
├── docker-compose.yml             (Main - all services)
├── .env                           (Environment variables)
├── README.md
├── CLAUDE.md                      (Build & setup commands)
├── RUNNING.md                    (Detailed running guide)
└── docs/                        (Comprehensive documentation)
```

---

## 🚀 Setup & Installation

### Prerequisites

```bash
# System requirements
- Docker & Docker Compose
- Node.js 18+ (for local frontend development)
- Java 25 (for local backend development - optional)
- Maven 3.8+ (for backend - optional)
- Git

# Check installations
docker --version
docker-compose --version
node --version
java --version
mvn --version
```

### Clone Repository

```bash
git clone https://github.com/yourname/stealing-from-paradise.git
cd stealing-from-paradise
```

### Setup Environment

```bash
# Copy env template
cp .env.example .env

# Edit .env with your settings (passwords, API keys, etc.)
nano .env

# Key variables
POSTGRES_USER=flashsale
POSTGRES_PASSWORD=YourSecurePassword123!
MONGO_INITDB_ROOT_USERNAME=admin
MONGO_INITDB_ROOT_PASSWORD=YourSecurePassword123!
VITE_API_URL=http://localhost:8080
```

---

## 🏃 Running Services

### Option 1: Docker Compose (Recommended - All Services)

```bash
# Start all services (backend + frontend + databases)
docker-compose up -d

# Wait 3-5 minutes for all services to start
docker-compose ps

# View logs
docker-compose logs -f

# Stop all services
docker-compose down

# Stop and remove volumes (clean slate)
docker-compose down -v
```

### Option 2: Local Development (Individual Services)

#### Backend - Terminal 1 (Discovery Service)

```bash
cd backend

# Build all services
mvn clean install -DskipTests

# Terminal 1: Start Discovery Service (Eureka)
cd discovery-service
mvn spring-boot:run
# Accessible at: http://localhost:8761
```

#### Backend - Terminal 2 (API Gateway)

```bash
cd backend/api-gateway
mvn spring-boot:run
# Accessible at: http://localhost:8080
```

#### Backend - Terminal 3+ (Other Services)

```bash
# Identity Service
cd backend/identity-service
mvn spring-boot:run
# Port: 8085

# Product Service
cd backend/product-service
mvn spring-boot:run
# Port: 8086

# Order Service (Axon)
cd backend/order-service
mvn spring-boot:run
# Port: 8088
# Requires Axon Server running

# Continue for other services...
```

#### Frontend - Terminal (Customer App)

```bash
# Customer App
cd frontend/apps/customer
npm install
npm run dev
# Accessible at: http://localhost:3000

# In another terminal: Seller App
cd frontend/apps/seller
npm install
npm run dev
# Accessible at: http://localhost:3001

# In another terminal: Admin App
cd frontend/apps/admin
npm install
npm run dev
# Accessible at: http://localhost:3002
```

### Option 3: Docker Build (Production)

```bash
# Build all Docker images
docker-compose build --no-cache

# Build specific service
docker-compose build customer-app
docker-compose build api-gateway

# Run production
docker-compose -f docker-compose.yml up -d
```

---

## 🔧 Development Workflow

### Backend Development

#### 1. Create New Feature in Axon Service (order-service)

```bash
# Create command
src/main/java/com/flashsale/order/domain/command/
  └── UpdateOrderCommand.java

# Create event
src/main/java/com/flashsale/order/domain/event/
  └── OrderUpdatedEvent.java

# Update aggregate
src/main/java/com/flashsale/order/domain/
  └── OrderAggregate.java
     - Add @CommandHandler method for UpdateOrderCommand
     - Add @EventSourcingHandler for OrderUpdatedEvent

# Update projection
src/main/java/com/flashsale/order/projection/
  └── OrderProjection.java
     - Add @EventHandler for OrderUpdatedEvent

# Update API
src/main/java/com/flashsale/order/api/
  └── OrderController.java
     - Add new endpoint
```

#### 2. Test Locally

```bash
cd backend/order-service

# Run tests
mvn test

# Start service
mvn spring-boot:run
```

#### 3. Build & Push

```bash
# Build
mvn clean package -DskipTests

# Docker build
docker build -t order-service:v1.0.0 .

# Docker push (optional)
docker push your-registry/order-service:v1.0.0
```

### Frontend Development

#### 1. Create New Page (customer app)

```bash
# Create page
src/pages/NewPage.tsx
  - Export React component
  - Use hooks from @shared (useAuthStore, useQuery)

# Update routing
src/App.tsx
  - Import page with lazy()
  - Add Route in Routes

# API calls
import apiClient from '@shared/lib/axios'
apiClient.get('/endpoint').then(...)

# Or use React Query
import { useQuery } from '@tanstack/react-query'
const { data } = useQuery({
  queryKey: ['key'],
  queryFn: () => apiClient.get('/endpoint'),
})
```

#### 2. Test Locally

```bash
cd frontend/apps/customer

# Start dev server
npm run dev

# Open http://localhost:3000

# TypeScript check
npm run tsc

# Build for production
npm run build
```

#### 3. Build & Deploy

```bash
# Build
npm run build
# Output: dist/ folder

# Docker build
docker build -t customer-app:v1.0.0 .

# Serve with nginx (in Dockerfile)
```

---

## 📊 Database Initialization

### PostgreSQL (Identity, Order, Payment Services)

```bash
# Docker initialization
docker exec flashsale-postgres psql -U flashsale -d flashsale \
  -f /docker-entrypoint-initdb.d/01-init-db.sql

# Or manual
psql -h localhost -U flashsale -d flashsale < backend/docker/postgres/init/01-init-db.sql

# Connect and verify
psql -h localhost -U flashsale -d flashsale
# \dt - list tables
# \q - quit
```

### MongoDB (Product, Notification Services)

```bash
# Docker initialization
docker exec flashsale-mongo mongo -u admin -p password123 \
  --authenticationDatabase admin admin < /docker-entrypoint-initdb.d/01-init.js

# Or manual
mongorestore --archive < backup.archive

# Connect and verify
mongosh -u admin -p password123
# use flashsale
# db.collections()
# db.products.find()
```

### Redis (Cache)

```bash
# Redis is automatically initialized
# No schema needed, just key-value store

# Connect
redis-cli -h localhost -p 6379

# Verify
PING   # Should return PONG
```

### Elasticsearch (Search)

```bash
# Check cluster health
curl http://localhost:9200/_cluster/health

# Create index
curl -X PUT http://localhost:9200/products

# Verify
curl http://localhost:9200/_cat/indices
```

---

## 🔗 API Endpoints

### Base URL
```
http://localhost:8080/api/v1
```

### Authentication
```bash
# Login
POST /auth/login
{
  "username": "user@example.com",
  "password": "password123"
}

# Response
{
  "data": {
    "accessToken": "eyJhbGc...",
    "userId": "user-id",
    "role": "CUSTOMER"
  }
}

# Use token in headers
Authorization: Bearer eyJhbGc...
```

### Products
```bash
GET  /products              # List all
GET  /products/{id}         # Get by ID
POST /products              # Create (seller only)
PUT  /products/{id}         # Update (seller only)
DELETE /products/{id}       # Delete (seller only)
```

### Orders
```bash
POST /orders                # Create order
GET  /orders                # List user orders
GET  /orders/{id}           # Get order details
```

### Cart (via Product Service)
```bash
GET  /cart                  # Get cart items
POST /cart/items            # Add item
PUT  /cart/items/{itemId}   # Update item quantity
DELETE /cart/items/{itemId} # Remove item
DELETE /cart                # Clear cart
```

### See Swagger UI
```
http://localhost:8080/swagger-ui.html
```

---

## 📈 Monitoring & Logging

### Service Health

```bash
# API Gateway
curl http://localhost:8080/actuator/health

# Each service
curl http://localhost:8085/actuator/health      # Identity
curl http://localhost:8088/actuator/health      # Order
curl http://localhost:8089/actuator/health      # Payment

# Eureka dashboard
http://localhost:8761
```

### Docker Logs

```bash
# View all logs
docker-compose logs

# Follow specific service
docker-compose logs -f api-gateway

# Last 100 lines
docker-compose logs --tail 100

# Save to file
docker-compose logs > logs.txt
```

### Database Logs

```bash
# PostgreSQL
docker-compose exec postgres logs -f

# MongoDB
docker-compose exec mongo logs -f

# Redis
docker-compose exec redis logs -f
```

---

## 🚀 Deployment

### Pre-Deployment Checklist

```bash
# ✅ Test everything locally
✓ Backend builds without errors
✓ Frontend builds without errors
✓ All services start correctly
✓ API endpoints respond
✓ Frontend pages load
✓ Database migrations applied

# ✅ Security
✓ .env file configured (secrets secure)
✓ Passwords changed from defaults
✓ SSL/HTTPS enabled (if needed)
✓ CORS properly configured
✓ JWT secrets strong

# ✅ Performance
✓ Redis cache enabled
✓ Elasticsearch indexed
✓ Database indexes created
✓ CDN configured (optional)

# ✅ Monitoring
✓ Logging configured
✓ Alerts set up
✓ Backup strategy planned
✓ Rollback plan ready
```

### Deployment Steps

```bash
# 1. Build all services
docker-compose build --no-cache

# 2. Push to registry (optional)
docker tag flashsale_api-gateway:latest myregistry/api-gateway:v1.0.0
docker push myregistry/api-gateway:v1.0.0

# 3. Deploy
docker-compose -f docker-compose.yml up -d

# 4. Verify
docker-compose ps
curl http://localhost:8080/actuator/health

# 5. Check frontend
curl http://localhost:3000/
```

### Kubernetes Deployment (Optional)

```bash
# If using Kubernetes instead of Docker Compose
kubectl apply -f k8s/

# Scale service
kubectl scale deployment api-gateway --replicas=3

# View services
kubectl get svc
kubectl get pods
```

---

## 🐛 Troubleshooting

### Build Errors

```bash
# Frontend: Module not found
cd frontend/apps/customer
rm -rf node_modules package-lock.json
npm install

# Backend: Compilation errors
cd backend
mvn clean install -DskipTests -X   # Verbose mode

# Docker: Build fails
docker-compose build --no-cache --progress=plain
```

### Runtime Errors

```bash
# Service won't start
docker-compose logs api-gateway

# Port already in use
lsof -i :8080
kill -9 PID

# Out of memory
docker system prune -a
docker volume prune

# Database connection error
docker-compose exec postgres ping -c 1 localhost
docker-compose logs postgres
```

### Performance Issues

```bash
# Check resource usage
docker stats

# Check database
psql -h localhost -U flashsale -d flashsale -c "EXPLAIN ANALYZE SELECT ...;"

# Clear cache
redis-cli FLUSHALL

# Rebuild Elasticsearch index
curl -X POST http://localhost:9200/products/_reindex
```

---

## 📚 Documentation Files

### Core Documentation

| File | Purpose | Details |
|------|---------|---------|
| **README.md** | Quick start guide | Project overview, setup, quick links |
| **CLAUDE.md** | Build & setup commands | Maven, Docker, Spring Boot |
| **docs/00_INDEX.md** | **START HERE** | Documentation navigation & index |

### Technical Documentation

| File | Purpose | Details |
|------|---------|---------|
| **docs/01_OVERVIEW.md** | Backend architecture & development | 11 services, Axon vs Traditional, setup |
| **02_API.md** | API specification | Endpoints, Kafka topics, request/response |
| **06_PAYMENT_SAGA_FLOW.md** | Axon Saga implementation | Payment orchestration, events |
| **CLAUDE.md** | Build & setup commands | Maven, Docker, Spring Boot config |

### Operations & Deployment

| File | Purpose | Details |
|------|---------|---------|
| **05_OPERATIONS.md** | Data retention & 23 cronjobs | Cleanup jobs, retention periods, SQL logic |
| **RUNNING.md** | Build & deployment commands | Docker, health checks, scaling |

### Business & Policies

| File | Purpose | Details |
|------|---------|---------|
| **03_BUSINESS.md** | Business logic & workflows (v5.3) | 9 workflows, policies, trust score, refunds |
| **04_POLICIES.md** | System policies & configuration (v3) | Trust score, flash sale, seller, loyalty, refunds |

### API & Architecture

| File | Purpose | Details |
|------|---------|---------|
| **02_API.md** | Complete API specification (v5.3) | All endpoints, request/response, Kafka topics |
| **erd.mermaid** | Entity-Relationship Diagram | Database schema visualization |

### Summary

- **Total Files**: 10 markdown docs + 1 ERD diagram
- **Total Lines**: 7,000+ lines of documentation
- **Coverage**: Architecture, backend, frontend, deployment, business logic, policies, API
- **Latest Update**: Tracking number for refunds added to API & Business docs (2026-04-15)

---

## 🤝 Contributing

### Code Style

```bash
# Backend: Java conventions
- Packages: com.flashsale.{service}.{layer}
- Classes: PascalCase
- Methods: camelCase
- Constants: UPPER_SNAKE_CASE

# Frontend: TypeScript conventions
- Components: PascalCase in .tsx
- Files: kebab-case
- Types: PascalCase with T prefix
- Hooks: camelCase starting with use
```

### Commit Messages

```bash
# Format: type(scope): message
git commit -m "feat(order-service): add order tracking"
git commit -m "fix(frontend): fix cart calculation"
git commit -m "docs(readme): update setup instructions"

# Types: feat, fix, docs, style, refactor, test, chore
```

### Pull Request Process

1. Create feature branch: `git checkout -b feature/my-feature`
2. Make changes and test locally
3. Commit with descriptive messages
4. Push: `git push origin feature/my-feature`
5. Create Pull Request with description
6. Request review
7. Merge after approval

---

## 📖 Related Documentation

### How to Find Documentation

**For Quick Start**: Start with [README.md](../README.md)
**For Complete Overview**: Read [01_OVERVIEW.md](01_OVERVIEW.md)
**For Navigation Guide**: See [00_INDEX.md](00_INDEX.md)

### Documentation by Role

**New Developer**: README.md → 01_OVERVIEW.md → CLAUDE.md
**Backend Dev**: 01_OVERVIEW.md → 02_API.md → 03_BUSINESS.md → 04_POLICIES.md
**Frontend Dev**: 01_OVERVIEW.md → 02_API.md → CLAUDE.md
**DevOps/Ops**: CLAUDE.md → RUNNING.md → 05_OPERATIONS.md
**Product Manager**: 01_OVERVIEW.md → 03_BUSINESS.md → 07_BUSINESS_FLOWS.md

### Key Business & Technical References

**Business Logic** (v5.3 RTS):
- [03_BUSINESS.md](03_BUSINESS.md) - 9 workflows, policies, Trust Score, Refunds, RTS
- [04_POLICIES.md](04_POLICIES.md) - System rules, configuration

**Operations**:
- [05_OPERATIONS.md](05_OPERATIONS.md) - 23 cronjobs, retention periods
- [RUNNING.md](../RUNNING.md) - Deployment procedures

**API & Architecture**:
- [02_API.md](02_API.md) - Complete API specification v5.3 RTS
- [07_BUSINESS_FLOWS.md](07_BUSINESS_FLOWS.md) - Luồng nghiệp vụ tổng hợp (Mermaid)
- [erd.mermaid](erd.mermaid) - Database schema diagram

### Latest Updates (v5.3)

✅ **Tracking Number for Refunds**
- Admin can now input tracking number when approving refunds
- Tracked in REFUND_ITEMS for audit trail
- Notification includes tracking info for Buyer
- See: [03_BUSINESS.md](03_BUSINESS.md) (Admin Duyệt Hoàn Tiền section)
- See: [02_API.md](02_API.md) (POST /admin/refunds/{refundId}/approve)

### Complete Documentation List

**Documentation files in `/docs` directory:**

| # | File | Purpose |
|---|------|---------|
| 1 | [00_INDEX.md](00_INDEX.md) | **START HERE** - Documentation index |
| 2 | [01_OVERVIEW.md](01_OVERVIEW.md) | Project architecture & setup |
| 3 | [02_API.md](02_API.md) | API specification v5.3 RTS |
| 4 | [03_BUSINESS.md](03_BUSINESS.md) | Business logic & workflows v5.3 |
| 5 | [04_POLICIES.md](04_POLICIES.md) | System policies v3 |
| 6 | [05_OPERATIONS.md](05_OPERATIONS.md) | 23 cronjobs & data retention v5.0 (per service) |
| 7 | [06_PAYMENT_SAGA_FLOW.md](06_PAYMENT_SAGA_FLOW.md) | Payment flow & Saga |
| 8 | [07_BUSINESS_FLOWS.md](07_BUSINESS_FLOWS.md) | Luồng nghiệp vụ tổng hợp (Mermaid) |
| 9 | [08_PAYMENT_ORDER_INTEGRATION.md](08_PAYMENT_ORDER_INTEGRATION.md) | Order-Payment integration |
| 10 | [erd.mermaid](erd.mermaid) | Database ERD |

**Additional documentation in project root:**
- [README.md](../README.md) - Quick start guide
- [CLAUDE.md](../CLAUDE.md) - Build & setup commands
- [RUNNING.md](../RUNNING.md) - Detailed running guide

---

## 📞 Support & Contact

- **Issues**: GitHub Issues
- **Discussions**: GitHub Discussions
- **Email**: dev@flashsale.example.com

---

## 📄 License

MIT License - See LICENSE file

---

## ✨ Summary

This project is a **production-ready e-commerce platform** with:

- ✅ **Scalable architecture**: Microservices with service discovery
- ✅ **Event-driven core**: Axon Framework for critical flows
- ✅ **Multiple frontends**: Separate apps for customers, sellers, admins
- ✅ **Advanced features**: Flash sales, Stripe payments, full-text search, tracking for refunds
- ✅ **Modern stack**: Java 25, Spring Boot 4.0, React 19, Vite
- ✅ **Containerized**: Docker & Docker Compose for easy deployment
- ✅ **Comprehensive documentation**: 13 files covering all aspects (7,000+ lines)

**Documentation Last Updated**: 2026-04-15  
**Latest Feature**: Tracking number for refunds (v5.3)  
**Status**: Production-Ready ✅

**Ready for production deployment! 🚀**

