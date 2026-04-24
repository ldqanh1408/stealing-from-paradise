# Deploy Files — Nguồn biến môi trường

File này tổng hợp tất cả file deployment/docker-compose và chỉ ra **biến nào được lấy từ đâu**.

---

## Ba Chế Độ Chạy (Dev / Prod / Frontend Mock)

### 1. `.dev` — Stripe CLI + Backend + Frontend

Dùng **khi dev trên local**, cần Stripe webhook local để test thanh toán.

```powershell
# Chạy từ root
docker-compose -f docker-compose.yml -f docker-compose.dev.yml up --build -d
```

Gồm: infrastructure + backend + frontend + **stripe-listener** (Stripe CLI forward events từ Stripe servers → `payment-service`).

---

### 2. `.prod` — Backend + Frontend (Stripe Server thật)

Dùng **khi staging/prod** hoặc khi muốn test với Stripe live/test mode mà không cần Stripe CLI.

```powershell
# Full stack (không stripe-listener)
docker-compose -f docker-compose.yml up --build -d
```

Hoặc stack rõ ràng:

```powershell
# Infrastructure + backend + frontend (no stripe-listener)
docker-compose -f docker-compose.yml -f docker-compose-backend.yml up --build -d
```

Gồm: infrastructure + backend + frontend. **Không có stripe-listener**. Stripe events được gửi trực tiếp từ Stripe Dashboard/Servers đến endpoint.

---

### 3. Frontend Mock — Chỉ Frontend (không backend)

Dùng **khi chỉ muốn dev UI/frontend mà không cần backend**. Dùng mock data, `VITE_BACKEND_MODE=mock`.

```powershell
# Chạy từ thư mục frontend/
cd frontend
docker-compose -f docker-compose.yml up --build -d
```

Gồm: 3 frontend apps (customer, seller, admin) trên network `flashsale-frontend` (isolated). Không gọi backend, dùng mock data.

---

## Tổng quan cấu trúc deploy

```
root/
├── docker-compose.yml                   ← Entry point (base): infra + backend + frontend + nginx
├── docker-compose.prod-pulled.yml      ← Production override: pull images from GHCR
├── docker-compose-backend.yml           ← Backend services override (optional)
├── docker-compose-infrastructure.yml   ← Infrastructure only override
├── docker-compose.dev.yml              ← Dev override: adds stripe-listener
├── nginx/                              ← Nginx reverse proxy config
└── .env                                ← Nguồn biến chính

frontend/
├── docker-compose.yml                   ← Standalone dev: hot-reload Vite (no backend)
├── .env                                ← Biến cho frontend standalone (local dev)
└── .env.example                        ← Template

.github/workflows/
└── deploy.yml                          ← CD: build → push GHCR → deploy server
```

---

## Docker Compose Override Stack

### Development (chạy trên máy local)

```bash
# 1. Full stack (infra + backend + frontend + nginx) — tất cả cùng network flashsale-net
docker-compose -f docker-compose.yml up --build -d

# 2. Chỉ infra (postgres, mongo, redis, kafka...)
docker-compose -f docker-compose-infrastructure.yml up -d

# 3. Backend + infra (không có frontend)
docker-compose -f docker-compose.yml -f docker-compose-backend.yml up --build -d

# 4. Frontend standalone (không cần backend, dùng mock data)
cd frontend && docker-compose -f docker-compose.yml up --build -d
```

### Production — CD Server (GitHub Actions deploy lên server)

```bash
# deploy.yml chạy lệnh này trên server (chỉ cần 2 file compose):
docker-compose -f docker-compose.yml -f docker-compose.prod-pulled.yml up -d
```

---

## Flow: Frontend gọi API Gateway

Request chain khi production:

```
Browser
  │
  │ GET /api/v1/products/123
  ▼
Nginx Reverse Proxy (port 80)
  │
  │ location /api/ → proxy_pass http://gateway/api/v1/
  │                         └─ strip /api/v1/ prefix (nginx proxy config)
  ▼
API Gateway (Spring Cloud Gateway, port 8080)
  │
  │ RouteConfig: stripPrefix(1) → /products/123
  │ → lb://product-service
  ▼
Product Service (port 8090)
```

**Điểm quan trọng về path stripping:**
- Nginx reverse-proxy: `proxy_pass http://gateway/api/v1/` — **giữ nguyên** path từ browser (`/api/v1/...`)
- API Gateway: `stripPrefix(1)` — bỏ `/api` → gửi `/v1/...` tới downstream services
- Backend services (product-service, etc.): nhận `/v1/...` → phải dùng `@RequestMapping("/v1/...")`

