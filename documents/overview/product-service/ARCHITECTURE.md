# Product Service — Architecture Overview

> Service: product-service (SVC-007, Port 8090)
> Database: PostgreSQL
> Source: `documents` micro-docs
> Generated: 2026-05-10

---

## Responsibility
Product catalog management, variant/price/stock management, category management, cart operations, and product image storage (MinIO).

## Tech Stack
- Java 25, Spring Boot 4.0.4
- PostgreSQL (products, product_variants, carts, cart_items, categories, product_images, stock_reservations)
- MinIO (product image object storage)
- Kafka (producer + consumer + request-reply)

## Key Features
- Seller product CRUD with variant matrix (color, size, etc.)
- Category management by admin
- SKU-first inventory with stock quantity tracking
- Cart with lazy price/stock validation on view
- Stock reservation via Redis DECR + DB optimistic locking (version field)
- Product image upload via MinIO presigned URLs
- Flash sale price sync (activate/deactivate)
- Checkout preview with TTL token

## PostgreSQL Tables

| Table | Purpose |
|-----------|---------|
| products | Product master data (name, description, seller_id, category_id, status) |
| product_variants | Variants (SKU, price, stock_quantity, variant_attributes as JSONB) |
| categories | Category tree with parent_id |
| product_images | Image URLs with sort_order |
| carts | User cart with items array |
| cart_items | Cart items (variant_id, quantity, price_snapshot) |
| stock_reservations | Pending stock holds during checkout (15min TTL) |

## Product Lifecycle
```
active → out_of_stock (all variants stock=0)
active → inactive (seller unpublishes)
out_of_stock → active (restock)
inactive → active (seller republishes)
```

## API Endpoints

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| GET | `/v1/products/{id}` | Public | Product detail with variants |
| POST | `/v1/products` | SELLER | Create product |
| PUT | `/v1/products/{id}` | SELLER | Update product |
| POST | `/v1/products/{id}/variants` | SELLER | Add variant |
| PUT | `/v1/variants/{id}` | SELLER | Update variant (price, stock, status) |
| POST | `/v1/categories` | ADMIN | Create category |
| GET | `/v1/cart` | BUYER | View cart |
| POST | `/v1/cart/items` | BUYER | Add to cart |
| PUT | `/v1/cart/items/{id}` | BUYER | Update cart item |
| DELETE | `/v1/cart/items/{id}` | BUYER | Remove from cart |
| GET | `/v1/inventory` | SELLER | View inventory |

> **Note:** Product listing and filtering has been moved to **Search Service** (`GET /search/products`).

## Kafka Integration

| Direction | Topic | Purpose |
|-----------|-------|---------|
| Produce | `product.created/updated/deleted` | Index in Search Service |
| Produce | `variant.price_updated` | Update search index |
| Produce | `variant.stock_updated` | Update search index |
| Produce | `cart.item_added` | Analytics |
| Produce | `flash_sale.price_sync` | Activate/deactivate flash prices in search |
| Consume | `order.created` | Lock stock |
| Consume | `order.cancelled` | Release stock |
| Consume | `order.returned` | Restore stock |
| Consume | `flash_sale.session_started` | Calculate flash prices |
| Consume | `flash_sale.session_ended` | Reset prices |
