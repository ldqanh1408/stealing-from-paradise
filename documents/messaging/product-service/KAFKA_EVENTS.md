# Kafka Events -- Product Service (Catalog + Cart + Inventory)

> Service: product-service (Port 8090)
> Source: `docs/services/product-service/KAFKA_EVENTS.md`, `docs/services/product-service/02_API_product_service.md`
> Generated: 2026-05-10 | Updated: 2026-05-23 (payload alignment + product.auto_hidden removed)

---

## Events Produced

### product.created

| Field | Value |
|-------|-------|
| **Consumers** | Search Service |
| **Trigger** | Seller creates product via `POST /products` |

**Payload:**
```json
{
  "topic": "product.created",
  "payload": {
    "productId": "uuid",
    "sellerId": "uuid",
    "name": "Ao Thun Nike Air Nam",
    "categoryId": "uuid",
    "status": "active",
    "timestamp": "2026-04-15T10:00:00Z"
  }
}
```

---

### product.updated

| Field | Value |
|-------|-------|
| **Consumers** | Search Service |
| **Trigger** | Seller updates product via `PUT /products/{id}`, publish via `POST /seller/products/{id}/publish`, unpublish via `POST /seller/products/{id}/unpublish` |

**Payload:**
```json
{
  "topic": "product.updated",
  "payload": {
    "productId": "uuid",
    "status": "ACTIVE",
    "timestamp": "2026-04-15T10:00:00Z"
  }
}
```

> Note: `productId` is sufficient for Search Service to look up full product details from the database. Additional fields (`name`, `categoryId`) are not included to minimize payload size.

---

### product.deleted

| Field | Value |
|-------|-------|
| **Consumers** | Search Service |
| **Trigger** | Seller deletes product via `DELETE /seller/products/{id}` |

**Payload:**
```json
{
  "topic": "product.deleted",
  "payload": {
    "productId": "uuid",
    "sellerId": "uuid",
    "timestamp": "2026-04-15T10:00:00Z"
  }
}
```

---

### category.updated

| Field | Value |
|-------|-------|
| **Consumers** | Search Service |
| **Trigger** | Admin updates category via `PUT /admin/categories/{id}` |

---

### variant.price_updated

| Field | Value |
|-------|-------|
| **Consumers** | Search Service |
| **Trigger** | Seller creates or updates variant price via `POST /seller/products/{id}/variants` or `PUT /seller/variants/{id}` |

**Payload:**
```json
{
  "topic": "variant.price_updated",
  "payload": {
    "variantId": "uuid",
    "productId": "uuid",
    "price": 380000,
    "originalPrice": 400000,
    "timestamp": "2026-04-15T10:00:00Z"
  }
}
```

---

### variant.stock_updated

| Field | Value |
|-------|-------|
| **Consumers** | Search Service |
| **Trigger** | Stock adjustment, variant creation, reservation release/return, or variant status change |

**Payload:**
```json
{
  "topic": "variant.stock_updated",
  "payload": {
    "variantId": "uuid",
    "productId": "uuid",
    "stockQuantity": 50,
    "status": "ACTIVE",
    "stockStatus": "in_stock",
    "timestamp": "2026-04-15T10:00:00Z"
  }
}
```

| `stockStatus` values | Meaning |
|---------------------|---------|
| `in_stock` | Variant is active and has stock |
| `out_of_stock` | Variant has zero stock |
| `unavailable` | Variant is inactive |
| `unknown` | Status could not be determined |

---

### inventory.adjusted

| Field | Value |
|-------|-------|
| **Consumers** | Search Service |
| **Trigger** | Seller restocks or adjusts stock via `POST /seller/inventory/restock` or `POST /seller/inventory/adjust` |

---

---

### stock.reservation.expired

| Field | Value |
|-------|-------|
| **Consumers** | Order Service, Notification Service |
| **Trigger** | `ReservationCleanupScheduler` (cron every minute) detects `stock_reservation.expires_at < NOW()` and `status = PENDING` |
| **Status** | NEW -- bo sung 2026-05-10 (MVP MUST-HAVE, xem `MVP_ANALYSIS.md` §3.1) |
| **Retention** | 7 days |
| **Partition Key** | `session_id` |

**Payload:**
```json
{
  "topic": "stock.reservation.expired",
  "event_id": "evt_20260510_001",
  "event_type": "stock.reservation.expired",
  "timestamp": "2026-05-10T10:15:00Z",
  "source_service": "product-service",
  "version": 1,
  "data": {
    "reservation_id": "11111111-2222-3333-4444-555555555555",
    "variant_id": "uuid-variant",
    "session_id": "chk_2026_05_10_abc123",
    "quantity": 2,
    "expired_at": "2026-05-10T10:15:00Z"
  }
}
```

**Consumer actions:**
- Order Service: neu `parent_orders.session_id` = nay va status `PENDING_PAYMENT` → cascade goi `order.payment_timeout` flow.
- Notification Service: thong bao buyer "Phien giu cho da het han".

---

---

### product.pending_review

| Field | Value |
|-------|-------|
| **Producer** | product-service (`POST /seller/products/{id}/submit`) |
| **Consumers** | notification-service (broadcast to admin queue) |
| **Trigger** | Seller submits product for admin review (`draft → pending`) |
| **Status** | RE-ACTIVATED 2026-05-10 v3 -- P3-11 APPROVED |
| **Retention** | 30 days |
| **Partition Key** | `product_id` |

