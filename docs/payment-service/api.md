# 💳 Payment Service API

**Service**: Payment Service (+ Refund Management v5.3)  
**Port**: 8085  
**Base URL**: `/api/v1`  
**Version**: v5.3 RTS

## Overview

Payment Service manages:
- Stripe Connect seller onboarding
- Payment processing & transactions
- **Refund management** (consolidated v5.3)
- Webhook handling
- Stripe integration

## 📡 Kafka Integration

### Produces (Events Published)
| Topic | Consumer | Purpose |
|-------|----------|---------|
| `payment.success` | Order Service | Payment succeeded |
| `payment.failed` | Order Service | Payment failed |
| `refund.requested` | Notification Service | Refund requested |
| `refund.admin_approved` | Loyalty, Notification Services | Refund approved |
| `refund.rejected` | Notification Service | Refund rejected |
| `refund.stripe_auto` | Order, Loyalty Services | Chargeback/dispute |

### Consumes (Events Listened)
| Topic | Producer | Purpose |
|-------|----------|---------|
| `order.created` | Order Service | Receive order for payment processing |

## Key Endpoints

### Stripe Onboarding (Seller)
```
POST   /stripe/onboarding/start              Begin Stripe onboarding
GET    /stripe/onboarding/status             Check onboarding status
POST   /stripe/onboarding/refresh-link       Refresh expired link
```

### Payment Information
```
GET    /payments/parent-order/{id}           Get transaction details
POST   /stripe/webhooks                      Stripe webhook receiver
```

### Full Refund
```
POST   /orders/parent/{id}/refund            Request full refund
GET    /orders/parent/{id}/refund            Get full refund status
```

### Partial Refund (Single Seller)
```
POST   /orders/{id}/refunds                  Request partial refund
```

### Partial Refund (Multiple Sellers)
```
POST   /orders/parent/{id}/refunds/partial   Multi-seller partial refund
```

### Refund Queries
```
GET    /orders/{id}/refunds                  Refund history
GET    /orders/{id}/refunds/{refundId}       Refund detail
GET    /orders/refunds                       All buyer's refunds
GET    /orders/{id}/refunds/presigned-url    Upload evidence URL
```

### Admin Refund Management
```
GET    /admin/refunds                        List all refunds (admin)
POST   /admin/refunds/{id}/approve           Approve refund (admin)
POST   /admin/refunds/{id}/reject            Reject refund (admin)
```

## Total Endpoints: 13

## For Complete Documentation

→ See **[/docs/api/06-payment-service.md](../api/06-payment-service.md)**

Contains:
- Full payment flow details
- Stripe webhook structure
- Full/Partial refund workflows
- Refund approval process
- Tracking number support (v5.3 NEW)

---

**Last Updated**: 2026-04-28  
**Version**: v5.3 RTS

