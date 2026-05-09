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

### cart.item_added

| Field | Value |
|-------|-------|
| **Consumers** | Analytics / audit |
| **Trigger** | Customer adds item via `POST /cart/items` |

**Payload:**
```json
{
  "topic": "cart.item_added",
  "payload": {
    "user_id": 42,
    "variant_id": "uuid",
    "quantity": 2,
    "price_snapshot": 350000,
    "timestamp": "2026-04-15T10:00:00Z"
  }
}
```

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
| **Action** | Lock stock for each variant in the order |

### order.confirmed (from Order Service)

| Field | Value |
|-------|-------|
| **Module** | Inventory |
| **Action** | Confirm stock reservation (status = confirmed) |

### order.failed (from Order Service)

| Field | Value |
|-------|-------|
| **Module** | Inventory |
| **Action** | Release stock reservation (status = released), restore Redis + DB |

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

### order.checkout_completed (from Order Service)

| Field | Value |
|-------|-------|
| **Module** | Cart |
| **Action** | Remove checked-out items from user's cart |

### order.auto_cancelled (from Order Service)

| Field | Value |
|-------|-------|
| **Module** | Inventory |
| **Action** | Unlock stock for unpaid orders after timeout |

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

### flash_sale.item_purchased (from Flash Sale Service)

| Field | Value |
|-------|-------|
| **Module** | Inventory |
| **Action** | Update sold count and remaining stock cache |

---

## Request-Reply (Product Service is Responder)

### cart.product_info -- Cart <-> Product Catalog

| Role | Service | Topic |
|------|---------|-------|
| Requester | Cart (Product Service internal) | `cart.product_info.request` |
| Responder | Product Catalog | `cart.product_info.response` |

**Request:**
```json
{ "product_id": "uuid", "variant_id": "uuid" }
```

**Response:**
```json
{ "name": "Ao Thun", "price": 150000, "image_url": "https://...", "available": true }
```

### order.stock_check -- Order <-> Product (Inventory)

| Role | Service | Topic |
|------|---------|-------|
| Requester | Order Service | `order.stock_check.request` |
| Responder | Product Service | `order.stock_check.response` |

**Request:**
```json
{ "items": [{ "variant_id": "uuid", "quantity": 2 }] }
```

**Response:**
```json
{ "all_available": true, "results": [{ "variant_id": "uuid", "available": true, "stock": 50 }] }
```

### order.cart_items -- Order <-> Product (Cart)

| Role | Service | Topic |
|------|---------|-------|
| Requester | Order Service | `order.cart_items.request` |
| Responder | Product Service | `order.cart_items.response` |

**Request:**
```json
{ "user_id": 42, "selected_item_ids": ["uuid-1", "uuid-2"] }
```

**Response:**
```json
{ "items": [{ "cart_item_id": "uuid-1", "variant_id": "uuid", "price": 150000, "quantity": 2 }] }
```
