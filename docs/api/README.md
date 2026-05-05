# 📚 API Documentation Summary

**Project**: stealing-from-paradise Marketplace  
**Version**: v5.5
**Status**: Reorganized & Consolidated
**Last Updated**: 2026-05-05

---

## 🎯 Documentation Reorganization

Tài liệu API gốc `02_API.md` (5,220 dòng) đã được tách ra thành **10 file service riêng biệt** với cấu trúc rõ ràng, mỗi file chứa:

✅ Tất cả endpoints của service  
✅ Kafka producers/consumers chi tiết  
✅ Request/Response examples  
✅ Error handling  
✅ Integration points

---

## 📁 File Organization

```
docs/
├── services/identity-service/02_API_identity_service.md   # 🔐 Auth, Users (31 endpoints)
├── services/product-service/02_API_product_service.md     # 📦 Products, Variants, Inventory, Cart (24 endpoints)
├── services/search-service/02_API_search_service.md       # 🔍 Search (routes configured, controllers WIP)
├── services/order-service/02_API_order_service.md         # 📋 Orders, Checkout, RTS, Refunds (18 endpoints)
├── services/payment-service/02_API_payment_service.md     # 💳 Payment, Stripe, Refunds, Transfers (15 endpoints)
├── services/flashsale-service/02_API_flash_sale_service.md # ⚡ Flash Sales (routes configured, controllers WIP)
├── services/notification-service/02_API_notification_service.md # 🔔 SSE Notifications (routes configured)
├── services/ai-chat-service/02_API_ai_chat.md             # 🤖 AI Chat (routes configured)
└── api/README.md                                           # 📋 This index file
```

**Total**: 102+ endpoints across 8 services (Cart merged into Product)

---

## 🔗 Service Consolidation

| Original | Consolidated Into | Rationale |
|----------|-------------------|-----------|
| Cart Service | Product Service | Cart data is product-adjacent, shared MongoDB backend |
| Refund Service | Payment Service | Refund logic depends on Stripe payment state |
| Admin Service | Identity Service | Admin không phải standalone process; endpoints route từ API Gateway vào Identity Service |

---

## 📊 Quick Service Reference

| # | Service | Port | File | Endpoints | Status |
|---|---------|------|------|-----------|--------|
| 1 | **Identity** (+ Admin) | 8081 | [Identity doc](../services/identity-service/02_API_identity_service.md) | 45 | Implemented |
| 2 | **Product (+ Cart)** | 8090 | [Product doc](../services/product-service/02_API_product_service.md) | 24 | Implemented |
| 3 | **Search** | 8091 | [Search doc](../services/search-service/02_API_search_service.md) | 0* | Routes configured |
| 4 | **Order** | 8083 | [Order doc](../services/order-service/02_API_order_service.md) | 18 | Implemented |
| 5 | **Payment (+ Refund)** | 8082 | [Payment doc](../services/payment-service/02_API_payment_service.md) | 15 | Implemented |
| 6 | **Flash Sale** | 8085 | [Flash Sale doc](../services/flashsale-service/02_API_flash_sale_service.md) | 0* | Routes configured |
| 7 | **Notification** | 8092 | [Notification doc](../services/notification-service/02_API_notification_service.md) | 0* | Routes configured |
| 8 | **AI Chat** | 8093 | [AI Chat doc](../services/ai-chat-service/02_API_ai_chat.md) | TBD | Implemented |
|| 9 | **Admin** | - | Merged into Identity Service | 14 | Merged |

> *Controllers still under development; gateway routes are configured.

---

## 🧭 Kafka Topics by Service

### Producers (Event Sources)

**Identity Service**:
- `account.locked` → Notification
- `account.unlocked` → Notification
- `seller.posting_suspended` → Notification
- `seller.posting_resumed` → Notification
- `product.approved` → Search, Notification
- `product.rejected` → Search, Notification
- `product.auto_hidden` → Search, Notification
- `appeal.resolved` → Notification (removed in MVP)

**Product Service**:
- `product.created` → Search
- `product.updated` → Search
- `product.deleted` → Search

**Order Service**:
- `order.created` → Inventory (lock stock)
- `order.cancelled` → Cart
- `order.shipped` → Notification
- `order.delivered` → Identity
- `order.returned` → Refund, Inventory (RTS)
- `order.checkout_completed` → Cart (clear cart)

