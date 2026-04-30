# 📚 API Documentation Summary

**Project**: stealing-from-paradise Marketplace  
**Version**: v5.4  
**Status**: Reorganized & Consolidated  
**Last Updated**: 2026-04-30

---

## 🎯 Documentation Reorganization

Tài liệu API gốc `02_API.md` (5,220 dòng) đã được tách ra thành **9 file service riêng biệt** với cấu trúc rõ ràng, mỗi file chứa:

✅ Tất cả endpoints của service  
✅ Kafka producers/consumers chi tiết  
✅ Request/Response examples  
✅ Error handling  
✅ Integration points

---

## 📁 File Organization

```
docs/
├── identity-service/02_API_identity_service.md   # 🔐 Auth, Users, Loyalty (31 endpoints)
├── product-service/02_API_product_service.md     # 📦 Products, Variants, Inventory, Cart (24 endpoints)
├── search-service/02_API_search_service.md       # 🔍 Search (routes configured, controllers WIP)
├── order-service/02_API_order_service.md         # 📋 Orders, Checkout, RTS, Refunds (16 endpoints)
├── payment-service/02_API_payment_service.md     # 💳 Payment, Stripe, Refunds (12 endpoints)
├── flashsale-service/02_API_flash_sale_service.md # ⚡ Flash Sales (routes configured, controllers WIP)
├── notification-service/02_API_notification_service.md # 🔔 SSE Notifications (routes configured)
├── admin-service/02_API_admin.md                 # 🛡️ Admin, Moderation (14 endpoints)
└── api/README.md                                 # 📋 This index file
```

**Total**: 97+ endpoints across 8 services (Cart merged into Product, Loyalty merged into Identity)

---

## 🔗 Service Consolidation

| Original | Consolidated Into | Rationale |
|----------|-------------------|-----------|
| Loyalty Service | Identity Service | User-centric, points management tied to user account |
| Cart Service | Product Service | Cart data is product-adjacent, shared MongoDB backend |
| Refund Service | Payment Service | Refund logic depends on Stripe payment state |

---

## 📊 Quick Service Reference

| # | Service | Port | File | Endpoints | Status |
|---|---------|------|------|-----------|--------|
| 1 | **Identity** | 8081 | [Identity doc](../identity-service/02_API_identity_service.md) | 31 | Implemented |
| 2 | **Product (+ Cart)** | 8082 | [Product doc](../product-service/02_API_product_service.md) | 24 | Implemented |
| 3 | **Search** | 8089 | [Search doc](../search-service/02_API_search_service.md) | 0* | Routes configured |
| 4 | **Order** | 8087 | [Order doc](../order-service/02_API_order_service.md) | 16 | Implemented |
| 5 | **Payment (+ Refund)** | 8085 | [Payment doc](../payment-service/02_API_payment_service.md) | 12 | Implemented |
| 6 | **Flash Sale** | 8086 | [Flash Sale doc](../flashsale-service/02_API_flash_sale_service.md) | 0* | Routes configured |
| 7 | **Notification** | 8088 | [Notification doc](../notification-service/02_API_notification_service.md) | 0* | Routes configured |
| 8 | **Admin** | - | [Admin doc](../admin-service/02_API_admin.md) | 14 | Implemented |

> *Controllers still under development; gateway routes are configured.

---

## 🧭 Kafka Topics by Service

### Producers (Event Sources)

**Identity Service**:
- `account.locked` → Notification
- `account.auto_locked` → Notification
- `account.unlocked` → Notification
- `appeal.resolved` → Notification
- `loyalty.points_earned` → Notification

**Product Service**:
- `product.created` → Search
- `product.updated` → Search
- `product.deleted` → Search

**Order Service**:
- `order.created` → Inventory (lock stock)
- `order.cancelled` → Cart, Loyalty
- `order.shipped` → Notification
- `order.delivered` → Identity, Loyalty (credit points)
- `order.returned` → Refund, Inventory (RTS)
- `order.checkout_completed` → Cart (clear cart)

**Payment Service**:
- `payment.success` → Order
- `payment.failed` → Order, Notification
- `refund.requested` → Notification
- `refund.admin_approved` → Notification
- `refund.rejected` → Notification
- `refund.stripe_auto` → Order, Loyalty (chargeback)

**Flash Sale Service**:
- `flash_sale.session_started` → Notification
- `flash_sale.session_ended` → Notification
- `flash_sale.item_approved` → Notification
- `flash_sale.item_sold` → Inventory

