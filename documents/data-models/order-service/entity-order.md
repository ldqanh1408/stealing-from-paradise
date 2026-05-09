# ENTITY-ORDER-002: ORDERS

**Stable ID:** ENTITY-ORDER-002
**Table:** `orders`
**Schema:** PostgreSQL (order-service, port 8083)
**Last Updated:** 2026-05-09

---

## ERD (Entity-Relationship Diagram)

```
┌──────────────────┐    ┌──────────────────┐
│  PARENT_ORDERS    │    │     SELLERS       │
│──────────────────│    │──────────────────│
│ id (BIGSERIAL PK)│    │ id (BIGSERIAL PK)│
└────────┬─────────┘    └────────┬─────────┘
         │ FK                    │ FK
         │                       │
┌──────────────────┐             │
│    CUSTOMERS      │             │
│──────────────────│             │
│ id (BIGSERIAL PK)│             │
└────────┬─────────┘             │
         │ FK                    │
         ▼                       ▼
┌──────────────────────────────────────────────────────────┐
│                        ORDERS                             │
│──────────────────────────────────────────────────────────│
│ id                BIGSERIAL PK                            │
│ parent_order_id   BIGINT FK → parent_orders.id            │
│ seller_id         BIGINT FK → sellers.id                  │
│ order_code        VARCHAR UNIQUE (readable display code)   │
│ customer_id       BIGINT FK → customers.id                │
│ total_amt         DECIMAL(18,2)                           │
│ final_amt         DECIMAL(18,2)                           │
│ net_payout_amount DECIMAL(18,2)                           │
│ status            VARCHAR(50)                             │
│ cancelled_by      VARCHAR(50)                             │
│ cancel_reason     TEXT                                    │
│ shipping_address  JSONB                                   │
│ shipping_deadline TIMESTAMP                               │
│ tracking_number   VARCHAR                                 │
│ carrier           VARCHAR                                 │
│ paid_at           TIMESTAMP                               │
│ return_window_end TIMESTAMP                               │
│ shipped_at        TIMESTAMP                               │
│ delivered_at      TIMESTAMP                               │
│ created_at        TIMESTAMP                               │
│ updated_at        TIMESTAMP                               │
└───────────────────────┬──────────────────────────────────┘
                        │ 1:N
                        ▼
┌──────────────────────────────────────────────────────────┐
│                     ORDER_ITEMS                           │
│──────────────────────────────────────────────────────────│
│ id         BIGSERIAL PK                                   │
│ order_id   BIGINT FK → orders.id                          │
│ ...                                                       │
└──────────────────────────────────────────────────────────┘
```

---

## Data Dictionary

| # | Column | Type | Constraints | Description |
|---|--------|------|-------------|-------------|
| 1 | `id` | BIGSERIAL | PK, NOT NULL | Auto-increment primary key |
| 2 | `parent_order_id` | BIGINT | FK → parent_orders.id, NOT NULL | Parent order grouping sub-orders |
| 3 | `seller_id` | BIGINT | FK → sellers.id, NOT NULL | Seller fulfilling this sub-order |
| 4 | `order_code` | VARCHAR | UNIQUE, NOT NULL | Human-readable display code (e.g. `DH20260506-1A2B3C`) |
| 5 | `customer_id` | BIGINT | FK → customers.id, NOT NULL | Buyer (denormalized from parent for direct access) |
| 6 | `total_amt` | DECIMAL(18,2) | NOT NULL | Sum of item prices before discounts for this sub-order |
| 7 | `final_amt` | DECIMAL(18,2) | NOT NULL | Actual amount charged for this sub-order |
| 8 | `net_payout_amount` | DECIMAL(18,2) | NULL | Net amount payable to seller after platform commission |
| 9 | `status` | VARCHAR(50) | NOT NULL, CHECK(status IN ('PENDING','PAID','SHIPPING','DELIVERED','RETURNED','REFUNDED','PARTIALLY_REFUNDED','CANCELLED')) | Sub-order lifecycle status |
| 10 | `cancelled_by` | VARCHAR(50) | NULL, CHECK(cancelled_by IN ('BUYER','SELLER','SYSTEM')) | Actor who cancelled; NULL if not cancelled |
| 11 | `cancel_reason` | TEXT | NULL | Reason provided at cancellation |
| 12 | `shipping_address` | JSONB | NULL | Snapshot of delivery address at checkout |
| 13 | `shipping_deadline` | TIMESTAMP | NULL | Deadline for seller to provide tracking (created_at + 3 days) |
| 14 | `tracking_number` | VARCHAR | NULL | Shipping carrier tracking number |
| 15 | `carrier` | VARCHAR | NULL | Shipping carrier name (e.g. GHTK, Viettel Post) |
| 16 | `paid_at` | TIMESTAMP | NULL | Timestamp when payment succeeded |
| 17 | `return_window_end` | TIMESTAMP | NULL | Deadline for buyer to request return (delivered_at + 7 days) |
| 18 | `shipped_at` | TIMESTAMP | NULL | Timestamp when seller provided tracking |
| 19 | `delivered_at` | TIMESTAMP | NULL | Timestamp when buyer confirmed receipt |
| 20 | `created_at` | TIMESTAMP | NOT NULL, DEFAULT NOW() | Order creation timestamp |
| 21 | `updated_at` | TIMESTAMP | NOT NULL, DEFAULT NOW() | Last modification timestamp |