**Payment Service**:
- `payment.success` → Order
- `payment.failed` → Order, Notification
- `refund.requested` → Notification
- `refund.admin_approved` → Notification
- `refund.rejected` → Notification
- `refund.stripe_auto` → Order (chargeback)

**Flash Sale Service**:
- `flash_sale.session_started` → Notification
- `flash_sale.session_ended` → Notification
- `flash_sale.item_approved` → Notification
- `flash_sale.item_sold` → Inventory

*(Admin events `product.approved`, `product.rejected`, `product.auto_hidden` thuộc Identity Service — xem [Identity Service doc](../services/identity-service/02_API_identity_service.md#🛡-admin-management))*

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
   
5. USER: POST /orders/{id}/refunds (within 7 days)
   └─ Payment Service creates refund request
   └─ Produce: refund.requested
   
6. ADMIN: POST /admin/refunds/{id}/approve
   └─ Stripe refund.create()
   └─ Produce: refund.admin_approved
   
7. NOTIFICATION: Listen to all events
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
| Identity (+ Admin) | 45 |
| Product (+ Cart) | 24 |
| Order | 18 |
| Payment (+ Refund) | 15 |
| Admin | 14 (merged into Identity) |
| Flash Sale | 0* |
| Notification | 0* |
| Search | 0* |
| **Total** | **102+** |

> *Controllers WIP; gateway routes configured

---

## 🚀 Getting Started

### 1️⃣ Start Here: Read the Overview
→ [Documentation Index](../00_INDEX.md) for service architecture & quick flows

### 2️⃣ Find Your Service
→ Look up your service in the table above

### 3️⃣ API Endpoints
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
**Status**: v5.5

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
[8081 Identity]    [8090 Product]
    ↓                   ↓
[JWT/Auth]         [8091 Search]
    │                   ↑
    │      ┌────────────┘
    │      │
    ├─→[8083 Order Service]
    │      ├─→[8082 Payment]
    │      │   ├─→[Stripe API]
    │      │   └─→[Refund]
    │      ├─→[Inventory]
    │      ├─→[8085 Flash Sale]
    │      └─→[8093 AI Chat]
    │
    ├─→[8092 Notification] ← All services via Kafka
    │   (SSE real-time)
    │
    └─→[Admin APIs (in Identity Service)]
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
- **[Business Logic](../business/03_BUSINESS.md)** - Business logic & workflows
- **[Operations](../operations/05_OPERATIONS.md)** - Data retention, 17 cronjobs
- **[Business Flows](../business/07_BUSINESS_FLOWS.md)** - Mermaid diagrams
- **[ERD Full System](../database/ERD_FULL_SYSTEM.md)** - Entity-Relationship Diagram

---

## ✨ v5.5 Features

✅ Multi-vendor order split  
✅ Real-time SSE notifications  
✅ Failed events management & retry  
✅ Return To Sender (RTS) workflow  
✅ Tracking number for refunds  
✅ Flash sale with Redis atomic operations
✅ AI Chat Support (multi-turn conversation with Tool calls, human-in-the-loop)  

---

## 🔍 Quick FAQ


**Q: Where is the Refund Service documentation?**  
A: Consolidated into Payment Service ([Payment Service doc](../services/payment-service/02_API_payment_service.md)) under "↩️ Refund Management APIs" section

**Q: How do I trace a Kafka event?**  
A: Each service file lists producers and consumers. Use these to follow event flow across services.

**Q: Which endpoints require JWT?**  
A: All except public endpoints (search, product view, register, login, category list)

**Q: How many Kafka topics are there?**  
A: See [Documentation Index](../00_INDEX.md) for complete Kafka topics catalog (47 topics)

---

## 📞 Support

- **API Issues**: Check error responses in respective service files
- **Integration Issues**: See "🔗 Integration Points" in each file
- **Kafka Events**: Check Kafka Integration sections
- **Business Logic**: See [03_BUSINESS.md](../business/03_BUSINESS.md)

---

**Created**: 2026-04-28  
**Consolidated from**: `02_API.md` (5,220 lines)  
**Services**: 10 consolidated files  
**Total Endpoints**: 102+  
**Status**: v5.5 Production Ready

