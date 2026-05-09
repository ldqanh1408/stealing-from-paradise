# ENTITY-PAYMENT-004: Refund

**Domain**: Payment Service  
**Table**: `REFUNDS`  
**Purpose**: Records buyer refund requests, admin review decisions, and Stripe refund processing. Supports FULL and PARTIAL refunds with evidence images and admin notes.  
**References**: [database-entities.md](../../../docs/database/database-entities.md#7-payments--transfers), [03_database_tables.md](../../../docs/services/payment-service/03_database_tables.md)

---

## ERD (Entity Context)

```
TRANSACTIONS           REFUNDS                              ORDERS
+--------------+       +-------------------------------+    +--------------+
| id  BIGSERIAL|<------| transaction_id  BIGINT FK     |--->| id  BIGSERIAL|
+--------------+       | order_id        BIGINT FK     |    +--------------+
                       | group_ref       UUID          |
                       | type            VARCHAR       |         REFUND_ITEMS
                       | amount          DECIMAL       |    +----------------------+
                       | status          VARCHAR       |    | refund_id  BIGINT FK |
                       | reason          VARCHAR       |<---|                      |
                       | refund_ref      VARCHAR       |    +----------------------+
                       | raw_response    JSONB         |
                       | created_at / updated_at       |
                       +-------------------------------+
```

---

## Data Dictionary (Core Entity)

| # | Column | Type | Constraints | Description |
|---|--------|------|-------------|-------------|
| 1 | `id` | BIGSERIAL | PK | Auto-increment primary key |
| 2 | `transaction_id` | BIGINT | FK -> TRANSACTIONS.id | Parent payment transaction |
| 3 | `order_id` | BIGINT | FK -> ORDERS.id | Sub-order being refunded |
| 4 | `group_ref` | UUID | -- | Groups multiple refunds from the same request |
| 5 | `type` | VARCHAR | -- | `FULL` / `PARTIAL` |
| 6 | `amount` | DECIMAL | -- | Total refund amount requested |
| 7 | `status` | VARCHAR | -- | `PENDING` / `SUCCESS` / `FAILED` / `REJECTED` |
| 8 | `reason` | VARCHAR | -- | Buyer-provided refund reason |
| 9 | `refund_ref` | VARCHAR | -- | Stripe refund ID (format: `re_xxx`) |
| 10 | `raw_response` | JSONB | -- | Raw Stripe refund response payload |
| 11 | `created_at` | TIMESTAMP | NOT NULL | Row creation timestamp |
| 12 | `updated_at` | TIMESTAMP | NOT NULL | Last update timestamp |

---

## Full Column Set (Service-Level Schema)

Per [03_database_tables.md](../../../docs/services/payment-service/03_database_tables.md), the service-level REFUNDS table includes additional columns:

| # | Column | Type | Constraints | Description |
|---|--------|------|-------------|-------------|
| 6a | `initiated_by` | VARCHAR | -- | `BUYER` / `SELLER` / `SYSTEM` |
| 6b | `refund_reason_type` | VARCHAR | -- | `BUYER_REQUEST` / `RETURN_TO_SENDER` / `ADMIN_OVERRIDE` |
| 8a | `evidence_images` | JSONB | -- | Array of MinIO image URLs proving damage/issue |
| 8b | `reject_reason` | VARCHAR | -- | Admin-provided rejection reason |
| 8c | `admin_note` | TEXT | -- | Admin internal note |
| 8d | `reviewed_by` | BIGINT | FK -> ADMINS.id | Admin who approved/rejected |
| 8e | `reviewed_at` | TIMESTAMP | -- | When admin reviewed |

---

## Indexes

| Index Name | Columns | Type | Purpose |
|------------|---------|------|---------|
| `pk_refunds` | `id` | PRIMARY KEY | Unique row identifier |
| `idx_refunds_transaction` | `transaction_id` | BTREE | Lookup refunds for a transaction |
| `idx_refunds_order` | `order_id` | BTREE | Lookup refunds for an order |
| `idx_refunds_group` | `group_ref` | BTREE | Group related refunds |

---

## Status States

| Status | Meaning | Trigger |
|--------|---------|---------|
| `PENDING` | Awaiting admin review | Buyer submits refund request |
| `SUCCESS` | Refund processed via Stripe | Stripe refund API succeeds |
| `FAILED` | Stripe refund failed | Stripe refund API error |
| `REJECTED` | Admin denied refund | Admin reject action |

---

## State Transitions

See [state-refund.md](../../state-diagrams/payment-service/state-refund.md)

---

## Business Rules

| Rule ID | Description |
|---------|-------------|
| BR-PAYMENT-016 | Refunds require `order.return_window_end` not passed |
| BR-PAYMENT-017 | Buyer must upload evidence images for `BUYER_REQUEST` type |
| BR-PAYMENT-018 | Admin must review and approve/reject before Stripe refund executes |
| BR-PAYMENT-019 | `group_ref` UUID groups multiple REFUND_ITEMS from one request |
| BR-PAYMENT-020 | RTS refunds (`RETURN_TO_SENDER`) auto-create full refund on `order.returned` |

---

## Related Entities

| Entity | Relationship | Via |
|--------|-------------|-----|
| TRANSACTIONS | N:1 | `transaction_id` FK |
| ORDERS | N:1 | `order_id` FK |
| REFUND_ITEMS | 1:N | `refund_id` in REFUND_ITEMS |
| ADMINS | N:1 | `reviewed_by` FK |
