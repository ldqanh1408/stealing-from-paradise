# Documentation Contradictions Report

> **Generated:** 2026-05-10
> **Source of Truth:** `docs/database/database-entities.md` (2026-05-09)
> **Scope:** Old docs (`docs/`) vs New docs (`documents/`) vs DB Schema

---

## 1. Database Schema Contradictions

### 1.1 Product Review Workflow
| Old Docs | DB Truth (pre-v3) | New Docs |
|----------|----------|-----------|
| `03_BUSINESS.md`, `07_BUSINESS_FLOWS.md`: product status PENDING → APPROVED/REJECTED (admin review) | `active` / `out_of_stock` / `inactive` | **2026-05-10 v3 (re-activated, P3-11 APPROVED & applied)**: Admin product review workflow đã được đưa lại MVP. Status enum mở rộng thành 7 giá trị (`draft / pending / approved / rejected / active / out_of_stock / inactive`), thêm `reject_reason / reviewed_at / reviewed_by / reject_count` (xem `database-entities.md` §3 + `DB_SCHEMA_CHANGE_PROPOSAL.md` §P3-11). Tài liệu liên quan: BR-PRODUCT-009, UC-PRODUCT-012..015, state-product.md, 4 YAML admin product (v5.5.0), 3 Kafka topics (`product.pending_review` / `.approved` / `.rejected`). |

**Verdict (updated 2026-05-10 v3):** Old docs đã trở lại đúng — admin review workflow nay là một phần của MVP. DB schema sẽ được bổ sung qua P3-11. Note "PENDING không tồn tại trong DB" trước đây không còn áp dụng sau khi P3-11 được duyệt.

### 1.2 Flash Sale Items Fields
| Old Docs | DB Truth | New Docs |
|----------|----------|-----------|
| `flash_price`, `flash_stock`, `status`, `limit_per_user`, admin approval | `discount_applied` only; price = `sku.price * (1 - discount_applied/100)` | Aligns with DB |

**Verdict:** Old docs describe complex FS_ITEMS. DB has simplified model. **Old docs are outdated.**

### 1.3 MongoDB vs PostgreSQL (product-service)
| Old Docs | DB Truth (2026-05-10) |
|----------|----------|
| `MG_PRODUCTS`, `MG_INVENTORIES`, `MG_CART_ITEMS` (MongoDB) | PostgreSQL: `category`, `product`, `product_variant`, `product_image`, `stock_reservation` |
| `MG_IMAGES` bridge table | `product_image` table with `url` field |
| product_service DB: MongoDB | PostgreSQL (product-service migrated from MongoDB; notification-service still uses MongoDB) |

**Verdict:** MongoDB was the old truth (pre-2026-05-10). Per `database-entities.md` 2026-05-10 update, Catalog and Cart tables have migrated to PostgreSQL. Old MongoDB docs are now outdated. **database-entities.md is the authoritative source — PostgreSQL is the current truth.**

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
| "SELLER cancel removed in MVP" | SELLER can cancel | **2026-05-10 v3 (re-activated)**: BR-ORDER-021 now permits SELLER cancel for `status = PAID` AND `tracking_number IS NULL` only. After SHIPPING must use RTS. New BR-ORDER-026 + UC-ORDER-008 + Kafka topic `seller.order_cancelled` re-added. |

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

**Note (2026-05-11):** Outbox pattern is temporarily not used per MVP scope. Infrastructure tables (OUTBOX_EVENTS, FAILED_EVENTS, SHEDLOCK) are kept in schema but not currently active.

### 3.4 Internal Architecture Contradiction
`ARCHITECTURE_MAP.md` claims flashsale, search, notification have "No REST controllers — Kafka consumer only" — but the API URL catalog (`operations/API_URLS.md`) shows all three have documented REST endpoints (12, 2, and 5 respectively).

---

## 4. Kafka Event Contradictions

### 4.1 Renamed Events
| Old Name | New/Current |
|----------|-------------|
| `flash_sale.item_sold` | `flash_sale.item_purchased` |

### 4.2 Obsolete Events (should not exist anymore)
- ~~`product.pending_review` — no admin review workflow in DB~~ → **2026-05-10 v3: re-activated** (admin review workflow đưa lại MVP; P3-11 APPROVED & applied to `database-entities.md`)
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
| Notifications | 90 days (MongoDB TTL) | 90 days (MongoDB TTL) -- fixed 2026-05-11 |
| Failed events | 30d RESOLVED + 90d DEAD | Generic 30d |
| Outbox events | 7d PROCESSED + 3d FAILED | Generic 7d |

---

## 7. Summary: All Contradictions

