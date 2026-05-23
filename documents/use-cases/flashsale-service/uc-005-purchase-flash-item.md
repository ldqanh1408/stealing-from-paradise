# UC-FLASHSALE-005: Purchase Flash Sale Item

|**Stable ID:** `UC-FLASHSALE-005`
|**Actor:** Customer (BUYER)
|**Priority:** CRITICAL
|**Auth:** JWT (BUYER)

---

## Brief
A customer purchases a flash sale item at the dynamically calculated flash price. Inventory and stock management is handled by the Checkout Service. Flash Sale Service validates session status and emits events for downstream processing.

---

## Preconditions

| # | Condition |
|---|-----------|
| P1 | Actor is authenticated as BUYER |
| P2 | Session exists and `status = ACTIVE` |
| P3 | `fs_item_id` references a registered product in the session |
| P4 | Product is available (not out of stock) |

---

## Main Flow

| Step | Actor | Action |
|------|-------|--------|
| 1 | Customer | Sends `POST /flash-sales/{id}/buy` with `fs_item_id`, `quantity`, `address_id` |
| 2 | System | Validates session is ACTIVE |
| 3 | System | Validates input: `quantity > 0` |
| 4 | System | Loads `fs_items.discount_applied` from PostgreSQL |
| 5 | System | Fetches `sku.price` from Product Service (`product_variant.price`) |
| 6 | System | Calculates `flash_price = sku.price * (1 - discount_applied / 100)` (BR-FLASHSALE-007) |
| 7 | System | Emits Kafka event `flash_sale.item_purchased` to trigger Checkout Service |
| 8 | System | Returns `201 Created` with order details and `timeout_at` (10 minutes to pay) |

---

## Alternate Flows

| # | Trigger | Action |
|---|---------|--------|
| A1 | Session not found | Return `404 SESSION_NOT_FOUND` |
| A2 | Session not ACTIVE | Return `400 SESSION_NOT_ACTIVE` |
| A3 | FS_ITEM not found | Return `400 PRODUCT_NOT_IN_FLASH_SALE` |
| A4 | User purchase limit exceeded | Return `400 LIMIT_EXCEEDED` |
| A5 | Invalid address | Return `400 INVALID_ADDRESS` |
| A6 | Product out of stock | Return `409 SOLD_OUT` |

---

## Postconditions

| # | Condition |
|---|-----------|
| PC1 | Kafka event `flash_sale.item_purchased` published to trigger Checkout Service |
| PC2 | Order created with `status = PENDING` and `is_flash_sale = true` |
| PC3 | `timeout_at` set to `NOW() + 10 minutes` for payment deadline |

---

## Cross-References

| Reference | Description |
|-----------|-------------|
| FR-FLASHSALE-009 | Purchase (forward to Checkout) |
| FR-FLASHSALE-010 | Dynamic flash price calculation |
| BR-FLASHSALE-007 | Dynamic price formula |
| ENTITY-FLASHSALE-002 | FS_ITEMS table |

---

## Related Use Cases

| UC | Relationship |
|----|-------------|
| UC-FLASHSALE-002 | Seller registered the item being purchased |
| UC-FLASHSALE-003 | Customer viewed session to find item |

---

*Generated: 2026-05-09*
