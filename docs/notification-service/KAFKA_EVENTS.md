# Kafka Events — Notification Service

**Service**: notification-service — Port 8092  
**Last Updated**: 2026-05-04

> Notification Service **chỉ consumer** (không produce event nào).  
> Lắng nghe 20+ topics từ tất cả services, gửi real-time notification qua SSE  
> và lưu vào MongoDB (TTL 90 ngày).

---

## 📊 Overview

| Metric | Value |
|--------|-------|
| **Events Produced** | 0 |
| **Events Consumed** | 20+ |
| **Output** | SSE streams + MongoDB storage |

---

## 📥 Events Consumed

### Account Events

| # | Topic | Producer | Action |
|---|-------|----------|--------|
| 1 | `account.locked` | 🔗 [Identity Service](../identity-service/KAFKA_EVENTS.md) | Send lock notification to user |
| 2 | `account.auto_locked` | 🔗 [Identity Service](../identity-service/KAFKA_EVENTS.md) | Send urgent lock notification |
| 3 | `account.unlocked` | 🔗 [Identity Service](../identity-service/KAFKA_EVENTS.md) | Send unlock confirmation |
| 4 | `seller.posting_suspended` | 🔗 [Identity Service](../identity-service/KAFKA_EVENTS.md) | Notify seller of suspension |
| 5 | `seller.posting_resumed` | 🔗 [Identity Service](../identity-service/KAFKA_EVENTS.md) | Notify seller of reinstatement |

### Product Events

| # | Topic | Producer | Action |
|---|-------|----------|--------|
| 8 | `product.pending_review` | 🔗 [Product Service](../product-service/KAFKA_EVENTS.md) | Alert admin to review |
| 9 | `product.approved` | 🔗 [Admin Service](../admin-service/KAFKA_EVENTS.md) | Notify seller of approval |
| 10 | `product.rejected` | 🔗 [Admin Service](../admin-service/KAFKA_EVENTS.md) | Notify seller of rejection with reason |
| 11 | `product.auto_hidden` | 🔗 [Admin Service](../admin-service/KAFKA_EVENTS.md) | Notify seller product auto-hidden |

### Order Events

| # | Topic | Producer | Action |
|---|-------|----------|--------|
| 14 | `order.shipped` | 🔗 [Order Service](../order-service/KAFKA_EVENTS.md) | Send shipping notification with tracking |
| 15 | `order.delivered` | 🔗 [Order Service](../order-service/KAFKA_EVENTS.md) | Send delivery confirmation |
| 16 | `order.cancelled` | 🔗 [Order Service](../order-service/KAFKA_EVENTS.md) | Send cancellation notification |
| 17 | `order.auto_cancelled` | 🔗 [Order Service](../order-service/KAFKA_EVENTS.md) | Send auto-cancellation notice |
| 18 | `order.returned` | 🔗 [Order Service](../order-service/KAFKA_EVENTS.md) | Notify buyer of refund initiation |

### Payment & Refund Events

