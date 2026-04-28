# ⚡ Flash Sale Service API

**Service**: Flash Sale Service  
**Port**: 8086  
**Base URL**: `/api/v1`  
**Version**: v5.3 RTS

## Overview

Flash Sale Service provides:
- Flash sale session management
- High-concurrency purchase handling (50k+ req/s)
- Redis Lua scripts for atomic operations
- Flash sale item approval workflow
- User reminders

## 📡 Kafka Integration

### Produces (Events Published)
| Topic | Consumer | Purpose |
|-------|----------|---------|
| `flash_sale.session_started` | Notification Service | Session started notification |
| `flash_sale.session_ended` | Notification Service | Session ended notification |
| `flash_sale.item_approved` | Notification Service | Item approved notification |
| `flash_sale.item_sold` | Inventory Service | Update sold count |

### Consumes (Events Listened)
- None directly

## Key Endpoints

### Sessions (Public)
```
GET    /flash-sale/sessions                 List sessions (UPCOMING/ACTIVE only)
GET    /flash-sale/sessions/{id}            Get session details with items
```

### Sessions (Admin)
```
POST   /flash-sale/sessions                 Create session (admin)
GET    /admin/flash-sale/sessions           List all sessions (admin)
PUT    /admin/flash-sale/sessions/{id}      Update session (admin)
DELETE /admin/flash-sale/sessions/{id}      Delete session (admin)
```

### Items (Seller)
```
POST   /flash-sale/sessions/{id}/items      Register product (seller)
```

### Items (Admin)
```
POST   /flash-sale/sessions/{id}/items/{id}/approve    Approve item (admin)
POST   /admin/flash-sale/items/{id}/reject             Reject item (admin)
```

### Purchase
```
POST   /flash-sale/sessions/{id}/buy        Buy flash sale item (Redis atomic)
```

### Reminders
```
POST   /flash-sale/sessions/{id}/reminders      Register reminder (buyer)
DELETE /flash-sale/sessions/{id}/reminders      Cancel reminder (buyer)
```

## High-Concurrency Design

**Redis Lua Script**:
```lua
-- Atomic check-and-decrement for oversell prevention
local key = "fs:item:" .. fs_item_id
local limit_key = "fs:user:" .. user_id .. ":" .. fs_item_id

-- Check stock
local stock = redis.call('GET', key)
if stock <= 0 then return {ERR, "SOLD_OUT"} end

-- Check user limit
local user_qty = redis.call('GET', limit_key) or 0
if user_qty >= limit_per_user then return {ERR, "LIMIT_EXCEEDED"} end

-- Decrement atomically
redis.call('DECR', key)
redis.call('INCR', limit_key)
return {OK, stock - 1}
```

## Total Endpoints: 11

## For Complete Documentation

→ See **[/docs/api/07-flash-sale-service.md](../api/07-flash-sale-service.md)**

Contains:
- Session lifecycle details
- Item approval workflow
- Lua script implementation
- High-concurrency patterns
- Reminder notification flow

---

**Last Updated**: 2026-04-28  
**Version**: v5.3 RTS

