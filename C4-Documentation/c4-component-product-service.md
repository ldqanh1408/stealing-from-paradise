# C4 Component Level: Product Service

## Overview

- **Name**: Product Service
- **Description**: Product catalog and cart management component handling products, product variants, categories, shopping cart operations, inventory tracking, and image uploads. Uses MongoDB for product catalog data, Redis for caching, and MinIO for image storage. Publishes domain events to Kafka for inter-service communication including product lifecycle events (created, updated, deleted, approved, rejected) and cart checkout integration.
- **Type**: Service
- **Technology**: Java 25, Spring Boot 4.0.4 (Virtual Threads), MongoDB, Redis, MinIO, Kafka

## Purpose

The Product Service is the central catalog and cart component for the FlashSale marketplace platform. It manages:

- **Product Lifecycle**: Full product lifecycle from creation through admin approval to publication. Products transition through states: DRAFT, PENDING, APPROVED (or REJECTED), PUBLISHED, and UNPUBLISHED. Sellers create products in DRAFT and submit them for review (PENDING). Admins approve or reject pending products. Approved products can be published by sellers.
- **Product Variant Management**: Each product can have multiple variants (SKUs) with unique SKU codes, tier names (e.g., "Black - 256GB"), and individual pricing. Variant creation auto-creates inventory records with zero stock.
- **Category Management**: Hierarchical category system with parent-child relationships (level 0 for root, level 1 for subcategories). URL-friendly slugs are unique. Categories are seeded in development mode.
- **Shopping Cart**: Consolidated cart functionality (formerly a standalone cart-service). Supports authenticated users adding items with stock validation, quantity updates, item removal, and cart clearing. Cart items are grouped by seller for multi-vendor checkout presentations.
- **Inventory Management**: Thread-safe inventory tracking using MongoDB's atomic `$inc` operator to prevent lost updates. Tracks four stock dimensions: total, locked (pending/paid orders), available, and flash-reserved. Provides restock and manual adjustment operations for sellers.
- **Image Uploads**: Presigned URL generation for direct MinIO uploads. Image paths follow the pattern `products-media/products/{sellerId}/{productId}/{uuid}.{ext}` with a 15-minute TTL and CDN domain reference.
- **Admin Moderation**: Admin approval/rejection workflow with rejection reasons and notes. Approving a product publishes a `product.approved` Kafka event consumed by both the product-service itself and downstream services.
- **Inter-service Communication**: Publishes product lifecycle events to Kafka. Implements Kafka request-reply pattern for cart item enrichment (order-service queries cart contents during checkout) and checkout-completed cleanup.

## Software Features

- **Product CRUD**: Sellers can create, read, update, and soft-delete (with `deletedAt` field) their products. All repository queries filter out soft-deleted products.
- **Product Lifecycle State Machine**: DRAFT -> PENDING (submit for review) -> APPROVED (admin) | REJECTED (admin) -> PUBLISHED (seller) | UNPUBLISHED (seller). Rejected products can be resubmitted.
- **Variant/SKU Management**: Sellers manage product variants with unique SKU codes (alphanumeric + hyphens, 3-50 chars), tier names (1-100 chars), and non-negative pricing. Variant deletion is blocked if stock is locked.
- **Hierarchical Categories**: Root and subcategory management with URL-friendly slugs. Category deletion is blocked if it has active products or subcategories.
- **Public Product Search**: Published products can be searched by name (regex match) and filtered by category, with pagination support.
- **Cart Operations**: Add items with stock validation, update quantities (auto-delete if quantity <= 0), remove individual items, clear entire cart. Cart responses group items by seller for multi-vendor checkout display.
- **Atomic Inventory Operations**: Stock mutations exclusively use MongoDB `$inc` for thread safety. Operations include: lock stock (checkout), unlock stock (cancel), reserve flash stock, release flash stock, consume locked stock (post-payment).
- **Inventory Audit Trail**: Every inventory adjustment creates an `InventoryLog` entry with SKU code, delta, reason, seller ID, and timestamp for traceability.
- **Presigned Image Uploads**: MinIO presigned PUT URLs with 15-minute TTL, organized by seller and product, referencing a CDN domain.
- **Seller Product Dashboard**: Sellers can list their own products with status filtering and pagination.
- **Flash Sale Flag**: Products have an `isFlash` boolean flag for flash sale designation.
- **Kafka Event Publishing**: Product lifecycle events (`product.created`, `product.updated`, `product.deleted`, `product.pending_review`, `product.approved`, `product.rejected`, `inventory.adjusted`) published for downstream consumption.
- **Kafka Request-Reply for Cart**: Responds to `order.cart_items.request` with enriched cart item data (product name, seller info, variant details). Listens to `order.checkout_completed` to remove purchased items.
- **Dev Data Seeding**: Two mutually exclusive seeding modes: `MongoInitializationConfig` (categories only, when `dev-data.enabled=false`) and `ProductDevDataLoader` (30 products across 5 sellers, variants, inventory, cart items, when `dev-data.enabled=true`).