| # | Topic | Producer | Action |
|---|-------|----------|--------|
| 19 | `payment.success` | 🔗 [Payment Service](../payment-service/KAFKA_EVENTS.md) | Send payment confirmation |
| 20 | `payment.failed` | 🔗 [Payment Service](../payment-service/KAFKA_EVENTS.md) | Send payment failure notification |
| 21 | `refund.requested` | 🔗 [Payment Service](../payment-service/KAFKA_EVENTS.md) | Notify seller of refund request |
| 22 | `refund.created` | 🔗 [Payment Service](../payment-service/KAFKA_EVENTS.md) | Notify refund record created |
| 23 | `refund.full_requested` | 🔗 [Payment Service](../payment-service/KAFKA_EVENTS.md) | Notify of full refund request (RTS) |
| 24 | `refund.admin_approved` | 🔗 [Payment Service](../payment-service/KAFKA_EVENTS.md) | Notify buyer and seller of approval |
| 25 | `refund.rejected` | 🔗 [Payment Service](../payment-service/KAFKA_EVENTS.md) | Notify buyer of rejection |
| 26 | `refund.rts_completed` | 🔗 [Payment Service](../payment-service/KAFKA_EVENTS.md) | Notify RTS completion |
| 27 | `stripe.account_suspended` | 🔗 [Payment Service](../payment-service/KAFKA_EVENTS.md) | Notify seller of Stripe suspension |
| 28 | `stripe.dispute.created` | 🔗 [Payment Service](../payment-service/KAFKA_EVENTS.md) | Notify dispute opened |
| 29 | `stripe.dispute.closed` | 🔗 [Payment Service](../payment-service/KAFKA_EVENTS.md) | Notify dispute resolved |
| 30 | `stripe.transfer.reversed` | 🔗 [Payment Service](../payment-service/KAFKA_EVENTS.md) | Notify transfer reversal |
| 31 | `stripe.payout.failed` | 🔗 [Payment Service](../payment-service/KAFKA_EVENTS.md) | Notify payout failure |
| 32 | `seller.stripe_requirement` | 🔗 [Payment Service](../payment-service/KAFKA_EVENTS.md) | Notify KYC requirement |

### Flash Sale Events

| # | Topic | Producer | Action |
|---|-------|----------|--------|
| 33 | `flash_sale.session_started` | 🔗 [Flash Sale Service](../flashsale-service/KAFKA_EVENTS.md) | Send SSE reminders |
| 34 | `flash_sale.session_ended` | 🔗 [Flash Sale Service](../flashsale-service/KAFKA_EVENTS.md) | Send session end notification |
| 35 | `flash_sale.item_approved` | 🔗 [Flash Sale Service](../flashsale-service/KAFKA_EVENTS.md) | Notify seller of approval |
| 36 | `flash_sale.item_rejected` | 🔗 [Flash Sale Service](../flashsale-service/KAFKA_EVENTS.md) | Notify seller of rejection |
| 37 | `flash_sale.reminder` | 🔗 [Worker Service](../worker-service/KAFKA_EVENTS.md) | Send SSE reminder to subscribers |

---

## 🔁 Flow: Consumer-Only Service Map

```
Identity Service ──→ account.*, seller.* ──┐
Product Service  ──→ product.pending_review                                     │
Admin Service    ──→ product.approved, product.rejected, product.auto_hidden    ├──→ Notification Service
Order Service    ──→ order.*                                                    │     (SSE + MongoDB)
Payment Service  ──→ payment.*, refund.*, stripe.*                              │
Flash Sale       ──→ flash_sale.*                                               │
Worker Service   ──→ flash_sale.reminder                                     ──┘
```

---

## 🔗 Related Kafka Docs

| Service | Link |
|---------|------|
| Identity Service | 🔗 [identity-service/KAFKA_EVENTS.md](../identity-service/KAFKA_EVENTS.md) |
| Product Service | 🔗 [product-service/KAFKA_EVENTS.md](../product-service/KAFKA_EVENTS.md) |
| Order Service | 🔗 [order-service/KAFKA_EVENTS.md](../order-service/KAFKA_EVENTS.md) |
| Payment Service | 🔗 [payment-service/KAFKA_EVENTS.md](../payment-service/KAFKA_EVENTS.md) |
| Flash Sale Service | 🔗 [flashsale-service/KAFKA_EVENTS.md](../flashsale-service/KAFKA_EVENTS.md) |
| Admin Service | 🔗 [admin-service/KAFKA_EVENTS.md](../admin-service/KAFKA_EVENTS.md) |
| Worker Service | 🔗 [worker-service/KAFKA_EVENTS.md](../worker-service/KAFKA_EVENTS.md) |
| Full Index | 🔗 [KAFKA_EVENTS.md](../KAFKA_EVENTS.md) |
