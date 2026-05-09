# UC-PRODUCT-008: View Cart (Customer)

| Attribute | Value |
|-----------|-------|
| **ID** | UC-PRODUCT-008 |
| **Actor** | Customer (JWT required) |
| **Priority** | HIGH |
| **Precondition** | Customer authenticated |
| **Postcondition** | Cart contents displayed with real-time enrichment |

---

## Main Flow

```
1. Customer navigates to cart page (or calls GET /cart)

2. System looks up cart by customer_id
   - IF no cart exists: returns empty cart (no auto-create)
   - Cart auto-created on first POST /cart/items

3. System fetches all cart_items for the cart

4. For each cart_item, system performs lazy evaluation:

   a. Fetch current product_variant data (batch from DB/Redis)

   b. Compare:
      - price_snapshot vs current variant.price
        IF different: flag price_changed, include old_price and new_price
      - variant.status
        IF != 'active': flag unavailable
      - variant.stock_quantity
        IF == 0: flag out_of_stock
        IF < quantity: flag insufficient_stock

5. System groups items by seller_id (from product.seller_id)

6. System calculates:
   - subtotal per seller
   - total_items count
   - total amount (SUM of selected items)

7. Returns response grouped by seller with all flags and calculated totals
```

---

## Response Structure
```json
{
  "cart_id": "uuid",
  "user_id": 42,
  "sellers": [
    {
      "seller_id": 5,
      "items": [
        {
          "cart_item_id": 201,
          "sku_code": "NK-AIR-RED-XL",
          "product_id": "uuid",
          "product_name": "Ao Thun Nike Air",
          "variant_name": "Do / XL",
          "unit_price": 350000,
          "quantity": 2,
          "is_flash": false,
          "subtotal": 700000,
          "warnings": {
            "price_changed": false,
            "out_of_stock": false,
            "unavailable": false
          },
          "added_at": "2026-04-14T15:30:00Z"
        }
      ],
      "subtotal": 700000
    }
  ],
  "total_items": 3,
  "total": 1200000
}
```

---

## UI Warnings Display

| Condition | UI Action |
|-----------|-----------|
| `price_changed` | Show old price (strikethrough) + new price + "Gia da thay doi" warning |
| `out_of_stock` | Dim item, disable checkbox, show "Het hang" |
| `unavailable` | Dim item, disable checkbox, show "San pham khong con ban" |
| `insufficient_stock` | Highlight quantity, show "Chi con X san pham" |

---

## Related Requirements

| Ref ID | Description |
|--------|-------------|
| FR-PRODUCT-016 | Get customer cart |
| BR-PRODUCT-009 | One cart per customer |
| BR-PRODUCT-011 | Price snapshot rules |
| BR-PRODUCT-012 | Quantity limits |
| ENTITY-PRODUCT-006 | CART |
| ENTITY-PRODUCT-007 | CART_ITEM |