## Code Elements

This component contains the following code-level elements:

- [c4-code-backend-product-service.md](./c4-code-backend-product-service.md) -- Full code-level documentation for the Product Service

### Key Classes

| Category | Classes |
|----------|---------|
| **Entry Point** | `ProductServiceApplication` |
| **Controllers** | `ProductController`, `AdminProductController`, `SellerProductController`, `CartController`, `CategoryController`, `InventoryController` |
| **Services** | `ProductService`, `AdminProductService`, `VariantService`, `CartService`, `CategoryService`, `InventoryManagementService`, `InventoryService`, `KafkaProducerService`, `MinioService` |
| **Domain Models** | `Product`, `ProductVariant`, `Category`, `Cart`, `CartItem`, `Inventory`, `InventoryLog` |
| **Domain Utilities** | `InventoryOperations` (atomic MongoDB `$inc` operations) |
| **Repositories** | `ProductRepository`, `ProductVariantRepository`, `CategoryRepository`, `CartRepository`, `CartItemRepository`, `InventoryRepository`, `InventoryLogRepository` |
| **Configuration** | `KafkaConfig`, `MinioConfig`, `MongoConfig`, `MongoInitializationConfig`, `ProductDevDataLoader`, `SecurityConfig`, `SecurityFilterConfig` |
| **DTOs** | 10 request DTOs, 6 response DTOs |

## Interfaces

### REST API (External -- via API Gateway)

**Public Endpoints:**

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/v1/products/{productId}` | None | Get product detail with variants and category info |
| `GET` | `/v1/products` | None | Search published products (query: `category`, `search`, `page`, `size`) |
| `GET` | `/v1/categories` | None | List all categories |

**Seller Endpoints:**

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/v1/products` | SELLER | Create new product (DRAFT) |
| `PUT` | `/v1/products/{productId}` | SELLER | Update product |
| `GET` | `/v1/products/{productId}/presigned-url` | SELLER | Get presigned URL for image upload |
| `GET` | `/v1/sellers/me/products` | SELLER | List seller's own products |
| `DELETE` | `/v1/seller/products/{productId}` | SELLER | Soft-delete product |
| `GET` | `/v1/seller/products/{productId}/variants` | SELLER | List variants for a product |
| `POST` | `/v1/seller/products/{productId}/variants` | SELLER | Create variant (auto-creates inventory) |
| `PUT` | `/v1/seller/variants/{variantId}` | SELLER | Update variant |
| `DELETE` | `/v1/seller/variants/{variantId}` | SELLER | Delete variant (blocked if stock_locked > 0) |
| `POST` | `/v1/seller/products/{productId}/submit` | SELLER | Submit product for review (DRAFT -> PENDING) |
| `POST` | `/v1/seller/products/{productId}/publish` | SELLER | Publish approved product (APPROVED -> PUBLISHED) |
| `POST` | `/v1/seller/products/{productId}/unpublish` | SELLER | Unpublish product (PUBLISHED -> UNPUBLISHED) |
| `PUT` | `/v1/inventory/{skuCode}/restock` | SELLER | Restock inventory (atomic `$inc`) |
| `POST` | `/v1/seller/inventory/adjust` | SELLER | Manual inventory adjustment |

**Admin Endpoints:**

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/v1/admin/products/pending` | ADMIN | List pending products (filterable by category/seller) |
| `POST` | `/v1/admin/products/{productId}/approve` | ADMIN | Approve pending product |
| `POST` | `/v1/admin/products/{productId}/reject` | ADMIN | Reject pending product with reason |
| `POST` | `/v1/admin/categories` | ADMIN | Create category |
| `PUT` | `/v1/admin/categories/{categoryId}` | ADMIN | Update category |
| `DELETE` | `/v1/admin/categories/{categoryId}` | ADMIN | Delete category |

**Cart Endpoints:**

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/v1/cart` | Authenticated | Get cart grouped by seller |
| `POST` | `/v1/cart/items` | Authenticated | Add item to cart (validates stock) |
| `PUT` | `/v1/cart/items/{itemId}` | Authenticated | Update item quantity |
| `DELETE` | `/v1/cart/items/{itemId}` | Authenticated | Remove item from cart |
| `DELETE` | `/v1/cart` | Authenticated | Clear entire cart |

