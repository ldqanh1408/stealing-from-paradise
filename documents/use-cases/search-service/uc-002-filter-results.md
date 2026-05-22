# UC-SEARCH-002: Filter Search Results (DEPRECATED)

|> **Service**: search-service (Port 8091)
|> **Use Case ID**: UC-SEARCH-002
|> **Priority**: N/A
|> **Status**: DEPRECATED - Merged into UC-SEARCH-001
|> **Source**: 02_API_search_service.md
|> **Last Updated**: 2026-05-22

---

## Deprecation Notice

> **This use case has been deprecated and merged into UC-SEARCH-001.**
>
> All filtering, sorting, and faceting functionality is now part of UC-SEARCH-001. The `GET /search/products` endpoint handles both browse (without keyword) and search (with keyword) scenarios, including all filter parameters.

---

## Migration Guide

All filtering operations should now use the consolidated endpoint:

### Before (Separate endpoints)
```
GET /products?category_id=X                    # Product Service
GET /search/products?q=keyword                 # Search Service
GET /search/products?q=keyword&category_id=X    # Search Service (filter + search)
```

### After (Consolidated)
```
GET /search/products                                    # Browse all (homepage)
GET /search/products?category_id=X                     # Browse category
GET /search/products?q=keyword                         # Full-text search
GET /search/products?q=keyword&category_id=X            # Search + filter
GET /search/products?category_id=X&price_min=100000    # Browse + filters
```

---

## Filter Parameters

All filters are now available in UC-SEARCH-001:

| Parameter | Type | Description |
|-----------|------|-------------|
| `q` | string | Search keywords (optional) |
| `category_id` | UUID | Filter by category |
| `price_min` | integer | Minimum price |
| `price_max` | integer | Maximum price |
| `in_stock` | boolean | In-stock only |
| `is_flash` | boolean | Flash sale only |
| `sort` | string | Sort order |

---

## Cross-References

|| Ref ID | Target |
|--------|--------|
| UC-SEARCH-001 | Consolidated endpoint (use this instead) |
| FR-SEARCH-001 | Full-text search requirement |
| FR-SEARCH-002 | Filter/facets requirement |

---

## History

| Date | Change |
|------|--------|
| 2026-05-22 | Deprecated. Merged into UC-SEARCH-001 for unified browse/search experience. |
