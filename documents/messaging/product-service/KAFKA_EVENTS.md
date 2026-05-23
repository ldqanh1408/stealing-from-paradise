# Kafka Events -- Product Service (Catalog + Cart + Inventory)

> Service: product-service (Port 8090)
> Source: `docs/services/product-service/KAFKA_EVENTS.md`, `docs/services/product-service/02_API_product_service.md`
> Generated: 2026-05-10

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
    "product_id": "uuid",
    "seller_id": "uuid",
    "name": "Ao Thun Nike Air Nam",
    "category_id": "uuid",
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
| **Trigger** | Seller updates product via `PUT /products/{id}` |

**Payload:**
```json
{
  "topic": "product.updated",
  "payload": {
    "product_id": "uuid",
    "name": "Updated Name",
    "category_id": "uuid",
    "status": "active",
    "timestamp": "2026-04-15T10:00:00Z"
  }
}
```

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
    "product_id": "uuid",
    "seller_id": "uuid",
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
| **Trigger** | Seller updates variant price via `PUT /seller/variants/{id}` |

**Payload:**
```json
{
  "topic": "variant.price_updated",
  "payload": {
    "variant_id": "uuid",
    "product_id": "uuid",
    "price": 380000,
    "original_price": 400000,
    "timestamp": "2026-04-15T10:00:00Z"
  }
}
```

---

### variant.stock_updated

| Field | Value |
|-------|-------|
| **Consumers** | Search Service |
| **Trigger** | Stock adjustment or variant status change |

**Payload:**
```json
{
  "topic": "variant.stock_updated",
  "payload": {
    "variant_id": "uuid",
    "product_id": "uuid",
    "stock_quantity": 50,
    "status": "active",
    "stock_status": "in_stock",
    "timestamp": "2026-04-15T10:00:00Z"
  }
}
```

---

### inventory.adjusted

| Field | Value |
|-------|-------|
| **Consumers** | Search Service |
| **Trigger** | Seller adjusts stock via `POST /seller/inventory/adjust` |

---

---

### stock.reservation.expired

| Field | Value |
|-------|-------|
| **Consumers** | Order Service, Notification Service |
| **Trigger** | JOB-13 phát hiện `stock_reservation.expires_at < NOW()` và `status = pending` |
| **Status** | NEW — bổ sung 2026-05-10 (MVP MUST-HAVE, xem `MVP_ANALYSIS.md` §3.1) |
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
- Order Service: nếu `parent_orders.session_id` = này và status `PENDING_PAYMENT` → cascade gọi `order.payment_timeout` flow.
- Notification Service: thông báo buyer "Phiên giữ chỗ đã hết hạn".

---


---

### product.pending_review

| Field | Value |
|-------|-------|
| **Producer** | product-service (`POST /seller/products/{id}/submit`) |
| **Consumers** | notification-service (broadcast to admin queue) |
| **Trigger** | Seller submits product for admin review (`draft → pending`) |
| **Status** | RE-ACTIVATED 2026-05-10 v3 — P3-11 APPROVED |
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
    "product_id": "uuid",
    "seller_id": 42,
    "category_id": "uuid",
    "name": "Ao Thun Nike Air Nam",
    "submitted_at": "2026-05-10T09:00:00Z",
    "reject_count": 0
  }
}
```

**Downstream effects:**
- Notification Service: NOTIF-PRODUCT-PENDING-REVIEW broadcast to all users with role=ADMIN.

> `reject_count` allows admins to prioritize first-time submissions over repeat-rejecters in the review queue (BR-PRODUCT-009.8 — 3-strike limit).

---

### product.approved

| Field | Value |
|-------|-------|
| **Producer** | product-service (`POST /admin/products/{id}/approve`) |
| **Consumers** | notification-service (notify seller), search-service (pre-warm; ES indexing on subsequent `product.activated`) |
| **Trigger** | Admin approves a pending product (`pending → approved`) |
| **Status** | RE-ACTIVATED 2026-05-10 v3 — P3-11 APPROVED |
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
    "product_id": "uuid",
    "seller_id": 42,
    "reviewed_by": 1,
    "reviewed_at": "2026-05-10T10:15:00Z",
    "reject_count": 0,
    "note": "San pham dat yeu cau"
  }
}
```

**Downstream effects:**
- Notification Service: NOTIF-PRODUCT-APPROVED to seller — "Sản phẩm của bạn đã được duyệt, hãy publish để mở bán".
- Search Service: pre-warm cache; actual ES upsert chỉ xảy ra khi seller publish (`product.activated`).

---

### product.rejected

| Field | Value |
|-------|-------|
| **Producer** | product-service (`POST /admin/products/{id}/reject`) |
| **Consumers** | notification-service (notify seller with reason) |
| **Trigger** | Admin rejects a pending product (`pending → rejected`) |
| **Status** | RE-ACTIVATED 2026-05-10 v3 — P3-11 APPROVED |
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
    "product_id": "uuid",
    "seller_id": 42,
    "reviewed_by": 1,
    "reviewed_at": "2026-05-10T10:20:00Z",
    "reject_reason": "Hinh anh khong ro rang, vui long chup lai",
    "reject_count": 1
  }
}
```

**Downstream effects:**
- Notification Service: NOTIF-PRODUCT-REJECTED to seller, body includes `{reject_reason}` so seller biết phải sửa gì.
- Product Service (self): tăng counter `reject_count`; nếu ≥3 → lock product khỏi auto-resubmit (BR-PRODUCT-009.8).

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

### product.auto_hidden

| Field | Value |
|-------|-------|
| **Trigger** | Product inactive for 30 days (cronjob) |
| **Consumers** | Search Service |

**Payload:**
```json
{
  "product_id": "uuid",
  "seller_id": "uuid",
  "reason": "inactive_30_days",
  "timestamp": "2026-05-12T00:00:00Z"
}
```

---

## Events Consumed

### order.created (from Order Service)

| Field | Value |
|-------|-------|
| **Module** | Inventory |
| **Action** | Lock stock for each variant in the order |

### order.cancelled (from Order Service)

| Field | Value |
|-------|-------|
| **Module** | Cart + Inventory |
| **Action** | Remove items from cart, unlock stock |

### order.returned (from Order Service)

| Field | Value |
|-------|-------|
| **Module** | Inventory |
| **Action** | Restore stock for returned items |

### flash_sale.session_started (from Flash Sale Service)

| Field | Value |
|-------|-------|
| **Module** | Pricing |
| **Action** | Query fs_items, fetch variant prices, calculate flash_price, emit `flash_sale.price_sync` |

### flash_sale.session_ended (from Flash Sale Service)

| Field | Value |
|-------|-------|
| **Module** | Cart + Pricing |
| **Action** | Remove expired flash items from cart, reset prices, emit `flash_sale.price_sync` (deactivate) |

---|-------|
| **Module** | Inventory |
| **Action** | Update sold count and remaining stock cache |

---

