# ENTITY-PRODUCT-007: CART_ITEM

> **Service**: product-service (Port 8090)
> **Database**: PostgreSQL
> **Table**: cart_items
> **Source**: database-entities.md Section 4, 03_database_tables.md Section 7

---

## ERD

```mermaid
erDiagram
    CART ||--o{ CART_ITEM : "cart_id"
    PRODUCT_VARIANT ||--o{ CART_ITEM : "variant_id"

    CART_ITEM {
        uuid id PK
        uuid cart_id "FK CASCADE"
        uuid variant_id "FK"
        int quantity "DEFAULT 1"
        decimal price_snapshot
        varchar variant_name_snapshot
        text variant_image_snapshot
        timestamp created_at
        timestamp updated_at
    }
```

---

## Data Dictionary

| # | Field | Type | Constraints | Meaning |
|---|--------|------|-------------|---------|
| 1 | `id` | UUID | PK | Unique cart item identifier |
| 2 | `cart_id` | UUID | NOT NULL, FK → cart.id ON DELETE CASCADE | Parent cart |
| 3 | `variant_id` | UUID | NOT NULL, FK → product_variant.id | The SKU in the cart |
| 4 | `quantity` | INT | NOT NULL, DEFAULT 1 | Desired quantity; > 0 and <= 1000 per API validation |
| 5 | `price_snapshot` | DECIMAL(18,2) | NOT NULL | Price at time of adding to cart; used for change detection |
| 6 | `variant_name_snapshot` | VARCHAR(500) | NULLABLE | Cached variant name for display even if variant is deleted |
| 7 | `variant_image_snapshot` | TEXT | NULLABLE | Cached image URL for display even if image is removed |
| 8 | `created_at` | TIMESTAMP | Auto-set | When item was added to cart |
| 9 | `updated_at` | TIMESTAMP | Auto-set | Last quantity/price update |

**UNIQUE(cart_id, variant_id)** — each variant appears at most once per cart.

---

## Indexes

| Index Name | Fields | Type | Purpose |
|------------|---------|------|---------|
| `idx_cart_item_cart` | `(cart_id)` | B-tree | Fetch all items for a given cart |
| `idx_cart_item_variant` | `(variant_id)` | B-tree | Find carts containing a specific variant |
| `idx_cart_item_cart_variant` | `(cart_id, variant_id)` | PostgreSQL UNIQUE constraint | Enforces one entry per variant per cart; enables UPSERT |

---

## Lazy Evaluation Strategy

When `GET /cart` is called, the service compares real-time variant data against snapshots:

| Condition | Flag Returned | UI Action |
|-----------|---------------|-----------|
| `product_variant.price != price_snapshot` | `price_changed` | Show old vs new price; prompt user to confirm update |
| `product_variant.stock_quantity == 0` | `out_of_stock` | Dim item, disable checkbox |
| `product_variant.status != 'active'` | `unavailable` | Dim item, disable checkbox |
| `product_variant.stock_quantity < quantity` | `insufficient_stock` | Warn user, cap quantity |

If any of these conditions exist at Checkout Preview, the API returns **409 Conflict** and the UI blocks progression until the cart is refreshed.

---

## Cross-References

| Ref ID | Type | Description |
|--------|------|-------------|
| FR-PRODUCT-017 | Functional Requirement | Add item to cart |
| FR-PRODUCT-018 | Functional Requirement | Update cart item quantity |
| FR-PRODUCT-019 | Functional Requirement | Remove item from cart |
| UC-PRODUCT-009 | Use Case | Add to cart (customer) |
| UC-PRODUCT-010 | Use Case | Update cart item (customer) |
| UC-PRODUCT-011 | Use Case | Remove from cart (customer) |
| BR-PRODUCT-010 | Business Rule | Cart item uniqueness (per variant) |
| BR-PRODUCT-011 | Business Rule | Price snapshot rules |
| BR-PRODUCT-012 | Business Rule | Quantity limits |
