# UC-PRODUCT-006: Manage Stock (Seller)

| Attribute | Value |
|-----------|-------|
| **ID** | UC-PRODUCT-006 |
| **Actor** | Seller (JWT SELLER role, variant owner) |
| **Priority** | HIGH |
| **Precondition** | Variant exists and seller owns the parent product |
| **Postcondition** | Stock quantity updated; variant and product status recomputed; Kafka event emitted |

---

## Main Flows

### Restock (Add Inventory)
```
1. Seller calls PUT /inventory/{skuCode}/restock
   Body: { quantity: 50, reason: "Nhap hang tu nha cung cap", note?: "..." }

2. System validates:
   - skuCode exists and seller owns the parent product (404 if not)
   - quantity > 0 (422 if not)

3. System updates product_variant:
   - stock_quantity += quantity
   - version += 1 (optimistic lock)
   - If stock_quantity > 0 and status was out_of_stock -> status = 'active'

4. Product status recomputed in same transaction

5. Emits variant.stock_updated Kafka event:
   Topic: variant.stock_updated
   Payload: { sku_code, delta: +50, reason, new_stock }

6. Returns 200
```

### Adjust Stock (Delta)
```
1. Seller calls POST /seller/inventory/adjust
   Body: { sku_code: "NK-AIR-RED-XL", delta: -5, reason: "Hang bi hong trong kho" }

2. System validates:
   - sku_code exists and seller owns (404 if not)
   - stock_quantity + delta >= 0 (422 if negative)

3. System updates with optimistic lock:
   UPDATE product_variant
   SET stock_quantity = stock_quantity + delta,
       version = version + 1,
       status = CASE WHEN stock_quantity + delta = 0 THEN 'out_of_stock'
                     WHEN stock_quantity + delta > 0 AND status = 'out_of_stock' THEN 'active'
                     ELSE status END
   WHERE id = :variantId AND version = :currentVersion

4. IF rows_affected = 0: retry or return 409 (concurrent modification)

5. Product status recomputed in same transaction

6. (No separate event -- variant.stock_updated already emitted)

7. Returns 200
```

### Query Stock
```
1. GET /inventory/{skuCode}
2. Returns: { sku_code, stock_total, stock_locked, stock_available, stock_flash_reserved, updated_at }
```

---

## Error Scenarios

| Scenario | Response |
|----------|----------|
| SKU not found or not owned | 404 |
| Negative stock result | 422 |
| Concurrent modification | 409 |
| Invalid quantity | 422 |

---

## Related Requirements

| Ref ID | Description |
|--------|-------------|
| FR-PRODUCT-011 | Update stock |
| BR-PRODUCT-005 | Stock validation and optimistic locking |
| BR-PRODUCT-003 | Product status transitions |
| ENTITY-PRODUCT-003 | PRODUCT_VARIANT |
