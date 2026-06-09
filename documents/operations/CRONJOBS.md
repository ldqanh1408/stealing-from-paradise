# Cronjobs Reference
Service: platform
Updated: 2026-06-09

## Design Principles

| Principle | Description |
|-----------|-------------|
| Service Ownership | Each job runs in the service that owns the primary data. |
| Idempotent | Jobs can run repeatedly without duplicating business effects. |
| Event Bridge | Jobs that change cross-service state publish Kafka events. |
| Retention | Financial/audit records are retained; user notifications use MongoDB TTL. |
| Operational Safety | Heavy cleanup runs outside peak hours where possible. |

## Implemented Scheduled Jobs

Source of truth: Java `@Scheduled` annotations in `backend/*-service/src/main/java`.

| Job | Schedule | Service | Class | Responsibility | Status |
|-----|----------|---------|-------|----------------|--------|
| JOB-01 | `fixedDelay=${flashsale.session-scheduler.delay-ms:60000}` | flashsale-service | `FlashSaleSessionScheduler` | Move flash sale sessions through UPCOMING/ACTIVE/ENDED and publish session lifecycle events. | IMPLEMENTED |
| JOB-08 | `${flashsale.scheduler.cleanup-cron:0 0 3 * * *}` | flashsale-service | `FlashSaleMaintenanceScheduler` | Clean up soft-deleted flash sale sessions after retention. | IMPLEMENTED |
| JOB-21 | `${flashsale.scheduler.reconcile-cron:0 0 4 * * *}` | flashsale-service | `FlashSaleMaintenanceScheduler` | Reconcile post-flash-sale item stock from Redis/JPA state. | IMPLEMENTED |
| JOB-07 | `${product.scheduler.stale-cart-cron:0 0 */2 * * *}` | product-service | `ProductCleanupScheduler` | Remove stale cart items. | IMPLEMENTED |
| JOB-10 | `${product.scheduler.hard-delete-cron:0 0 3 * * SUN}` | product-service | `ProductCleanupScheduler` | Hard-delete products after soft-delete retention. | IMPLEMENTED |
| JOB-16 | `${product.scheduler.auto-hide-cron:0 0 2 * * *}` | product-service | `ProductCleanupScheduler` | Auto-hide rejected products after retention. | IMPLEMENTED |
| JOB-13 | `${order.scheduler.auto-cancel-cron:0 */10 * * * *}` | order-service | `OrderLifecycleScheduler` | Auto-cancel stale pending parent orders and publish `order.auto_cancelled`. | IMPLEMENTED |
| JOB-22 | `${order.scheduler.auto-deliver-cron:0 0 */6 * * *}` | order-service | `OrderLifecycleScheduler` | Auto-deliver stale shipping orders and publish order delivery events. | IMPLEMENTED |
| JOB-15 | `${payment.scheduler.onboarding-url-cron:0 0 * * * *}` | payment-service | `StripeOnboardingUrlScheduler` | Nullify expired Stripe onboarding URLs. | IMPLEMENTED |
| JOB-23 | `0 */5 * * * *` | payment-service | `PayoutScheduler` | Process seller payouts after the return window. | IMPLEMENTED |
| RES-01 | `${reservation.cleanup.interval-ms:180000}` | product-service | `ReservationCleanupScheduler` | Expire stock reservations and publish `stock.reservation.expired`. | IMPLEMENTED |

## Native TTL / Non-Cron Retention

| ID | Mechanism | Store | Responsibility | Status |
|----|-----------|-------|----------------|--------|
| JOB-09 | MongoDB TTL index | `mg_notifications.created_at` | Delete notifications after 90 days. | IMPLEMENTED |

## Deferred Jobs

| Job | Description | Reason |
|-----|-------------|--------|
| JOB-04 | Outbox event publisher | Outbox pattern is deferred; current services publish Kafka directly. |
| JOB-05 | Outbox event cleanup | Depends on JOB-04/outbox tables. |
| JOB-06 | Failed event cleanup | Depends on deferred failed-event/DLQ audit tables. |
| JOB-12 | ShedLock stale-lock cleanup | ShedLock is not currently part of the implemented scheduler stack. |

## Retention Policies

| Domain | Retention | Use Case |
|--------|-----------|----------|
| notifications | 90 days | MongoDB TTL auto-delete. |
| flash sale soft-deletes | 30 days | Cleanup by `FlashSaleMaintenanceScheduler`. |
| product soft-deletes | 90 days | Cleanup by `ProductCleanupScheduler`. |
| seller transfers | retained | Financial reconciliation and payout audit. |
| transactions/refunds | retained | Payment compliance and support audit. |
