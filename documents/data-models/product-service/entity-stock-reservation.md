# ENTITY-PRODUCT-005: STOCK_RESERVATION

> **Service**: product-service (Port 8090)
> **Schema**: catalog
> **Source**: database-entities.md Section 3, 03_database_tables.md Section 5

---

## ERD

```mermaid
erDiagram
    PRODUCT_VARIANT ||--o{ STOCK_RESERVATION : "variant_id"

    STOCK_RESERVATION {
        uuid id PK
        uuid variant_id FK
        varchar session_id "checkout session"
        int quantity
        varchar status "pending/confirmed/released"
        timestamp expires_at "NOW()+15min"
        timestamp created_at
        timestamp updated_at
    }
```

---

## Data Dictionary

| # | Column | Type | Constraints | Meaning |
|---|--------|------|-------------|---------|
| 1 | `id` | UUID | PK, DEFAULT gen_random_uuid() | Unique reservation identifier |
| 2 | `variant_id` | UUID | FK REFERENCES product_variant(id), NOT NULL | Reserved variant (SKU) |
| 3 | `session_id` | VARCHAR(100) | NOT NULL | Checkout session ID; links to Order Service's `parent_orders.session_id` |
| 4 | `quantity` | INT | NOT NULL | Number of units reserved |
| 5 | `status` | VARCHAR(50) | NOT NULL, DEFAULT 'pending' | Reservation lifecycle: `pending`, `confirmed`, `released` |
| 6 | `expires_at` | TIMESTAMP | NOT NULL | TTL = NOW() + 15 minutes; cleanup job releases expired pending reservations |
| 7 | `created_at` | TIMESTAMP | DEFAULT NOW() | Row creation timestamp |
| 8 | `updated_at` | TIMESTAMP | DEFAULT NOW() | Last modification timestamp |

---

## Indexes

| Index Name | Columns | Type | Purpose |
|------------|---------|------|---------|
| `idx_reservation_variant` | `variant_id` | B-tree | Check active reservations for a given variant |
| `idx_reservation_session` | `session_id` | B-tree | Lookup by checkout session |
| `idx_reservation_status` | `status` | B-tree | Filter by status (e.g., pending cleanup) |
| `idx_reservation_expires` | `expires_at` | B-tree | Cleanup job: find expired `pending` reservations |

---

## Reservation Flow

```
1. Customer clicks "Dat hang"
   -> INSERT stock_reservation (status=pending, expires_at=NOW()+15min)
   -> Redis: DECRBY stock:{variant_id} {quantity}
   -> DB: UPDATE product_variant SET stock_quantity = stock_quantity - {quantity}
          WHERE stock_quantity >= {quantity} AND version = N
   -> rows_affected = 0? -> Rollback, return "out of stock"

2. Payment succeeds
   -> UPDATE stock_reservation SET status = 'confirmed'
   -> Stock already deducted; no further action

3. Payment fails / timeout
   -> UPDATE stock_reservation SET status = 'released'
   -> Redis: INCR stock:{variant_id} {quantity}
   -> DB: UPDATE product_variant SET stock_quantity = stock_quantity + {quantity}

4. Cleanup job (runs every 1-5 min)
   -> Find all reservations WHERE status = 'pending' AND expires_at < NOW()
   -> Release each (step 3)
```

---

## Cross-References

| Ref ID | Type | Description |
|--------|------|-------------|
| FR-PRODUCT-014 | Functional Requirement | Reserve stock during checkout |
| FR-PRODUCT-015 | Functional Requirement | Release expired reservations |
| UC-PRODUCT-007 | Use Case | Reserve stock (system) |
| BR-PRODUCT-007 | Business Rule | Reservation expiry (15 min TTL) |
| BR-PRODUCT-008 | Business Rule | Optimistic lock for concurrent reservations |
| state-stock-reservation.md | State Diagram | pending -> confirmed / released |
