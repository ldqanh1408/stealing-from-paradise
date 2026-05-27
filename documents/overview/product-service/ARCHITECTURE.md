# Product Service — Architecture Overview

> Service: product-service (SVC-007, Port 8090)
> Database: PostgreSQL
> Source: `documents` micro-docs
> Generated: 2026-05-10 | Updated: 2026-05-25 (`inventory.adjusted` event removed -- `variant.stock_updated` is the sole stock-update event; updated header date)

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
- SKU-first inventory with stock quantity tracking via PostgreSQL with pessimistic locking for reservations
- Cart with lazy price/stock validation on view
- Stock reservation via DB pessimistic locking (SELECT ... FOR UPDATE)
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
draft → pending → approved → active ↔ out_of_stock
                      ↓               ↓
                  rejected      inactive
```

> Products are visible on the marketplace only when in `active` or `out_of_stock` state. The Search Service indexes products only when they transition from `approved` to `active` (via `product.activated` event).

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
| Produce | `product.activated` | Index product in Search Service (sole indexing trigger: `approved → active`) |
| Produce | `product.deactivated` | Remove/hide product from Search Service (`active/out_of_stock → inactive`) |
| Produce | `product.updated` | Update product fields in Search Service (name, description, attributes, images) |
| Produce | `product.deleted` | Remove product from Search Service |
| Produce | `product.pending_review` | Notify admins of pending review |
| Produce | `product.approved` | Notify seller — product approved |
| Produce | `product.rejected` | Notify seller with rejection reason |
| Produce | `variant.stock_updated` | Update search index |
| Produce | `flash_sale.price_sync` | Activate/deactivate flash prices in search |
| Consume | `order.created` | Lock stock |
| Consume | `order.cancelled` | Release stock |
| Consume | `order.returned` | Restore stock |
| Consume | `flash_sale.session_started` | Calculate flash prices |
| Consume | `flash_sale.session_ended` | Reset prices |
