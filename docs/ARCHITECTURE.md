# 🏗️ Service Architecture & Kafka Flow

Visual guide to service interactions and Kafka event flow.

---

## 📐 High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     FRONTEND (Web/Mobile)                       │
│              Browser / Native App / PWA                         │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ↓ (HTTPS)
┌──────────────────────────────────────────────────────────────────┐
│               API Gateway (Spring Cloud Gateway)                 │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ - JWT Validation (RS256)                               │    │
│  │ - Request Routing                                       │    │
│  │ - Rate Limiting                                         │    │
│  │ - Load Balancing                                        │    │
│  └─────────────────────────────────────────────────────────┘    │
└──────────────────────────┬──────────────────────────────────────┘
                           │
        ┌──────────────────┼──────────────────┬──────────────┐
        ↓                  ↓                  ↓              ↓
    ┌────────┐        ┌────────┐        ┌────────┐     ┌────────┐
    │Identity│        │Product │        │Search  │ ... │Others  │
    │Service │        │Service │        │Service │     │Services│
    │ :8081  │        │ :8082  │        │ :8089  │     │        │
    └────────┘        └────────┘        └────────┘     └────────┘
        │                  │                  ↑
        │                  └──────────────────┘
        │                    (Product index)
        │
    ┌───┴───────────────────────┐
    │   Shared Infrastructure   │
    ├──────────────────────────┤
    │ PostgreSQL (Master Data) │
    │ MongoDB (Cart, Notif)    │
    │ Elasticsearch (Search)   │
    │ Redis (Cache, PubSub)    │
    │ MinIO (File Storage)     │
    │ Kafka (Event Streaming)  │
    │ Stripe (Payment)         │
    └──────────────────────────┘
```

---

## 🛒 Buyer Journey (Request Flow)

```
┌─────────────┐
│   Buyer     │
└──────┬──────┘
       │
       ├─1─→ [POST /auth/login]           → Identity Service ✅
       │      └─ Get JWT Token (expires: 15min)
       │
       ├─2─→ [GET /search/products?q=...]  → Search Service ✅
       │      └─ Elasticsearch full-text search
       │
       ├─3─→ [GET /products/{id}]          → Product Service ✅
       │      └─ Get product details
       │
       ├─4─→ [POST /cart/items]            → Cart Service ✅
       │      ├─ Validate JWT
       │      ├─ Check inventory real-time (Product Service)
       │      └─ Store in MongoDB (TTL 30 days)
       │
       ├─5─→ [GET /loyalty/balance]        → Identity Service ✅
       │      └─ Check available loyalty points
       │
       ├─6─→ [POST /orders/checkout]       → Order Service ✅
       │      ├─ Validate items & stock
       │      ├─ Split by seller (multi-vendor)
       │      ├─ Create Stripe PaymentIntent
       │      ├─ 🔴 Produce: order.created
       │      └─ Return parent_order_id + payment_url
       │
       ├─7─→ [Stripe Payment Modal]        → Stripe API 🌐
       │      └─ User enters card details
       │
       ├─8─→ [Stripe Webhook]              → Payment Service ✅
       │      ├─ payment_intent.succeeded
       │      ├─ 🔴 Produce: payment.success
       │      └─ Update TRANSACTIONS table
       │
       ├─9─→ [GET /orders]                 → Order Service ✅
       │      └─ User sees "PAID" status
       │
       ├─10→ [GET /notifications/stream]   → Notification Service ✅
       │      └─ Real-time SSE updates
       │           - "Order shipped"
       │           - "Delivered"
       │
       └─11→ [POST /orders/{id}/confirm-received] → Order Service ✅
              ├─ 🔴 Produce: order.delivered
              ├─ Loyalty points credited
              └─ Seller trust score +5
