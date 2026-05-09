# UC-SEARCH-002: Filter Search Results

> **Service**: search-service (Port 8091)
> **Use Case ID**: UC-SEARCH-002
> **Priority**: MEDIUM
> **Source**: 02_API_search_service.md

---

## Brief

User refines search results by applying filters (category, price range, stock status, flash sale) and selecting a sort order. The system updates results with facet counts for the filter sidebar.

---

## Actors

| Actor | Role |
|-------|------|
| Shopper | Applies filters and sort options |
| System | Elasticsearch query engine with aggregations |

---

## Preconditions

| # | Condition |
|---|-----------|
| 1 | Search query has been executed (UC-SEARCH-001) or user is browsing a category |
| 2 | Elasticsearch `skus` index is operational |

---

## Main Flow

| Step | Actor | Action |
|------|-------|--------|
| 1 | User | Selects a category filter from the sidebar |
| 2 | Client | Appends `category_id={id}` to GET /search/products params |
| 3 | Server | Adds `term: { category_id }` to `bool.filter` |
| 4 | User | Adjusts price range slider (e.g., 100k-300k) |
| 5 | Client | Appends `price_min=100000&price_max=300000` |
| 6 | Server | Adds `range: { price: { gte: 100000, lte: 300000 } }` to `bool.filter` |
| 7 | User | Toggles `in_stock` filter |
| 8 | Client | Appends `in_stock=true` |
| 9 | Server | Adds `term: { stock_status: "in_stock" }` to `bool.filter` |
| 10 | User | Changes sort to "Price: Low to High" |
| 11 | Client | Appends `sort=price_asc` |
| 12 | Server | Sorts by `price ASC` with tiebreaker |
| 13 | Server | Executes query with aggregations: by color, by size, price stats |
| 14 | Server | Returns products + `filters: { colors: [...], sizes: [...] }` |
| 15 | Client | Updates product grid and filter sidebar with facet counts |

---

## Alternate Flows

| Flow | Condition | Action |
|------|-----------|--------|
| A1 | User clears all filters | Reload base search/browse query without filter params |
| A2 | Filter combination yields 0 results | Show "0 results" with suggestion to broaden filters |
| A3 | `is_flash=true` | Add `exists: { field: "flash_session_id" }` filter |
| A4 | Sort by `sold_desc` | Requires `sold_count` field populated via `order.created` events |

---

## Postconditions

| # | Condition |
|---|-----------|
| 1 | Filtered results displayed with active filter tags |
| 2 | Facet counts in sidebar reflect current filtered result set |

---

## Cross-References

| Ref ID | Target |
|--------|--------|
| FR-SEARCH-002 | Filter/facets requirement |
| BR-SEARCH-001-04 | Query construction rules |
| BR-SEARCH-001-05 | Sorting rules |
| UC-SEARCH-001 | Base search |
| ENTITY-SEARCH-001 | SKU document mapping |
