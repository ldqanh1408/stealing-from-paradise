# Product Service Operations

**Service:** product-service | **Port:** 8090 | **Database:** MongoDB (product_db)

## Overview

Product catalog, variant management, cart operations, and stock reservations. Images stored in MinIO. Kafka events emitted for search indexing on catalog changes.

## Key MongoDB Collections

| Collection | Purpose |
|---|---|
| `mg_products` | Product catalog (name, price, category, seller_id) |
| `mg_product_variants` | SKU-level variants (size, color, stock, price) |
| `mg_carts` | User shopping carts |
| `mg_cart_items` | Line items within a cart |
| `mg_categories` | Product category hierarchy |
| `mg_product_images` | Image metadata and MinIO object keys |
| `mg_stock_reservations` | Temporary stock holds during checkout |

## Running Locally

```bash
docker-compose up -d product-service
# Standalone: ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `MONGODB_URI` | Yes | `mongodb://localhost:27017` | MongoDB connection string |
| `MONGODB_DATABASE` | Yes | `product_db` | Database name |
| `KAFKA_BOOTSTRAP_SERVERS` | Yes | `localhost:9092` | Kafka broker |
| `MINIO_ENDPOINT` | Yes | `http://localhost:9000` | MinIO S3 endpoint |
| `MINIO_ACCESS_KEY` | Yes | — | MinIO access key |
| `MINIO_SECRET_KEY` | Yes | — | MinIO secret key |
| `MINIO_BUCKET` | Yes | `product-images` | MinIO bucket name |

## Health Check

```
GET /actuator/health
```

## Common Operational Tasks

### Create a Category
```javascript
// mongosh
use product_db
db.mg_categories.insertOne({ name: "Electronics", slug: "electronics", parent_id: null, is_active: true, created_at: new Date() })
```

### View Product Variants
```javascript
use product_db
db.mg_product_variants.find({ product_id: ObjectId("<id>") }, { sku: 1, size: 1, color: 1, stock: 1, price: 1 }).toArray()
```

### Check Stock Reservations
```javascript
use product_db
// Expiring within 5 min
db.mg_stock_reservations.find({ status: "ACTIVE", expires_at: { $lt: new Date(Date.now() + 300000) } }).toArray()
// Already expired
db.mg_stock_reservations.find({ status: "ACTIVE", expires_at: { $lt: new Date() } }).toArray()
```

### Clear Expired Carts
```javascript
use product_db
db.mg_carts.deleteMany({ updated_at: { $lt: new Date(Date.now() - 7*86400000) } })
```

### Verify MinIO Connectivity
```bash
mc ping local
mc ls local/product-images/ --limit 5
```

## Admin Endpoints (RBAC)

> **Re-activated 2026-05-10 v3 — P3-11 APPROVED & applied (status enum 7 values + reviewer columns + reject_count).**

All `/admin/products/*` endpoints require **role=ADMIN** in JWT. Non-admin → `403 FORBIDDEN`.

| Endpoint | Method | Use Case |
|----------|--------|----------|
| `/admin/products/pending` | GET | UC-PRODUCT-013 — list products awaiting review |
| `/admin/products/{productId}/approve` | POST | UC-PRODUCT-014 — approve product |
| `/admin/products/{productId}/reject` | POST | UC-PRODUCT-015 — reject with reason ≥10 chars |

**Operational SLA:** `pending` queue processed within 24h (BR-PRODUCT-009.11). Older items get an internal alert (post-MVP — tracked via dashboard).

**Reviewer audit columns** (P3-11 applied): `products.reject_reason`, `products.reviewed_at`, `products.reviewed_by`, `products.reject_count` are persisted on every approve/reject for compliance.

## Troubleshooting

| Symptom | Likely Cause | Check |
|---|---|---|
| Product images not loading | MinIO unreachable or bucket missing | `mc ping local`; `mc ls local/product-images/` |
| Cart not saving | MongoDB write concern or pool exhaustion | `db.serverStatus().connections` |
| Stock reservation leaking | Expired reservations not cleaned | Query `mg_stock_reservations` for expired ACTIVE rows |
| Search results stale | Kafka event not emitted | Check `product-events` topic; verify consumer lag |
| Variant stock mismatch | Race condition on stock decrement | Use `findAndModify` with `stock > 0` guard in app code |