```

---

## 🏪 Seller Journey (Request Flow)

```
┌──────────────┐
│   Seller     │
└──────┬───────┘
       │
       ├─1─→ [POST /auth/register]         → Identity Service ✅
       │      └─ Create account + Get JWT
       │
       ├─2─→ [POST /users/me/roles/seller] → Identity Service ✅
       │      └─ Add SELLER role
       │
       ├─3─→ [POST /stripe/onboarding/start] → Payment Service ✅
       │      ├─ Get Stripe Connect link
       │      └─ Redirect to Stripe verification
       │
       ├─4─→ [POST /products]              → Product Service ✅
       │      ├─ Create product (DRAFT)
       │      ├─ Upload images via presigned-url
       │      └─ 🔴 Produce: product.created
       │
       ├─5─→ [POST /seller/products/{id}/submit] → Product Service ✅
       │      └─ Submit for admin review (PENDING)
       │
       ├─6─→ [GET /sellers/me/orders]      → Order Service ✅
       │      └─ List orders from buyers
       │
       ├─7─→ [PUT /orders/{id}/tracking]   → Order Service ✅
       │      ├─ Update tracking number
       │      ├─ 🔴 Produce: order.shipped
       │      └─ Notification → Buyer
       │
       ├─8─→ [POST /flash-sale/sessions/{id}/items] → Flash Sale ✅
       │      ├─ Register product for flash sale
       │      └─ Status: PENDING (await admin approval)
       │
       └─9─→ [GET /sellers/me/products]    → Product Service ✅
              └─ Manage product list
```

---

## 👨‍⚖️ Admin Journey (Moderation Flow)

```
┌─────────────┐
│    Admin    │
└──────┬──────┘
       │
       ├─1─→ [GET /admin/products/pending] → Product Service ✅
       │      └─ List products awaiting review
       │
       ├─2─→ [POST /admin/products/{id}/approve] → Product Service ✅
       │      ├─ Approve product
       │      ├─ 🔴 Produce: product.approved
       │      └─ Notification → Seller
       │
       ├─3─→ [POST /admin/products/{id}/reject] → Product Service ✅
       │      ├─ Reject with reason
       │      ├─ 🔴 Produce: product.rejected
       │      └─ Notification → Seller
       │
       ├─4─→ [GET /admin/users]            → Identity Service ✅
       │      └─ List all users with filters
       │
       ├─5─→ [POST /admin/users/{id}/lock] → Identity Service ✅
       │      ├─ Lock account + revoke JWTs
       │      ├─ 🔴 Produce: account.locked
       │      └─ Notification → User
       │
       ├─6─→ [GET /admin/refunds]          → Payment Service ✅
       │      └─ List pending refund requests
       │
       ├─7─→ [POST /admin/refunds/{id}/approve] → Payment Service ✅
       │      ├─ Stripe refund.create()
       │      ├─ 🔴 Produce: refund.admin_approved
       │      ├─ Update trust score if caused_by=SELLER
       │      └─ Notification → Buyer + Seller
       │
       ├─8─→ [GET /admin/failed-events]    → Admin Service ✅
       │      └─ List failed Kafka/job events
       │
       └─9─→ [POST /admin/failed-events/{id}/retry] → Admin Service ✅
              └─ Re-publish failed event to Kafka
```

---

## 🔴 Kafka Event Flow (Core Business Logic)

### Order Creation to Delivery

```
ORDER LIFECYCLE
───────────────

1. [POST /orders/checkout] → Order Service
   │
   └─→ CREATE PARENT_ORDER + N sub-orders (per seller)
       ├─ Status: PENDING
       ├─ 🔴 PRODUCE: order.created
       │   ├─ {orderId, sellerId, items[], total}
       │   │
       │   ├─→ [Inventory Service] CONSUMER
       │   │   └─ Lock stock for each SKU
       │   │
       │   └─→ [Search Service] CONSUMER (optional)
       │       └─ Update search index
       │
       └─ Waiting for payment...

