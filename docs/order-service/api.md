# 📋 Order Service API

**Service**: Order Service  
**Port**: 8087  
**Base URL**: `/api/v1`  
**Version**: v5.3 RTS

## Overview

Order Service handles:
- Checkout from cart (multi-vendor split)
- Order management (list, detail, cancel)
- Order fulfillment (tracking, delivery confirmation)
- **Return To Sender (RTS)** workflow (NEW v5.3)
- Saga pattern with Axon CQRS

## 📡 Kafka Integration

### Produces (Events Published)
| Topic | Consumer | Purpose |
|-------|----------|---------|
| `order.created` | Inventory Service | Lock stock |
| `order.cancelled` | Cart, Loyalty Services | Unlock stock, refund points |
| `order.shipped` | Notification Service | Shipping notification |
| `order.delivered` | Identity, Loyalty Services | Credit points, update trust score |
| `order.returned` | Refund, Inventory Services | RTS refund processing |
| `order.checkout_completed` | Cart Service | Clear cart items |

### Consumes (Events Listened)
| Topic | Producer | Purpose |
|-------|----------|---------|
| `payment.success` | Payment Service | Mark order as PAID |

## Key Endpoints

### Checkout & Order Creation
```
POST   /orders/checkout         Create order from cart (multi-vendor split)
```

### Order Management
```
GET    /orders                  List buyer's orders (with filters)
GET    /orders/{id}             Get order detail (buyer/seller)
GET    /orders/parent/{id}      Get parent order detail (buyer)
```

### Order Actions
```
POST   /orders/{id}/cancel                Cancel order
PUT    /orders/{id}/tracking              Update tracking number
POST   /orders/{id}/confirm-received      Confirm delivery
POST   /orders/{id}/return-to-sender      RTS (seller confirms return)
```

### Seller Operations
```
GET    /sellers/me/orders       List seller's orders received
```

## Order Status Flow

```
PENDING
  ↓ (payment received)
PAID
  ↓ (seller ships)
SHIPPING
  ↓ (buyer receives)
DELIVERED
  ↓ (if needed)
PARTIALLY_REFUNDED / REFUNDED / RETURNED / CANCELLED
```

## Total Endpoints: 8

## For Complete Documentation

→ See **[/docs/api/05-order-service.md](../api/05-order-service.md)**

Contains:
- Full checkout request/response
- Multi-vendor order split logic
- RTS (Return To Sender) workflow details
- Loyalty points integration
- Saga CQRS architecture details

---

**Last Updated**: 2026-04-28  
**Version**: v5.3 RTS

