# Traceability Matrix: Search Service

> **Service**: search-service (Port 8091)
> **Database**: Elasticsearch
> **Architecture**: SKU-first, field collapsing

---

## FR <-> UC Mapping

| FR ID | FR Name | UC ID |
|-------|---------|-------|
| FR-SEARCH-001 | Full-Text Product Search | UC-SEARCH-001 |
| FR-SEARCH-002 | Filtering and Facets | UC-SEARCH-002 |
| FR-SEARCH-003 | Autocomplete / Suggestions | UC-SEARCH-001 (alternate flow) |
| FR-SEARCH-004 | Reindex Management | UC-SEARCH-003 |
| FR-SEARCH-005 | Kafka Event Consumption | UC-SEARCH-001, UC-SEARCH-002 (data ingestion) |

---

## UC <-> BR Mapping

| UC ID | BR ID(s) |
|-------|----------|
| UC-SEARCH-001 | BR-SEARCH-001-01, BR-SEARCH-001-02, BR-SEARCH-001-04, BR-SEARCH-001-05, BR-SEARCH-001-07 |
| UC-SEARCH-002 | BR-SEARCH-001-04, BR-SEARCH-001-05, BR-SEARCH-001-07, BR-SEARCH-001-08 |
| UC-SEARCH-003 | BR-SEARCH-001-06, BR-SEARCH-001-08 |

---

## Entity <-> UC Mapping

| Entity | UC ID(s) |
|--------|----------|
| ENTITY-SEARCH-001 (SKU Document) | UC-SEARCH-001, UC-SEARCH-002, UC-SEARCH-003 |

---

## State <-> UC/BR Mapping

| State Transition | Triggering UC | Triggering BR |
|------------------|---------------|---------------|
| EMPTY -> INDEXED | UC-SEARCH-003 | BR-SEARCH-001-06 |
| INDEXED -> UPDATED | — | BR-SEARCH-001-03 |
| INDEXED -> HIDDEN | — | BR-SEARCH-001-03 |
| INDEXED -> REMOVED | — | BR-SEARCH-001-03 |

---

## API <-> FR Mapping

| API Endpoint | Method | Auth | FR ID |
|--------------|--------|------|-------|
| /search/products | GET | Public | FR-SEARCH-001, FR-SEARCH-002 |
| /search/products/suggest | GET | Public | FR-SEARCH-003 |
| /search/reindex | POST | Admin | FR-SEARCH-004 |

---

## Kafka <-> Entity Mapping

| Kafka Topic | Affected Index | Operation |
|-------------|---------------|-----------|
| product.approved | skus | Bulk index |
| product.updated | skus | Update_by_query |
| product.deleted | skus | Delete / set is_active=false |
| product.rejected | skus | Delete / set is_active=false |
| product.auto_hidden (post-MVP) | skus | Set is_active=false |
| category.updated | skus | Update_by_query |
| inventory.adjusted | skus | Partial update (stock_status) |
| order.created | skus | Update sold_count (optional) |
| account.locked (post-MVP) | skus | Update_by_query (hide seller) |

---

## Source Document Traceability

| This Document | Source File | Section |
|---------------|-------------|---------|
| ENTITY-SEARCH-001 | database-entities.md | Section 10 |
| ENTITY-SEARCH-001 | data-models/search-service/entity-search-document.md | Index mapping |
| API contracts | api-contracts/search-service/ | All endpoints |
| Kafka info | messaging/search-service/KAFKA_EVENTS.md | Consumer topics |
