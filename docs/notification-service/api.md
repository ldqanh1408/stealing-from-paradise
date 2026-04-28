# 🔔 Notification Service API

**Service**: Notification Service  
**Port**: 8088  
**Base URL**: `/api/v1`  
**Version**: v5.3 RTS

## Overview

Notification Service provides:
- Real-time SSE (Server-Sent Events) updates
- Notification history with pagination
- Mark read functionality
- Event aggregation from all services
- MongoDB with 90-day TTL

## 📡 Kafka Integration

### Produces (Events Published)
- None (notification aggregator only)

### Consumes (Events Listened)
| Topic | Producer | Purpose |
|-------|----------|---------|
| `account.locked` | Identity Service | Account lock notification |
| `account.auto_locked` | Worker Service | Auto-lock notification |
| `account.unlocked` | Identity Service | Account unlock notification |
| `appeal.resolved` | Identity Service | Appeal decision notification |
| `loyalty.points_earned` | Identity Service | Points earned notification |
| `product.rejected` | Admin Service | Product rejection notification |
| `order.shipped` | Order Service | Shipping notification |
| `order.returned` | Order Service | Return notification |
| `order.auto_cancelled` | Worker Service | Auto-cancel notification |
| `refund.requested` | Payment Service | Refund request notification |
| `refund.admin_approved` | Payment Service | Refund approval notification |
| `refund.rejected` | Payment Service | Refund rejection notification |
| `flash_sale.session_started` | Flash Sale Service | Flash sale start notification |
| `flash_sale.session_ended` | Flash Sale Service | Flash sale end notification |
| `flash_sale.item_approved` | Flash Sale Service | Item approval notification |
| `flash_sale.item_rejected` | Flash Sale Service | Item rejection notification |
| `seller.posting_suspended` | Identity Service | Posting suspension notification |
| `seller.posting_resumed` | Identity Service | Posting resumption notification |

## Key Endpoints

### Real-Time Updates (SSE)
```
GET    /notifications/stream       WebSocket SSE stream (keep-alive)
```

**Format**: `text/event-stream`
```
data: {"notif_id":"...","type":"...","title":"...","body":"..."}
```

### Notification History
```
GET    /notifications              Get notifications (paginated)
```

**Query Parameters**:
```
is_read  - true/false (filter read status)
page     - Page number (default: 0)
size     - Items per page (default: 20, max: 100)
```

### Mark as Read
```
PATCH  /notifications/{id}/read           Mark single as read
PATCH  /notifications/read-all             Mark all as read
```

### Statistics
```
GET    /notifications/unread-count        Get unread count
```

## Notification Types

- `ACCOUNT_LOCKED` - Account locked
- `ACCOUNT_UNLOCKED` - Account unlocked
- `REFUND_APPROVED` - Refund approved
- `REFUND_REJECTED` - Refund rejected
- `ORDER_SHIPPED` - Order shipped
- `PRODUCT_REJECTED` - Product rejected
- `FLASH_SALE_STARTED` - Flash sale started
- `FLASH_SALE_ENDED` - Flash sale ended
- `LOYALTY_POINTS_EARNED` - Points credited
- (15+ more types)

## Total Endpoints: 5

## For Complete Documentation

→ See **[/docs/api/08-notification-service.md](../api/08-notification-service.md)**

Contains:
- SSE implementation details
- Full notification types list
- Kafka consumer patterns
- MongoDB TTL configuration
- Real-time update examples

---

**Last Updated**: 2026-04-28  
**Version**: v5.3 RTS

