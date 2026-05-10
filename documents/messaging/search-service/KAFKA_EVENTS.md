# Kafka Events -- Search Service

> Service: search-service (SVC-008, Port 8091)
> Database: Elasticsearch (index: skus)
> Source: `documents/messaging/KAFKA_CATALOG.md`, `documents/overview/search-service/ARCHITECTURE.md`
> Generated: 2026-05-10

---

## Events Consumed (Consumer-Only)

Search Service is a **consumer-only** service. It does NOT produce any Kafka events. All index updates are triggered by consuming events from other services.

### product.created (from Product Service)

| Field | Value |
|-------|-------|
| **GroupId** | search-service-product-group |
| **Action** | Index new product document into Elasticsearch `skus` index |

**Payload:**
```json
{
  "product_id": "uuid",
  "seller_id": "uuid",
  "name": "Ao Thun Nike Air Nam",
  "category_id": "uuid",
  "status": "active",
  "timestamp": "2026-05-10T08:00:00Z"
}
```

### product.updated (from Product Service)

| Field | Value |
|-------|-------|
| **GroupId** | search-service-product-group |
| **Action** | Update existing product document in ES index |

**Payload:**
```json
{
  "product_id": "uuid",
  "name": "Updated Name",
  "category_id": "uuid",
  "status": "active",
  "timestamp": "2026-05-10T08:00:00Z"
}
```

### product.deleted (from Product Service)

| Field | Value |
|-------|-------|
| **GroupId** | search-service-product-group |
| **Action** | Remove product document from ES index |

**Payload:**
```json
{
  "product_id": "uuid",
  "seller_id": "uuid",
  "timestamp": "2026-05-10T08:00:00Z"
}
```

### category.updated (from Product Service)

| Field | Value |
|-------|-------|
| **GroupId** | search-service-product-group |
| **Action** | Reindex all products in the updated category |

### variant.price_updated (from Product Service)

| Field | Value |
|-------|-------|
| **GroupId** | search-service-product-group |
| **Action** | Update price field in ES document for the affected variant |

**Payload:**
```json
{
  "variant_id": "uuid",
  "product_id": "uuid",
  "price": 380000,
  "original_price": 400000,
  "timestamp": "2026-05-10T08:00:00Z"
}
```

### variant.stock_updated (from Product Service)

| Field | Value |
|-------|-------|
| **GroupId** | search-service-product-group |
| **Action** | Update stock_status field in ES document |

**Payload:**
```json
{
  "variant_id": "uuid",
  "product_id": "uuid",
  "stock_quantity": 50,
  "status": "active",
  "stock_status": "in_stock",
  "timestamp": "2026-05-10T08:00:00Z"
}
```

### inventory.adjusted (from Product Service)

| Field | Value |
|-------|-------|
| **GroupId** | search-service-product-group |
| **Action** | Update inventory/stock data in ES index |

### flash_sale.price_sync (from Product Service)

| Field | Value |
|-------|-------|
| **GroupId** | search-service-flashsale-group |
| **Action** | `activate`: apply flash prices to ES documents; `deactivate`: reset to original prices |

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

**None.** Search Service is consumer-only. All data flows IN via Kafka and OUT via REST API (`GET /v1/search`).

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
1. Admin triggers reindex (manual or cron)
2. Search Service queries Product Service REST API for all active products
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
| search-service-product-group | product.created, product.updated, product.deleted, category.updated, variant.price_updated, variant.stock_updated, inventory.adjusted | 3 | Idempotent by event_id |
| search-service-flashsale-group | flash_sale.price_sync | 1 | Sequential processing required |

---

## Idempotency

All consumers deduplicate by `event_id` using a processed events cache (Redis, TTL 24h):

```java
if (processedEventCache.isProcessed(event.event_id)) return;
processEvent(event);
processedEventCache.markProcessed(event.event_id);
```
