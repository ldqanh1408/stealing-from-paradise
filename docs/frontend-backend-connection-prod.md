# Frontend-Backend Connection in Production Mode

This document describes how the frontend applications connect to backend services when running in `.prod` mode.

## Architecture Overview

```
                        INTERNET
                            |
                            v
    +---------------------------------------------------------------+
    |                         NGINX (Port 80)                        |
    |                                                               |
    |   customer-app:80   <--reverse-proxy--  /customer-app/*       |
    |   seller-app:80     <--reverse-proxy--  /seller-app/*         |
    |   admin-app:80      <--reverse-proxy--  /admin-app/*          |
    |                                                               |
    |   /api/*  ----->  fs-gateway:8080  (Spring Cloud Gateway)     |
    +---------------------------------------------------------------+
                            |
                            | /api/v1/* (stripPrefix removes /api)
                            v
    +---------------------------------------------------------------+
    |              EUREKA SERVICE REGISTRY (Port 8761)              |
    +---------------------------------------------------------------+
                            |
            +---------------+---------------+---------------+
            |               |               |               |
            v               v               v               v
    +-------------+  +-------------+  +-------------+  +-------------+
    |  IDENTITY   |  |   PRODUCT   |  |   ORDER     |  |  PAYMENT    |
    |  SERVICE    |  |   SERVICE   |  |   SERVICE   |  |  SERVICE    |
    |  :8081      |  |   :8082     |  |   :8083     |  |  :8084      |
    +-------------+  +-------------+  +-------------+  +-------------+
            |               |               |               |
            v               v               v               v
    +-------------+  +-------------+  +-------------+  +-------------+
    |   POSTGRES  |  |   MONGODB   |  |   POSTGRES  |  |   POSTGRES  |
    |  :5432      |  |   :27017    |  |  :5432      |  |  :5432      |
    +-------------+  +-------------+  +-------------+  +-------------+
```

## Request Flow

1. **Browser** sends request to `https://yourdomain.com/api/v1/identity/users/me`
2. **Nginx** receives the request on port 80, forwards `/api/**` to `fs-gateway:8080`
3. **Spring Cloud Gateway** strips the `/api` prefix, routes `v1/identity/**` to `identity-service:8081`
4. **Identity Service** processes the request and queries **PostgreSQL**

## Nginx Reverse Proxy Configuration

Nginx serves all three frontend apps under path-based routes:

| Route | Upstream | Description |
|-------|----------|-------------|
| `/customer-app/*` | `customer-app:80` | Customer-facing web app |
| `/seller-app/*` | `seller-app:80` | Seller portal |
| `/admin-app/*` | `admin-app:80` | Admin dashboard |
| `/api/*` | `fs-gateway:8080` | All API requests |

### Location Block Detail

```nginx
location /customer-app/ {
    proxy_pass http://customer-app:80/;
    # strips /customer-app prefix, rewrites to /
}

location /api/ {
    proxy_pass http://fs-gateway:8080/;
    # strips /api prefix, rewrites to /* (gateway handles remaining path)
}
```

## Frontend Environment Configuration

In `.prod` mode, all three frontend apps use the same API base URL:

```env
# Frontend .env.prod
VITE_API_URL=/api/v1
```

The frontend uses a **relative path** (`/api/v1`), which means the browser sends requests to the **same origin** where the frontend is hosted. Nginx then routes those to the gateway.

### API Endpoints Called from Frontend

| Frontend | Endpoint | Full URL |
|----------|----------|----------|
| Customer App | Current User | `/api/v1/identity/users/me` |
| Customer App | Flash Sales | `/api/v1/flashsale/sales/active` |
| Seller App | Products | `/api/v1/product/products` |
| Seller App | Create Order | `/api/v1/order/orders` |
| Admin App | All Users | `/api/v1/identity/admin/users` |

## Spring Cloud Gateway Routing

The gateway uses Eureka-based dynamic routing with `stripPrefix(1)` to remove the `/api` prefix:

```java
// RouteConfig.java
.route("identity-service", r -> r.path("/v1/identity/**")
    .filters(f -> f.stripPrefix(1))
    .uri("lb://identity-service"))

.route("product-service", r -> r.path("/v1/product/**")
    .filters(f -> f.stripPrefix(1))
    .uri("lb://product-service"))
```

### Gateway Strip Prefix Example

```
Incoming:  /api/v1/identity/users/me
           ^strip ^remove
After strip:  /v1/identity/users/me  ->  identity-service
```

## Docker Compose Production Setup

All services run in Docker with these network configurations:

```yaml
# docker-compose.prod.yml (simplified)
services:
  nginx:
    ports:
      - "80:80"

  customer-app:
    image: customer-app:prod
    # internal only, no exposed ports

  seller-app:
    image: seller-app:prod

  admin-app:
    image: admin-app:prod

  fs-gateway:
    image: fs-gateway:prod
    environment:
      - SPRING_PROFILES_ACTIVE=prod
```

## Service Port Map

| Service | Internal Port | External | Registered in Eureka |
|---------|--------------|----------|---------------------|
| `fs-gateway` | 8080 | Via nginx | Yes |
| `identity-service` | 8081 | Internal only | Yes |
| `product-service` | 8082 | Internal only | Yes |
| `order-service` | 8083 | Internal only | Yes |
| `payment-service` | 8084 | Internal only | Yes |
| `flashsale-service` | 8085 | Internal only | Yes |
| `notification-service` | 8086 | Internal only | Yes |
| `fs-eureka` | 8761 | Internal only | N/A |
| `nginx` | 80 | **80 (host)** | N/A |

## Key Points

1. **Single Entry Point**: All external traffic enters through nginx on port 80
2. **No CORS Issues**: Frontend and API share the same origin (`/api/v1`), avoiding CORS complexity
3. **Service Discovery**: Gateway uses Eureka to resolve service names to internal IPs
4. **Prefix Stripping**: Gateway removes `/api` so downstream services receive clean paths (`/v1/identity/**`)
5. **Docker Network**: All services communicate over the internal `app-network` Docker network
6. **Health Checks**: Each service exposes `/actuator/health` for nginx upstream validation
