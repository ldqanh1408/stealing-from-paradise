## Cronjobs Reference
Service: platform
Generated: 2026-05-09
Updated: 2026-05-11

### Design Principles

| Principle | Description |
|-----------|-------------|
| Service Ownership | Each job runs in the service that owns the primary data |
| Distributed Lock | ShedLock (PostgreSQL provider) ensures single-node execution |
| Soft Delete First | Data marked before hard delete, with grace period |
| Audit Trail | Financial tables (TRANSACTIONS, REFUNDS) never hard-deleted |
| Idempotent | Every cleanup job must be idempotent |
| Off-peak | Heavy jobs run 02:00–05:00 UTC+7 |
| Batch Size | Max 500–1000 records per batch to avoid table locks |

### Cronjob Inventory (15 real cronjobs (1 implemented: JOB-23, 14 post-MVP) + JOB-09 MongoDB TTL)

> **JOB ID gaps:** JOB-03, JOB-11, JOB-14, JOB-17, JOB-18, JOB-19, JOB-20 are intentionally skipped (reserved for future use).

#### flashsale-service (:8085)

| Job | Cron | Description | Tables | Status |
|-----|------|-------------|--------|--------|
| JOB-01 | */1 * * * * | Session lifecycle: UPCOMING→ACTIVE→ENDED | FS_SESSIONS | NOT_IMPLEMENTED (post-MVP) |
| JOB-02 | */5 * * * * | Reminder dispatcher | FS_REMINDERS | NOT_IMPLEMENTED (post-MVP) |
| JOB-08 | 0 3 * * * | Flash sale data cleanup (soft-deleted > 30d) | FS_SESSIONS | NOT_IMPLEMENTED (post-MVP) |
| JOB-21 | 0 4 * * * | Stock reconciliation post-flash-sale | PRODUCT_VARIANT | NOT_IMPLEMENTED (post-MVP) |

> **CROSS-SERVICE NOTE (JOB-21):** JOB-21 operates on `PRODUCT_VARIANT` (owned by product-service) but is scheduled in flashsale-service. This is a known cross-service concern for MVP — flashsale-service has the most accurate view of post-sale stock delta and reconciles directly. Post-MVP, this should move to product-service or use a Kafka event-driven approach.

#### product-service (:8090)

| Job | Cron | Description | Tables | Status |
|-----|------|-------------|--------|--------|
| JOB-07 | 0 */2 * * * | Stale cart cleanup (> 24h inactive) | CART, CART_ITEM | NOT_IMPLEMENTED (post-MVP) |
| JOB-10 | 0 3 * * 0 | Soft-deleted products hard cleanup (> 90d) | PRODUCT | NOT_IMPLEMENTED (post-MVP) |
| JOB-16 | 0 2 * * * | Auto-hide rejected products (> 30d) | PRODUCT | NOT_IMPLEMENTED (post-MVP) |

#### order-service (:8083)

| Job | Cron | Description | Tables | Status |
|-----|------|-------------|--------|--------|
| JOB-13 | */10 * * * * | Auto-cancel stale PENDING orders (> 30min) | PARENT_ORDERS, ORDERS | NOT_IMPLEMENTED (post-MVP) |
| JOB-22 | 0 */6 * * * | Auto-deliver stale SHIPPING orders (> 7d no update) | ORDERS | NOT_IMPLEMENTED (post-MVP) |

#### payment-service (:8082)

| Job | Cron | Description | Tables | Status |
|-----|------|-------------|--------|--------|
| JOB-04 | */5 * * * * | Outbox event publisher | OUTBOX_EVENTS | NOT_IMPLEMENTED (outbox pattern deferred) |
| JOB-05 | 0 2 * * * | Outbox events cleanup (> 7d) | OUTBOX_EVENTS | NOT_IMPLEMENTED (outbox pattern deferred) |
| JOB-06 | 0 2 * * * | Failed events cleanup (> 30d) | FAILED_EVENTS | NOT_IMPLEMENTED (outbox pattern deferred) |
| JOB-12 | 0 3 * * * | ShedLock stale lock cleanup | SHEDLOCK | NOT_IMPLEMENTED (post-MVP) |
| JOB-15 | 0 */1 * * * | Nullify expired Stripe onboarding URLs (> 24h) | SELLER_STRIPE_ACCOUNTS | NOT_IMPLEMENTED (post-MVP) |
| **JOB-23** | **0 */5 * * * * (every 5 min)** | **PayoutScheduler — process seller payouts after return window expiry (batch 100)** | **SELLER_TRANSFERS** | **IMPLEMENTED** |

#### notification-service (:8092)

| Job | Cron | Description | Tables | Status |
|-----|------|-------------|--------|--------|
| JOB-09 | — | TTL Index (MongoDB native — NOT a real cronjob) | MG_NOTIFICATIONS | N/A (MongoDB TTL Index) |

> **AUDIT NOTE (2026-05-10, updated 2026-05-11):** Java source audit reveals only JOB-23 (PayoutScheduler in payment-service) has an actual `@Scheduled` annotation in the codebase. The other 14 real cronjobs have NO corresponding `@Scheduled` in Java and are NOT_IMPLEMENTED. JOB-09 is not a cronjob at all -- it is a MongoDB TTL Index. JOB-04, JOB-05, JOB-06 (outbox pattern) are deferred post-MVP. See CONTRADICTIONS.md #19, #33, #34.

### Retention Policies

| Domain | Retention | Use Case |
|--------|-----------|----------|
| account.* | 7 days | Account security events |
| product.* | 30 days | Product lifecycle tracking |
| order.* | 30 days | Order history & audit |
| payment.*, refund.* | 90 days | Payment compliance |
| outbox_events | 7 days | Event relay cleanup (deferred post-MVP) |
| failed_events | 30 days | DLQ audit trail (deferred post-MVP) |
| notifications | 90 days (MongoDB TTL auto-delete) | User notification history |