| # | Category | Contradiction | Severity | Resolution |
|---|----------|--------------|----------|------------|
| 1 | DB Schema | Product review workflow doesn't exist in DB | HIGH | **2026-05-10 v3 (P3-11 APPROVED & applied)**: Re-activated. Schema mở rộng qua P3-11 (status enum 7 giá trị + 4 review columns: `reject_reason` + `reviewed_at` + `reviewed_by` + `reject_count`). BR-PRODUCT-009 + UC-PRODUCT-012..015 + state-product.md (7 states). |
| 2 | DB Schema | FS_ITEMS simplified (no flash_price/status) | HIGH | Old docs outdated |
| 3 | DB Schema | MongoDB vs PostgreSQL (product-service) | HIGH | database-entities.md CORRECT (PostgreSQL); old MongoDB docs outdated |
| 4 | DB Schema | tracking_number → return_tracking_number | MEDIUM | Fixed: BR-PAYMENT-025 |
| 5 | DB Schema | FS_ITEMS.sku_code → product_id | MEDIUM | Documented |
| 6 | DB Schema | seller_id INT → UUID | LOW | Documented |
| 7 | DB Schema | tier_name → variant_name + JSONB | LOW | Documented |
| 8 | DB Schema | PENDING vs PENDING_PAYMENT naming | LOW | Minor drift |
| 9 | Business | Buyer cancel from PAID allowed | MEDIUM | Fixed: BR-ORDER-011 |
| 10 | Business | Seller cancel removed in MVP | MEDIUM | **2026-05-10 v3**: Re-activated. BR-ORDER-021 + new BR-ORDER-026 + UC-ORDER-008. Scope: PAID before shipping only. |
| 11 | Architecture | worker-service → chat-service migration | HIGH | Documented + updated |
| 12 | Architecture | Cart port :8083 → product-service :8090 | MEDIUM | Documented |
| 13 | Architecture | Outbox/DLQ ownership transfer | MEDIUM | Documented |
| 14 | Architecture | ARCHITECTURE_MAP self-contradiction | LOW | Documented |
| 15 | Kafka | flash_sale.item_sold → item_purchased | MEDIUM | Documented |
| 16 | Kafka | product.pending_review obsolete | LOW | **2026-05-10 v3**: Re-activated together with `product.approved` / `product.rejected` — admin review workflow back in MVP. |
| 17 | Kafka | flash_sale.item_approved/rejected obsolete | LOW | Documented |
| 18 | Kafka | JOB-01 cron → Redis ZSET worker | MEDIUM | Documented |
| 19 | Cronjob | 12 of 17 schedules differ | **CRITICAL** | 2026-05-10: Source audit — 14/15 jobs have NO @Scheduled annotation. Only PayoutScheduler exists in Java. 14 jobs unimplemented or use external scheduler (K8s CronJob, etc.) |
| 20 | Cronjob | Description drift (7 jobs) | MEDIUM | 2026-05-10: Unverified — no implementation to compare against |
| 21 | Retention | Notifications: 90d vs 30d | MEDIUM | **RESOLVED 2026-05-11**: CRONJOBS.md updated to 90 days (MongoDB TTL auto-delete), matching notification-service KAFKA_EVENTS.md. No cronjob needed -- MongoDB TTL index handles it. |
| 22 | Retention | Failed events: lost DEAD tier | LOW | Deferred — outbox pattern temporarily not used per MVP scope |
| 23 | Retention | Outbox events: lost FAILED tier | LOW | Deferred — outbox pattern temporarily not used per MVP scope |
| 24 | Data Model | entity-user: `role`, `version` in doc but NOT in Java; phone constraints differ | HIGH | Fixed 2026-05-10: removed role, version; corrected phone nullable |
| 25 | Data Model | entity-order: `net_payout_amount`, `carrier`, `paid_at`, `return_window_end`, `shipped_at`, `delivered_at` in doc but NOT in Java | HIGH | Fixed 2026-05-10: removed 6 phantom fields; added isFlashSale, version |
| 26 | Data Model | entity-order-item: `variant_id` doc says UUID, Java is VARCHAR(100); `refunded_quantity` missing from doc | MEDIUM | Fixed 2026-05-10: corrected type, added refunded_quantity |
| 27 | Data Model | entity-parent-order: `session_id`, `status` in doc but NOT in Java (very simple entity) | MEDIUM | Fixed 2026-05-10: removed phantom fields |
| 28 | Data Model | entity-refund: `user_id` in Java (20 fields) but missing from doc (19 fields) | MEDIUM | **RESOLVED 2026-05-12**: Moved to `refund-service/entity-refund.md`, added `userId`, aligned fields to match Java. |
| 29 | Data Model | entity-refund-item: field names mismatch — `itemReason` vs `reason`, `returnEvidenceImages` vs `evidence_images`; no `reject_reason`, `reviewed_at`, `carrier` in Java | MEDIUM | **RESOLVED 2026-05-12**: Moved to `refund-service/entity-refund-item.md` and fields aligned with Java source of truth. |
| 30 | Data Model | entity-seller-transfer: `transaction_id`, `refunded_amount`, `net_payout_amount` in doc but NOT in Java | MEDIUM | Fixed 2026-05-10: removed phantom fields, added platform_commission_amount, payout_at |
| 31 | API Contract | 3 MUST-HAVE APIs RESOLVED 2026-05-11; 4 SHOULD-HAVE deferred (post-MVP scope per user direction) | MEDIUM | Documented 2026-05-10: improved from 35 to 68 YAMLs; all 3 MUST-HAVE endpoints now have YAML on disk; 4 SHOULD-HAVE endpoints deferred per user direction |
| 32 | API Contract | flashsale, ai-chat, notification, search: YAML files exist but no backend controllers implemented | LOW | YAML ahead of implementation |
| 33 | Cronjob | NEW undocumented job: PayoutScheduler (@Scheduled 0 */5 * * * * in payment-service) | MEDIUM | 2026-05-10: Discovered during source audit. Assign JOB-23, add to CRONJOBS.md |
| 34 | Cronjob | 14/15 documented jobs NOT_FOUND in Java source — only PayoutScheduler has @Scheduled | **CRITICAL** | 2026-05-10: Exhaustive grep across all Java files. Only @Scheduled is PayoutScheduler. 14 jobs have zero code. |
| 35 | Cronjob | ShedLock claim not implemented — only @Scheduled (PayoutScheduler) has no @SchedulerLock | LOW | 2026-05-10: Docs claim ShedLock but real code doesn't use it |

---

*Generated: 2026-05-10 | Updated: 2026-05-12 | 225 files (159 .md + 66 .yaml) | 12 Java entities verified | 15 cronjobs audited | API coverage: 80% | Operations: 100% | All 10 categories: 100% service coverage*