**Admin Service**:
- `product.approved` → Search
- `product.rejected` → Notification

### Consumers (Event Listeners)

**Search Service**: 5 topics
- `product.approved`, `product.updated`, `product.deleted`
- `product.auto_hidden`, `inventory.adjusted`, `category.updated`

**Cart** (in Product Service): 1 topic
- `order.checkout_completed` (remove items after checkout)

**Notification Service**: 15+ topics
- Listens to all major events (auth, order, refund, flash sale, etc.)

---

## 🔐 Authentication by Endpoint Type

| Type | Requirement | Example |
|------|-------------|---------|
| **Public** | None | `GET /search/products`, `GET /products/{id}` |
| **User** | JWT Required | `GET /users/me`, `POST /cart/items` |
| **Seller** | JWT + SELLER role | `POST /products`, `GET /sellers/me/orders` |
| **Buyer** | JWT + BUYER role | `POST /orders/checkout`, `POST /orders/{id}/refunds` |
| **Admin** | JWT + ADMIN role | `POST /admin/products/{id}/approve` |

---

## 🔄 Data Flow Examples

### Complete Order → Payment → Refund Flow

```
1. USER: POST /orders/checkout
   └─ Order Service creates PARENT_ORDER + N sub-orders
   └─ Produce: order.created
   
2. INVENTORY: Listen to order.created
   └─ Lock stock for each item
   
3. PAYMENT: Stripe payment processing
   └─ Webhook: payment_intent.succeeded
   └─ Produce: payment.success
   
4. ORDER SERVICE: Listen to payment.success
   └─ Update orders to PAID status
   └─ Produce: order.shipped (when seller updates tracking)
   
5. LOYALTY: Listen to order.delivered
   └─ Credit points (PENDING → CONFIRMED)
   
6. USER: POST /orders/{id}/refunds (within 7 days)
   └─ Payment Service creates refund request
   └─ Produce: refund.requested
   
7. ADMIN: POST /admin/refunds/{id}/approve
   └─ Stripe refund.create()
   └─ Produce: refund.admin_approved
   
8. NOTIFICATION: Listen to all events
   └─ Send real-time updates via SSE
   └─ Store in MongoDB (TTL 90 days)
```

### Flash Sale Purchase Flow

```
1. ADMIN: POST /flash-sale/sessions
   └─ Create session (UPCOMING)
   
2. SELLER: POST /flash-sale/sessions/{id}/items
   └─ Register product with special price/stock
   └─ Status: PENDING (waiting admin approval)
   
3. ADMIN: POST /flash-sale/sessions/{id}/items/{id}/approve
   └─ Status: APPROVED
   └─ Produce: flash_sale.item_approved
   
4. JOB-01: Every minute
   └─ Update session status (UPCOMING → ACTIVE → ENDED)
   
5. BUYER: POST /flash-sale/sessions/{id}/buy
   └─ Redis Lua script (atomic check-and-decrement)
   └─ Creates cart item (high concurrency safe)
   └─ Produce: flash_sale.item_sold
   
6. BUYER: POST /orders/checkout
   └─ Checkout flash sale items (same as regular checkout)
```

---

## 📊 Endpoint Statistics

### By Type

| Type | Count |
|------|-------|
| GET (read-only) | ~32 |
| POST (create) | ~38 |
| PUT (update) | ~15 |
| DELETE (remove) | ~10 |
| PATCH (partial update) | ~3 |
| **Total** | **95+** |

### By Access Level

| Level | Count |
|-------|-------|
| Public | ~8 |
| User (JWT required) | ~45 |
| Seller | ~18 |
| Buyer | ~15 |
| Admin | ~14 |

### By Service

| Service | Endpoints |
|---------|-----------|
| Identity (+ Loyalty) | 31 |
| Product (+ Cart) | 24 |
| Order | 16 |
| Payment (+ Refund) | 12 |
| Admin | 14 |
| Flash Sale | 0* |
| Notification | 0* |
| Search | 0* |
| **Total** | **97+** |

> *Controllers WIP; gateway routes configured

---

## 🚀 Getting Started

### 1️⃣ Start Here: Read the Overview
→ [00-index.md](00-index.md) for service architecture & quick flows

### 2️⃣ Find Your Service
→ Look up your service in the table above

### 3️⃣ Review Endpoints
→ See all HTTP methods, request/response format