**Inventory Endpoints:**

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/v1/inventory/{skuCode}` | Authenticated | Get inventory by SKU |

### Kafka Topics

**Consumed:**

| Topic | Purpose |
|-------|---------|
| `product.approved` | Receive admin approval event (consumed internally to sync product status) |
| `order.cart_items.request` | Request-reply: receive cart item enrichment requests from order-service |
| `order.checkout_completed` | Cleanup: remove purchased items from cart after successful checkout |
| `inventory.adjusted` | Placeholder for future cart availability updates |

**Produced:**

| Topic | Purpose |
|-------|---------|
| `product.created` | New product created by seller |
| `product.updated` | Product details updated |
| `product.deleted` | Product soft-deleted |
| `product.pending_review` | Product submitted for admin review |
| `product.approved` | Admin approved product (with `ProductApprovedPayload`) |
| `product.rejected` | Admin rejected product |
| `inventory.adjusted` | Inventory stock adjusted |
| `order.cart_items.response` | Request-reply: enriched cart item data for order-service checkout |

## Dependencies

### Other Components

| Component | Interaction | Protocol |
|-----------|-------------|----------|
| **Order Service** | Provides cart item data via Kafka request-reply during checkout; receives checkout_completed events to clean up carts | Kafka |
| **Identity Service** | User role verification (indirect, via API Gateway headers) | HTTP Headers |
| **Notification Service** | Product lifecycle events consumed for seller/buyer notifications (indirect) | Kafka |
| **Flash Sale Service** | Product and inventory data for flash sale operations (indirect) | Kafka |

### External Systems

| System | Purpose | Configuration |
|--------|---------|---------------|
| **MongoDB** | Primary data store for products, variants, categories, carts, cart items, inventories, inventory logs | `spring.mongodb.host` / `port` / `database` (default: `localhost:27017/fs_product`) |
| **Redis** | Available for cart/product caching (dependency declared, not actively used in current code) | `spring.data.redis.host` / `port` (default: `localhost:6379`) |
| **Kafka** | Event-driven inter-service communication (product lifecycle, cart request-reply, checkout cleanup) | `spring.kafka.bootstrap-servers` (default: `localhost:9092`) |
| **MinIO** | Product image storage via presigned URL uploads (bucket: `products-media`) | `minio.url` / `access-key` / `secret-key` (default: `http://localhost:9000`) |
| **Eureka** | Service discovery registration | `eureka.client.serviceUrl.defaultZone` (default: `http://localhost:8761/eureka/`) |
| **API Gateway** | JWT decoding, request routing, `X-User-*` header injection | Stateless, header-based |

## Component Diagram

