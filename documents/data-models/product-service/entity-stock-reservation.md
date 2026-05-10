# ENTITY-PRODUCT-005: STOCK_RESERVATION

> **Service**: product-service (Port 8090)
> **Database**: MongoDB
> **Collection**: mg_stock_reservations
> **Source**: database-entities.md Section 3, 03_database_tables.md Section 5

---

## ERD

```mermaid
erDiagram
    PRODUCT_VARIANT ||--o{ STOCK_RESERVATION : "variant_id"

    STOCK_RESERVATION {
        objectid _id PK
        objectid variant_id "reference"
        string session_id "checkout session"
        numberint quantity
        string status "pending/confirmed/released"
        isodate expires_at "NOW()+15min"
        isodate created_at
        isodate updated_at
    }
```

---

## Data Dictionary

| # | Field | Type | Constraints | Meaning |
|---|--------|------|-------------|---------|
| 1 | `_id` | ObjectId | PK, auto-generated | Unique reservation identifier |
| 2 | `variant_id` | ObjectId | NOT NULL, application-level reference | Reserved variant (SKU) |
| 3 | `session_id` | String | NOT NULL | Checkout session ID; links to Order Service's `parent_orders.session_id` |
| 4 | `quantity` | NumberInt | NOT NULL | Number of units reserved |
| 5 | `status` | String | NOT NULL, DEFAULT 'pending' | Reservation lifecycle: `pending`, `confirmed`, `released` |
| 6 | `expires_at` | ISODate | NOT NULL | TTL = NOW() + 15 minutes; MongoDB TTL index auto-removes expired pending reservations |
| 7 | `created_at` | ISODate | Auto-set | Document creation timestamp |
| 8 | `updated_at` | ISODate | Auto-set | Last modification timestamp |

---

## Indexes

| Index Name | Fields | Type | Purpose |
|------------|---------|------|---------|
| `idx_reservation_variant` | `{ variant_id: 1 }` | B-tree | Check active reservations for a given variant |
| `idx_reservation_session` | `{ session_id: 1 }` | B-tree | Lookup by checkout session |
| `idx_reservation_status` | `{ status: 1 }` | B-tree | Filter by status (e.g., pending cleanup) |
| `idx_reservation_expires` | `{ expires_at: 1 }` | TTL | MongoDB TTL index with `expireAfterSeconds: 0`; auto-deletes documents when `expires_at` passes |
| `idx_reservation_cleanup` | `{ status: 1, expires_at: 1 }` | B-tree | Cleanup job: find expired `pending` reservations not yet cleaned by TTL |

---

## Reservation Flow

```
1. Customer clicks "Dat hang"
   -> INSERT stock_reservation (status=pending, expires_at=NOW()+15min)
   -> Redis: DECRBY stock:{variant_id} {quantity}
   -> DB: Update product_variant field stock_quantity = stock_quantity - {quantity}
          with optimistic lock check on version field
   -> Update fails? -> Rollback, return "out of stock"

2. Payment succeeds
   -> UPDATE stock_reservation SET status = 'confirmed'
   -> Stock already deducted; no further action

3. Payment fails / timeout
   -> UPDATE stock_reservation SET status = 'released'
   -> Redis: INCR stock:{variant_id} {quantity}
   -> DB: Update product_variant field stock_quantity = stock_quantity + {quantity}

4. TTL index + cleanup job
   -> TTL index on expires_at auto-deletes expired documents
   -> Cleanup job (runs every 1-5 min) handles stock restoration for expired pending reservations
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
