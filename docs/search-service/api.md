# 🔍 Search Service API

**Service**: Search Service  
**Port**: 8089  
**Base URL**: `/api/v1`  
**Version**: v5.3 RTS

## Overview

Search Service provides:
- Full-text search with Elasticsearch
- Product search with filters & facets
- Autocomplete suggestions
- Real-time index updates from Product Service

## 📡 Kafka Integration

### Produces (Events Published)
- None (read-only service)

### Consumes (Events Listened)
| Topic | Producer | Purpose |
|-------|----------|---------|
| `product.approved` | Admin Service | Index approved products |
| `product.updated` | Product Service | Update product index |
| `product.deleted` | Product Service | Remove from index |
| `product.auto_hidden` | Worker Service (JOB-16) | Hide rejected products |
| `inventory.adjusted` | Product Service | Update stock status |

## Key Endpoints

### Product Search
```
GET /search/products         Search products (full-text + filters)
```

**Query Parameters**:
```
q              - Search keyword (full-text)
category_id    - Filter by category
price_min      - Minimum price
price_max      - Maximum price
in_stock       - Only in-stock items (default: true)
is_flash       - Only flash sale items
sort           - relevance|price_asc|price_desc|newest|sold_desc
page           - Page number (default: 0)
size           - Items per page (default: 20, max: 100)
```

### Autocomplete
```
GET /search/products/suggest    Get search suggestions
```

**Query Parameters**:
```
q     - User search input (min 2 chars)
size  - Number of suggestions (default: 5, max: 10)
```

## Total Endpoints: 2

## For Complete Documentation

→ See **[/docs/api/03-search-service.md](../api/03-search-service.md)**

Contains:
- Full response examples
- Filter & facet examples
- Elasticsearch index structure
- Real-time synchronization details

---

**Last Updated**: 2026-04-28  
**Version**: v5.3 RTS

