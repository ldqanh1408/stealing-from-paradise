# UC-PRODUCT-011: Remove from Cart (Customer)

| Attribute | Value |
|-----------|-------|
| **ID** | UC-PRODUCT-011 |
| **Actor** | Customer (JWT required) |
| **Priority** | HIGH |
| **Precondition** | Cart item exists and belongs to customer |
| **Postcondition** | Cart item removed from cart |

---

## Main Flows

### Remove Single Item
```
1. Customer clicks "Xoa" button on a cart item
2. Frontend calls DELETE /cart/items/{itemId}
3. System validates:
   - cart_item exists and belongs to customer's cart -> 404 if not
4. System deletes cart_item row
5. Returns 200
```

### Clear Entire Cart (Delete All Items)
```
1. Customer clicks "Xoa tat ca" or similar
2. Frontend calls DELETE /cart
3. System validates cart exists for customer
4. System deletes all cart_item rows (CASCADE)
   - Cart record itself is retained (status stays active)
5. Returns 200
```

---

## UI Flow

```
Cart page:
  Individual "Xoa" link/button per item
    -> Confirmation dialog (optional)
    -> DELETE /cart/items/{itemId}
    -> Item removed from UI
    -> Cart totals recalculated

  "Xoa toan bo gio hang" button
    -> Confirmation dialog
    -> DELETE /cart
    -> All items removed
    -> Empty cart state shown
```

---

## Post-Removal Behavior

```
- If all items removed: cart remains with status=active, ready for new items
- If checked-out items: removed automatically via order.checkout_completed Kafka event
- If flash sale expired items: removed by JOB-07 (flash_sale.session_ended event)
```

---

## Error Scenarios

| Scenario | Response |
|----------|----------|
| Cart item not found | 404 |
| Cart item belongs to different user | 404 |
| Cart not found (for DELETE /cart) | No error; idempotent |

---

## Related Requirements

| Ref ID | Description |
|--------|-------------|
| FR-PRODUCT-019 | Remove item from cart |
| FR-PRODUCT-020 | Clear entire cart |
| ENTITY-PRODUCT-006 | CART |
| ENTITY-PRODUCT-007 | CART_ITEM |
