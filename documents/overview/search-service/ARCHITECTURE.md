# Search Service — Architecture Overview

> Service: search-service (SVC-008, Port 8091)
> Database: Elasticsearch
> Source: `documents` micro-docs
> Generated: 2026-05-10

---

## Responsibility
Full-text product search with Vietnamese language support. Consumer-only service that indexes products from Kafka events into Elasticsearch.

## Tech Stack
- Java 25, Spring Boot 4.0.4
- Elasticsearch 8.10
- Kafka consumer (product events)
- ICU Analysis plugin for Vietnamese text

## Key Features
- Full-text search with Vietnamese text analysis (no-diacritic, fuzzy, synonyms)
- SKU-first indexing with field collapsing by product_id
- Faceted filtering (category, price range, brand, rating)
- Sort by price, newest, relevance with stable tiebreaker (sort_id)
- Flash sale price overlays via `flash_sale.price_sync` events
- Pagination up to 10,000 results (ES max_result_window)

## Elasticsearch Index: `skus`

| Parameter | Value | Reason |
|-----------|-------|--------|
| max_result_window | 10,000 | ES hard limit |
| Page size | 40 | Max 250 pages |
| track_total_hits | 10,000 | Count up to 10k then show "10,000+" |
| Tiebreaker | sort_id ASC | Mandatory for stable pagination |

## Vietnamese Text Analysis

| Problem | Solution |
|---------|----------|
| No-diacritic typing ("ao thun") | `asciifolding` filter with `preserve_original: true` |
| Spelling errors ("ao thunn") | `fuzziness: AUTO` in query |
| Synonyms | Synonym filter with `synonyms/vi_product.txt` file |

## Domain Model

| Document | Index | Key Fields |
|----------|-------|------------|
| SearchDocument | skus | product_id, variant_id, name, description, category_id, price, flash_price, stock_status, seller_id, created_at |

## Kafka Integration (Consumer-Only)

| Direction | Topic | Source | Action |
|-----------|-------|--------|--------|
| Consume | `product.created` | Product Service | Index new product |
| Consume | `product.updated` | Product Service | Update index |
| Consume | `product.deleted` | Product Service | Remove from index |
| Consume | `category.updated` | Product Service | Reindex category |
| Consume | `variant.price_updated` | Product Service | Update price |
| Consume | `variant.stock_updated` | Product Service | Update stock status |
| Consume | `inventory.adjusted` | Product Service | Update inventory |
| Consume | `flash_sale.price_sync` | Product Service | Apply/remove flash prices |

## Reindex Flow
1. Admin triggers reindex via API or cron
2. Service queries Product Service for all active products
3. Batch index into Elasticsearch (bulk API)
4. Atomic alias swap (skus_v1 → skus) for zero-downtime index rotation
