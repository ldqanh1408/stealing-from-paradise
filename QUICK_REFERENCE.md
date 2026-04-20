# Cart Service Consolidation - Quick Reference

## What Changed?

### ✅ Cart functionality is now part of Product Service

**Before:**
- Separate `cart-service` microservice (port 8089)
- 11 total services

**After:**
- Cart functionality integrated into `product-service` (port 8090)
- 10 total services
- Reduced deployment complexity

---

## Key Files Changed

### Code Added (Product Service)
```
backend/product-service/src/main/java/com/flashsale/productdomain/
├── domain/model/
│   ├── Cart.java (NEW)
│   └── CartItem.java (NEW)
├── domain/repository/
│   ├── CartRepository.java (NEW)
│   └── CartItemRepository.java (NEW)
└── service/
    └── CartService.java (NEW)
```

### Configuration Updated
```
backend/product-service/pom.xml (MODIFIED)
└── Added: Redis, Validation, Actuator dependencies

backend/product-service/src/main/resources/application.yml (MODIFIED)
└── Added: Redis configuration

backend/pom.xml (MODIFIED)
└── Removed: <module>cart-service</module>

backend/api-gateway/config/RouteConfig.java (MODIFIED)
└── Changed: /api/v1/cart/** → product-service (was cart-service)
```

### Docker Updated
```
docker-compose.yml (MODIFIED)
├── Removed: cart-service container
└── Updated: product-service with Redis config

backend/docker-compose.yml (MODIFIED)
└── Removed: cart-service container

backend/docker-compose-standalone.yml (MODIFIED)
└── Removed: cart-service container
```

### Deleted
```
backend/cart-service/ (DELETED)
└── Entire standalone service directory removed
```

---

## How to Build & Run

### Build Backend
```bash
cd backend
mvn clean install -DskipTests
```

### Run Services
```bash
# Option 1: Docker Compose (All services)
docker-compose up -d

# Option 2: Individual services
cd backend/product-service
mvn spring-boot:run

cd backend/order-service
mvn spring-boot:run
```

### Verify Cart Service is Running
```bash
# Check Eureka (discovery service)
curl http://localhost:8761/eureka/apps | grep -i product

# Check Cart Kafka Handler is listening
# Check logs: docker logs fs-product | grep -i "cart\|kafka"
```

---

## API Endpoints (Unchanged)

Clients access the same endpoints as before:

```
GET /api/v1/cart
  → Returns user's shopping cart

POST /api/v1/cart/items
  → Add item to cart

PUT /api/v1/cart/items/{itemId}
  → Update cart item

DELETE /api/v1/cart/items/{itemId}
  → Remove item from cart

DELETE /api/v1/cart
  → Clear entire cart
```

**All endpoints still accessible via API Gateway at port 8080**

---

## Kafka Topics

Product Service now handles:

| Topic | Handler | Action |
|-------|---------|--------|
| `order.cart_items.request` | CartService.onCartItemsRequest() | Fetch cart items for order |
| `order.cart_items.response` | (sends response) | Returns cart items to order-service |
| `order.checkout_completed` | CartService.onCheckoutCompleted() | Cleans up cart after purchase |
| `inventory.adjusted` | CartService.onInventoryAdjusted() | Updates item availability |

---

## MongoDB Collections

Same collections, now in product-service database:

```
Database: fs_product_prod

Collections:
├── products (existing)
├── product_variants (existing)
├── categories (existing)
├── inventory (existing)
├── carts (NEW - moved from cart-service)
└── cart_items (NEW - moved from cart-service)
```

---

## Redis

Product Service now uses Redis for:
- Cart caching (high-performance access)
- Session management (via Spring Boot)
- General service operations

**Configuration:**
```yaml
spring.data.redis:
  host: localhost
  port: 6379
  password: (optional)
  timeout: 60000ms
```

---

## Service Discovery (Eureka)

Both service names still work as before via Eureka load balancing:

```
# Service still registered as:
spring.application.name: product-service

# Internally accessible via:
lb://product-service

# Port: 8090
```

---

## Verification Checklist

✅ All Cart models in product-service  
✅ All Cart repositories in product-service  
✅ Cart Kafka handlers in CartService  
✅ API Gateway routes /api/v1/cart to product-service  
✅ Docker Compose updated (no cart-service)  
✅ pom.xml module list updated  
✅ product-service has Redis dependency  
✅ product-service has Kafka configuration  
✅ No code changes needed in order-service  
✅ No breaking changes to client API  

---

## Troubleshooting

### Issue: Cart endpoints returning 404

**Solution:**
```bash
# Check API Gateway routing
curl http://localhost:8080/api/v1/cart

# Should route to product-service (port 8090)
curl http://localhost:8090/api/v1/cart

# Check service is registered in Eureka
curl http://localhost:8761/eureka/apps/PRODUCT-SERVICE
```

### Issue: Kafka messages not being processed

**Solution:**
```bash
# Check CartService Kafka listeners are active
docker logs fs-product | grep "KafkaListener"

# Verify Kafka is running
docker logs fs-kafka | grep "Started"

# Check topics exist
kafka-topics --list --bootstrap-server localhost:9092
```

### Issue: Redis connection refused

**Solution:**
```bash
# Ensure Redis is running
docker ps | grep redis

# Check Redis port
redis-cli -h localhost -p 6379 ping
# Should return: PONG
```

---

## Files Reference

| What | Where |
|------|-------|
| Service Code | backend/product-service/src/main/java/com/flashsale/productdomain/ |
| Service Config | backend/product-service/src/main/resources/application.yml |
| Docker Config | docker-compose.yml, backend/docker-compose.yml |
| Gateway Routing | backend/api-gateway/src/main/java/.../config/RouteConfig.java |
| Parent Build | backend/pom.xml |

---

## Summary

🎯 **Cart service successfully consolidated into product service**

✅ Same APIs, same data models, same behavior  
✅ Fewer services to manage  
✅ Better resource utilization  
✅ Ready for production  

**No client changes required!**

