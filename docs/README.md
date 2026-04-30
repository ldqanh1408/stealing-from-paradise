# 📚 Stealing From Paradise - API Documentation

**Project**: E-commerce Marketplace  
**Version**: v5.4  
**Last Updated**: 2026-04-30

---

## 📖 Documentation Guide

### 🚀 Quick Start
- **[RUNNING.md](RUNNING.md)** - How to run the project locally
- **[ARCHITECTURE.md](ARCHITECTURE.md)** - System architecture & design

### 📡 API Documentation

**Service APIs**: See `api/` folder for each microservice

| Service | Port | Endpoints | File |
|---------|------|-----------|------|
| Identity + Loyalty | 8081 | 31 | [identity-service/02_API_identity_service.md](identity-service/02_API_identity_service.md) |
| Product + Cart | 8082 | 29 | [product-service/02_API_product_service.md](product-service/02_API_product_service.md) |
| Search | 8089 | 1* | [search-service/02_API_search_service.md](search-service/02_API_search_service.md) |
| Order | 8087 | 16 | [order-service/02_API_order_service.md](order-service/02_API_order_service.md) |
| Payment + Refund | 8085 | 12 | [payment-service/02_API_payment_service.md](payment-service/02_API_payment_service.md) |
| Flash Sale | 8086 | 0* | [flashsale-service/02_API_flash_sale_service.md](flashsale-service/02_API_flash_sale_service.md) |
| Notification | 8088 | 0* | [notification-service/02_API_notification_service.md](notification-service/02_API_notification_service.md) |
| Admin | - | 14 | [admin-service/02_API_admin.md](admin-service/02_API_admin.md) |

> *Search, Flash Sale, and Notification services have gateway routes configured but controllers are still under development.

**Total**: 100+ endpoints across 8 services (Cart merged into Product, Loyalty merged into Identity)

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
├── product-service/      (Products, Variants, Inventory + Cart)
├── search-service/       (Elasticsearch)
├── order-service/        (Orders, Checkout, RTS, Refunds)
├── payment-service/      (Stripe, Payments, Refunds)
├── flashsale-service/    (Flash Sales, High Concurrency)
├── notification-service/ (Real-time SSE)
├── worker-service/       (Background Jobs)
└── common-lib/           (Shared DTOs, Security, Events)

docs/
├── identity-service/     (Identity + Loyalty API)
├── product-service/      (Product + Cart API)
├── order-service/        (Order API)
├── payment-service/      (Payment + Refund API)
├── flashsale-service/    (Flash Sale API)
├── search-service/       (Search API)
├── notification-service/ (Notification API)
├── admin-service/        (Admin API)
├── api/                  (API index)
├── ARCHITECTURE.md       (System Design)
├── KAFKA_EVENTS.md       (Event Documentation)
├── 09_RUNNING.md         (How to Run)
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
| **Total Services** | 8 (+ worker) |
| **Total Endpoints** | 97+ |
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

## ✨ v5.4 Features

✅ Trust Score Tier system (6 levels)  
✅ Multi-vendor order split  
✅ Real-time SSE notifications  
✅ Return To Sender (RTS) workflow  
✅ Cart merged into Product Service  
✅ Loyalty merged into Identity Service  
✅ Consolidated Refund Management  
✅ 35+ Kafka topics for event-driven architecture  
✅ High-concurrency Flash Sale (50k+ req/s)  

---

## 📞 Support

For API documentation details, see:
- **Service APIs**: Individual service docs in `identity-service/`, `product-service/`, etc.
- **API Index**: [api/README.md](api/README.md) for complete API overview
- **Event Architecture**: [KAFKA_EVENTS.md](KAFKA_EVENTS.md)
- **System Design**: [ARCHITECTURE.md](ARCHITECTURE.md)

---

**Version**: v5.4  
**Status**: Production Ready  
**Last Updated**: 2026-04-30

