# ENTITY-ORDER-001: PARENT_ORDERS

**Stable ID:** ENTITY-ORDER-001
**Table:** `parent_orders`
**Schema:** PostgreSQL (order-service, port 8083)
**Last Updated:** 2026-05-09

---

## ERD (Entity-Relationship Diagram)

```
┌──────────────────┐       ┌──────────────────────────┐
│    CUSTOMERS      │       │   STOCK_RESERVATION       │
│──────────────────│       │──────────────────────────│
│ id (BIGSERIAL PK)│       │ id (UUID PK)             │
└────────┬─────────┘       │ session_id (VARCHAR 100)  │
         │                 └────────────┬─────────────┘
         │ FK                          │ FK (UNIQUE)
         ▼                             ▼
┌──────────────────────────────────────────────────────────┐
│                    PARENT_ORDERS                          │
│──────────────────────────────────────────────────────────│
│ id            BIGSERIAL PK                                │
│ customer_id   BIGINT FK → customers.id                    │
│ session_id    VARCHAR(100) FK →                           │
│               stock_reservation.session_id UNIQUE          │
│ total_amt     DECIMAL(18,2)                               │
│ final_amt     DECIMAL(18,2)                               │
│ status        VARCHAR(50)                                 │
│ created_at    TIMESTAMP                                   │
│ updated_at    TIMESTAMP                                   │
└───────────────────────┬──────────────────────────────────┘
                        │ 1:N
                        ▼
┌──────────────────────────────────────────────────────────┐
│                       ORDERS                              │
│──────────────────────────────────────────────────────────│
│ id              BIGSERIAL PK                              │
│ parent_order_id BIGINT FK → parent_orders.id              │
│ ...                                                       │
└──────────────────────────────────────────────────────────┘
```

---

## Data Dictionary

| # | Column | Type | Constraints | Description |
|---|--------|------|-------------|-------------|
| 1 | `id` | BIGSERIAL | PK, NOT NULL | Auto-increment primary key |
| 2 | `customer_id` | BIGINT | FK → customers.id, NOT NULL | Buyer who placed the order |
| 3 | `session_id` | VARCHAR(100) | FK → stock_reservation.session_id, UNIQUE, NOT NULL | Checkout reservation session; one parent per session |
| 4 | `total_amt` | DECIMAL(18,2) | NOT NULL | Sum of all sub-order total_amt before platform adjustments |
| 5 | `final_amt` | DECIMAL(18,2) | NOT NULL | Actual amount charged to buyer via Stripe |
| 6 | `status` | VARCHAR(50) | NOT NULL, CHECK(status IN ('PENDING_PAYMENT','PAID','CANCELLED')) | Parent order lifecycle status |
| 7 | `created_at` | TIMESTAMP | NOT NULL, DEFAULT NOW() | Order creation timestamp |
| 8 | `updated_at` | TIMESTAMP | NOT NULL, DEFAULT NOW() | Last modification timestamp |

### Status Transitions

```
PENDING_PAYMENT ──┬──▶ PAID
                  │
                  └──▶ CANCELLED
```

| Status | Description | Trigger |
|--------|-------------|---------|
| `PENDING_PAYMENT` | Awaiting Stripe payment confirmation | POST /orders/checkout |
| `PAID` | Payment confirmed by Stripe webhook | Consumed `payment.success` Kafka event |
| `CANCELLED` | All sub-orders cancelled or payment timeout | All sub-orders CANCELLED or JOB-13 auto-cancel |

---

## Indexes

| Index Name | Columns | Type | Purpose |
|------------|---------|------|---------|
| `parent_orders_pkey` | `id` | PRIMARY KEY B-tree | Primary key lookup |
| `idx_parent_orders_customer` | `customer_id` | B-tree | List buyer's parent orders |
| `idx_parent_orders_session` | `session_id` | UNIQUE B-tree | Lookup by checkout session; enforce 1:1 |
| `idx_parent_orders_status` | `status` | B-tree | Filter by payment status |

---

## Relationships

| From | To | Cardinality | On Delete |
|------|----|-------------|-----------|
| `parent_orders.customer_id` | `customers.id` | N:1 | RESTRICT |
| `parent_orders.session_id` | `stock_reservation.session_id` | 1:1 | RESTRICT |
| `orders.parent_order_id` | `parent_orders.id` | 1:N | RESTRICT |

---

## Business Rules

| Rule ID | Rule |
|---------|------|
| BR-ORDER-001 | One parent order per checkout session (enforced by UNIQUE session_id) |
| BR-ORDER-002 | `parent_order.final_amt` = SUM(all sub-orders.final_amt) |
| BR-ORDER-003 | `parent_order.status` transitions: PENDING_PAYMENT → PAID or PENDING_PAYMENT → CANCELLED |
| BR-ORDER-004 | Parent order CANCELLED only when ALL sub-orders are CANCELLED |

---

## Cross-References

- **ENTITY-ORDER-002:** [ORDERS](entity-order.md)
- **ENTITY-ORDER-003:** [ORDER_ITEMS](entity-order-item.md)
- **BR:** [br-checkout.md](../../business-rules/order-service/br-checkout.md)
- **BR:** [br-order-lifecycle.md](../../business-rules/order-service/br-order-lifecycle.md)
- **API:** [api-post-orders-checkout.yaml](../../api-contracts/order-service/api-post-orders-checkout.yaml)
- **State:** [state-order.md](../../state-diagrams/order-service/state-order.md)
- **Traceability:** [traceability-matrix.md](../../traceability/order-service/traceability-matrix.md)
