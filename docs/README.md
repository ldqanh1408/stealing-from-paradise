# 📚 Stealing From Paradise - API Documentation

**Project**: E-commerce Marketplace  
**Version**: v5.3 RTS  
**Last Updated**: 2026-04-28

---

## 📖 Documentation Guide

### 🚀 Quick Start
- **[RUNNING.md](RUNNING.md)** - How to run the project locally
- **[ARCHITECTURE.md](ARCHITECTURE.md)** - System architecture & design

### 📡 API Documentation

**Service APIs**: See `api/` folder for each microservice

| Service | Port | Endpoints | File |
|---------|------|-----------|------|
| Identity + Loyalty | 8081 | 21 | [api/identity-service.md](api/identity-service.md) |
| Product | 8082 | 16 | [api/product-service.md](api/product-service.md) |
| Search | 8089 | 2 | [api/search-service.md](api/search-service.md) |
| Cart | 8083 | 5 | [api/cart-service.md](api/cart-service.md) |
| Order | 8087 | 8 | [api/order-service.md](api/order-service.md) |
| Payment + Refund | 8085 | 13 | [api/payment-service.md](api/payment-service.md) |
| Flash Sale | 8086 | 11 | [api/flash-sale-service.md](api/flash-sale-service.md) |
| Notification | 8088 | 5 | [api/notification-service.md](api/notification-service.md) |
| Admin | - | 14 | [api/admin-service.md](api/admin-service.md) |

**Total**: 95+ endpoints across 9 consolidated services

### 🧭 Kafka Topics
- **[KAFKA_EVENTS.md](KAFKA_EVENTS.md)** - All 35+ topics with event payloads and consumer patterns

### 📊 System Design
- **[ARCHITECTURE.md](ARCHITECTURE.md)** - Architecture diagrams, request flows, Kafka event flows

---

## 🏗️ Technology Stack

| Component | Version |
|-----------|---------|
| Java | 25 |
| Spring Boot | 4.0.4 |
| JWT | RS256 |
| Payment | Stripe Connect |
| Message Queue | Kafka |
| Cache | Redis |
| Databases | PostgreSQL, MongoDB, Elasticsearch |

---

## 📁 Project Structure

```
backend/
├── api-gateway/          (Spring Cloud Gateway)
├── discovery-service/    (Eureka)
├── identity-service/     (Auth + User Management + Loyalty)
├── product-service/      (Products, Variants, Inventory)
├── search-service/       (Elasticsearch)
├── order-service/        (Orders, Checkout, RTS)
├── payment-service/      (Stripe, Payments, Refunds)
├── flashsale-service/    (Flash Sales, High Concurrency)
├── notification-service/ (Real-time SSE)
├── worker-service/       (Background Jobs)
└── common-lib/           (Shared Libraries)

frontend/
├── apps/                 (Client Applications)
└── shared/               (Shared UI Components)

docs/
├── api/                  (Service API Documentation)
├── ARCHITECTURE.md       (System Design)
├── KAFKA_EVENTS.md       (Event Documentation)
├── RUNNING.md            (How to Run)
└── README.md             (This File)
```

---

## 🧭 Quick Navigation

### I want to...

| Goal | Go to |
|------|-------|
| **Understand the system** | [ARCHITECTURE.md](ARCHITECTURE.md) |
| **Run the project** | [RUNNING.md](RUNNING.md) |
| **Implement a feature** | See relevant service in `api/` |
| **Understand Kafka** | [KAFKA_EVENTS.md](KAFKA_EVENTS.md) |
| **View Identity API** | [api/identity-service.md](api/identity-service.md) |
| **View Order API** | [api/order-service.md](api/order-service.md) |
| **View Payment API** | [api/payment-service.md](api/payment-service.md) |
| **Debug event flow** | [KAFKA_EVENTS.md](KAFKA_EVENTS.md) → [ARCHITECTURE.md](ARCHITECTURE.md) |

---

## 🔐 Authentication

All API endpoints use **JWT (RS256)** for authentication except public endpoints:

```
Authorization: Bearer <jwt_token>
```

**Public Endpoints**:
- `GET /search/products` - Search products
- `GET /products/{id}` - Product details
- `GET /categories` - List categories
- `POST /auth/register` - Register new user
- `POST /auth/login` - Login

---

## 🛠️ Development

### Setup Local Environment

1. Clone repository
2. Configure `.env` files (see [RUNNING.md](RUNNING.md))
3. Run Docker Compose: `docker-compose up -d`
4. Start services

### Services Communication

- **Synchronous**: REST API (HTTP)
- **Asynchronous**: Kafka Events
- **Shared Cache**: Redis
- **Service Discovery**: Eureka

---

## 📊 API Statistics

| Metric | Value |
|--------|-------|
| **Total Services** | 9 |
| **Total Endpoints** | 95+ |
| **Kafka Topics** | 35+ |
| **Authentication** | JWT (RS256) |
| **Response Format** | JSON |
| **Rate Limiting** | Per user tier |

---

## 🔗 Related Documentation

- **[erd.mermaid](erd.mermaid)** - Entity-Relationship Diagram
- **[09_RUNNING.md](09_RUNNING.md)** - Detailed running instructions
- **[ARCHITECTURE.md](ARCHITECTURE.md)** - Complete architecture

---

## ✨ v5.3 Features

✅ Trust Score Tier system (6 levels)  
✅ Multi-vendor order split  
✅ Real-time SSE notifications  
✅ Return To Sender (RTS) workflow  
✅ Consolidated Loyalty Service  
✅ Consolidated Refund Management  
✅ 35+ Kafka topics for event-driven architecture  
✅ High-concurrency Flash Sale (50k+ req/s)  

---

## 📞 Support

For API documentation details, see:
- **Quick Reference**: Service files in `api/` folder
- **Detailed Reference**: Full endpoint specs with examples
- **Event Architecture**: [KAFKA_EVENTS.md](KAFKA_EVENTS.md)
- **System Design**: [ARCHITECTURE.md](ARCHITECTURE.md)

---

**Version**: v5.3 RTS  
**Status**: Production Ready  
**Last Updated**: 2026-04-28

