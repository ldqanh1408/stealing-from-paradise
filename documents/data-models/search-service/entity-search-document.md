# ENTITY-SEARCH-001: SKU Search Document (Elasticsearch)

> **Service**: search-service (Port 8091)
> **Database**: Elasticsearch (not PostgreSQL)
> **Index**: `skus`
> **Architecture**: SKU-first with field collapsing by `product_id`
> **Source**: database-entities.md Section 10, 03_database_tables.md
> **Updated**: 2026-05-26 (removed `variant_name` from index mapping -- unused; removed `discount_pct` -- derived from price+original_price, unused in filter/sort/response; removed `product_created_at` and `sku_updated_at` -- unused sort; removed `sold_count` -- unused sort; removed `order.created` consumer)

---

## Index Settings

```json
{
  "settings": {
    "number_of_shards": 3,
    "number_of_replicas": 1,
    "analysis": {
      "analyzer": {
        "vietnamese_analyzer": {
          "type": "custom",
          "tokenizer": "standard",
          "filter": ["lowercase", "asciifolding"]
        }
      }
    }
  }
}
```

---

## Mapping

| # | Field | ES Type | Indexed | Notes |
|---|-------|---------|---------|-------|
| 1 | `sku_id` | keyword | Yes | PK of product_variant |
| 2 | `product_id` | keyword | Yes | Field collapse target |
| 3 | `seller_id` | keyword | Yes | FK to USERS.id |
| 4 | `product_name` | text (vi_analyzer) + .keyword | Yes | Full-text search primary field, boost 3x |
| 5 | `product_slug` | keyword | Yes | SEO-friendly URL path |
| 6 | `product_description` | text (vi_analyzer) | Yes | Full-text search secondary field |
| 7 | `product_attributes` | object (dynamic: true) | Yes | Product-level dynamic attributes |
| 8 | `category_id` | keyword | Yes | Category filter |
| 9 | `category_path` | keyword | Yes | Full category path for breadcrumbs |
| 10 | `variant_attributes` | object (dynamic: true) | Yes | Variant-level dynamic attributes (color, size, etc.) |
| 11 | `sku_code` | keyword | Yes | Unique SKU code |
| 12 | `price` | double | Yes | Current selling price (flash-sale adjusted) |
| 13 | `original_price` | double | Yes | Original price before flash discount |
| 14 | `has_discount` | boolean | Yes | Whether price < original_price |
| 15 | `flash_session_id` | keyword | Yes | Active flash sale session, null if none |
| 16 | `stock_status` | keyword | Yes | `in_stock` or `out_of_stock` |
| 17 | `product_status` | keyword | Yes | Product lifecycle status |
| 18 | `sku_status` | keyword | Yes | SKU lifecycle status |
| 19 | `is_active` | boolean | Yes | Composite active flag (product + SKU both active) |
| 20 | `thumbnail_url` | keyword | **index: false** | Product thumbnail, stored but not searchable |
| 21 | `sku_image_url` | keyword | **index: false** | SKU image, stored but not searchable |
| 22 | `seller_name` | text + .keyword | Yes | Seller shop name, text searchable |

---

## Architecture: SKU-First with Field Collapsing

| Design Decision | Rationale |
|-----------------|-----------|
| One document per SKU | Filter runs on root-level fields -- fast and correct |
| Collapse by `product_id` | Groups SKUs into one product card on listing |
| `inner_hits` for representative SKU | Picks cheapest in-stock SKU for price display |
| No nested documents | Avoids nested query complexity and performance penalty |
| Partial updates per SKU | Update one document (price/stock) instead of reindexing entire product |

---

## Index Rebuild / Reindex

Triggered via Kafka events from the Product Service (consumer-only):

| Kafka Topic | ES Action | Scope | Notes |
|-------------|-----------|-------|-------|
| `product.activated` | Bulk index all SKU documents | Product + all SKUs | **Primary indexing event** -- product must be approved + published |
| `product.deactivated` | Set `is_active = false` | All SKUs of product | Do NOT delete -- allows fast reactivation |
| `product.updated` | Update_by_query by `product_id` | Product-level fields | Name, description, attributes, images |
| `product.deleted` | Delete documents by `product_id` | All SKUs of product | Permanent removal |
| `variant.stock_updated` | Partial _update: `stock_status` | Single SKU | |
| `category.updated` | Update_by_query by `category_id` | Category fields | |

---

## Vietnamese Text Analysis

| Concern | Solution |
|---------|----------|
| Unaccented input ("ao thun") | `asciifolding` token filter with `preserve_original: true` |
| Misspellings ("ao thunn") | `fuzziness: AUTO` in multi_match query |
| Synonyms | Synonym filter file: `synonyms/vi_product.txt` |
| Recommended plugin | `analysis-icu` for Unicode normalization |

---

## Cross-References

| Ref ID | Type | Description |
|--------|------|-------------|
| UC-SEARCH-001 | Use Case | Full-text search + filtering consolidated |
| UC-SEARCH-003 | Use Case | Trigger reindex |
| BR-SEARCH-001 | Business Rule | Search business rules |
| ST-SEARCH-001 | State Diagram | Index lifecycle states |
| FR-SEARCH-001 | Functional Req | Full-text search |
| FR-SEARCH-002 | Functional Req | Filtering |
| FR-SEARCH-003 | Functional Req | Autocomplete / suggestions |
| FR-SEARCH-004 | Functional Req | Reindex management |
| FR-SEARCH-005 | Functional Req | Kafka event consumption |
| DB-10 | Database Section | database-entities.md Section 10 |
| KAFKA_EVENTS.md | Kafka Events | Search Service Kafka events (source of truth) |