2. [Stripe Webhook: payment_intent.succeeded]
   │
   ├─→ Payment Service
   │   └─ Update TRANSACTIONS.status = SUCCESS
   │       ├─ 🔴 PRODUCE: payment.success
   │       │   └─→ [Order Service] CONSUMER
   │       │       └─ Update ORDERS.status = PAID
       │
       └─ Waiting for seller to ship...

3. [PUT /orders/{id}/tracking] → Order Service
   │
   ├─ Update tracking_number, carrier
   │
   ├─ 🔴 PRODUCE: order.shipped
   │   └─→ [Notification Service] CONSUMER
   │       ├─ Create notification
   │       ├─ Send SSE to buyer
   │       └─ Store in MongoDB (TTL 90 days)
   │
   └─ Status: SHIPPING
       └─ Waiting for buyer to receive...

4. [POST /orders/{id}/confirm-received] → Order Service
   │
   ├─ Validate order status = SHIPPING
   │
   ├─ 🔴 PRODUCE: order.delivered
   │   │
   │   ├─→ [Identity Service] CONSUMER
   │   │   └─ Add trust_score[seller] += 5
   │   │
   │   ├─→ [Loyalty Service] CONSUMER
   │   │   └─ Move points from PENDING → CONFIRMED
   │   │       ├─ 🔴 PRODUCE: loyalty.points_earned
   │   │       │   └─→ [Notification Service]
   │   │       │       └─ Send "Points credited" notification
   │   │       │
   │   │       └─ Store transaction in LOYALTY_TRANSACTIONS
   │   │
   │   └─→ [Notification Service] CONSUMER
   │       └─ Send "Delivered" notification
   │
   └─ Status: DELIVERED
       └─ Order complete!

(If refund happens before DELIVERED)
5. [POST /orders/{id}/refunds] → Refund Service
   │
   ├─ Validate order can be refunded
   │
   ├─ 🔴 PRODUCE: refund.requested
   │   └─→ [Notification Service] CONSUMER
   │       └─ Notify seller of refund request
   │
   ├─ Waiting for admin approval...
   │
   └─ Admin: [POST /admin/refunds/{id}/approve]
       ├─ Stripe refund.create()
       │
       ├─ 🔴 PRODUCE: refund.admin_approved
       │   │
       │   ├─→ [Loyalty Service] CONSUMER
       │   │   └─ Return refunded loyalty points
       │   │
       │   └─→ [Notification Service] CONSUMER
       │       ├─ Notify buyer: "Refund approved"
       │       └─ Notify seller: "Refund processed"
       │
       └─ Webhook: charge.refunded
           └─ Update REFUNDS.status = SUCCESS
```

---

## ⚡ Flash Sale Purchase Flow

```
FLASH SALE LIFECYCLE
────────────────────

1. [POST /flash-sale/sessions] (Admin)
   └─ Create session: UPCOMING
       ├─ start_time, end_time
       └─ Status: UPCOMING

2. [JOB-01 Every minute]
   ├─ Check time
   ├─ Update session status (UPCOMING → ACTIVE)
   │   └─ 🔴 PRODUCE: flash_sale.session_started
   │       └─→ [Notification Service]
   │           └─ Notify registered reminders
   │
   └─ Update session status (ACTIVE → ENDED)
       └─ 🔴 PRODUCE: flash_sale.session_ended
           └─→ [Notification Service]
               └─ Notify buyers of session end

3. [Seller] → [POST /flash-sale/sessions/{id}/items]
   │
   ├─ Register product with special price/stock
   │
   └─ Status: PENDING (awaiting admin approval)

4. [Admin] → [POST /flash-sale/sessions/{id}/items/{id}/approve]
   │
   ├─ Validate conditions (6 checks)
   │
   ├─ Status: APPROVED
   │
   ├─ 🔴 PRODUCE: flash_sale.item_approved
   │   └─→ [Notification Service]
   │       └─ Notify seller of approval
   │
   └─ Ready for sale!

