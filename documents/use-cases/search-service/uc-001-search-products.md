# UC-SEARCH-001: Search Products (Full-Text)

> **Service**: search-service (Port 8091)
> **Use Case ID**: UC-SEARCH-001
> **Priority**: HIGH
> **Source**: 02_API_search_service.md

---

## Brief

User enters keywords in the search bar. The system performs full-text search against the Elasticsearch index and returns collapsed product results (one card per product).

---

## Actors

| Actor | Role |
|-------|------|
| Shopper (any user) | Enters search keywords |
| System | Elasticsearch query engine |

---

## Preconditions

| # | Condition |
|---|-----------|
| 1 | Elasticsearch `skus` index is populated and healthy |
| 2 | Vietnamese text analyzer is configured |

---

## Main Flow

| Step | Actor | Action |
|------|-------|--------|
| 1 | User | Types keywords in search bar, presses Enter |
| 2 | Client | Sends GET /search/products?q={keywords}&page=0&size=20 |
| 3 | Server | Constructs `multi_match` query across `product_name^3`, `product_description`, `product_attributes.*` |
| 4 | Server | Adds `bool.filter`: `is_active = true` |
| 5 | Server | Applies field collapsing by `product_id` with `inner_hits` (cheapest in-stock SKU) |
| 6 | Server | Sorts by `_score DESC` |
| 7 | Server | Returns `{"total_results": N, "products": [...], "page": 0, "size": 20, "total_pages": M}` |
| 8 | Client | Renders product cards with representative price, image, seller name |

---

## Alternate Flows

| Flow | Condition | Action |
|------|-----------|--------|
| A1 | User types while searching | Client calls GET /search/products/suggest for autocomplete |
| A2 | No results found | Returns `{"total_results": 0, "products": []}` |
| A3 | Fuzzy match returns unexpected results | User refines query with more specific keywords |
| A4 | Page beyond `max_result_window` | Returns empty result set (ES limit 10,000) |

---

## Postconditions

| # | Condition |
|---|-----------|
| 1 | User sees relevant products matching search query |
| 2 | Products are grouped (one card per product via field collapsing) |

---

## Exceptions

| Code | Condition | Response |
|------|-----------|----------|
| 503 | Elasticsearch unavailable | HTTP 503, error message |
| 400 | `q` parameter empty or < 1 character | HTTP 400 |

---

## Cross-References

| Ref ID | Target |
|--------|--------|
| FR-SEARCH-001 | Full-text search requirement |
| BR-SEARCH-001-04 | Query construction rules |
| BR-SEARCH-001-01 | Field collapsing rules |
| BR-SEARCH-001-02 | Vietnamese text analysis |
| ENTITY-SEARCH-001 | SKU document mapping |
