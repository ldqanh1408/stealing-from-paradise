# Documentation Contradictions Report

> **Generated:** 2026-05-10
> **Source of Truth:** `docs/database/database-entities.md` (2026-05-09)
> **Scope:** Old docs (`docs/`) vs New docs (`documents/`) vs DB Schema

---

## 1. Database Schema Contradictions

### 1.1 Product Review Workflow
| Old Docs | DB Truth | New Docs |
|----------|----------|-----------|
| `03_BUSINESS.md`, `07_BUSINESS_FLOWS.md`: product status PENDING → APPROVED/REJECTED (admin review) | `active` / `out_of_stock` / `inactive` | Aligns with DB |

**Verdict:** Old docs describe admin product review flow that doesn't exist in DB. **Old docs are outdated.**

### 1.2 Flash Sale Items Fields
| Old Docs | DB Truth | New Docs |
|----------|----------|-----------|
| `flash_price`, `flash_stock`, `status`, `limit_per_user`, admin approval | `discount_applied` only; price = `sku.price * (1 - discount_applied/100)` | Aligns with DB |

**Verdict:** Old docs describe complex FS_ITEMS. DB has simplified model. **Old docs are outdated.**

### 1.3 MongoDB → PostgreSQL Migration
| Old Docs | DB Truth |
|----------|----------|
| `MG_PRODUCTS`, `MG_INVENTORIES`, `MG_CART_ITEMS` (MongoDB) | PostgreSQL: `PRODUCT`, `PRODUCT_VARIANT`, `CART`, `CART_ITEM` |
| `MG_IMAGES` bridge table | `PRODUCT_IMAGE.url` text column |
| product_service DB: MongoDB | PostgreSQL (only notification-service uses MongoDB) |

**Verdict:** Old docs predate catalog+cart migration from MongoDB to PostgreSQL. **Old docs are outdated.**

### 1.4 Field Name Errors
| Old Docs | DB Truth |
|----------|----------|
| `REFUND_ITEMS.tracking_number` | `REFUND_ITEMS.return_tracking_number` |
| `FS_ITEMS.sku_code` | `FS_ITEMS.product_id` (UUID) |
| `seller_id` as INT | `seller_id` as UUID (in product context) |
| `tier_name` | Replaced by `variant_name` + `variant_attributes` (JSONB) |

### 1.5 Order Status Naming
| Old Docs | DB Truth |
|----------|----------|
| "PENDING" for all initial states | `PARENT_ORDERS.status = PENDING_PAYMENT` |

**Minor naming drift.** New docs use correct DB names.

---

## 2. Business Rule Contradictions

### 2.1 Buyer Cancellation Scope
| Old (`03_BUSINESS.md`) | New Original | Fixed |
|------------------------|-------------|-------|
| Buyer can cancel from PENDING **or PAID** | Only PENDING → CANCELLED | BR-ORDER-011 updated to allow PENDING/PAID |

### 2.2 Seller Cancellation
| Old (`03_BUSINESS.md`) | New Original | Fixed |
|------------------------|-------------|-------|
| "SELLER cancel removed in MVP" | SELLER can cancel | BR-ORDER-021 updated: "removed in MVP" |

---

## 3. Service Architecture Contradictions

### 3.1 worker-service → chat-service Migration
- Old docs: `worker-service (:8086)` — outbox relay, DLQ, deadlines, flash_sale.reminder
- Git commit `06a8d0f`: "migrate worker-service to chat-service"
- New docs: Port 8086 absent; chat-service (:8093) exists
- Kafka: `flash_sale.reminder` producer reassigned; `order.created`/`payment.success` consumer removed

### 3.2 Cart Service Port
| Old Docs | Truth |
|----------|-------|
| Cart service on :8083 | `:8083` is order-service; cart lives in product-service (:8090) |

### 3.3 Outbox/DLQ Ownership
| Old Docs | New Docs |
|----------|----------|
| worker-service owns OUTBOX_EVENTS, FAILED_EVENTS | payment-service (JOB-04, JOB-05, JOB-06) |

### 3.4 Internal Architecture Contradiction
`ARCHITECTURE_MAP.md` claims flashsale, search, notification have "No REST controllers — Kafka consumer only" — but `API_URLS_COMPACT.md` shows all three have documented REST endpoints (12, 2, and 5 respectively).

---

## 4. Kafka Event Contradictions

### 4.1 Renamed Events
| Old Name | New/Current |
|----------|-------------|
| `flash_sale.item_sold` | `flash_sale.item_purchased` |

### 4.2 Obsolete Events (should not exist anymore)
- `product.pending_review` — no admin review workflow in DB
- `flash_sale.item_approved` — no item approval in DB
- `flash_sale.item_rejected` — no item rejection in DB

### 4.3 Missing Consumers
- flashsale KAFKA_EVENTS.md: Missing Product Service as consumer of `flash_sale.session_started`

### 4.4 Flash Sale Activation Mechanism
| Old Docs | Truth |
|----------|-------|
| JOB-01 cron for session status transitions | Redis ZSET worker triggers near-zero latency transitions |

