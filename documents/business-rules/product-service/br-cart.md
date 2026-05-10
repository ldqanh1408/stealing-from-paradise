# BR-PRODUCT-009 through BR-PRODUCT-012: Cart Business Rules

> **Service**: product-service (Port 8090)
> **Domain**: Cart -- Cart and Cart Items
> **Source**: 03_database_tables.md Sections 6-7, product_service_ui_logic.md Section 3, 02_API_product_service.md

---

## BR-PRODUCT-009: One Cart Per Customer

| Rule | Detail |
|------|--------|
| Uniqueness | Unique index on `customer_id` in `mg_carts` collection |
| Lazy creation | Cart is created on first `POST /cart/items` call |
| Status | Currently always `active` |
| Cart clearing | `DELETE /cart` removes all items and resets the cart |

---

## BR-PRODUCT-010: Cart Item Uniqueness (Per Variant)

| Rule | Detail |
|------|--------|
| Constraint | Unique compound index on `{ cart_id, variant_id }` in `mg_cart_items` collection |
| UPSERT behavior | If variant already in cart, `POST /cart/items` increments quantity instead of creating duplicate |
| Quantity cap | quantity > 0 and <= 1000 per API validation |

**IF** a `POST /cart/items` request specifies a `variant_id` already present in the cart
**THEN** the existing item's quantity is incremented by the requested amount (not replaced).

---

## BR-PRODUCT-011: Price Snapshot Rules

| Rule | Detail |
|------|--------|
| Snapshot at add time | `price_snapshot` = `product_variant.price` at the moment of `POST /cart/items` |
| Lazy comparison | On `GET /cart`, compare `price_snapshot` to current `product_variant.price` |
| Price change flag | If different, return `price_changed` flag with old and new values |
| Checkout gate | At Checkout Preview, ANY price mismatch results in 409 Conflict -- cart must be refreshed first |

**IF** `product_variant.price != cart_item.price_snapshot` at cart read time
**THEN** flag item as `price_changed` with display showing old price (strikethrough) and new price.

---

## BR-PRODUCT-012: Quantity Limits and Stock Validation

| Rule | Detail |
|------|--------|
| Maximum quantity | 1000 per item |
| Minimum quantity | 1; setting to 0 is invalid (use DELETE to remove) |
| Stock check on add | Soft check: `stock_quantity` must be >= requested quantity |
| Stock check on update | `PUT /cart/items/{id}` validates `quantity <= stock_available` |
| Insufficient stock | Returns 422 |
| Low stock warning | When `stock_quantity <= 5`, UI shows "Con X san pham" warning |

---

## Cart Items -- Lazy Evaluation Matrix

| Check | Condition | API Flag | UI Behavior |
|-------|-----------|----------|-------------|
| Price change | `price != price_snapshot` | `price_changed` | Show old/new price + "Cap nhat gio" button |
| Out of stock | `stock_quantity == 0` | `out_of_stock` | Dim item, disable checkbox |
| Unavailable | `status != 'active'` | `unavailable` | Dim item, disable checkbox |
| Insufficient | `stock < quantity` | `insufficient_stock` | Cap displayed qty, show warning |

---

## Cross-References

| Ref ID | Entity |
|--------|--------|
| ENTITY-PRODUCT-006 | CART |
| ENTITY-PRODUCT-007 | CART_ITEM |
| ENTITY-PRODUCT-005 | STOCK_RESERVATION |
| UC-PRODUCT-008 | View cart |
| UC-PRODUCT-009 | Add to cart |
| UC-PRODUCT-010 | Update cart item |
| UC-PRODUCT-011 | Remove from cart |
| FR-PRODUCT-016 through 022 | Cart functional requirements |
