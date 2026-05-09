# UC-FLASHSALE-005: Purchase Flash Sale Item

**Stable ID:** `UC-FLASHSALE-005`
**Actor:** Customer (BUYER)
**Priority:** CRITICAL
**Auth:** JWT (BUYER)

---

## Brief
A customer purchases a flash sale item at the dynamically calculated flash price. The purchase is processed atomically via a Redis Lua script to prevent oversell under high concurrency (50k+ req/s).

---

## Preconditions

| # | Condition |
|---|-----------|
| P1 | Actor is authenticated as BUYER |
| P2 | Session exists and `status = ACTIVE` |
| P3 | `fs_item_id` references a registered product in the session |
| P4 | Product has available stock (Redis `flash_sale:stock:{fs_item_id} > 0`) |

---

## Main Flow

| Step | Actor | Action |
|------|-------|--------|
| 1 | Customer | Sends `POST /flash-sales/{id}/buy` with `fs_item_id`, `quantity`, `address_id` |
| 2 | System | Validates session is ACTIVE |
| 3 | System | Validates input: `quantity > 0` |
| 4 | System | Loads `fs_items.discount_applied` from PostgreSQL |
| 5 | System | Fetches `sku.price` from Product Service (product_variant.price) |
| 6 | System | Calculates `flash_price = sku.price * (1 - discount_applied / 100)` (BR-FLASHSALE-008) |
| 7 | System | Executes Redis Lua script: atomic stock decrement + user limit check (BR-FLASHSALE-005) |
| 8 | System (Redis) | On success: creates order, publishes `flash_sale.item_purchased` |
| 9 | System | Returns `201 Created` with order details and `timeout_at` (10 minutes to pay) |

---

## Alternate Flows

| # | Trigger | Action |
|---|---------|--------|
| A1 | Session not found | Return `404 SESSION_NOT_FOUND` |
| A2 | Session not ACTIVE | Return `400 SESSION_NOT_ACTIVE` |
| A3 | FS_ITEM not found | Return `400 PRODUCT_NOT_IN_FLASH_SALE` |
| A4 | Stock exhausted (Redis check) | Return `409 SOLD_OUT` |
| A5 | User purchase limit exceeded | Return `400 LIMIT_EXCEEDED` |
| A6 | Invalid address | Return `400 INVALID_ADDRESS` |

---

## Redis Lua Script Logic (BR-FLASHSALE-005)

```
1. Check stock: GET flash_sale:stock:{fs_item_id}
   IF stock < quantity THEN RETURN SOLD_OUT
2. Check user limit: GET flash_sale:user:{session_id}:{user_id}
   IF count + quantity > limit THEN RETURN LIMIT_EXCEEDED
3. Decrement stock: DECRBY flash_sale:stock:{fs_item_id} quantity
4. Increment user count: INCRBY flash_sale:user:{session_id}:{user_id} quantity
5. Create order record
6. PUBLISH flash_sale.item_purchased
7. RETURN success with order_id
```

---

## Postconditions

| # | Condition |
|---|-----------|
| PC1 | Redis stock counter decremented atomically |
| PC2 | Order created with `status = PENDING` and `is_flash_sale = true` |
| PC3 | `timeout_at` set to `NOW() + 10 minutes` for payment deadline |
| PC4 | Kafka event `flash_sale.item_purchased` published |

---

## Cross-References

| Reference | Description |
|-----------|-------------|
| FR-FLASHSALE-009 | Purchase via Redis Lua |
| FR-FLASHSALE-010 | Dynamic flash price calculation |
| BR-FLASHSALE-005 | Redis Lua atomic buy |
| BR-FLASHSALE-008 | Dynamic price formula |
| ENTITY-FLASHSALE-002 | FS_ITEMS table |

---

## Related Use Cases

| UC | Relationship |
|----|-------------|
| UC-FLASHSALE-002 | Seller registered the item being purchased |
| UC-FLASHSALE-003 | Customer viewed session to find item |

---

*Generated: 2026-05-09*
