# Frontend-Backend Communication Architecture

## Overview

The FlashSale platform has 3 frontend applications and 1 backend (API Gateway as the single entry point). All communication between browser and backend flows through Nginx, but the routing chain differs significantly between **Dev** and **Prod** modes.

---

## Architecture Diagram

```
                        ┌─────────────────────────────────────────┐
                        │         DOCKER NETWORK (flashsale-net)   │
                        │                                         │
  Browser ──────────────┤  ┌──────────────────┐    ┌───────────┐  │
  (http://localhost)    │  │  reverse-proxy   │    │  gateway  │  │
                        │  │   (nginx :80)   │───>│  :8080    │  │
                        │  └────────┬─────────┘    └───────────┘  │
                        │           │                            │
                        │  ┌────────┴──────────┐                 │
                        │  │                   │                  │
                        │  ▼                   ▼                  │
                        │  / → customer-app   /api/* → gateway   │
                        │      :3000              (proxied)       │
                        │  /seller/* → seller-app                        │
                        │      :3001                                 │
                        │  /admin/* → admin-app                       │
                        │      :3002                                 │
                        └─────────────────────────────────────────┘
```

---

## 2. Dev Mode

### Startup Command

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up --build -d
```

### How It Works

#### Frontend: Vite Dev Server

Each frontend app runs as a **Vite hot-reload dev server** inside a Docker container:

| App          | Container    | Port | Vite Target      |
|-------------|-------------|------|-----------------|
| Customer     | fs-customer-fe | 3000 | `localhost:8080` |
| Seller       | fs-seller-fe   | 3001 | `localhost:8080` |
| Admin        | fs-admin-fe    | 3002 | `localhost:8080` |

The key Vite proxy config (identical in all 3 apps):

```typescript
proxy: {
  '/api': {
    target: process.env.VITE_PROXY_TARGET || 'http://localhost:8080',
    changeOrigin: true,
  },
},
```

The browser loads `http://localhost:3000`. When the React app calls `/api/auth/login`, Vite's dev server intercepts it and forwards to `http://localhost:8080/api/auth/login` (the gateway).

> **Note:** `VITE_API_URL` is set to `/api` in `.env`, but in dev mode the **Vite proxy** handles the routing — the browser still sees requests going to `/api/*`. The proxy runs server-side inside the Vite process.

#### Backend: Spring Boot Microservices

Gateway + all services run as Spring Boot containers with `SPRING_PROFILES_ACTIVE=dev`.

#### Nginx `reverse-proxy`

The `nginx/docker-entrypoint.sh` generates the Nginx config at runtime. Key routes:

| Browser URL           | Proxied To             | Note |
|----------------------|----------------------|------|
| `http://localhost/`  | `customer-app:3000`  | Root = customer SPA |
| `http://localhost/seller/*` | `seller-app:3001` | Seller SPA |
| `http://localhost/admin/*`  | `admin-app:3002`  | Admin SPA |
| `http://localhost/api/*`    | `gateway:8080/api/v1/` | Strips `/api/` and adds `/api/v1/` |

The `/api/` location uses `proxy_pass http://gateway;` (no trailing slash), so `/api/v1/auth` is forwarded unchanged to gateway.

#### The App's Own `nginx.conf` — **NOT USED in Dev**

Each frontend app has a `nginx.conf`, but in dev mode these files are **never loaded**. The containers run `npm run dev` (Node/Vite), not nginx. These configs are only used in production builds.

#### Sequence: Browser → Backend (Dev)

```
1. Browser → http://localhost/api/auth/login
   └─> Docker host port 3000 (customer-app) OR
   └─> Docker host port 80   (reverse-proxy) → customer-app:3000

2. customer-app (Vite) receives /api/auth/login
   └─> Vite proxy forwards to http://localhost:8080/api/auth/login

3. Gateway receives the request, routes to identity-service
```

#### How Browser Reaches the App (Dev)

- **Option A: Direct access** — Browser hits `http://localhost:3000` directly. Vite dev server proxies `/api` to gateway. No reverse-proxy involvement.
- **Option B: Via reverse-proxy** — Browser hits `http://localhost/` which proxies to customer-app:3000. Same Vite proxy behavior applies.

---

## 3. Prod Mode

### Startup Command

```bash
docker compose \
  -f docker-compose.yml \
  -f docker-compose.prod-pulled.yml \
  --env-file .env.production \
  up -d
```

### How It Works

#### Frontend: Nginx Static Server

Each frontend app is a **built React SPA** served by its own nginx container:

| App          | Container    | Port | Image         |
|-------------|-------------|------|--------------|
| Customer     | fs-customer-fe | 80   | `nginx:alpine` |
| Seller       | fs-seller-fe   | 80   | `nginx:alpine` |
| Admin        | fs-admin-fe    | 80   | `nginx:alpine` |

The `Dockerfile.prod` copies the `nginx.conf` into the container:

```dockerfile
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY dist /usr/share/nginx/html
```

Each app's `nginx.conf` handles `/api/` requests:

```nginx
location /api/ {
    proxy_pass http://fs-gateway:8080;
    # No trailing slash → /api/v1/auth → gateway receives /api/v1/auth
    ...
}
```

The SPA fallback serves `index.html` for all non-API routes:

```nginx
location / {
    try_files $uri $uri/ /index.html;
}
```

#### Backend: Pre-built Spring Boot Images

Images are pulled from GHCR (`ghcr.io/${IMAGE_PREFIX}/<service>:prod-latest`). No rebuild on server.

#### Nginx `reverse-proxy`

Same config generation via `docker-entrypoint.sh`, but environment variables come from `.env.production`.

