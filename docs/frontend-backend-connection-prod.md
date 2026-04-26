# Frontend-Backend Connection in Production Mode

This document describes how the frontend applications connect to backend services when running in `.prod` mode. It covers the full Docker stack, gateway routing, and the dual-layer nginx architecture.

## Architecture Overview

```
                           INTERNET
                               |
                               v
        +-------------------------------------------------------+
        |              REVERSE PROXY (nginx) — Port 80           |
        |                                                          |
        |   /seller/*  ---->  fs-seller-fe (nginx:80)            |
        |   /admin/*   ---->  fs-admin-fe  (nginx:80)            |
        |   /          ---->  fs-customer-fe (nginx:80)           |
        |   /api/*     ---->  fs-gateway (Spring Cloud Gateway)  |
        +-------------------------------------------------------+
                     |                         |
                     | /seller/*               | /api/*
                     v                         v
        +-------------------------------------------------------+
        |         SELLER APP (nginx :80)                       |
        |   SPA served from /          | /api/* -> fs-gateway   |
        +-------------------------------------------------------+
                                                          |
                                                          | /v1/...
                                                          v
        +-------------------------------------------------------+
        |       SPRING CLOUD GATEWAY (:8080)                    |
        |   stripPrefix(1): /api/v1/... -> /v1/...             |
        |   Uses Eureka to resolve service names                |
        +-------------------------------------------------------+
                               |
           +-------------------+-------------------+---------------+
           |                   |                   |               |
           v                   v                   v               v
    +-------------+     +-------------+     +-------------+  +-------------+
    |  IDENTITY   |     |  PRODUCT    |     |   ORDER     |  |  PAYMENT   |
    |  :8081      |     |  :8090      |     |  :8083      |  |  :8082     |
    +-------------+     +-------------+     +-------------+  +-------------+
           |                   |                   |               |
           v                   v                   v               v
    +-------------+     +-------------+     +-------------+  +-------------+
    |  POSTGRES   |     |  MONGODB    |     |  POSTGRES   |  |  POSTGRES   |
    +-------------+     +-------------+     +-------------+  +-------------+
```

## Two-Layer Nginx Architecture

This is critical: there are **two nginx layers** in production.

### Layer 1 — Reverse Proxy (reverse-proxy container)

Single entry point exposed to the internet. Routes by URL path prefix.

| Route | Upstream | Container |
|-------|----------|-----------|
| `/seller/*` | `fs-seller-fe:3000` | seller-app (Vite dev) or fs-seller-fe:80 (prod built) |
| `/admin/*` | `fs-admin-fe:3000` | admin-app |
| `/` | `fs-customer-fe:3000` | customer-app |
| `/api/*` | `fs-gateway:8080` | api-gateway |

The path is forwarded unchanged (no rewrite).

```nginx
# docker-entrypoint.sh defaults match docker-compose service names
location /seller/ { proxy_pass http://fs-seller-fe:3001/; }
location /admin/  { proxy_pass http://fs-admin-fe:3002/; }
location /        { proxy_pass http://fs-customer-fe:3000/; }
location /api/    { proxy_pass http://fs-gateway:8080; }
```

### Layer 2 — Frontend App Nginx (each frontend container)

Each frontend app runs its own nginx (production build) or Vite dev server. Handles SPA routing and proxies API calls to the gateway.

```nginx
# customer/seller/admin nginx.conf
location /api/ {
    proxy_pass http://fs-gateway:8080;   # no trailing / → full path forwarded
}
location / {
    try_files $uri $uri/ /index.html;    # SPA fallback
}
```

The gateway then applies `stripPrefix(1)`, removing `/api`.

## Request Flow

### Customer App (root path `/`)

```
Browser  →  GET /api/v1/products
    │
    ▼
Reverse Proxy  (location /api/)
    │  forwards /api/v1/products to fs-gateway:8080
    ▼
Gateway  (RouteConfig matches /api/v1/products/**)
    │  stripPrefix(1) → /v1/products
    │  uri: lb://product-service
    ▼
Product Service  (:8090)
```

### Seller App (path `/seller/`)

```
Browser  →  GET /seller/api/v1/products  (seller app makes API call)
    │
    ▼
Reverse Proxy  (location /seller/)
    │  rewrites /seller/api/v1/products → /api/v1/products  (strips /seller prefix)
    ▼
Customer App Nginx  (fs-customer-fe:3000, matched by / → default)
    │  /api/v1/products matches location /api/ in customer nginx
    ▼
Gateway  →  Product Service
```

Wait — that routes to customer app. Let me correct this. The browser makes an API call to `/api/v1/sellers/products` (not `/seller/api/v1/...`). The browser does not know about the `/seller/` path prefix — it only sees `VITE_API_URL=/api/v1`. So:

