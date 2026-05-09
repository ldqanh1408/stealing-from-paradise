# FR-SEARCH: Search Service Functional Requirements

> **Service**: search-service (Port 8091)
> **Database**: Elasticsearch
> **Source**: 02_API_search_service.md

---

## FR-SEARCH-001: Full-Text Product Search

| Attribute | Value |
|-----------|-------|
| **ID** | FR-SEARCH-001 |
| **Endpoints** | GET /search/products |
| **Method** | GET |
| **Auth** | Public |

**Description**: Search products by keywords with Vietnamese text analysis. Results are collapsed by `product_id` (SKU-first architecture).

**Acceptance Criteria**:
| # | Criterion |
|---|-----------|
| 1 | Supports unaccented queries (e.g., "ao thun" matches "áo thun") |
| 2 | Supports fuzzy matching for misspellings (fuzziness: AUTO) |
| 3 | `product_name` has 3x boost over other text fields |
| 4 | Results collapsed by `product_id` (one card per product) |
| 5 | Representative SKU chosen via `inner_hits`: cheapest in-stock variant |
| 6 | Response includes `total_results`, `page`, `size`, `total_pages`, `products[]` |

---

## FR-SEARCH-002: Filtering and Facets

| Attribute | Value |
|-----------|-------|
| **ID** | FR-SEARCH-002 |
| **Endpoints** | GET /search/products |
| **Method** | GET |
| **Auth** | Public |

**Description**: Filter search results by category, price range, stock status, and flash sale status. Return aggregation facets for the filter sidebar.

**Acceptance Criteria**:
| # | Criterion |
|---|-----------|
| 1 | Filter by `category_id` (includes subcategories) |
| 2 | Filter by `price_min` and `price_max` range |
| 3 | Filter by `in_stock` (default: true) |
| 4 | Filter by `is_flash` (flash sale products only) |
| 5 | Sort by `relevance`, `price_asc`, `price_desc`, `newest`, `sold_desc` |
| 6 | Aggregation facets returned for color, size, price range |

---

## FR-SEARCH-003: Autocomplete / Suggestions

| Attribute | Value |
|-----------|-------|
| **ID** | FR-SEARCH-003 |
| **Endpoints** | GET /search/products/suggest |
| **Method** | GET |
| **Auth** | Public |

**Description**: Return search suggestions as the user types (minimum 2 characters).

**Acceptance Criteria**:
| # | Criterion |
|---|-----------|
| 1 | Returns `{"suggestions": [...]}` array of strings |
| 2 | Minimum 2 characters for query |
| 3 | Default 5 suggestions, max 10 |
| 4 | Results deduplicated |

---

## FR-SEARCH-004: Reindex Management

| Attribute | Value |
|-----------|-------|
| **ID** | FR-SEARCH-004 |
| **Endpoints** | POST /search/reindex |
| **Method** | POST |
| **Auth** | Admin JWT |

**Description**: Trigger full reindex of the Elasticsearch index from the Product Service database.

**Acceptance Criteria**:
| # | Criterion |
|---|-----------|
| 1 | Admin-only operation (401/403 for non-admin) |
| 2 | Rejects concurrent reindex requests (409) |
| 3 | Reports reindex status (started, completed, failed) |
| 4 | Reindex does not block search queries (zero-downtime via alias swap) |

---

## FR-SEARCH-005: Kafka Event Consumption

| Attribute | Value |
|-----------|-------|
| **ID** | FR-SEARCH-005 |
| **Description** | Consume 10 Kafka topics to maintain Elasticsearch index in near real-time |

**Acceptance Criteria**:
| # | Criterion |
|---|-----------|
| 1 | `product.approved` -> Bulk index all SKU documents |
| 2 | `product.updated` -> Update product-level fields by product_id |
| 3 | `product.deleted` -> Remove from index |
| 4 | `inventory.adjusted` -> Partial update: stock_status |
| 5 | `category.updated` -> Update category fields |
| 6 | All updates use partial updates (not full reindex) for SKU-level changes |

---

## Cross-References

| Ref ID | Target |
|--------|--------|
| UC-SEARCH-001 | Search products |
| UC-SEARCH-002 | Filter results |
| UC-SEARCH-003 | Reindex |
| BR-SEARCH-001 | Search business rules |
| ST-SEARCH-001 | Index state |
| ENTITY-SEARCH-001 | SKU document mapping |
