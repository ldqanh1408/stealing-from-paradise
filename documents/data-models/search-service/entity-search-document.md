# ENTITY-SEARCH-001: SKU Search Document (Elasticsearch)

> **Service**: search-service (Port 8091)
> **Database**: Elasticsearch (not PostgreSQL)
> **Index**: `skus`
> **Architecture**: SKU-first with field collapsing by `product_id`
> **Source**: database-entities.md Section 10, 03_database_tables.md

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
| 10 | `variant_name` | keyword | Yes | Display name of variant |
| 11 | `variant_attributes` | object (dynamic: true) | Yes | Variant-level dynamic attributes (color, size, etc.) |
| 12 | `sku_code` | keyword | Yes | Unique SKU code |
| 13 | `price` | double | Yes | Current selling price (flash-sale adjusted) |
| 14 | `original_price` | double | Yes | Original price before flash discount |
| 15 | `has_discount` | boolean | Yes | Whether price < original_price |
| 16 | `discount_pct` | integer | Yes | Discount percentage (0-100) |
| 17 | `flash_session_id` | keyword | Yes | Active flash sale session, null if none |
| 18 | `stock_status` | keyword | Yes | `in_stock` or `out_of_stock` |
| 19 | `product_status` | keyword | Yes | Product lifecycle status |
| 20 | `sku_status` | keyword | Yes | SKU lifecycle status |
| 21 | `is_active` | boolean | Yes | Composite active flag (product + SKU both active) |
| 22 | `thumbnail_url` | keyword | **index: false** | Product thumbnail, stored but not searchable |
| 23 | `sku_image_url` | keyword | **index: false** | SKU image, stored but not searchable |
| 24 | `seller_name` | text + .keyword | Yes | Seller shop name, text searchable |
| 25 | `product_created_at` | date | Yes | Product creation date, sortable |
| 26 | `sku_updated_at` | date | Yes | SKU last update, sortable |

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

| Kafka Topic | ES Action | Scope |
|-------------|-----------|-------|
| `product.approved` | Bulk index all SKU documents | Product + all SKUs |
| `product.updated` | Update_by_query by `product_id` | Product-level fields |
| `product.deleted` | Delete documents or set `is_active = false` | All SKUs of product |
| `product.auto_hidden` | Update `is_active = false` | All SKUs of product |
| `inventory.adjusted` | Partial update: `stock_status` | Single SKU |
| `category.updated` | Update_by_query by `category_id` | Category fields |
| `order.created` | Update `sold_count` (optional) | Affected products |
| `account.locked` | Update_by_query by `seller_id`: set hidden | All SKUs of seller |

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
| UC-SEARCH-001 | Use Case | Full-text search products |
| UC-SEARCH-002 | Use Case | Filter search results |
| UC-SEARCH-003 | Use Case | Trigger reindex |
| BR-SEARCH-001 | Business Rule | Search business rules |
| ST-SEARCH-001 | State Diagram | Index lifecycle states |
| FR-SEARCH-001 | Functional Req | Full-text search |
| FR-SEARCH-002 | Functional Req | Filtering and facets |
| FR-SEARCH-003 | Functional Req | Reindex management |
| DB-10 | Database Section | database-entities.md Section 10 |
