# ENTITY-PAYMENT-004: Refund

**Domain**: Payment Service  
**Table**: `refunds`  
**Purpose**: Records buyer refund requests, admin review decisions, and Stripe refund processing. Supports FULL and PARTIAL refunds with evidence images and admin notes.  
**Last Updated**: 2026-05-10 (verified against Java source)

---

## ERD (Entity Context)

```
TRANSACTIONS           REFUNDS                              ORDERS
+--------------+       +-------------------------------+    +--------------+
| id  BIGSERIAL|<------| transaction_id  BIGINT FK     |--->| id  BIGSERIAL|
+--------------+       | order_id        BIGINT FK     |    +--------------+
                       | user_id         BIGINT             
                       | group_ref       UUID          |         REFUND_ITEMS
                       | type            VARCHAR       |    +----------------------+
                       | initiated_by    VARCHAR       |    | refund_id  BIGINT FK |
                       | amount          DECIMAL       |<---|                      |
                       | status          VARCHAR       |    +----------------------+
                       | reason          TEXT          |
                       | evidence_images JSONB         |
                       | reject_reason   TEXT          |
                       | admin_note      TEXT          |
                       | reviewed_by     BIGINT        |
                       | reviewed_at     TIMESTAMP     |
                       | refund_ref      VARCHAR       |
                       | raw_response    JSONB         |
                       | created_at / updated_at       |
                       +-------------------------------+
```

---

## Data Dictionary

| # | Column | Type | Constraints | Description |
|---|--------|------|-------------|-------------|
| 1 | `id` | BIGSERIAL | PK | Auto-increment primary key |
| 2 | `transaction_id` | BIGINT | FK → transactions.id, NOT NULL | Parent payment transaction |
| 3 | `order_id` | BIGINT | FK → orders.id, NOT NULL | Sub-order being refunded |
| 4 | `user_id` | BIGINT | NULLABLE | Buyer who requested the refund |
| 5 | `group_ref` | UUID | NULLABLE | Groups multiple refunds from the same request |
| 6 | `type` | VARCHAR | NOT NULL | `FULL` / `PARTIAL` |
| 7 | `initiated_by` | VARCHAR | NOT NULL | `BUYER` / `SELLER` / `SYSTEM` |
| 8 | `refund_reason_type` | VARCHAR | NULLABLE | `BUYER_REQUEST` / `RETURN_TO_SENDER` / `ADMIN_OVERRIDE` |
| 9 | `amount` | DECIMAL | NOT NULL | Total refund amount requested |
| 10 | `reason` | TEXT | NULLABLE | Buyer-provided refund reason |
| 11 | `status` | VARCHAR | NOT NULL, DEFAULT 'PENDING' | `PENDING` / `SUCCESS` / `FAILED` / `REJECTED` |
| 12 | `evidence_images` | JSONB | NULLABLE | Array of MinIO image URLs proving damage/issue |
| 13 | `reject_reason` | TEXT | NULLABLE | Admin-provided rejection reason |
| 14 | `admin_note` | TEXT | NULLABLE | Admin internal note |
| 15 | `reviewed_by` | BIGINT | NULLABLE | Admin who approved/rejected |
| 16 | `reviewed_at` | TIMESTAMP | NULLABLE | When admin reviewed |
| 17 | `refund_ref` | VARCHAR | NULLABLE | Stripe refund ID (format: `re_xxx`) |
| 18 | `raw_response` | JSONB | NULLABLE | Raw Stripe refund response payload |
| 19 | `created_at` | TIMESTAMP | NOT NULL | Row creation timestamp |
| 20 | `updated_at` | TIMESTAMP | NOT NULL | Last update timestamp |

---

## Indexes

| Index Name | Columns | Type | Purpose |
|------------|---------|------|---------|
| `pk_refunds` | `id` | PRIMARY KEY | Unique row identifier |
| `idx_refunds_order` | `order_id` | BTREE | Lookup refunds for an order |

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
| BR-PAYMENT-016 | Refunds require order delivered within 7 days |
| BR-PAYMENT-017 | Buyer must upload evidence images for `BUYER_REQUEST` type |
| BR-PAYMENT-018 | Admin must review and approve/reject before Stripe refund executes |
| BR-PAYMENT-019 | `group_ref` UUID groups multiple REFUNDS from one request |
| BR-PAYMENT-020 | RTS refunds (`RETURN_TO_SENDER`) auto-create full refund on `order.returned` |

---

## Related Entities

| Entity | Relationship | Via |
|--------|-------------|-----|
| TRANSACTIONS | N:1 | `transaction_id` FK |
| ORDERS | N:1 | `order_id` FK |
| REFUND_ITEMS | 1:N | `refund_id` in REFUND_ITEMS |
