# Kafka Events — Product Service (incl. Cart & Inventory)

**Service**: product-service — Port 8090  
**Last Updated**: 2026-05-05

> Product Service quản lý catalog sản phẩm, variants, giỏ hàng (Cart) và tồn kho (Inventory).  
> Tất cả các module này dùng chung MongoDB và service process.

---

## 📤 Events Produced

### 1. `product.created`
|| Field | Value |
||-------|-------|
|| **Consumers** | 🔗 [Search Service](../search-service/KAFKA_EVENTS.md) |
|| **Trigger** | Seller creates product via `POST /products` |

**Consumer Actions**:
- **Search Service**: Prepare indexing (wait for APPROVED status)

---

### 2. `product.updated`
|| Field | Value |
||-------|-------|
|| **Consumers** | 🔗 [Search Service](../search-service/KAFKA_EVENTS.md) |
|| **Trigger** | Seller updates product via `PUT /products/{productId}` |

**Consumer Actions**:
- **Search Service**: Update Elasticsearch index

---

### 3. `product.deleted`
|| Field | Value |
||-------|-------|
|| **Consumers** | 🔗 [Search Service](../search-service/KAFKA_EVENTS.md) |
|| **Trigger** | Seller deletes product via `DELETE /products/{productId}` |

**Consumer Actions**:
- **Search Service**: Remove from Elasticsearch index

---

### 4. `product.pending_review`
|| Field | Value |
||-------|-------|
|| **Consumers** | 🔗 [Notification Service](../notification-service/KAFKA_EVENTS.md) |
|| **Trigger** | Seller creates/updates product → status becomes PENDING_REVIEW |

**Consumer Actions**:
- **Notification Service**: Alert admin to review product

---

### 5. `category.updated`
|| Field | Value |
||-------|-------|
|| **Consumers** | 🔗 [Search Service](../search-service/KAFKA_EVENTS.md) |
|| **Trigger** | Admin updates category via `PUT /admin/categories/{categoryId}` |

**Consumer Actions**:
- **Search Service**: Update category filters and facets in Elasticsearch

---

### 6. `inventory.adjusted`
|| Field | Value |
||-------|-------|
|| **Consumers** | 🔗 [Search Service](../search-service/KAFKA_EVENTS.md) |
|| **Trigger** | Seller adjusts stock via `POST /seller/inventory/adjust` |

**Consumer Actions**:
- **Search Service**: Update product stock status in index


---

### 7. `variant.price_updated`
|| Field | Value |
||-------|-------|
|| **Consumers** | 🔗 [Search Service](../search-service/KAFKA_EVENTS.md) |
|| **Trigger** | Seller updates variant price via `PUT /seller/variants/{variantId}` |

**Consumer Actions**:
- **Search Service**: Update price and min_price in Elasticsearch index

---

### 8. `variant.stock_updated`
|| Field | Value |
||-------|-------|
|| **Consumers** | 🔗 [Search Service](../search-service/KAFKA_EVENTS.md) |
|| **Trigger** | Seller adjusts stock or variant status changes |

**Consumer Actions**:
- **Search Service**: Update stock_status in Elasticsearch index

---

## 📥 Events Consumed

### 1. `order.created` ← 🔗 [Order Service](../order-service/KAFKA_EVENTS.md)
|| Field | Value |
||-------|-------|
|| **Module** | Inventory |
|| **Action** | Lock stock for each SKU in the order |
|| **Producer** | Order Service (checkout initiated) |

### 2. `order.confirmed` ← 🔗 [Order Service](../order-service/KAFKA_EVENTS.md)
|| Field | Value |
||-------|-------|
|| **Module** | Inventory |
|| **Action** | Confirm stock reservation; mark as deducted |
|| **Producer** | Order Service (payment succeeded) |

### 3. `order.failed` ← 🔗 [Order Service](../order-service/KAFKA_EVENTS.md)
|| Field | Value |
||-------|-------|
|| **Module** | Inventory |
|| **Action** | Release stock reservation; restore inventory |
|| **Producer** | Order Service (payment failed / timeout) |

### 4. `order.cancelled` ← 🔗 [Order Service](../order-service/KAFKA_EVENTS.md)
|| Field | Value |
||-------|-------|
|| **Module** | Cart + Inventory |
|| **Action** | Cart: remove items; Inventory: unlock stock |
|| **Producer** | Order Service |

### 5. `order.returned` ← 🔗 [Order Service](../order-service/KAFKA_EVENTS.md)
|| Field | Value |
||-------|-------|
|| **Module** | Inventory |
|| **Action** | Restore stock for returned items |
|| **Producer** | Order Service (RTS flow) |

