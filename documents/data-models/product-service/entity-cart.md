# ENTITY-PRODUCT-006: CART

> **Service**: product-service (Port 8090)
> **Database**: PostgreSQL
> **Table**: carts
> **Source**: database-entities.md Section 4, 03_database_tables.md Section 6

---

## ERD

```mermaid
erDiagram
    CART ||--o{ CART_ITEM : "cart_id"

    CART {
        uuid id PK
        uuid customer_id UK "1 customer = 1 cart"
        varchar status "active"
        timestamp created_at
        timestamp updated_at
    }
```

---

## Data Dictionary

| # | Field | Type | Constraints | Meaning |
|---|--------|------|-------------|---------|
| 1 | `id` | UUID | PK | Unique cart identifier |
| 2 | `customer_id` | UUID | UNIQUE NOT NULL | Customer ID from Identity Service; exactly 1 active cart per customer |
| 3 | `status` | VARCHAR(50) | NOT NULL, DEFAULT 'active' | Cart state: currently always `active` |
| 4 | `created_at` | TIMESTAMP | Auto-set | Row creation timestamp |
| 5 | `updated_at` | TIMESTAMP | Auto-set | Last modification timestamp |

---

## Indexes

| Index Name | Fields | Type | Purpose |
|------------|---------|------|---------|
| `idx_cart_customer` | `(customer_id)` | PostgreSQL UNIQUE constraint | Fast cart lookup by customer; enforces 1-cart-per-customer |

---

## Business Rules Summary

| Rule | Detail |
|------|--------|
| One cart per customer | UNIQUE constraint on `customer_id` enforces 1 active cart per customer |
| Cart auto-creation | Cart is lazily created on first `POST /cart/items` |
| Cart cleared on checkout | When `order.checkout_created` event is consumed, checked-out items are removed |
| Expired flash-sale items | When `flash_sale.session_ended` event arrives, JOB-07 removes expired flash items |

---

## Cross-References

| Ref ID | Type | Description |
|--------|------|-------------|
| FR-PRODUCT-016 | Functional Requirement | Get customer cart |
| UC-PRODUCT-008 | Use Case | View cart (customer) |
| BR-PRODUCT-009 | Business Rule | One cart per customer |
| state-cart.md | State Diagram | active -> converted (on checkout) |
