# BR-PRODUCT-001 through BR-PRODUCT-008: Catalog Business Rules

> **Service**: product-service (Port 8090)
> **Domain**: Catalog -- Categories, Products, Variants, Images, Stock
> **Source**: 03_database_tables.md, product_service_ui_logic.md, 02_API_product_service.md

---

## BR-PRODUCT-001: Category Hierarchy Constraints

| Rule | Detail |
|------|--------|
| Self-referencing tree | `parent_id` references `category.id`; NULL = root |
| No circular references | Application must enforce acyclic parent-child relationships |
| Active propagation | Setting `is_active = FALSE` on a parent hides all descendant categories and their products from storefront |
| Slug uniqueness | `slug` is UNIQUE across all categories; enforced at DB level |
| Sort order | `sort_order` ASC determines display order in menus; defaults to 0 |

**IF** `parent_id IS NOT NULL` **THEN** the referenced category must exist and must not create a cycle.

---

## BR-PRODUCT-002: Leaf-Only Product Assignment

| Rule | Detail |
|------|--------|
| Products attach to leaf categories only | `POST /products` validates that `category_id` has no children |
| API validation | Returns 422 if category is non-leaf |

**IF** `category_id` has children in the category tree **THEN** reject product creation with error "Products can only be assigned to leaf categories".

---

## BR-PRODUCT-003: Product Status Transitions

| From | To | Trigger | Constraint |
|------|-----|---------|------------|
| `active` | `out_of_stock` | All variants reach `stock_quantity = 0` | Automatic; computed in same transaction as variant update |
| `out_of_stock` | `active` | Any variant restocked to `stock_quantity > 0` | Automatic |
| `active` | `inactive` | Seller calls `POST /seller/products/{id}/unpublish` | Manual |
| `inactive` | `active` | Seller calls `POST /seller/products/{id}/publish` | Manual |
| `out_of_stock` | `inactive` | Seller calls `POST /seller/products/{id}/unpublish` | Manual |

**Product status is derived from variant states.** After any variant change (stock, status, add/delete), the product `status` is recomputed:
- Has >=1 active variant with stock > 0 -> `active`
- All variants have stock = 0 -> `out_of_stock` (still visible)
- Seller manually disabled -> `inactive`

---

## BR-PRODUCT-004: Variant Code Uniqueness

| Rule | Detail |
|------|--------|
| `variant_code` | UNIQUE across all variants in the system |
| Format | 3-50 characters, alphanumeric + dash only |
| Conflict response | 409 if `variant_code` already exists |

---

## BR-PRODUCT-005: Stock Validation and Optimistic Locking

| Rule | Detail |
|------|--------|
| Stock never negative | `stock_quantity` cannot go below 0; validated in application layer |
| Optimistic lock | `version` column prevents lost updates on concurrent stock mutations |
| Stock adjustment | `POST /seller/inventory/adjust` with `delta` (can be negative for deductions) |
| Restock | `PUT /inventory/{skuCode}/restock` adds quantity with reason audit log |

**IF** `stock_quantity - requested < 0` **THEN** reject with 422 "Insufficient stock".
**IF** `UPDATE ... WHERE version = N` returns 0 rows **THEN** retry or return 409 "Concurrent modification detected".

---

## BR-PRODUCT-006: Image Validation

| Rule | Detail |
|------|--------|
| Formats | JPEG, PNG, WebP only |
| Count per product | 1-10 images |
| Upload flow | `GET /products/{id}/presigned-url` returns MinIO PUT URL (15 min TTL) |
| Storage path | `products/{seller_id}/{product_id}/{uuid}.{ext}` |
| Thumbnail logic | `sort_order = 0` (smallest value) = primary/thumbnail image |

---

## BR-PRODUCT-007: Reservation Expiry (15-Minute TTL)

| Rule | Detail |
|------|--------|
| TTL | `expires_at = NOW() + 15 minutes` |
| Cleanup job | Runs every 1-5 minutes to release expired `pending` reservations |
| Release action | `status = 'released'`, Redis `INCR`, DB stock restored |

**IF** `status = 'pending' AND expires_at < NOW()` **THEN** automatic release.

---

## BR-PRODUCT-008: Variant Status Logic

| From | To | Trigger |
|------|-----|---------|
| `active` | `out_of_stock` | `stock_quantity` reaches 0 |
| `out_of_stock` | `active` | `stock_quantity` restored to > 0 |
| `active` | `inactive` | Seller manually disables via variant update |
| `inactive` | `active` | Seller manually enables |
| `inactive` | `out_of_stock` | N/A (inactive variants are not tracked for stock) |

---

## Cross-References

| Ref ID | Entity | 
|--------|--------|
| ENTITY-PRODUCT-001 | CATEGORY |
| ENTITY-PRODUCT-002 | PRODUCT |
| ENTITY-PRODUCT-003 | PRODUCT_VARIANT |
| ENTITY-PRODUCT-004 | PRODUCT_IMAGE |
| ENTITY-PRODUCT-005 | STOCK_RESERVATION |
| UC-PRODUCT-002 | Manage categories |
| UC-PRODUCT-003 | Create product |
| UC-PRODUCT-004 | Manage variants |
| UC-PRODUCT-005 | Upload images |
| UC-PRODUCT-006 | Manage stock |
| UC-PRODUCT-007 | Reserve stock |