### 6. `order.checkout_completed` ← 🔗 [Order Service](../order-service/KAFKA_EVENTS.md)
|| Field | Value |
||-------|-------|
|| **Module** | Cart |
|| **Action** | Remove checked-out items from user's cart |
|| **Producer** | Order Service |

### 7. `order.auto_cancelled` ← 🔗 [Order Service](../order-service/KAFKA_EVENTS.md)
|| Field | Value |
||-------|-------|
|| **Module** | Inventory |
|| **Action** | Unlock stock for unpaid orders after timeout |
|| **Producer** | Order Service (Axon Deadline / JOB-13) |

### 8. `flash_sale.item_sold` ← 🔗 [Flash Sale Service](../flashsale-service/KAFKA_EVENTS.md)
|| Field | Value |
||-------|-------|
|| **Module** | Inventory |
|| **Action** | Update sold count and remaining stock cache |
|| **Producer** | Flash Sale Service (Redis atomic buy) |

### 9. `flash_sale.session_ended` ← 🔗 [Flash Sale Service](../flashsale-service/KAFKA_EVENTS.md)
|| Field | Value |
||-------|-------|
|| **Module** | Cart |
|| **Action** | Remove expired flash sale items from cart (JOB-07) |
|| **Producer** | Flash Sale Service |

---

## 🔄 Request-Reply

Product Service là **Responder** cho 3 cặp request-reply sau:

### 1. `cart.product_info` — Cart ↔ Product catalog
|| Role | Service | Topic |
|------|---------|-------|
| **Requester** | Cart (Product Service internal) | `cart.product_info.request` |
| **Responder** | **Product catalog** | `cart.product_info.response` |

**Cycle**:
```
Cart Module ──cart.product_info.request──→ Product Catalog Module
  (Requester)   (productId, variantId)            (Responder)
       │                                            │ look up product info
       │◀──cart.product_info.response──────────────│
       │     (name, price, imageUrl, available)     │
       │                                            │
       └──→ display in cart                         ┘
```

### 2. `order.stock_check` — Order ↔ Product (Inventory)
|| Role | Service | Topic |
|------|---------|-------|
| **Requester** | 🔗 [Order Service](../order-service/KAFKA_EVENTS.md) | `order.stock_check.request` |
| **Responder** | **Product Service (Inventory)** | `order.stock_check.response` |

**Cycle**:
```
Order Service (Requester) ──order.stock_check.request──→ Product Service (Responder)
    │                       (items[{variantId, qty}])           │
    │                                                        │ check stock
    │◀──order.stock_check.response──────────────────────────│
    │     ({allAvailable, results[{variant, available, ...}]})   │
    │                                                        │
    └──→ if allAvailable → proceed checkout                  ┘
```

### 3. `order.cart_items` — Order ↔ Product (Cart)
|| Role | Service | Topic |
|------|---------|-------|
| **Requester** | 🔗 [Order Service](../order-service/KAFKA_EVENTS.md) | `order.cart_items.request` |
| **Responder** | **Product Service (Cart)** | `order.cart_items.response` |

**Cycle**:
```
Order Service (Requester) ──order.cart_items.request──→ Product Service (Responder)
    │                       (userId, selectedItemIds)       │
    │                                                        │ get cart items
    │◀──order.cart_items.response──────────────────────────│
    │     (items[{cartItemId, variantId, price, qty, ...}])     │
    │                                                        │
    └──→ create order with cart items                        ┘
```

Xem chi tiết: 🔗 [11_KAFKA_REQUEST_REPLY.md](../../messaging/11_KAFKA_REQUEST_REPLY.md) (#1 Cart↔Product, #2 Stock Check, #4 Cart Items)

---

## 🔗 Related Kafka Docs

| Service | Link |
|---------|------|
| Order Service | 🔗 [order-service/KAFKA_EVENTS.md](../order-service/KAFKA_EVENTS.md) |
| Search Service | 🔗 [search-service/KAFKA_EVENTS.md](../search-service/KAFKA_EVENTS.md) |
| Notification Service | 🔗 [notification-service/KAFKA_EVENTS.md](../notification-service/KAFKA_EVENTS.md) |
| Flash Sale Service | 🔗 [flashsale-service/KAFKA_EVENTS.md](../flashsale-service/KAFKA_EVENTS.md) |
| Full Index | 🔗 [KAFKA_EVENTS.md](../../messaging/KAFKA_EVENTS.md) |