Nếu backend controllers dùng `@RequestMapping("/products")` (không có `/v1/`), thì cần thêm `stripPrefix(2)` ở gateway.

---

## Chi tiết từng file

### `docker-compose.yml` (root — base entry point)

| Nguồn biến | Chi tiết |
|---|---|
| **env_file: .env** | Đọc tất cả biến từ `../.env` (root) |
| **environment:** | Override một số biến cụ thể |
| **Ports, image versions** | Từ `.env` qua `${VAR:-default}` |

**Lấy biến từ:** `ROOT .env`

```yaml
# Ví dụ: postgres dùng
env_file: .env               # ← ../.env
environment:
  POSTGRES_USER: ${POSTGRES_USER}
  POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
```

---

### `docker-compose-backend.yml`

| Nguồn biến | Chi tiết |
|---|---|
| **env_file: .env** | Đọc từ `../.env` (root) |
| **environment:** | Truyền biến cần thiết vào container |

**Lấy biến từ:** `ROOT .env`

---

### `docker-compose-infrastructure.yml`

| Nguồn biến | Chi tiết |
|---|---|
| **env_file: .env** | Đọc từ `../.env` (root) |
| **image: postgres:${POSTGRES_VER:-15.4-alpine}** | Version từ .env |
| **command / healthcheck** | Dùng biến từ .env |

**Lấy biến từ:** `ROOT .env`

---

### `docker-compose.prod-pulled.yml` (root — CD production override)

Gộp tất cả image overrides vào một file duy nhất ở root thay vì rải ở `backend/` và `frontend/`.

| Biến | Nguồn |
|---|---|
| `IMAGE_PREFIX` | Set trong `deploy.yml` |
| `STRIPE_WEBHOOK_SECRET_PROD` | **GitHub Secrets** — KHÔNG đọc từ .env |
| `SERVER_BIND` | Fallback default: `0.0.0.0` |

---

### `frontend/docker-compose.yml` (standalone dev)

| Nguồn biến | Chi tiết |
|---|---|
| **env_file: .env** | Đọc từ `frontend/.env` (local) |
| **VITE_API_URL** | Fallback: `${VITE_API_URL:-/api/v1}` — nếu frontend/.env không set, dùng `/api/v1` |

**Lấy biến từ:** `frontend/.env` → fallback về `/api/v1`

```yaml
env_file: .env
environment:
  - VITE_API_URL=${VITE_API_URL:-/api/v1}   # fallback an toàn
  - VITE_BACKEND_MODE=${VITE_BACKEND_MODE:-mock}
```

**Network:** `flashsale-frontend` (isolated). Standalone mode dùng mock data, nên không cần gọi backend.

| Nguồn biến | Chi tiết |
|---|---|
| **image:** | `ghcr.io/${IMAGE_PREFIX}/reverse-proxy:prod-latest` |
| **env_file: .env** | Kế thừa từ `docker-compose.yml` (reverse-proxy có `env_file: .env`) |

**Lấy biến từ:** `ROOT .env` qua `docker-compose.yml` chain

| Biến | Nguồn |
|---|---|
| `IMAGE_PREFIX` | Set trong `deploy.yml` → truyền qua SSH |
| `NGINX_PORT`, `GATEWAY_HOST`, etc. | Từ `ROOT .env` |

---

## deploy.yml (GitHub Actions) — Biến và nguồn

### Biến set trong workflow

| Biến | Giá trị | Nguồn |
|---|---|---|
| `REGISTRY` | `ghcr.io` | Hardcode trong `env:` |
| `IMAGE_PREFIX` | `${{ github.repository_owner }}/flashsale` | Hardcode trong `env:` |
| `IMAGE_TAG` | `prod-latest` | Step meta |
| `DEPLOY_USER` | `${{ secrets.DEPLOY_USER }}` | GitHub Secrets |
| `SERVER_IP` | `${{ secrets.SERVER_IP }}` | GitHub Secrets |
| `SSH_PRIVATE_KEY` | `${{ secrets.SSH_PRIVATE_KEY }}` | GitHub Secrets |
| `GITHUB_TOKEN` | `${{ secrets.GITHUB_TOKEN }}` | GitHub Actions auto-provide |

### File được SCP lên server

| File | Đích trên server |
|---|---|
| `docker-compose.yml` | `/opt/flashsale/` |
| `docker-compose.prod-pulled.yml` | `/opt/flashsale/` |

### File KHÔNG được SCP lên server

| File | Vấn đề |
|---|---|
| `.env` (root) | **Không được upload.** |
| `frontend/docker-compose.yml` | Không cần cho CD (frontend dùng prod-pulled) |

**Hệ quả:** `${VAR}` không có default sẽ thành empty string. Tất cả biến trong compose files phải có default: `${VAR:-default}`.