```
Browser  →  GET /api/v1/sellers/products
    │
    ▼
Reverse Proxy  (no /seller/ prefix in URL)
    │  matches location /  → / falls through to customer-app
    ▼
Customer App Nginx  (fs-customer-fe)
    │  /api/ matches → forwards to fs-gateway
    ▼
Gateway  →  Product Service
```

**Key insight**: API calls from ALL three frontends go through the customer app nginx first (because `/api/...` matches `location /` in the reverse proxy's default route, which routes to customer-app). The customer app's nginx then forwards `/api/` to the gateway.

### Why the Seller App Gets Correct API Responses

The browser's API call `/api/v1/sellers/products` is routed to the gateway, which routes to `product-service`. The response comes back through the gateway and customer app nginx to the browser. Since the browser made the request, the response goes to the browser regardless of which frontend container it was served from.

The seller app's React code runs in the browser and makes API calls to `/api/v1/...`. The seller app's nginx config (`location /api/`) is not involved — the reverse proxy routes `/api/` to the gateway, not to the seller app nginx.

**Correction**: The reverse proxy does NOT route `/api/` to individual frontend apps. It routes `/api/` to the gateway directly. So the frontend apps' own `/api/` nginx proxies are only used in the standalone frontend Docker setup (no reverse proxy layer).

## Docker Compose Service Names

The nginx `docker-entrypoint.sh` defaults must match the docker-compose service names:

| nginx Default Env | docker-compose Service Name | Container Name |
|-------------------|---------------------------|----------------|
| `fs-gateway` | `api-gateway` | `fs-gateway` |
| `fs-customer-fe` | `customer-app` | `fs-customer-fe` |
| `fs-seller-fe` | `seller-app` | `fs-seller-fe` |
| `fs-admin-fe` | `admin-app` | `fs-admin-fe` |

The defaults in `docker-entrypoint.sh` use container names (e.g., `fs-customer-fe`), which resolve via Docker's internal DNS to the correct container IP. The service name in docker-compose can be different (`customer-app`) — Docker's DNS resolves both service names and container names.

## Frontend Build-Time Configuration

In production, `VITE_API_URL` is baked into the React app at **build time** (not runtime):

```yaml
# deploy.yml — CI builds with VITE_API_URL baked into the bundle
- name: Build frontend app
  env:
    VITE_API_URL: /api/v1
  run: npm ci && npm run build
```

```dockerfile
# Dockerfile.prod — nginx serves pre-built React app
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY dist /usr/share/nginx/html   # VITE_API_URL=/api/v1 baked into JS bundles
```

### Why Bake at Build Time?

Vite replaces `import.meta.env.VITE_*` variables at build time. The resulting JS bundle contains the literal string `/api/v1`. This means:

- No need to pass `VITE_API_URL` as a runtime environment variable to the container
- Changing the API URL requires rebuilding the frontend
- The `env_file: .env.production` in `docker-compose.prod-pulled.yml` does NOT affect the baked-in value

### Frontend Production vs Dev Behavior

| Scenario | Frontend App | VITE_API_URL | How /api/* reaches gateway |
|----------|-------------|--------------|---------------------------|
| Standalone dev (`cd frontend`) | Vite dev server | empty (mock mode) | Not applicable |
| Root dev (`docker compose`) | Vite dev server :3000 | `/api/v1` | `VITE_PROXY_TARGET=http://fs-gateway:8080` in Vite config |
| Standalone prod (built) | nginx :80 | `/api/v1` (baked) | Frontend nginx `location /api/` proxies to `fs-gateway:8080` |
| Root prod (docker compose) | nginx :80 | `/api/v1` (baked) | Reverse proxy routes `/api/` to gateway |

## Gateway Route Configuration

All routes match `/api/v1/**` and use `stripPrefix(1)` to remove the `/api` prefix:

```java
// RouteConfig.java
.route("product-read", r -> r
    .path("/api/v1/products/**", "/api/v1/categories/**", "/api/v1/seller/**")
    .filters(f -> f.stripPrefix(1))
    .uri("lb://product-service"))
```

### Path Transformation Example

```
Browser / frontend:  /api/v1/identity/auth/register
                         ^^^^^^^^^ remove this (stripPrefix(1))
                         ↓
Gateway forwards:  /v1/identity/auth/register
                         ↓
Identity service:  @RequestMapping("/v1/auth")  ← matches ✓
```

### All Gateway Routes

| Route Name | Path Pattern | Downstream Service | Port |
|------------|-------------|-------------------|------|
| identity-public | `/api/v1/auth/**`, `/api/v1/users/register` | identity-service | 8081 |
| identity-protected | `/api/v1/users/**`, `/api/v1/loyalty/**` | identity-service | 8081 |
| product-read | GET `/api/v1/products/**`, `/categories/**`, `/seller/**` | product-service | 8090 |
| product-write | POST/PUT/DELETE `/api/v1/products/**` | product-service | 8090 |
| cart | `/api/v1/cart/**` | product-service | 8090 |
| seller-orders | `/api/v1/sellers/**` | order-service | 8083 |
| order | `/api/v1/orders/**` | order-service | 8083 |
| stripe-webhook | `/api/v1/stripe/webhooks` | payment-service | 8082 |
| stripe-onboarding | `/api/v1/stripe/onboarding/**` | payment-service | 8082 |
| payment | `/api/v1/payments/**`, `/refunds/**` | payment-service | 8082 |
| fs-read | GET `/api/v1/flash-sales/**` | flashsale-service | 8085 |
| fs-buy | POST `/api/v1/flash-sales/*/buy` | flashsale-service | 8085 |
| fs-write | POST/PUT/DELETE `/api/v1/flash-sales/**` | flashsale-service | 8085 |
| worker | `/api/v1/workers/**`, `/jobs/**` | worker-service | 8086 |
| search | `/api/v1/search/**` | search-service | 8091 |
| notification | `/api/v1/notifications/**` | notification-service | 8092 |

## Production Docker Compose

### Backend Services (docker-compose.prod-pulled.yml)

```yaml
services:
  discovery-service:
    image: ghcr.io/${IMAGE_PREFIX}/discovery-service:prod-latest
    env_file: .env.production

  api-gateway:
    image: ghcr.io/${IMAGE_PREFIX}/api-gateway:prod-latest
    env_file: .env.production
    ports: []                  # No host binding — gateway is internal only
    expose: ["8080"]           # Exposed to Docker network only

  identity-service:
    image: ghcr.io/${IMAGE_PREFIX}/identity-service:prod-latest
    env_file: .env.production

  # ... all other services ...
```

### Frontend Apps (docker-compose.prod-pulled.yml)

```yaml
services:
  customer-app:
    image: ghcr.io/${IMAGE_PREFIX}/customer-app:prod-latest
    env_file: .env.production   # VITE_API_URL baked at build time, not runtime

  seller-app:
    image: ghcr.io/${IMAGE_PREFIX}/seller-app:prod-latest
    env_file: .env.production

  admin-app:
    image: ghcr.io/${IMAGE_PREFIX}/admin-app:prod-latest
    env_file: .env.production
```

### Reverse Proxy

```yaml
services:
  reverse-proxy:
    image: ghcr.io/${IMAGE_PREFIX}/reverse-proxy:prod-latest
    env_file: .env.production
    ports: ["${PORT_REVERSE_PROXY:-80}:${NGINX_PORT:-80}"]
```

## Key Differences: .dev vs .prod

| Aspect | .dev | .prod |
|--------|------|-------|
| Gateway port | Exposed to host (`8080:8080`) | Internal only (no host binding) |
| Frontend containers | Vite dev server (node:22) | Pre-built nginx (nginx:alpine) |
| API routing | Vite proxy (`VITE_PROXY_TARGET`) | nginx `location /api/` |
| Frontend build | Built on server | Pre-built in CI, pulled as image |
| API URL source | `VITE_API_URL` from .env at runtime | `VITE_API_URL` baked at build time |
| Reverse proxy env | From root `.env` | From `.env.production` (CD generated) |

## Port Summary

| Service | Internal Port | Host Exposed | Network |
|---------|--------------|-------------|---------|
| `reverse-proxy` | 80 | **80 (configurable)** | External |
| `api-gateway` | 8080 | No (internal only) | flashsale-net |
| `discovery-service` | 8761 | No | flashsale-net |
| `identity-service` | 8081 | No | flashsale-net |
| `payment-service` | 8082 | No | flashsale-net |
| `order-service` | 8083 | No | flashsale-net |
| `flashsale-service` | 8085 | No | flashsale-net |
| `product-service` | 8090 | No | flashsale-net |
| `search-service` | 8091 | No | flashsale-net |
| `notification-service` | 8092 | No | flashsale-net |
| `worker-service` | 8086 | No | flashsale-net |
| `customer-app` | 80 (nginx) / 3000 (Vite) | 3000 | flashsale-net |
| `seller-app` | 80 (nginx) / 3001 (Vite) | 3001 | flashsale-net |
| `admin-app` | 80 (nginx) / 3002 (Vite) | 3002 | flashsale-net |

## Security Headers

Both nginx layers add security headers:

```
X-Frame-Options: SAMEORIGIN
X-XSS-Protection: 1; mode=block
X-Content-Type-Options: nosniff
Referrer-Policy: no-referrer-when-downgrade
Permissions-Policy: camera=(), microphone=(), geolocation=()
```

## Rate Limiting (Reverse Proxy Only)

```nginx
limit_req_zone $binary_remote_addr zone=api_limit:10m rate=10r/s;    # 10 req/s for API
limit_req_zone $binary_remote_addr zone=frontend_limit:10m rate=20r/s; # 20 req/s for frontend
```

Applied at the `location /api/` and `location /` blocks respectively.