---

## 5. Cronjob Schedule Contradictions (12 of 17 jobs differ)

| Job | Old (`05_OPERATIONS.md`) | New (`CRONJOBS.md`) | Delta |
|-----|------------------------|-------------------|-------|
| JOB-02 | Every 1 min | Every 5 min | 5x |
| JOB-04 | Every 10 sec | Every 5 min | **30x** |
| JOB-05 | 03:00 daily | 02:00 daily | 1h offset |
| JOB-06 | Day 30 monthly | 02:00 daily | Cadence change |
| JOB-07 | 04:00 daily | Every 2 min | **Massive** |
| JOB-08 | 02:00 daily | 03:00 daily | 1h offset |
| JOB-12 | 05:00 daily | 03:00 daily | 2h offset |
| JOB-13 | Every 5 min | Every 10 min | 2x |
| JOB-15 | 02:00 daily | Every 1 min | **Massive** |
| JOB-16 | 03:00 Sunday | 02:00 daily | Weekly→Daily |
| JOB-21 | Every 5 min (ACTIVE) | 04:00 daily | Real-time→Batch |
| JOB-22 | 02:00 daily | Every 6 hours | 1x→4x |

**Neither source is authoritative.** Must reconcile against actual `@Scheduled` annotations in Java source code.

### 5.1 Cronjob Description Drift
- **JOB-02**: Old says "reminder 15 min before flash sale" — New is generic "Reminder dispatcher"
- **JOB-07**: Old cleans flash-sale + inactive carts >90d — New says ">24h inactive"
- **JOB-08**: Old has tier-specific retention (0/30/180/365 days) — New simplified to ">30d"
- **JOB-10**: Old: deleted_at>30d + stock_locked=0 + MinIO+ES cleanup — New: ">90d"
- **JOB-13**: Old: 30min normal / 10min flash-sale — New: only ">30min"
- **JOB-21**: Old: real-time Redis-DB reconciliation — New: post-sale batch
- **JOB-22**: Old: RTS exclusion + Kafka outbox — New: lost both details

---

## 6. Retention Policy Contradictions

| Domain | Old (`05_OPERATIONS.md`) | New (`CRONJOBS.md`) |
|--------|-------------------------|---------------------|
| Notifications | 90 days (MongoDB TTL) | 30 days |
| Failed events | 30d RESOLVED + 90d DEAD | Generic 30d |
| Outbox events | 7d PROCESSED + 3d FAILED | Generic 7d |

---

## 7. Summary: All Contradictions

| # | Category | Contradiction | Severity | Resolution |
|---|----------|--------------|----------|------------|
| 1 | DB Schema | Product review workflow doesn't exist in DB | HIGH | Old docs outdated |
| 2 | DB Schema | FS_ITEMS simplified (no flash_price/status) | HIGH | Old docs outdated |
| 3 | DB Schema | MongoDB → PostgreSQL migration | HIGH | Old docs outdated |
| 4 | DB Schema | tracking_number → return_tracking_number | MEDIUM | Fixed: BR-PAYMENT-025 |
| 5 | DB Schema | FS_ITEMS.sku_code → product_id | MEDIUM | Documented |
| 6 | DB Schema | seller_id INT → UUID | LOW | Documented |
| 7 | DB Schema | tier_name → variant_name + JSONB | LOW | Documented |
| 8 | DB Schema | PENDING vs PENDING_PAYMENT naming | LOW | Minor drift |
| 9 | Business | Buyer cancel from PAID allowed | MEDIUM | Fixed: BR-ORDER-011 |
| 10 | Business | Seller cancel removed in MVP | MEDIUM | Fixed: BR-ORDER-021 |
| 11 | Architecture | worker-service → chat-service migration | HIGH | Documented + updated |
| 12 | Architecture | Cart port :8083 → product-service :8090 | MEDIUM | Documented |
| 13 | Architecture | Outbox/DLQ ownership transfer | MEDIUM | Documented |
| 14 | Architecture | ARCHITECTURE_MAP self-contradiction | LOW | Documented |
| 15 | Kafka | flash_sale.item_sold → item_purchased | MEDIUM | Documented |
| 16 | Kafka | product.pending_review obsolete | LOW | Documented |
| 17 | Kafka | flash_sale.item_approved/rejected obsolete | LOW | Documented |
| 18 | Kafka | JOB-01 cron → Redis ZSET worker | MEDIUM | Documented |
| 19 | Cronjob | 12 of 17 schedules differ | **CRITICAL** | Reconcile from source |
| 20 | Cronjob | Description drift (7 jobs) | MEDIUM | Reconcile from source |
| 21 | Retention | Notifications: 90d vs 30d | MEDIUM | Reconcile from source |
| 22 | Retention | Failed events: lost DEAD tier | LOW | Documented |
| 23 | Retention | Outbox events: lost FAILED tier | LOW | Documented |

---

*Generated: 2026-05-10 | Files audited: 33 old docs vs 164 new docs*
