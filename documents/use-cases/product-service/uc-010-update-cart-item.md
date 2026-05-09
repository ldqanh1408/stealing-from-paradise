# UC-PRODUCT-010: Update Cart Item (Customer)

| Attribute | Value |
|-----------|-------|
| **ID** | UC-PRODUCT-010 |
| **Actor** | Customer (JWT required) |
| **Priority** | HIGH |
| **Precondition** | Cart item exists and belongs to customer |
| **Postcondition** | Cart item quantity updated |

---

## Main Flow

```
1. Customer opens cart page
2. Customer adjusts quantity using [+]/[-] buttons on a cart item
3. Frontend calls PUT /cart/items/{itemId}
   Body: { quantity: 3 }

4. System validates:
   a. cart_item exists and belongs to customer's cart -> 404 if not
   b. quantity > 0 -> 422 if 0 (use DELETE to remove)
   c. quantity <= product_variant.stock_quantity -> 422 if exceeds
   d. product_variant.status = 'active' -> 422 if inactive/out_of_stock

5. System updates cart_item.quantity

6. Returns 200 with updated item data
```

---

## UI Constraints

```
[−] button: disabled when quantity = 1
[+] button: disabled when quantity = stock_quantity
Stock warning: "Con X san pham" when stock <= 5
```

---

## Edge Cases

| Scenario | Handling |
|----------|----------|
| Variant was restocked after being out_of_stock | Allow update; flags auto-clear on next GET /cart |
| Variant became inactive while in cart | Update rejected with 422; item flagged unavailable on GET /cart |
| Price changed since adding | Update succeeds (affects quantity only); price_changed flag on GET /cart |
| quantity = 0 | Invalid; return 422, suggest DELETE |

---

## Error Scenarios

| Scenario | Response |
|----------|----------|
| Cart item not found | 404 |
| Cart item belongs to different user | 404 |
| quantity > stock_available | 422 |
| quantity = 0 | 422 "Use DELETE to remove item" |
| Variant inactive | 422 |

---

## Related Requirements

| Ref ID | Description |
|--------|-------------|
| FR-PRODUCT-018 | Update cart item quantity |
| BR-PRODUCT-012 | Quantity limits and stock validation |
| ENTITY-PRODUCT-007 | CART_ITEM |