5. [Buyer High Concurrency] → [POST /flash-sale/sessions/{id}/buy]
   │
   ├─ Redis Lua Script (ATOMIC)
   │   └─ Decrement counter if stock > 0
   │       └─ Check limit_per_user
   │
   ├─ If success:
   │   ├─ Add to cart
   │   ├─ 🔴 PRODUCE: flash_sale.item_sold
   │   │   └─→ [Inventory Service]
   │   │       └─ Update sold_count
   │   │
   │   └─ Response 201: Added to cart
   │
   └─ If sold out:
       └─ Response 409: SOLD_OUT

6. [Buyer] → [POST /orders/checkout]
   └─ Same as regular checkout (can include flash sale items)
```

---

## 💳 Refund Flow

```
REFUND LIFECYCLE
────────────────

1. [Buyer] → [POST /orders/{orderId}/refunds]
   │
   ├─ Validate order state (PAID/SHIPPING/DELIVERED)
   │
   ├─ Create REFUNDS record
   │   ├─ Status: PENDING
   │   └─ Reason: user-provided
   │
   ├─ 🔴 PRODUCE: refund.requested
   │   └─→ [Notification Service]
   │       └─ Notify seller
   │
   └─ Response: Refund created, awaiting manual approval

2. [Admin] → [GET /admin/refunds]
   │
   └─ Filter by status=PENDING

3. [Admin] → [POST /admin/refunds/{refundId}/approve]
   │
   ├─ Stripe refund.create({pi_id, amount})
   │
   ├─ Update REFUNDS.status = SUCCESS
   │
   ├─ Adjust loyalty if refund amount > loyalty spent
   │
   ├─ Adjust trust_score if caused_by=SELLER
   │
   ├─ 🔴 PRODUCE: refund.admin_approved
   │   │
   │   ├─→ [Loyalty Service] CONSUMER
   │   │   └─ Update LOYALTY_TRANSACTIONS
   │   │
   │   └─→ [Notification Service] CONSUMER
   │       ├─ Notify buyer: "Refund processed"
   │       ├─ Notify seller: "Trust score -5"
   │       └─ Send amount info
   │
   └─ Stripe Webhook: charge.refunded
       └─ Confirm payment refund (double-check)

4. [Seller] → [POST /orders/{id}/return-to-sender]
   │
   ├─ RTS (Return To Sender) - Seller received goods back
   │
   ├─ Upload evidence images
   │
   ├─ Status: RETURNED
   │
   ├─ 🔴 PRODUCE: order.returned
   │   └─→ [Refund Service] CONSUMER
   │       └─ Auto-create full refund
   │
   └─ Notification sent to buyer (refund in progress)
```

---

## 🔐 Token Revocation Flow

```
JWT TOKEN LIFECYCLE
───────────────────

1. [User] → [POST /auth/login]
   │
   ├─ Verify credentials (password hash)
   │
   ├─ Check account status (ACTIVE/LOCKED)
   │
   ├─ Generate JWT (RS256 signed)
   │   ├─ access_token: 15 min expiry
   │   └─ refresh_token: 7 days expiry
   │
   └─ Store JTI in Redis cache

2. [User] → Uses token in Header: Authorization: Bearer <token>
   │
   ├─ API Gateway validates signature (RS256)
   │
   ├─ Check Redis blocklist (revoked tokens)
   │
   └─ If valid: Route to service

3. [User/Admin] → [POST /auth/logout]
   │
   ├─ Add JTI to Redis REVOKED_TOKENS set
   │   └─ TTL: token expiry time
   │
   └─ Response: "Logged out"

4. [Admin] → [POST /admin/users/{userId}/lock]
   │
   ├─ Set USERS.status = LOCKED
   │
   ├─ Fetch all active JTIs for this user
   │
   ├─ Add all JTIs to Redis REVOKED_TOKENS
   │
   ├─ 🔴 PRODUCE: account.locked
   │   └─→ [Notification Service]
   │       └─ Notify user of account lock
   │
   └─ All tokens for this user invalidated

