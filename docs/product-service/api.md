# 📦 Product Service API

**Service**: Product Service  
**Port**: 8082  
**Base URL**: `/api/v1`  
**Version**: v5.3 RTS

## Overview

Product Service manages:
- Product catalog (CRUD operations)
- Product variants & SKUs
- Inventory & stock management  
- Product categories
- Image uploads (MinIO presigned URLs)

## 📡 Kafka Integration

### Produces (Events Published)
| Topic | Consumer | Purpose |
|-------|----------|---------|
| `product.created` | Search Service | Index new product |
| `product.updated` | Search Service | Update product index |
| `product.deleted` | Search Service | Remove from index |

### Consumes (Events Listened)
- None

## Key Endpoints

### Categories
```
GET    /categories                  List all categories (public)
POST   /admin/categories            Create category (admin)
PUT    /admin/categories/{id}       Update category (admin)
DELETE /admin/categories/{id}       Delete category (admin)
```

### Products
```
POST   /products                    Create product (seller)
GET    /products/{id}               Get product detail (public)
PUT    /products/{id}               Update product (seller)
DELETE /products/{id}               Delete product (seller)
GET    /sellers/me/products         List my products (seller)
```

### Product Lifecycle
```
POST   /seller/products/{id}/submit      Submit for review (seller)
POST   /seller/products/{id}/publish     Publish product (seller)
POST   /seller/products/{id}/unpublish   Unpublish product (seller)
```

### Variants
```
GET    /seller/products/{id}/variants         List variants (seller)
POST   /seller/products/{id}/variants         Create variant (seller)
PUT    /seller/variants/{id}                  Update variant (seller)
DELETE /seller/variants/{id}                  Delete variant (seller)
```

### Inventory
```
POST   /seller/inventory/adjust              Adjust stock (seller)
GET    /seller/inventory/{sku}/logs          Stock history (seller)
GET    /inventory/{sku}                      Check stock (jwt)
PUT    /inventory/{sku}/restock              Restock (seller)
```

## Total Endpoints: 16

## For Complete Documentation

→ See **[/docs/api/02-product-service.md](../api/02-product-service.md)**

Contains:
- Full request/response examples
- Variant management details
- Inventory tracking
- Image upload procedures
- Integration with Search Service

---

**Last Updated**: 2026-04-28  
**Version**: v5.3 RTS

