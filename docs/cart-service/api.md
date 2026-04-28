# 🛒 Cart Service API

**Service**: Cart Service  
**Port**: 8083  
**Base URL**: `/api/v1`  
**Version**: v5.3 RTS

## Overview

Cart Service manages:
- Multi-seller shopping cart
- Cart items (add, update, remove)
- MongoDB with 30-day TTL
- Real-time stock validation
- Flash sale item management

## 📡 Kafka Integration

### Produces (Events Published)
- None (local state only)

### Consumes (Events Listened)
| Topic | Producer | Purpose |
|-------|----------|---------|
| `order.checkout_completed` | Order Service | Remove items after checkout |

## Key Endpoints

### Cart Management
```
GET    /cart                    Get current cart
POST   /cart/items              Add item to cart
PUT    /cart/items/{id}         Update item quantity
DELETE /cart/items/{id}         Remove item from cart
DELETE /cart                    Clear entire cart
```

## Cart Response Format

```json
{
  "sellers": [
    {
      "seller_id": 5,
      "seller_name": "Shop Nike VN",
      "items": [
        {
          "cart_item_id": 201,
          "sku_code": "NK-AIR-RED-XL",
          "product_name": "Áo Thun Nike Air",
          "variant_name": "Đỏ / XL",
          "unit_price": 350000,
          "quantity": 2,
          "stock_available": 95,
          "is_flash": false,
          "fs_item_id": null,
          "flash_price": null
        }
      ]
    }
  ],
  "total_items": 2,
  "subtotal": 700000
}
```

## Total Endpoints: 5

## For Complete Documentation

→ See **[/docs/api/04-cart-service.md](../api/04-cart-service.md)**

Contains:
- Full request/response examples
- Multi-seller grouping logic
- Trust Score tier limits
- Flash sale item integration
- Stock validation details

---

**Last Updated**: 2026-04-28  
**Version**: v5.3 RTS

