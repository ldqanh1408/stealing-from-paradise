# ENTITY-ORDER-003: ORDER_ITEMS

**Stable ID:** ENTITY-ORDER-003
**Table:** `order_items`
**Schema:** PostgreSQL (order-service, port 8083)
**Last Updated:** 2026-05-09

---

## ERD (Entity-Relationship Diagram)

```
┌──────────────────────┐    ┌──────────────────────┐
│       ORDERS          │    │   PRODUCT_VARIANT     │
│──────────────────────│    │──────────────────────│
│ id (BIGSERIAL PK)    │    │ id (UUID PK)         │
└──────────┬───────────┘    └──────────┬───────────┘
           │ FK                        │ FK
           │                           │
           ▼                           ▼
┌──────────────────────────────────────────────────────────┐
│                     ORDER_ITEMS                           │
│──────────────────────────────────────────────────────────│
│ id              BIGSERIAL PK                              │
│ order_id        BIGINT FK → orders.id                     │
│ sku_code        VARCHAR (snapshot)                        │
│ variant_id      UUID FK → product_variant.id              │
│ name_snapshot   VARCHAR                                   │
│ image_snapshot  VARCHAR                                   │
│ price_snapshot  DECIMAL(18,2)                             │
│ quantity        INT                                       │
│ fs_item_id      BIGINT FK → fs_items.id (nullable)        │
│ created_at      TIMESTAMP                                 │
└──────────────────────┬───────────────────────────────────┘
                       │
                       │ FK (nullable)
                       ▼
           ┌──────────────────────┐
           │      FS_ITEMS         │
           │──────────────────────│
           │ id (BIGSERIAL PK)    │
           └──────────────────────┘
```

---

## Data Dictionary

| # | Column | Type | Constraints | Description |
|---|--------|------|-------------|-------------|
| 1 | `id` | BIGSERIAL | PK, NOT NULL | Auto-increment primary key |
| 2 | `order_id` | BIGINT | FK → orders.id, NOT NULL | Sub-order this line item belongs to |
| 3 | `sku_code` | VARCHAR | NOT NULL | SKU code snapshot at time of purchase |
| 4 | `variant_id` | UUID | FK → product_variant.id, NOT NULL | Product variant purchased |
| 5 | `name_snapshot` | VARCHAR | NOT NULL | Product name snapshot at time of purchase |
| 6 | `image_snapshot` | VARCHAR | NOT NULL | Product image URL snapshot at time of purchase |
| 7 | `price_snapshot` | DECIMAL(18,2) | NOT NULL | Unit price snapshot at time of purchase |
| 8 | `quantity` | INT | NOT NULL, CHECK(quantity > 0) | Quantity purchased |
| 9 | `fs_item_id` | BIGINT | FK → fs_items.id, NULLABLE | Flash sale item reference; NULL = regular purchase |
| 10 | `created_at` | TIMESTAMP | NOT NULL, DEFAULT NOW() | Line item creation timestamp |

### Snapshot Fields

All `snapshot` fields capture values at purchase time and are **never updated** after creation. This ensures historical accuracy even if product details change later.

| Snapshot Field | Source at Checkout | Purpose |
|----------------|-------------------|---------|
| `sku_code` | `product_variant.variant_code` | Identify SKU sold |
| `name_snapshot` | `product.name` | Display in order history |
| `image_snapshot` | `product_variant.image_url` or `product_image.url` | Display in order detail |
| `price_snapshot` | `product_variant.price` (or flash sale computed price) | Financial record |

---

## Indexes

| Index Name | Columns | Type | Purpose |
|------------|---------|------|---------|
| `order_items_pkey` | `id` | PRIMARY KEY B-tree | Primary key lookup |
| `idx_order_items_order` | `order_id` | B-tree | Find all items in an order |
| `idx_order_items_variant` | `variant_id` | B-tree | Find orders containing a variant |
| `idx_order_items_fs_item` | `fs_item_id` | B-tree | Find orders from a flash sale item |

---

## Relationships

| From | To | Cardinality | On Delete |
|------|----|-------------|-----------|
| `order_items.order_id` | `orders.id` | N:1 | CASCADE |
| `order_items.variant_id` | `product_variant.id` | N:1 | RESTRICT |
| `order_items.fs_item_id` | `fs_items.id` | N:1 | SET NULL |

---

## Computed Values

| Expression | Description |
|------------|-------------|
| `price_snapshot * quantity` | Line total for this item |
| `SUM(price_snapshot * quantity) OVER order_id` | Sub-order total (should equal `orders.total_amt`) |

---

## Business Rules

| Rule ID | Rule |
|---------|------|
| BR-ORDER-020 | All snapshot fields populated at checkout and immutable thereafter |
| BR-ORDER-021 | `quantity` must be >= 1 |
| BR-ORDER-022 | `fs_item_id` populated only when item was purchased via flash sale |
| BR-ORDER-023 | Refund quantity per item cannot exceed `quantity` minus already-refunded quantity |

---

## Cross-References

- **ENTITY-ORDER-001:** [PARENT_ORDERS](entity-parent-order.md)
- **ENTITY-ORDER-002:** [ORDERS](entity-order.md)
- **BR:** [br-checkout.md](../../business-rules/order-service/br-checkout.md)
- **BR:** [br-order-lifecycle.md](../../business-rules/order-service/br-order-lifecycle.md)
- **API:** [api-post-orders-checkout.yaml](../../api-contracts/order-service/api-post-orders-checkout.yaml)
- **Traceability:** [traceability-matrix.md](../../traceability/order-service/traceability-matrix.md)
