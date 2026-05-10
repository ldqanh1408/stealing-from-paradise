# Flash Sale Service — Architecture Overview

> Service: flashsale-service (SVC-006, Port 8085)
> Database: PostgreSQL + Axon + Redis
> Source: `documents` micro-docs
> Generated: 2026-05-10

---

## Responsibility
Time-limited flash sale sessions with high-concurrency purchase handling via Redis Lua atomic scripts.

## Tech Stack
- Java 25, Spring Boot 4.0.4
- Axon Framework 4.13.0 (CQRS/ES)
- PostgreSQL via JPA
- Redis (ZSET triggers, Lua atomic buy, stock cache)
- Kafka (event producer)

## Key Features
- Session management (UPCOMING → ACTIVE → ENDED)
- Redis ZSET worker for near-zero latency session transitions (100ms poll vs 60s cron)
- Redis Lua atomic buy script (50k+ req/s concurrency)
- Per-user purchase limits enforced atomically in Redis
- Auto-calculated flash_price = sku.price × (1 - discount/100)
- Seller product registration with deadline enforcement
- User reminder system

## Architecture Pattern
**CQRS/ES with Redis Worker:**
- Commands: `CreateFlashSaleSessionCommand`, `RegisterFlashSaleItemCommand`
- Events: `FlashSaleSessionStartedEvent`, `FlashSaleSessionEndedEvent`, `FlashSaleItemPurchasedEvent`
- Redis Worker: polls `flash_sale:triggers` ZSET every 100ms for zero-latency state transitions

## Redis Data Structures

| Key | Type | Purpose |
|-----|------|---------|
| `flash_sale:triggers` | ZSET | Session start/end triggers (score = epoch ms) |
| `flash_sale:stock:{fs_item_id}` | STRING | Remaining flash sale stock |
| `flash_sale:users:{fs_item_id}` | HASH | Per-user purchase counts |

## Lua Buy Script Logic
```
1. GET flash_sale:stock:{item_id} → check > 0
2. HGET flash_sale:users:{item_id} {user_id} → check < limit
3. DECRBY flash_sale:stock:{item_id} {qty}
4. HINCRBY flash_sale:users:{item_id} {user_id} {qty}
5. Return SUCCESS / SOLD_OUT / LIMIT_EXCEEDED
```

## Domain Model

| Entity | Table | Key Fields |
|--------|-------|------------|
| FlashSaleSession | fs_sessions | id, name, start_time, end_time, discount, registration_deadline, status |
| FlashSaleItem | fs_items | id, session_id, product_id, discount_applied |
| FlashSaleReminder | fs_reminders | id, user_id, session_id, reminded_at |

## Kafka Integration

| Direction | Topic | Purpose |
|-----------|-------|---------|
| Produce | `flash_sale.session_started` | Trigger price sync in Product Service |
| Produce | `flash_sale.session_ended` | Reset prices, clear cart items |
| Produce | `flash_sale.item_purchased` | Update inventory in Product Service |