5. [JOB-18 Nightly]
   │
   ├─ Cleanup Redis REVOKED_TOKENS
   │
   └─ Remove expired entries
```

---

## 🔄 Event-Driven Architecture Pattern

```
┌──────────────────────────────────────────────────────┐
│            SERVICE A (Event Producer)               │
│  ┌──────────────────────────────────────────────┐   │
│  │ Business Logic                               │   │
│  │ (e.g., Process order, create refund)         │   │
│  └──────────────┬───────────────────────────────┘   │
│                 │                                     │
│                 └─→ Kafka.send("topic.name", event)  │
└────────────────────────────┬──────────────────────────┘
                             │
                   ┌─────────┴─────────┐
                   │                   │
        ┌──────────▼──────────┐  ┌────▼────────────────┐
        │    Kafka Topic      │  │  (Scalable queue)  │
        │   order.delivered   │  │  Retention: 7 days │
        └────────┬────────────┘  └────────────────────┘
                 │
     ┌───────────┼───────────┐
     │           │           │
  ┌──▼──┐    ┌──▼──┐    ┌──▼──┐
  │Cons.│    │Cons.│    │Cons.│
  │ 1   │    │ 2   │    │ 3   │
  └─────┘    └─────┘    └─────┘
     │           │           │
  [LS Svc]  [Notif Svc]  [Admin Log]
   (points)  (SSE update) (audit trail)


KEY BENEFITS:
✅ Loose coupling (services don't know each other)
✅ High scalability (consumers work independently)
✅ Fault tolerance (events persist in Kafka)
✅ Event replay (debug/replay from topic)
✅ Multi-consumer support (1 event → many handlers)
```

---

## 📊 Kafka Topics Organized by Domain

### User & Account Events
```
account.locked          (Identity → Notification, Search)
account.auto_locked     (Worker → Notification)
account.unlocked        (Identity → Notification)
seller.posting_suspended (Identity → Notification)
seller.posting_resumed   (Identity → Notification)
```

### Product Events
```
product.created         (Product → Search)
product.updated         (Product → Search)
product.deleted         (Product → Search)
product.approved        (Admin → Search)
product.rejected        (Admin → Notification)
product.auto_hidden     (Worker → Search, Notification)
category.updated        (Product → Search)
inventory.adjusted      (Product → Search)
```

### Order Events
```
order.created           (Order → Inventory, Search)
order.cancelled         (Order → Cart, Loyalty, Notification)
order.shipped           (Order → Notification)
order.delivered         (Order → Identity, Loyalty, Notification)
order.returned          (Order → Refund, Inventory, Notification)
order.checkout_completed (Order → Cart)
order.auto_cancelled    (Worker → Notification)
```

### Payment & Refund Events
```
payment.success         (Payment → Order, Notification)
payment.failed          (Payment → Order, Notification)
refund.requested        (Refund → Notification)
refund.admin_approved   (Refund → Loyalty, Notification)
refund.rejected         (Refund → Notification)
refund.stripe_auto      (Payment → Order, Loyalty)
```

### Flash Sale Events
```
flash_sale.session_started   (Flash Sale → Notification)
flash_sale.session_ended     (Flash Sale → Notification)
flash_sale.item_approved     (Flash Sale → Notification)
flash_sale.item_rejected     (Flash Sale → Notification)
flash_sale.item_sold         (Flash Sale → Inventory)
flash_sale.reminder          (Worker → Notification)
```

### Loyalty Events
```
loyalty.points_earned   (Identity → Notification)
loyalty.points_used     (Order → Identity)
loyalty.points_refunded (Refund → Identity)
loyalty.points_expired  (Worker → Notification)
```

### Trust Score & Appeal Events
```
trust_score.warning     (Identity → Notification)
appeal.resolved         (Identity → Notification)
```

---

**Last Updated**: 2026-04-28  
**Version**: v5.3 RTS  
**Format**: Markdown with ASCII diagrams

