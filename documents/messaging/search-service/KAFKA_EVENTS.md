# Kafka Events -- Search Service

> Service: search-service (SVC-008, Port 8091)
> Database: Elasticsearch (index: skus)
> Source: `documents/messaging/KAFKA_CATALOG.md`, `documents/overview/search-service/ARCHITECTURE.md`
> Generated: 2026-05-10 | Updated: 2026-05-25 (overhaul: product.activated is sole ES indexing trigger; removed product.created/rejected/approved from consumer list)

---

## Events Consumed (Consumer-Only)

Search Service is a **consumer-only** service. It does NOT produce any Kafka events. All index updates are triggered by consuming events from other services.

### product.activated (from Product Service)

|| Field | Value |
|-------|-------|
| **GroupId** | search-service-product-group |
| **Action** | Bulk-index all SKU documents for this product into Elasticsearch `skus` index. This is the **sole event that triggers initial ES indexing** for a product. |

> A product reaches `product.activated` only after: `draft → pending (submit) → approved (admin) → active (seller publish)`. Products in `draft`, `pending`, `rejected` states are never indexed.

**Payload:**
```json
{
  "event_id": "evt_20260525_001",
  "event_type": "product.activated",
  "timestamp": "2026-05-25T10:00:00Z",
  "source_service": "product-service",
  "version": 1,
  "data": {
    "productId": "uuid",
    "sellerId": 42,
    "name": "Ao Thun Nike Air Nam",
    "categoryId": "uuid",
    "status": "active"
  }
}
```

---

### product.deactivated (from Product Service)

|| Field | Value |
|-------|-------|
| **GroupId** | search-service-product-group |
| **Action** | Set `is_active = false` on all ES documents for this product (do NOT delete, so reactivation is fast). Product remains in index but is excluded from search results. |

**Payload:**
```json
{
  "event_id": "evt_20260525_002",
  "event_type": "product.deactivated",
  "timestamp": "2026-05-25T11:00:00Z",
  "source_service": "product-service",
  "version": 1,
  "data": {
    "productId": "uuid",
    "sellerId": 42,
    "status": "inactive"
  }
}
```

---

### product.updated (from Product Service)

|| Field | Value |
|-------|-------|
| **GroupId** | search-service-product-group |
| **Action** | Update existing product document in ES index (update_by_query by product_id). Used for field-level changes (name, description, attributes, images) while product is already active or inactive. |

> Publish/unpublish transitions use `product.activated`/`product.deactivated`. This event does NOT change `is_active`.

**Payload:**
```json
{
  "event_id": "evt_20260525_003",
  "event_type": "product.updated",
  "timestamp": "2026-05-25T12:00:00Z",
  "source_service": "product-service",
  "version": 1,
  "data": {
    "productId": "uuid"
  }
}
```

---

### product.deleted (from Product Service)

|| Field | Value |
|-------|-------|
| **GroupId** | search-service-product-group |
| **Action** | Remove all ES documents for this product (delete by product_id). |

**Payload:**
```json
{
  "event_id": "evt_20260525_004",
  "event_type": "product.deleted",
  "timestamp": "2026-05-25T13:00:00Z",
  "source_service": "product-service",
  "version": 1,
  "data": {
    "productId": "uuid",
    "sellerId": 42
  }
}
```

---

### category.updated (from Product Service)

|| Field | Value |
|-------|-------|
| **GroupId** | search-service-product-group |
| **Action** | Reindex all products in the updated category (update_by_query by category_id) |

---

### variant.price_updated (from Product Service)

|| Field | Value |
|-------|-------|
| **GroupId** | search-service-product-group |
| **Action** | Update price field in ES document for the affected variant (partial _update on single document) |

**Payload:**
```json
{
  "event_id": "evt_20260510_004",
  "event_type": "variant.price_updated",
  "timestamp": "2026-05-10T08:00:00Z",
  "source_service": "product-service",
  "version": 1,
  "data": {
    "variantId": "uuid",
    "productId": "uuid",
    "price": 380000,
    "originalPrice": 400000
  }
}
```

---

### variant.stock_updated (from Product Service)

|| Field | Value |
|-------|-------|
| **GroupId** | search-service-product-group |
| **Action** | Update stock_status field in ES document (partial _update on single document) |

**Payload:**
```json
{
  "event_id": "evt_20260510_005",
  "event_type": "variant.stock_updated",
  "timestamp": "2026-05-10T08:00:00Z",
  "source_service": "product-service",
  "version": 1,
  "data": {
    "variantId": "uuid",
    "productId": "uuid",
    "stockQuantity": 50,
    "status": "active",
    "stockStatus": "in_stock"
  }
}
```

---

### inventory.adjusted (from Product Service)

|| Field | Value |
|-------|-------|
| **GroupId** | search-service-product-group |
| **Action** | Update inventory/stock data in ES index (partial _update: stock_status) |

---

### flash_sale.price_sync (from Product Service)

|| Field | Value |
|-------|-------|
| **GroupId** | search-service-flashsale-group |
| **Action** | `activate`: apply flash prices to ES documents; `deactivate`: reset to original prices (bulk update) |

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

## Events Produced

**None.** Search Service is consumer-only. All data flows IN via Kafka and OUT via REST API (`GET /search/products`).

---

## Request-Reply

Search Service does NOT participate in any Kafka request-reply patterns.

---

## Elasticsearch Index Management

### Index: `skus`

- **Type**: Product search documents (SKU-first with field collapsing by product_id)
- **max_result_window**: 10,000
- **Page size**: 40 products/page
- **track_total_hits**: 10,000
- **Tiebreaker**: `sort_id ASC` (mandatory for stable pagination)

### Reindex Flow

```
1. Admin triggers reindex (manual via POST /search/reindex, or cron)
2. Search Service queries Product Service REST API for all ACTIVE products only
   (draft/pending/approved/rejected/inactive products are excluded)
3. Bulk-index into ES via _bulk API
4. Atomic alias swap: skus_v{N} → skus (zero-downtime rotation)
```

### Vietnamese Text Analysis

| Problem | Solution |
|---------|----------|
| No-diacritic typing | `asciifolding` filter with `preserve_original: true` |
| Spelling errors | `fuzziness: AUTO` in query |
| Synonyms | Synonym filter: `synonyms/vi_product.txt` |

---

## Consumer Groups

| Group ID | Topics | Concurrency | Notes |
|----------|--------|-------------|-------|
| search-service-product-group | product.activated, product.deactivated, product.updated, product.deleted, category.updated, variant.price_updated, variant.stock_updated, inventory.adjusted | 3 | Idempotent by event_id |
| search-service-flashsale-group | flash_sale.price_sync | 1 | Sequential processing required |

---

## Idempotency

All consumers deduplicate by `event_id` using a processed events cache (Redis, TTL 24h):

```java
if (processedEventCache.isProcessed(event.event_id)) return;
processEvent(event);
processedEventCache.markProcessed(event.event_id);
```

---

## Events No Longer Consumed

The following events from the previous design are **removed** because products are never indexed before admin approval:

| Event | Reason for Removal |
|-------|-------------------|
| `product.created` | Product starts as `draft`; indexing deferred until `product.activated` |
| `product.approved` | Does not change ES state; pre-warm removed — actual indexing via `product.activated` |
| `product.rejected` | Product was never indexed; no ES action needed |