**Biến production cần đặt trong GitHub Secrets** (vì không upload .env):

| Biến | Nơi đặt |
|---|---|
| `STRIPE_WEBHOOK_SECRET_PROD` | GitHub Secrets → export vào SSH command |
| `.env` trên server | SCP trước deploy hoặc set server-side |

---

## Root .env — Biến dùng ở đâu

### Infrastructure (`docker-compose.yml`, `docker-compose-infrastructure.yml`)

```
POSTGRES_VER, MONGO_VER, REDIS_VER, KAFKA_VER, ELASTIC_VER, AXON_VER
PORT_POSTGRES, PORT_MONGO, PORT_REDIS, PORT_MINIO, PORT_MINIO_CONSOLE, PORT_ELASTIC
PORT_ZOOKEEPER, PORT_KAFKA, PORT_AXON_GUI, PORT_AXON_GRPC
POSTGRES_USER, POSTGRES_PASSWORD
MONGO_INITDB_ROOT_USERNAME, MONGO_INITDB_ROOT_PASSWORD
REDIS_PASSWORD
TZ
ES_JAVA_OPTS (infra-level)
```

### Backend Services (`docker-compose.prod-pulled.yml`)

```
PORT_DISCOVERY, PORT_GATEWAY, PORT_IDENTITY, PORT_PAYMENT, PORT_ORDER,
PORT_PRODUCT, PORT_FLASHSALE, PORT_SEARCH, PORT_NOTIFICATION, PORT_WORKER

JWT_SECRET, JWT_EXPIRATION_MS, JWT_REFRESH_EXPIRATION_MS
DB_HOST, DB_NAME, DB_NAME_PRODUCT, DB_NAME_NOTIFICATION
POSTGRES_USER, POSTGRES_PASSWORD
MONGO_HOST, MONGO_INITDB_ROOT_USERNAME, MONGO_INITDB_ROOT_PASSWORD
REDIS_HOST, REDIS_PASSWORD
KAFKA_SERVER, AXON_SERVER, ELASTIC_URI, MINIO_URL
EUREKA_URI

STRIPE_PUBLISHABLE_KEY, STRIPE_SECRET_KEY, STRIPE_WEBHOOK_SECRET,
STRIPE_WEBHOOK_SECRET_PROD, STRIPE_PLATFORM_FEE_PERCENTAGE

MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD, MAIL_FROM

JVM_OPTS_GATEWAY, JVM_OPTS_IDENTITY, JVM_OPTS_PAYMENT, JVM_OPTS_ORDER,
JVM_OPTS_FLASHSALE, JVM_OPTS_PRODUCT, JVM_OPTS_SEARCH, JVM_OPTS_NOTIFICATION,
JVM_OPTS_WORKER, JVM_OPTS_COMMON

MINIO_ACCESS_KEY, MINIO_SECRET_KEY, MINIO_URL

VITE_API_URL, NEXT_PUBLIC_API_URL, NEXT_PUBLIC_WS_URL
SPRING_PROFILES_ACTIVE, TZ
```

### Frontend (`frontend/docker-compose.yml`, `frontend/docker-compose.prod.yml`)

```
VITE_API_URL          ← frontend/.env (fallback: /api/v1)
VITE_BACKEND_MODE     ← frontend/.env (default: mock)
PORT_CUSTOMER, PORT_SELLER, PORT_ADMIN, PORT_GATEWAY  ← frontend/.env
NODE_ENV, TZ
```

### Nginx Reverse Proxy (`docker-compose.yml`)

```
PORT_REVERSE_PROXY, NGINX_PORT
REVERSE_PROXY_GATEWAY_HOST, REVERSE_PROXY_GATEWAY_PORT
REVERSE_PROXY_CUSTOMER_HOST, REVERSE_PROXY_CUSTOMER_PORT
REVERSE_PROXY_SELLER_HOST, REVERSE_PROXY_SELLER_PORT
REVERSE_PROXY_ADMIN_HOST, REVERSE_PROXY_ADMIN_PORT
TZ
```

---

## Checklist trước deploy

- [ ] `.env` có đầy đủ giá trị production
- [ ] `STRIPE_WEBHOOK_SECRET_PROD` nằm trong **GitHub Secrets** (không phải .env server)
- [ ] `REDIS_PASSWORD` không rỗng
- [ ] `IMAGE_PREFIX` khớp với GitHub repo owner
- [ ] Backend controllers dùng đúng `@RequestMapping` path — kiểm tra stripPrefix(1) ở gateway gửi `/v1/...` hay `/...`
- [ ] Build và test local trước khi merge vào main