Routes:

| Browser URL           | Proxied To             | Note |
|----------------------|----------------------|------|
| `http://localhost/`  | `customer-app:80`    | Root = customer SPA |
| `http://localhost/seller/*` | `seller-app:80` | Seller SPA |
| `http://localhost/admin/*`  | `admin-app:80`  | Admin SPA |
| `http://localhost/api/*`    | `gateway:8080/api/v1/` | Strips `/api/` and adds `/api/v1/` |

#### Sequence: Browser → Backend (Prod)

```
1. Browser → http://localhost/api/auth/login
   └─> Docker host port 80 (reverse-proxy)
   └─> reverse-proxy (nginx) sees /api/
       └─> proxies to gateway:8080/api/v1/ (receives /api/v1/auth/login)
           └─> gateway routes to identity-service

   OR (when going through a specific app):

2. Browser → http://localhost/admin/api/auth/login  ← rare, admin uses its own nginx
   └─> reverse-proxy proxies /admin/* → admin-app:80
   └─> admin-app's nginx sees /api/
       └─> proxies to gateway:8080/api/v1/
```

In practice, all three apps' nginx configs have identical `/api/` proxy rules pointing to `http://fs-gateway:8080`. Whether the request goes through the reverse-proxy or directly to an app's nginx, the gateway receives the same path.

---

## 4. Key Configuration Files

| File | Purpose | Dev | Prod |
|------|---------|-----|------|
| `.env` | Central config (source of truth) | Used | Overridden by `.env.production` |
| `vite.config.ts` | Vite proxy: `/api` → `localhost:8080` | **Active** (Vite dev server) | Not used |
| `nginx.conf` (in each app) | App-level nginx serving static build | **Not loaded** (Vite runs instead) | **Active** (nginx serves build) |
| `nginx/docker-entrypoint.sh` | Generates reverse-proxy config at runtime | **Active** | **Active** |
| `nginx/reverse-proxy.conf` | Documentation of generated config | Reference only | Reference only |
| `docker-compose.yml` | Base compose (dev builds) | Used | Used as base |
| `docker-compose.dev.yml` | Adds stripe-listener service | **Merged** | Not used |
| `docker-compose.prod-pulled.yml` | Pulls pre-built images from GHCR | Not used | **Merged** |

---

## 5. Environment Variables Flow

```
.env (root)
├── VITE_API_URL=/api            ← read by frontend at build time
├── VITE_STRIPE_PUBLISHABLE_KEY   ← injected into container by env_file
├── VITE_BACKEND_MODE=real        ← set in docker-compose.yml environment:
└── VITE_PROXY_TARGET             ← used by vite.config.ts (not set = localhost:8080)
                                     defaults to http://localhost:8080

.env.production (CD server deploy)
├── Same VITE_ vars as .env
└── No VITE_PROXY_TARGET (Vite not used in prod)
```

> **Why `VITE_API_URL=/api` works in both modes:**
> - In **dev**: Vite proxy forwards `/api/*` to gateway unchanged. Browser sees `/api/*`.
> - In **prod**: Each app's nginx and reverse-proxy strip `/api/` and add `/api/v1/`. Gateway receives `/api/v1/*`.
> - In both cases the browser calls `/api/*` on the same origin, and nginx/Vite routes it to the gateway as `/api/v1/*`.

---

## 6. Trailing Slash Trap (Critical)

Both the reverse-proxy and app-level nginx configs use `proxy_pass` with a **trailing slash + path rewrite**:

```nginx
location /api/ {
    proxy_pass http://gateway/api/v1/;  # strips /api/, adds /api/v1/
}
```

This achieves two things simultaneously:
1. **Strips** the matched location prefix `/api/` from the request URI
2. **Appends** `/api/v1/` to what remains

So browser calls `/api/auth/login` → gateway receives `/api/v1/auth/login`.

> **If you used `proxy_pass http://gateway;` (no trailing slash)**, nginx would forward `/api/auth/login` unchanged, which doesn't match any gateway route.

---

## 7. Ports Summary

| Service           | Container Port | Host Port (Dev) | Host Port (Prod) |
|------------------|---------------|-----------------|-----------------|
| reverse-proxy    | 80            | 80              | 80              |
| api-gateway      | 8080          | 8080            | (internal only) |
| customer-app     | 3000 (dev) / 80 (prod) | 3000      | (internal only) |
| seller-app       | 3001 (dev) / 80 (prod) | 3001      | (internal only) |
| admin-app        | 3002 (dev) / 80 (prod) | 3002      | (internal only) |

> **Dev:** Gateway and frontend apps expose ports for direct access (useful for debugging).
> **Prod:** Only reverse-proxy:80 is exposed externally. All internal services communicate over Docker network.

---

## 8. Debugging Tips

### Check if Vite proxy is working (dev)

```bash
# Inside customer-app container
curl -I http://localhost:8080/api/v1/health
```

### Check if reverse-proxy routing is correct (prod)

```bash
# From inside any container on flashsale-net
curl http://fs-gateway:8080/actuator/health
curl http://customer-app/api/v1/auth/login  # Should proxy to gateway
```

### Compare config generated vs documentation

```bash
docker exec fs-reverse-proxy cat /etc/nginx/conf.d/default.conf
```

### Verify nginx proxy_pass behavior

```bash
# Without trailing slash (correct)
docker exec fs-customer-fe cat /etc/nginx/conf.d/default.conf | grep -A3 "location /api/"

# Should show: proxy_pass http://fs-gateway:8080;
# NOT: proxy_pass http://fs-gateway:8080/;
```
