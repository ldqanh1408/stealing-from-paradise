# ENTITY-PAYMENT-005: Refund Item

**Domain**: Payment Service  
**Table**: `refund_items`  
**Purpose**: Line-item detail for each refund, linking individual order items to their refund amounts, return tracking, and evidence.  
**Last Updated**: 2026-05-10 (verified against Java source)

---

## ERD (Entity Context)

```
REFUNDS               REFUND_ITEMS                      
+--------------+      +--------------------------------+
| id  BIGSERIAL|<-----| refund_id          BIGINT FK   |
| group_ref    |      | id                BIGSERIAL PK |
+--------------+      | item_id           BIGINT FK    |
                      | quantity          INT NOT NULL |
                      | refund_amount     DECIMAL      |
                      | item_reason       TEXT         |
                      | status            VARCHAR      |
                      | return_tracking_number VARCHAR  |
                      | return_evidence_images JSONB   |
                      | returned_at       TIMESTAMP    |
                      +--------------------------------+
```

---

## Data Dictionary

| # | Column | Type | Constraints | Description |
|---|--------|------|-------------|-------------|
| 1 | `id` | BIGSERIAL | PK | Auto-increment primary key |
| 2 | `refund_id` | BIGINT | FK → refunds.id, NOT NULL | Parent refund record |
| 3 | `item_id` | BIGINT | FK → order_items.id, NOT NULL | The order line item being refunded |
| 4 | `quantity` | INT | NOT NULL | Quantity being refunded from this item |
| 5 | `refund_amount` | DECIMAL | NULLABLE | Amount refunded for this item |
| 6 | `item_reason` | TEXT | NULLABLE | Reason specific to this item |
| 7 | `status` | VARCHAR | NOT NULL, DEFAULT 'PENDING' | `PENDING` / `SUCCESS` / `FAILED` |
| 8 | `return_tracking_number` | VARCHAR | NULLABLE | Return shipment tracking number |
| 9 | `return_evidence_images` | JSONB | NULLABLE | Array of MinIO image URLs as evidence |
| 10 | `returned_at` | TIMESTAMP | NULLABLE | When seller confirmed receipt of returned goods |

Note: Field `item_reason` (Java: `itemReason`) is documented as `reason` in older docs. Field `return_evidence_images` (Java: `returnEvidenceImages`) was previously documented as `evidence_images`. The Java entity does NOT contain `reject_reason`, `reviewed_at`, or `carrier` fields — those are on the parent `refunds` table.

---

## Indexes

| Index Name | Columns | Type | Purpose |
|------------|---------|------|---------|
| `pk_refund_items` | `id` | PRIMARY KEY | Unique row identifier |

---

## Business Rules

| Rule ID | Description |
|---------|-------------|
| BR-PAYMENT-021 | Sum of all REFUND_ITEMS.refund_amount for a refund equals REFUNDS.amount |
| BR-PAYMENT-022 | Each REFUND_ITEM must reference a valid ORDER_ITEM from the same order |
| BR-PAYMENT-024 | `return_tracking_number` populated after buyer ships return; `returned_at` after seller confirms |

---

## Related Entities

| Entity | Relationship | Via |
|--------|-------------|-----|
| REFUNDS | N:1 | `refund_id` FK |
| ORDER_ITEMS | N:1 | `item_id` FK |