**Payload:**
```json
{
  "topic": "product.pending_review",
  "event_id": "evt_20260510_pending_001",
  "event_type": "product.pending_review",
  "timestamp": "2026-05-10T09:00:00Z",
  "source_service": "product-service",
  "version": 1,
  "data": {
    "productId": "uuid",
    "sellerId": 42,
    "categoryId": "uuid",
    "name": "Ao Thun Nike Air Nam",
    "submittedAt": "2026-05-10T09:00:00Z",
    "rejectCount": 0
  }
}
```

**Downstream effects:**
- Notification Service: NOTIF-PRODUCT-PENDING-REVIEW broadcast to all users with role=ADMIN.

> `rejectCount` allows admins to prioritize first-time submissions over repeat-rejecters in the review queue (BR-PRODUCT-009.8 -- 3-strike limit).

---

### product.approved

| Field | Value |
|-------|-------|
| **Producer** | product-service (`POST /admin/products/{id}/approve`) |
| **Consumers** | notification-service (notify seller), search-service (pre-warm; ES indexing on subsequent `product.activated`) |
| **Trigger** | Admin approves a pending product (`pending → approved`) |
| **Status** | RE-ACTIVATED 2026-05-10 v3 -- P3-11 APPROVED |
| **Retention** | 30 days |
| **Partition Key** | `product_id` |

**Payload:**
```json
{
  "topic": "product.approved",
  "event_id": "evt_20260510_approve_001",
  "event_type": "product.approved",
  "timestamp": "2026-05-10T10:15:00Z",
  "source_service": "product-service",
  "version": 1,
  "data": {
    "productId": "uuid",
    "sellerId": 42,
    "reviewedBy": 1,
    "reviewedAt": "2026-05-10T10:15:00Z",
    "rejectCount": 0,
    "note": "San pham dat yeu cau"
  }
}
```

**Downstream effects:**
- Notification Service: NOTIF-PRODUCT-APPROVED to seller -- "San pham cua ban da duoc duyet, hay publish de mo ban".
- Search Service: pre-warm cache; actual ES upsert chi xay ra khi seller publish (`product.activated`).

---

### product.rejected

| Field | Value |
|-------|-------|
| **Producer** | product-service (`POST /admin/products/{id}/reject`) |
| **Consumers** | notification-service (notify seller with reason) |
| **Trigger** | Admin rejects a pending product (`pending → rejected`) |
| **Status** | RE-ACTIVATED 2026-05-10 v3 -- P3-11 APPROVED |
| **Retention** | 30 days |
| **Partition Key** | `product_id` |

**Payload:**
```json
{
  "topic": "product.rejected",
  "event_id": "evt_20260510_reject_001",
  "event_type": "product.rejected",
  "timestamp": "2026-05-10T10:20:00Z",
  "source_service": "product-service",
  "version": 1,
  "data": {
    "productId": "uuid",
    "sellerId": 42,
    "reviewedBy": 1,
    "reviewedAt": "2026-05-10T10:20:00Z",
    "rejectReason": "Hinh anh khong ro rang, vui long chup lai",
    "rejectCount": 1
  }
}
```

**Downstream effects:**
- Notification Service: NOTIF-PRODUCT-REJECTED to seller, body includes `{rejectReason}` so seller biet phai sua gi.
- Product Service (self): tang counter `rejectCount`; neu >=3 → lock product khoi auto-resubmit (BR-PRODUCT-009.8).

---

### flash_sale.price_sync

| Field | Value |
|-------|-------|
| **Consumers** | Search Service |
| **Trigger** | Product Service receives `flash_sale.session_started` or `flash_sale.session_ended` |

**Activate payload:**
```json
{
  "event": "flash_sale.price_sync",
  "action": "activate",
  "session_id": 1,
  "items": [
    {
      "sku_id": "uuid",
      "product_id": "uuid",
      "flash_price": 160000,
      "original_price": 200000,
      "has_discount": true,
      "discount_pct": 20
    }
  ],
  "timestamp": "2026-05-10T08:00:00Z"
}
```

**Deactivate payload:**
```json
{
  "event": "flash_sale.price_sync",
  "action": "deactivate",
  "session_id": 1,
  "items": [
    { "sku_id": "uuid", "product_id": "uuid" }
  ],
  "timestamp": "2026-05-10T10:00:00Z"
}
```

---

## Events Consumed

### order.created (from Order Service)

| Field | Value |
|-------|-------|
| **Module** | Inventory |
| **Action** | Confirm all PENDING stock reservations for the given `sessionId` |

### order.cancelled (from Order Service)

| Field | Value |
|-------|-------|
| **Module** | Inventory |
| **Action** | Release all PENDING stock reservations for the given `sessionId`, unlock stock |

### order.returned (from Order Service)

| Field | Value |
|-------|-------|
| **Module** | Inventory |
| **Action** | Restore stock for each returned item by calling `restoreStockOnReturn(variantId, quantity)` |

### flash_sale.session_started (from Flash Sale Service)

| Field | Value |
|-------|-------|
| **Module** | Pricing |
| **Action** | Apply flash prices to variants from `flashPriceMap`, save `originalPrice`, emit `flash_sale.price_sync` (activate) |

### flash_sale.session_ended (from Flash Sale Service)

| Field | Value |
|-------|-------|
| **Module** | Pricing |
| **Action** | Restore original prices for all variants with `originalPrice != null`, emit `flash_sale.price_sync` (deactivate) |

---