### Status Enum (8 States)

| # | Status | Description |
|---|--------|-------------|
| 1 | `PENDING` | Order created, awaiting payment |
| 2 | `PAID` | Payment confirmed via Stripe webhook |
| 3 | `SHIPPING` | Seller uploaded tracking number |
| 4 | `DELIVERED` | Buyer confirmed receipt (or auto-confirmed by JOB-22) |
| 5 | `RETURNED` | Seller confirmed RTS (Return To Sender) |
| 6 | `REFUNDED` | Full refund processed |
| 7 | `PARTIALLY_REFUNDED` | Partial refund processed (some items refunded) |
| 8 | `CANCELLED` | Order cancelled before shipping |

---

## Indexes

| Index Name | Columns | Type | Purpose |
|------------|---------|------|---------|
| `orders_pkey` | `id` | PRIMARY KEY B-tree | Primary key lookup |
| `idx_orders_parent_order` | `parent_order_id` | B-tree | Find sub-orders by parent |
| `idx_orders_seller` | `seller_id` | B-tree | Seller order listing |
| `idx_orders_customer` | `customer_id` | B-tree | Buyer order listing |
| `idx_orders_order_code` | `order_code` | UNIQUE B-tree | Lookup by display code |
| `idx_orders_status` | `status` | B-tree | Filter by status |
| `idx_orders_created_at` | `created_at` | B-tree | Date-range queries |

---

## Relationships

| From | To | Cardinality | On Delete |
|------|----|-------------|-----------|
| `orders.parent_order_id` | `parent_orders.id` | N:1 | RESTRICT |
| `orders.seller_id` | `sellers.id` | N:1 | RESTRICT |
| `orders.customer_id` | `customers.id` | N:1 | RESTRICT |
| `order_items.order_id` | `orders.id` | 1:N | CASCADE |

---

## JSONB Structure: shipping_address

```json
{
  "full_address": "123 Nguyen Trai, Phuong 2, Q.3, TP.HCM",
  "province_id": 79,
  "district_id": 760,
  "address_id": 7
}
```

| Field | Type | Description |
|-------|------|-------------|
| `full_address` | string | Full delivery address text |
| `province_id` | integer | Province/city code |
| `district_id` | integer | District code |
| `address_id` | integer | Reference to addresses.id in identity DB |

---

## Business Rules

| Rule ID | Rule |
|---------|------|
| BR-ORDER-010 | Sub-order created per seller during multi-vendor checkout |
| BR-ORDER-011 | `order_code` format: `OR-YYYYMMDD-{id}` |
| BR-ORDER-012 | `shipping_deadline` = `created_at` + 3 days |
| BR-ORDER-013 | `return_window_end` = `delivered_at` + 7 days |
| BR-ORDER-014 | Cancel only allowed when status = PENDING |
| BR-ORDER-015 | Ship (tracking) only allowed when status = PAID |
| BR-ORDER-016 | Confirm delivery only allowed when status = SHIPPING |
| BR-ORDER-017 | RTS only allowed when status = SHIPPING |
| BR-ORDER-018 | Buyer refund request only allowed when status = DELIVERED AND within return_window_end |

---

## Cross-References

- **ENTITY-ORDER-001:** [PARENT_ORDERS](entity-parent-order.md)
- **ENTITY-ORDER-003:** [ORDER_ITEMS](entity-order-item.md)
- **BR:** [br-checkout.md](../../business-rules/order-service/br-checkout.md)
- **BR:** [br-order-lifecycle.md](../../business-rules/order-service/br-order-lifecycle.md)
- **API:** All order-service API contracts
- **State:** [state-order.md](../../state-diagrams/order-service/state-order.md)
- **Traceability:** [traceability-matrix.md](../../traceability/order-service/traceability-matrix.md)