### 4️⃣ Understand Kafka Integration
→ Check "Kafka Integration" section for event architecture

### 5️⃣ See Examples
→ Each endpoint includes full request/response examples

---

## 📖 Documentation Structure (Each Service File)

```markdown
# SERVICE NAME

**Service Name**: Name + Port  
**Status**: v5.3 RTS

## 📡 Kafka Integration

### Produces (Event Publisher)
- List of events this service publishes

### Consumes (Event Subscriber)
- List of events this service listens to

## [Endpoint Sections]

- Full endpoint documentation
- Request/response examples
- Error responses
- Query parameters

## 📊 Summary

- Endpoint count by type
- Kafka topics summary

## 🔗 Integration Points

- Table showing which services depend on this service
- Data flow relationships
```

---

## 🔄 Service Dependencies (Call Graph)

```
┌─────────────────────────────────────┐
│      API Gateway (Port 8080)        │
│    - JWT validation                 │
│    - Request routing                │
└─────────────────────────────────────┘
              ↓ routes to
    ┌─────────┴─────────┐
    ↓                   ↓
[8081 Identity]    [8082 Product]
    ↓                   ↓
[JWT/Auth]         [8089 Search]
    │                   ↑
    │      ┌────────────┘
    │      │
    ├─→[8087 Order Service]
    │      ├─→[8085 Payment]
    │      │   ├─→[Stripe API]
    │      │   └─→[Refund]
    │      │
    │      ├─→[Inventory]
    │      ├─→[8086 Flash Sale]
    │      └─→[8084 Loyalty] (now in Identity)
    │
    ├─→[8088 Notification] ← All services via Kafka
    │   (SSE real-time)
    │
    └─→[Admin APIs]
        (Moderation, Config)
```

---

## 🛠️ Technology Stack

| Component | Version |
|-----------|---------|
| Java | 25 |
| Spring Boot | 4.0.4 |
| JWT | RS256 |
| Payment | Stripe Connect |
| Message Queue | Kafka |
| Cache | Redis |
| Databases | PostgreSQL, MongoDB, Elasticsearch |
| API Gateway | Spring Cloud Gateway |
| Service Discovery | Eureka |

---

## 📚 Related Documentation

- **[02_API.md](../02_API.md)** - Original unified API (deprecated, use individual service files)
- **[03_BUSINESS.md](../03_BUSINESS.md)** - Business logic & workflows
- **[04_POLICIES.md](../04_POLICIES.md)** - System rules, configuration
- **[05_OPERATIONS.md](../05_OPERATIONS.md)** - Data retention, 23 cronjobs
- **[07_BUSINESS_FLOWS.md](../07_BUSINESS_FLOWS.md)** - Mermaid diagrams
- **[erd.mermaid](../erd.mermaid)** - Entity-Relationship Diagram

---

## ✨ v5.3 Features

✅ Trust Score Tier system (6 levels)  
✅ Multi-vendor order split  
✅ Real-time SSE notifications  
✅ Failed events management & retry  
✅ Return To Sender (RTS) workflow  
✅ Tracking number for refunds  
✅ Loyalty points integration  
✅ Flash sale with Redis atomic operations  

---

## 🔍 Quick FAQ

**Q: Where is the Loyalty Service documentation?**  
A: Consolidated into Identity Service ([01-identity-service.md](01-identity-service.md)) under "⭐ Loyalty Service Endpoints" section

**Q: Where is the Refund Service documentation?**  
A: Consolidated into Payment Service ([06-payment-service.md](06-payment-service.md)) under "↩️ Refund Management APIs" section

**Q: How do I trace a Kafka event?**  
A: Each service file lists producers and consumers. Use these to follow event flow across services.

**Q: Which endpoints require JWT?**  
A: All except public endpoints (search, product view, register, login, category list)

**Q: How many Kafka topics are there?**  
A: See `00-index.md` for complete Kafka topics catalog (35+ topics)

---

## 📞 Support

- **API Issues**: Check error responses in respective service files
- **Integration Issues**: See "🔗 Integration Points" in each file
- **Kafka Events**: Check Kafka Integration sections
- **Business Logic**: See [03_BUSINESS.md](../03_BUSINESS.md)

---

**Created**: 2026-04-28  
**Consolidated from**: [02_API.md](../02_API.md) (5,220 lines)  
**Services**: 9 consolidated files  
**Total Endpoints**: 95+  
**Status**: v5.3 RTS Production Ready