```mermaid
C4Component
    title Component Diagram for Product Service Container

    Container_Boundary(product_service, "Product Service") {
        Component(product_controller, "Product Controller", "Spring REST Controller", "Public product search, detail, and seller product CRUD with presigned URL generation.")
        Component(admin_product_controller, "Admin Product Controller", "Spring REST Controller", "Admin-only product approval workflow: list pending, approve, reject.")
        Component(seller_product_controller, "Seller Product Controller", "Spring REST Controller", "Seller-only product lifecycle: submit for review, publish, unpublish, variant CRUD.")
        Component(cart_controller, "Cart Controller", "Spring REST Controller", "Shopping cart CRUD: add items with stock validation, update quantities, remove, clear.")
        Component(category_controller, "Category Controller", "Spring REST Controller", "Public category listing and admin CRUD for hierarchical categories.")
        Component(inventory_controller, "Inventory Controller", "Spring REST Controller", "Inventory lookup, seller restock, and manual adjustment endpoints.")
        Component(product_service_svc, "Product Service", "Spring Service", "Core product business logic: CRUD, lifecycle transitions, Kafka event publishing, product enrichment.")
        Component(admin_product_service, "Admin Product Service", "Spring Service", "Admin moderation: approve/reject with Kafka event publishing.")
        Component(variant_service, "Variant Service", "Spring Service", "Variant CRUD with seller ownership verification and stock-lock guard on deletion.")
        Component(cart_service, "Cart Service", "Kafka Listener", "Kafka request-reply for order-service cart queries and checkout cleanup.")
        Component(category_service, "Category Service", "Spring Service", "Category CRUD with slug uniqueness and active-product deletion guards.")
        Component(inventory_management_service, "Inventory Management Service", "Spring Service", "Inventory restock and manual adjust with atomic $inc operations and audit logging.")
        Component(inventory_service, "Inventory Service", "Spring Service", "Low-level atomic stock decrease using MongoDB $inc.")
        Component(kafka_producer_service, "Kafka Producer Service", "Spring Service", "Generic Kafka producer with async JSON serialization and logging.")
        Component(minio_service, "Minio Service", "Spring Service", "Presigned PUT URL generation for product image uploads to MinIO.")
        Component(inventory_ops, "Inventory Operations", "Domain Utility", "Thread-safe atomic inventory operations using MongoDB $inc: lock, unlock, reserve, release, consume, check.")
        ComponentDb(product_entity, "Product", "MongoDB Document", "products collection: sellerId, categoryId, name, status lifecycle, isFlash, soft delete support.")
        ComponentDb(variant_entity, "ProductVariant", "MongoDB Document", "product_variants collection: skuCode (unique), tierName, price, product reference.")
        ComponentDb(category_entity, "Category", "MongoDB Document", "categories collection: name, slug (unique), parentId (self-referential), level.")
        ComponentDb(cart_entity, "Cart", "MongoDB Document", "carts collection: userId (unique), totalItems (denormalized count).")
        ComponentDb(cart_item_entity, "CartItem", "MongoDB Document", "cart_items collection: cartId, userId, skuCode, priceSnapshot, quantity, flash sale item ref.")
        ComponentDb(inventory_entity, "Inventory", "MongoDB Document", "inventories collection: skuCode (unique), stockTotal, stockLocked, stockAvailable, stockFlashReserved.")
        ComponentDb(inventory_log_entity, "InventoryLog", "MongoDB Document", "inventory_logs collection: skuCode, delta, reason, sellerId, timestamp (audit trail).")
    }

    Container_Ext(api_gateway, "API Gateway", "JWT decoding, request routing")
    Container_Ext(order_service, "Order Service", "Order management")
    Container_Ext(mongodb, "MongoDB", "fs_product database")
    Container_Ext(kafka, "Apache Kafka", "Event Bus")
    Container_Ext(minio, "MinIO / S3", "Object Storage")
    Container_Ext(eureka, "Eureka", "Service Discovery")

    Rel(api_gateway, product_controller, "Routes HTTP requests", "REST")
    Rel(api_gateway, admin_product_controller, "Routes HTTP requests", "REST")
    Rel(api_gateway, seller_product_controller, "Routes HTTP requests", "REST")
    Rel(api_gateway, cart_controller, "Routes HTTP requests", "REST")
    Rel(api_gateway, category_controller, "Routes HTTP requests", "REST")
    Rel(api_gateway, inventory_controller, "Routes HTTP requests", "REST")

    Rel(product_controller, product_service_svc, "Calls", "Java method")
    Rel(product_controller, minio_service, "Generates presigned URLs", "Java method")
    Rel(admin_product_controller, admin_product_service, "Calls", "Java method")
    Rel(seller_product_controller, product_service_svc, "Calls lifecycle methods", "Java method")
    Rel(seller_product_controller, variant_service, "Calls variant methods", "Java method")
    Rel(category_controller, category_service, "Calls", "Java method")
    Rel(inventory_controller, inventory_management_service, "Calls", "Java method")

    Rel(product_service_svc, kafka_producer_service, "Publishes lifecycle events", "Java method")
    Rel(admin_product_service, kafka_producer_service, "Publishes approval events", "Java method")
    Rel(inventory_management_service, kafka_producer_service, "Publishes inventory events", "Java method")

    Rel(cart_service, kafka, "Consumes cart_items.request / checkout_completed", "Kafka")
    Rel(cart_service, kafka, "Produces cart_items.response", "Kafka")
    Rel(kafka, order_service, "Delivers cart data and checkout events", "Kafka")

    Rel(product_service_svc, product_entity, "Manages", "MongoRepository")
    Rel(variant_service, variant_entity, "Manages", "MongoRepository")
    Rel(category_service, category_entity, "Manages", "MongoRepository")
    Rel(inventory_management_service, inventory_entity, "Manages", "MongoRepository")
    Rel(inventory_ops, inventory_entity, "Atomic $inc operations", "MongoTemplate")

    Rel(product_entity, mongodb, "Persisted to", "MongoDB Driver")
    Rel(variant_entity, mongodb, "Persisted to", "MongoDB Driver")
    Rel(category_entity, mongodb, "Persisted to", "MongoDB Driver")
    Rel(cart_entity, mongodb, "Persisted to", "MongoDB Driver")
    Rel(cart_item_entity, mongodb, "Persisted to", "MongoDB Driver")
    Rel(inventory_entity, mongodb, "Persisted to", "MongoDB Driver")
    Rel(inventory_log_entity, mongodb, "Persisted to", "MongoDB Driver")

    Rel(minio_service, minio, "Generates presigned PUT URLs", "MinIO SDK")
    Rel(product_service, eureka, "Registers with", "Eureka Client")
```
