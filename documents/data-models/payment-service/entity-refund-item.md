# ENTITY-PAYMENT-005: Refund Item

**Domain**: Payment Service  
**Table**: `REFUND_ITEMS`  
**Purpose**: Line-item detail for each refund, linking individual order items to their refund amounts, return tracking, and evidence. Supports granular per-item refund reasons and return status tracking.  
**References**: [database-entities.md](../../../docs/database/database-entities.md#7-payments--transfers), [03_database_tables.md](../../../docs/services/payment-service/03_database_tables.md)

---

## ERD (Entity Context)

```
REFUNDS               REFUND_ITEMS                         ORDER_ITEMS
+--------------+      +--------------------------------+   (Order Svc)
| id  BIGSERIAL|<-----| refund_id          BIGINT FK   |  +--------------+
| group_ref    |      | id                BIGSERIAL PK |  | id  BIGSERIAL|
+--------------+      | item_id           BIGINT FK    |->|              |
                      | refund_amount      DECIMAL     |  +--------------+
                      | reason             VARCHAR     |
                      | status             VARCHAR     |
                      | evidence_images    JSONB       |
                      | reject_reason      VARCHAR     |
                      | reviewed_at        TIMESTAMP   |
                      | return_tracking_number VARCHAR  |
                      | carrier            VARCHAR     |
                      | returned_at        TIMESTAMP   |
                      +--------------------------------+
```

---

## Data Dictionary

| # | Column | Type | Constraints | Description |
|---|--------|------|-------------|-------------|
| 1 | `id` | BIGSERIAL | PK | Auto-increment primary key |
| 2 | `refund_id` | BIGINT | FK -> REFUNDS.id | Parent refund record |
| 3 | `item_id` | BIGINT | FK -> ORDER_ITEMS.id | The order line item being refunded |
| 4 | `refund_amount` | DECIMAL | -- | Amount refunded for this item |
| 5 | `reason` | VARCHAR | -- | Reason specific to this item |
| 6 | `status` | VARCHAR | -- | `PENDING` / `SUCCESS` / `FAILED` |
| 7 | `evidence_images` | JSONB | -- | Array of MinIO image URLs as evidence |
| 8 | `reject_reason` | VARCHAR | -- | Admin rejection reason for this item |
| 9 | `reviewed_at` | TIMESTAMP | -- | When admin reviewed this item |
| 10 | `return_tracking_number` | VARCHAR | -- | Return shipment tracking number |
| 11 | `carrier` | VARCHAR | -- | Shipping carrier for return |
| 12 | `returned_at` | TIMESTAMP | -- | When seller confirmed receipt of returned goods |

---

## Indexes

| Index Name | Columns | Type | Purpose |
|------------|---------|------|---------|
| `pk_refund_items` | `id` | PRIMARY KEY | Unique row identifier |
| `idx_refund_items_refund` | `refund_id` | BTREE | Lookup items for a refund |
| `idx_refund_items_item` | `item_id` | BTREE | Lookup refund history for an order item |

---

## Status Values

| Status | Meaning |
|--------|---------|
| `PENDING` | Awaiting admin review |
| `SUCCESS` | Refund processed for this item |
| `FAILED` | Refund failed for this item |

---

## Business Rules

| Rule ID | Description |
|---------|-------------|
| BR-PAYMENT-021 | Sum of all REFUND_ITEMS.refund_amount for a refund equals REFUNDS.amount |
| BR-PAYMENT-022 | Each REFUND_ITEM must reference a valid ORDER_ITEM from the same order |
| BR-PAYMENT-023 | `evidence_images` required when buyer claims item damage or wrong item |
| BR-PAYMENT-024 | `return_tracking_number` populated after buyer ships return; `returned_at` after seller confirms |

---

## Related Entities

| Entity | Relationship | Via |
|--------|-------------|-----|
| REFUNDS | N:1 | `refund_id` FK |
| ORDER_ITEMS | N:1 | `item_id` FK |
