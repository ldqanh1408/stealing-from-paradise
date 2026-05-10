# ENTITY-PRODUCT-003: PRODUCT_VARIANT

> **Service**: product-service (Port 8090)
> **Database**: MongoDB
> **Collection**: mg_product_variants
> **Source**: database-entities.md Section 3, 03_database_tables.md Section 3

---

## ERD

```mermaid
erDiagram
    PRODUCT ||--o{ PRODUCT_VARIANT : "product_id"
    PRODUCT_VARIANT ||--o{ STOCK_RESERVATION : "variant_id"
    PRODUCT_VARIANT ||--o{ CART_ITEM : "variant_id"
    PRODUCT_VARIANT ||--o{ PRODUCT_IMAGE : "variant_id"

    PRODUCT_VARIANT {
        objectid _id PK
        objectid product_id "reference"
        string variant_code UK
        string variant_name
        object variant_attributes
        numberdecimal price "NOT NULL"
        numberdecimal original_price
        numberint stock_quantity "DEFAULT 0"
        string status "active/out_of_stock/inactive"
        numberint version "DEFAULT 1, optimistic lock"
        string image_url
        isodate created_at
        isodate updated_at
    }
```

---

## Data Dictionary

| # | Field | Type | Constraints | Meaning |
|---|--------|------|-------------|---------|
| 1 | `_id` | ObjectId | PK, auto-generated | Unique variant (SKU) identifier |
| 2 | `product_id` | ObjectId | NOT NULL, application-level reference | Parent product. No CASCADE; deletion handled in application layer. |
| 3 | `variant_code` | String | Unique | Internal seller SKU code (e.g., "NK-AIR-RED-XL"); 3-50 chars, alphanumeric+dash |
| 4 | `variant_name` | String | NULLABLE | Group label for variant dimension (e.g., "Mau sac, Size") |
| 5 | `variant_attributes` | Object | NULLABLE | Concrete variant values (e.g., `{"color":"Den","size":"M"}`) |
| 6 | `price` | NumberDecimal | NOT NULL | Current sale price in VND; > 0, max 9,999,999,999 |
| 7 | `original_price` | NumberDecimal | NULLABLE | Original/strikethrough price for discount display |
| 8 | `stock_quantity` | NumberInt | NOT NULL, DEFAULT 0 | Available inventory count |
| 9 | `status` | String | NOT NULL, DEFAULT 'active' | Variant lifecycle: `active`, `out_of_stock`, `inactive` |
| 10 | `version` | NumberInt | NOT NULL, DEFAULT 1 | Optimistic lock for concurrent stock updates; enforced at application layer |
| 11 | `image_url` | String | NULLABLE | Quick variant image URL (from MinIO) |
| 12 | `created_at` | ISODate | Auto-set | Document creation timestamp |
| 13 | `updated_at` | ISODate | Auto-set | Last modification timestamp |

### `status` Values

| Value | Meaning |
|-------|---------|
| `active` | On sale with stock > 0 |
| `out_of_stock` | stock_quantity = 0; visible but purchase disabled |
| `inactive` | Seller hidden; not shown to customers |

### Variant Matrix Example

```
variant_attributes: {"color":"Den","size":"M"}  -> price 150000, stock 10, active
variant_attributes: {"color":"Den","size":"L"}  -> price 150000, stock  5, active
variant_attributes: {"color":"Trang","size":"M"} -> price 160000, stock  0, out_of_stock
variant_attributes: {"color":"Trang","size":"L"} -> price 160000, stock  8, active
```

Frontend groups by `variant_attributes` keys to render the selection matrix.

---

## Indexes

| Index Name | Fields | Type | Purpose |
|------------|---------|------|---------|
| `idx_variant_product` | `{ product_id: 1 }` | B-tree | List all variants of a product |
| `idx_variant_status` | `{ status: 1 }` | B-tree | Filter active/inactive variants |
| `idx_variant_price` | `{ price: 1 }` | B-tree | Sort/filter by price range |
| `idx_variant_attributes` | `{ variant_attributes: "text" }` | Text | Search by variant attribute values |

---

## Cross-References

| Ref ID | Type | Description |
|--------|------|-------------|
| FR-PRODUCT-009 | Functional Requirement | Add variant to product |
| FR-PRODUCT-010 | Functional Requirement | Update variant |
| FR-PRODUCT-011 | Functional Requirement | Update stock |
| UC-PRODUCT-004 | Use Case | Manage variants (seller) |
| UC-PRODUCT-006 | Use Case | Manage stock (seller) |
| UC-PRODUCT-007 | Use Case | Reserve stock (system) |
| BR-PRODUCT-004 | Business Rule | Variant code uniqueness |
| BR-PRODUCT-005 | Business Rule | Stock validation and optimistic locking |
