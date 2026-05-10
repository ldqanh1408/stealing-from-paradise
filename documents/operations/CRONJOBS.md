## Cronjobs Reference
Service: platform
Generated: 2026-05-09

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

### Cronjob Inventory (17 jobs)

#### flashsale-service (:8085)

| Job | Cron | Description | Tables |
|-----|------|-------------|--------|
| JOB-01 | */1 * * * * | Session lifecycle: UPCOMING→ACTIVE→ENDED | FS_SESSIONS |
| JOB-02 | */5 * * * * | Reminder dispatcher | FS_REMINDERS |
| JOB-08 | 0 3 * * * | Flash sale data cleanup (soft-deleted > 30d) | FS_SESSIONS |
| JOB-21 | 0 4 * * * | Stock reconciliation post-flash-sale | PRODUCT_VARIANT |

#### product-service (:8090)

| Job | Cron | Description | Tables |
|-----|------|-------------|--------|
| JOB-07 | 0 */2 * * * | Stale cart cleanup (> 24h inactive) | CART, CART_ITEM |
| JOB-10 | 0 3 * * 0 | Soft-deleted products hard cleanup (> 90d) | PRODUCT |
| JOB-16 | 0 2 * * * | Auto-hide rejected products (> 30d) | PRODUCT |

#### order-service (:8083)

| Job | Cron | Description | Tables |
|-----|------|-------------|--------|
| JOB-13 | */10 * * * * | Auto-cancel stale PENDING orders (> 30min) | PARENT_ORDERS, ORDERS |
| JOB-22 | 0 */6 * * * | Auto-deliver stale SHIPPING orders (> 7d no update) | ORDERS |

#### payment-service (:8082)

| Job | Cron | Description | Tables |
|-----|------|-------------|--------|
| JOB-04 | */5 * * * * | Outbox event publisher | OUTBOX_EVENTS |
| JOB-05 | 0 2 * * * | Outbox events cleanup (> 7d) | OUTBOX_EVENTS |
| JOB-06 | 0 2 * * * | Failed events cleanup (> 30d) | FAILED_EVENTS |
| JOB-12 | 0 3 * * * | ShedLock stale lock cleanup | SHEDLOCK |
| JOB-15 | 0 */1 * * * | Nullify expired Stripe onboarding URLs (> 24h) | SELLER_STRIPE_ACCOUNTS |
| **JOB-23** | **0 */5 * * * * (every 5 min)** | **PayoutScheduler — process seller payouts after return window expiry (batch 100)** | **SELLER_TRANSFERS** |

> **⚠️ AUDIT NOTE (2026-05-10):** Java source audit reveals only JOB-23 (PayoutScheduler) has an actual `@Scheduled` annotation in the codebase. The other 14 jobs (JOB-01 through JOB-22) have NO corresponding `@Scheduled` in Java. They may be implemented via external scheduling (K8s CronJob, separate scheduler service) or are not yet implemented. See CONTRADICTIONS.md #19, #33, #34.

#### notification-service (:8092)

| Job | Cron | Description | Tables |
|-----|------|-------------|--------|
| JOB-09 | — | TTL Index (MongoDB native, no cron needed) | MG_NOTIFICATIONS |

### Retention Policies

| Domain | Retention | Use Case |
|--------|-----------|----------|
| account.* | 7 days | Account security events |
| product.* | 30 days | Product lifecycle tracking |
| order.* | 30 days | Order history & audit |
| payment.*, refund.* | 90 days | Payment compliance |
| outbox_events | 7 days | Event relay cleanup |
| failed_events | 30 days | DLQ audit trail |
| notifications | 30 days | User notification history |
