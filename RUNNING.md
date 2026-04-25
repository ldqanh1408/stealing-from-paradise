# Flash Sale Platform — Running Guide

**Project**: stealing-from-paradise
**Entry Script**: `flashsale-build.ps1`
**Last Updated**: 2026-04-24

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [First-Time Setup](#2-first-time-setup)
3. [Quick Start](#3-quick-start)
4. [Script Command Reference](#4-script-command-reference)
5. [Running Modes](#5-running-modes)
6. [Development Workflows](#6-development-workflows)
7. [Docker Compose File Architecture](#7-docker-compose-file-architecture)
8. [Service Ports & URLs](#8-service-ports--urls)
9. [Container Names](#9-container-names)
10. [Database Connections](#10-database-connections)
11. [Stripe Webhook Setup](#11-stripe-webhook-setup)
12. [Troubleshooting](#12-troubleshooting)

---

## 1. Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| Docker Desktop | Latest | Run all services in containers |
| Maven | 3.8+ | Build backend JARs (optional — can build in Docker) |
| Java | 25 | Run backend locally (optional) |
| Node.js | 22+ | Run frontend locally (optional) |

> **Note**: All services can run fully inside Docker. Local Maven/Node.js are only needed if you want to build on the host.

---

## 2. First-Time Setup

### 2.1 Copy environment file

```powershell
cp .env.example .env
```

### 2.2 Fill in secrets in `.env`

```
JWT_SECRET=<generate-a-256bit-base64-string>
POSTGRES_PASSWORD=<strong-password>
REDIS_PASSWORD=<strong-password>
MONGO_INITDB_ROOT_PASSWORD=<strong-password>
MINIO_ACCESS_KEY=<your-access-key>
MINIO_SECRET_KEY=<your-secret-key>
STRIPE_SECRET_KEY=sk_test_...
STRIPE_PUBLISHABLE_KEY=pk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...  # From: stripe listen --print-secret
```

### 2.3 Start Docker Desktop

Make sure Docker Desktop is running before using any commands.

---

## 3. Quick Start

### Full Stack — DEV mode (recommended for daily dev)

```powershell
# Build backend JARs (one-time or after backend code changes)
.\flashsale-build.ps1 mvn-all

# Start everything: infra + backend + frontend + stripe-listener
.\flashsale-build.ps1 dev
```

### Backend Only (no frontend)

```powershell
.\flashsale-build.ps1 be-dev
```

### Frontend Only — Mock Data (no backend)

```powershell
# All 3 apps via npm on host (hot-reload, opens 3 terminal windows)
.\flashsale-build.ps1 fe-dev-all

# Or all 3 apps via Docker (mock data)
.\flashsale-build.ps1 fe-docker-all
```

### Stop Everything

```powershell
.\flashsale-build.ps1 stop all
```

---

## 4. Script Command Reference

Run from the **project root**: `.\flashsale-build.ps1 <action> [target]`

### 4.1 Maven — Backend Build

| Action | Description |
|--------|-------------|
| `mvn-all` | Build ALL 12 Maven modules (clean install, skip tests) |
| `mvn <service>` | Build single Maven module |
| `mvn-clean <service>` | Clean + build single Maven module |

Services: `discovery`, `gateway`, `identity`, `payment`, `order`, `flashsale`, `product`, `search`, `notification`, `worker`, `common-lib`, `dev-data-runner`

```powershell
# Examples
.\flashsale-build.ps1 mvn gateway
.\flashsale-build.ps1 mvn order
.\flashsale-build.ps1 mvn-clean discovery
```

### 4.2 npm — Frontend Build

| Action | Description |
|--------|-------------|
| `npm-install-all` | Run `npm install` for all frontend apps + shared |
| `npm-install <app>` | Run `npm install` for specific app |
| `npm-all` | Build ALL 3 frontend apps (npm install + build) |
| `npm <app>` | Build single frontend app |

Apps: `customer`, `seller`, `admin`, `shared`

```powershell
# Examples
.\flashsale-build.ps1 npm customer
.\flashsale-build.ps1 npm-all
.\flashsale-build.ps1 npm-install-all
```

### 4.3 Frontend Dev Modes

| Action | Description |
|--------|-------------|
| `fe-dev <app>` | ONE frontend app via npm on host (hot-reload, mock data, opens new terminal) |
| `fe-dev-all` | ALL 3 frontend apps via npm on host (3 terminal windows, hot-reload, mock data) |
| `fe-docker <app>` | ONE frontend app via Docker (mock data) |
| `fe-docker-all` | ALL 3 frontend apps via Docker (mock data) |

Apps: `customer` (port 3000), `seller` (port 3001), `admin` (port 3002)

> `fe-dev` writes `.env.local` with `VITE_BACKEND_MODE=mock` and starts the Vite dev server directly on the host.

### 4.4 Backend & Fullstack Modes

| Action | Description |
|--------|-------------|
| `infra-up` | Start ONLY infrastructure (postgres, mongo, redis, kafka, elasticsearch, minio, axonserver) |
| `infra-down` | Stop infrastructure |
| `be-dev` | Start infra + backend (all backend containers, no frontend) |
| `be-down` | Stop backend |
| `dev` | Start full stack in DEV mode (infra + backend + frontend + stripe-listener) |
| `dev-down` | Stop dev stack |
| `prod` | Start full stack in PROD mode (infra + backend + frontend, no stripe-listener) |
| `prod-down` | Stop prod stack |

### 4.5 Single-Service Container Commands

| Action | Description |
|--------|-------------|
| `svc-build <service>` | Build ONE backend service Docker image |
| `svc-run <service>` | Start infra + build + run ONE service container |
| `svc-up <service>` | Start ONE service container (already built, no rebuild) |
| `svc-rm <service>` | Remove ONE service container |

Services: `discovery`, `gateway`, `identity`, `payment`, `order`, `flashsale`, `product`, `search`, `notification`, `worker`

```powershell
# Examples
.\flashsale-build.ps1 svc-build payment   # Build image
.\flashsale-build.ps1 svc-run order     # Build + run order service (auto-starts infra)
.\flashsale-build.ps1 svc-up gateway     # Start existing gateway container
.\flashsale-build.ps1 svc-rm payment     # Remove payment container
```

### 4.6 Stop / Down Commands

| Action | Description |
|--------|-------------|
| `stop infra` | Stop infrastructure |
| `stop be` | Stop backend |
| `stop fe` | Stop frontend |
| `stop dev` | Stop dev stack |
| `stop prod` | Stop prod stack |
| `stop all` | Stop ALL containers (keeps volumes) |
| `down <mode>` | Alias for `stop <mode>` (giống hệt) |

### 4.7 Per-Service Operations (NEW)

Các lệnh per-service để control 1 container riêng lẻ trong khi dev:

| Action | Description |
|--------|-------------|
| `restart <target>` | Restart container KHÔNG rebuild (nhanh, chỉ apply env changes) |
| `reset <target>` | Stop + remove + REBUILD + start (dùng khi đổi source code) |
| `shell <service>` | Mở interactive bash/sh shell trong container đang chạy |
| `fe-down [<app>]` | Stop 1 hoặc tất cả frontend apps |

`<target>` có thể là:
- Tên service: `gateway`, `order`, `customer`, ...
- Group alias: `be` (toàn bộ backend), `fe` (toàn bộ frontend), `infra`, `all`

```powershell
# Restart 1 service (giữ nguyên image, apply env mới nếu có)
.\flashsale-build.ps1 restart gateway
.\flashsale-build.ps1 restart fe                  # restart cả 3 FE apps
.\flashsale-build.ps1 restart all                 # restart toàn bộ

# Reset 1 service (rebuild image + restart container)
.\flashsale-build.ps1 reset customer              # rebuild customer-app
.\flashsale-build.ps1 reset be                    # rebuild + restart toàn bộ backend

# Mở shell vào container để debug
.\flashsale-build.ps1 shell postgres              # bash vào postgres
.\flashsale-build.ps1 shell gateway               # bash/sh vào api-gateway
.\flashsale-build.ps1 shell redis                 # vào redis (sh only)

# Stop frontend riêng lẻ
.\flashsale-build.ps1 fe-down seller              # chỉ stop seller-app
.\flashsale-build.ps1 fe-down                     # stop tất cả FE
```

**Khác biệt giữa `restart` và `reset`:**
- `restart` chỉ stop+start container (giữ nguyên image). Dùng khi đổi env vars hoặc cần "đá" container.
- `reset` rebuild image rồi recreate container. Dùng khi đổi source code hoặc Dockerfile.

### 4.8 Utility Commands

| Action | Description |
|--------|-------------|
| `logs all` | Stream logs from ALL containers |
| `logs be` | Stream logs from backend containers |
| `logs fe` | Stream logs from frontend containers |
| `logs infra` | Stream logs from infrastructure |
| `logs <service>` | Stream logs from a specific container |
| `ps` / `status` | List all running containers |
| `clean` | Stop all + remove volumes (DESTRUCTIVE — asks confirmation) |
| `help` | Show help message |

```powershell
# Examples
.\flashsale-build.ps1 logs gateway
.\flashsale-build.ps1 logs postgres
.\flashsale-build.ps1 ps
.\flashsale-build.ps1 clean
```

---

## 5. Running Modes

### Mode Comparison

| Mode | Command | Infra | Backend | Frontend | Stripe CLI |
|------|---------|-------|---------|----------|------------|
| **dev** | `dev` | Yes | Yes | Yes | Yes |
| **prod** | `prod` | Yes | Yes | Yes | No |
| **be-dev** | `be-dev` | Yes | Yes | No | No |
| **fe-dev** | `fe-dev <app>` | No | No | Yes | No |
| **infra** | `infra-up` | Yes | No | No | No |

### What Each Mode Includes

**`dev`**: Infrastructure + Backend + Frontend + Stripe CLI
- All backend microservices
- All frontend apps
- `fs-stripe-listener` (forwards Stripe webhooks to payment-service)
- Best for: full-stack development with payment testing

**`prod`**: Infrastructure + Backend + Frontend (no Stripe CLI)
- Same as dev but without `fs-stripe-listener`
- Stripe webhooks come directly from Stripe Dashboard to your server
- Best for: staging environment

**`be-dev`**: Infrastructure + Backend
- All backend microservices
- No frontend, no Stripe CLI
- Best for: backend-only development, API testing with Postman

**`fe-dev`**: Frontend only on host machine
- Runs via npm on the host (not in Docker)
- Mock data mode (no real backend needed)
- Hot-reload enabled
- Best for: frontend-only UI development

**`infra-up`**: Infrastructure only
- Databases, caches, queues, search engine
- Best for: running backend in IDE while using Docker infra

---

## 6. Development Workflows

### 6.1 Full-Stack Development (daily dev)

```powershell
# Start everything
.\flashsale-build.ps1 dev

# After code changes:
# Backend code changed:
.\flashsale-build.ps1 mvn <service>     # Rebuild JAR cho service đó
.\flashsale-build.ps1 reset <service>   # Stop + rebuild + restart container

# Frontend code changed:
# No rebuild needed — hot-reload via volumes
# Just refresh the browser

# Chỉ đổi env vars (không đổi code):
.\flashsale-build.ps1 restart gateway   # apply env mới, không rebuild

# Full reset:
.\flashsale-build.ps1 stop all
.\flashsale-build.ps1 mvn-all
.\flashsale-build.ps1 dev
```

### 6.2 Backend-Only Development

```powershell
# Option A: Backend in Docker, infra in Docker
.\flashsale-build.ps1 be-dev

# Option B: Backend in IDE, infra in Docker
.\flashsale-build.ps1 infra-up
# Then run services in IDE:
cd backend
mvn spring-boot:run -pl identity-service
```

### 6.3 Frontend-Only Development

```powershell
# Option A: npm on host (recommended for UI work)
.\flashsale-build.ps1 fe-dev-all
# Opens 3 terminal windows with hot-reload

# Option B: Docker (sandboxed)
.\flashsale-build.ps1 fe-docker-all
```

### 6.4 Single Service Development

```powershell
# Build and run only one backend service (e.g., order-service)
# Useful when you only need to test one service
.\flashsale-build.ps1 svc-run order

# Then test via gateway:
curl http://localhost:8080/api/v1/orders/...
```

### 6.5 Per-Service Iteration (NEW)

Workflow nhanh nhất để iterate trên 1 service:

```powershell
# 1. Đổi source code của order-service
# 2. Rebuild JAR
.\flashsale-build.ps1 mvn order

# 3. Reset container (bao gồm rebuild image + restart)
.\flashsale-build.ps1 reset order

# 4. Tail log để verify
.\flashsale-build.ps1 logs order
```

**Khi service không healthy / hành xử lạ**:
```powershell
# Mở shell vào container để debug
.\flashsale-build.ps1 shell gateway
# Trong container:
ps aux               # check Java process
cat /app/app.jar     # verify file
exit
```

**Khi đổi config trong .env (không cần rebuild)**:
```powershell
.\flashsale-build.ps1 restart be       # restart toàn bộ backend, apply env mới
# hoặc 1 service:
.\flashsale-build.ps1 restart payment
```

**Khi frontend stuck cache cũ**:
```powershell
.\flashsale-build.ps1 reset customer    # rebuild image, recreate container
# Plus hard refresh browser: Ctrl + Shift + R
```

### 6.6 Frontend-Only Development (mock mode, no backend)

```powershell
# Cách 1: Vite dev server trực tiếp trên host (recommended cho UI work)
.\flashsale-build.ps1 fe-dev customer
# Mở http://localhost:3000 — dùng mock data, hot-reload

# Cách 2: Tất cả 3 apps cùng lúc trong 3 cửa sổ
.\flashsale-build.ps1 fe-dev-all

# Cách 3: Qua Docker (nếu muốn isolate)
.\flashsale-build.ps1 fe-docker-all

# Stop chỉ 1 frontend app
.\flashsale-build.ps1 fe-down seller

# Stop tất cả frontend (giữ backend + infra chạy)
.\flashsale-build.ps1 fe-down
```

`fe-dev` tự động ghi `.env.local` với `VITE_BACKEND_MODE=mock`, axios sẽ trả mock data thay vì gọi backend thật.

---

## 7. Docker Compose File Architecture

```
project-root/
|
|-- docker-compose.yml                    # Base: infra + backend + frontend + nginx
|-- docker-compose.dev.yml               # DEV override: adds fs-stripe-listener
|-- docker-compose-infrastructure.yml    # Infrastructure only
|-- docker-compose-backend.yml           # Backend services only
|-- docker-compose.prod-pulled.yml     # PROD: pulls images from GHCR (CD deploy)
|-- flashsale-build.ps1                 # Unified build & run script
|
|-- backend/
|   |-- discovery-service/
|   |-- api-gateway/
|   |-- identity-service/
|   |-- product-service/
|   |-- order-service/
|   |-- payment-service/
|   |-- flashsale-service/
|   |-- search-service/
|   |-- notification-service/
|   |-- worker-service/
|   |-- common-lib/
|   |-- dev-data-runner/
|   |-- docker/
|   |   |-- postgres/init/        # SQL init scripts
|   |   |-- mongo/init/           # MongoDB init scripts
|   |   |-- kafka/create-topics.sh
|   |   |-- axon-init/
|   |   |-- Dockerfile.dev         # Dev: copies pre-built JARs
|   |   |-- Dockerfile.prod       # Prod: builds JARs inside Docker
|   |-- docker-compose.yml       # Standalone backend (dev)
|   |-- docker-compose.infra-only.yml
|   |-- docker-compose.prod.yml   # Prod: builds JARs inside Docker
|   |-- docker-compose.prod-pulled.yml
|
|-- frontend/
|   |-- apps/
|   |   |-- customer/             # Customer app (port 3000)
|   |   |-- seller/              # Seller app (port 3001)
|   |   |-- admin/               # Admin app (port 3002)
|   |   |-- Dockerfile.dev        # Dev: Vite HMR via volume mounts
|   |   |-- Dockerfile.prod       # Prod: nginx static
|   |-- shared/                  # Shared components (components, api/, lib/)
|   |-- docker compose.yml       # Standalone frontend (DEV, space in name!)
|   |-- docker-compose.prod.yml # Prod: nginx containers
|   |-- docker-compose.prod-pulled.yml
|   |-- docker-compose.yml       # PROD standalone
|
|-- nginx/                        # Reverse proxy configs
|-- .env                          # Environment variables
|-- .env.example                  # Template
|-- .github/workflows/            # CI/CD pipelines
```

### Compose File Combinations

| Command | Compose Files | Result |
|---------|--------------|--------|
| `dev` | `docker-compose.yml` + `docker-compose.dev.yml` | Full stack + `fs-stripe-listener` |
| `prod` | `docker-compose.yml` | Full stack (no `fs-stripe-listener`) |
| `be-dev` | `docker-compose.yml` + `docker-compose-backend.yml` | Backend + `fs-stripe-listener` |
| `infra-up` | `docker-compose.yml` + `docker-compose-infrastructure.yml` | Infrastructure only |

> **Important**: Frontend standalone compose file is `docker compose.yml` (with a **space**), located in `frontend/` directory.

### Backend Dev vs Prod Build Strategy

| Mode | Dockerfile | Build Location | Host Maven Needed |
|------|-----------|---------------|-------------------|
| **Dev** | `Dockerfile.dev` | Host machine (`mvn package`) | Yes |
| **Prod** | `Dockerfile.prod` | Inside Docker | No |

- **Dev workflow**: Run `.\flashsale-build.ps1 mvn-all` to build JARs on host → `Dockerfile.dev` copies JARs into slim JRE image → fast container starts
- **Prod workflow**: `docker-compose.prod.yml` triggers `Dockerfile.prod` → Maven runs inside Docker → final image is slim

---

## 8. Service Ports & URLs

### Backend Services

| Service | Container | Port | URL |
|---------|-----------|------|-----|
| API Gateway | `fs-gateway` | 8080 | http://localhost:8080 |
| Discovery (Eureka) | `fs-discovery` | 8761 | http://localhost:8761 |
| Identity | `fs-identity` | 8081 | http://localhost:8081 |
| Payment | `fs-payment` | 8082 | http://localhost:8082 |
| Order | `fs-order` | 8083 | http://localhost:8083 |
| Flash Sale | `fs-flashsale` | 8085 | http://localhost:8085 |
| Worker | `fs-worker` | 8086 | http://localhost:8086 |
| Product | `fs-product` | 8090 | http://localhost:8090 |
| Search | `fs-search` | 8091 | http://localhost:8091 |
| Notification | `fs-notification` | 8092 | http://localhost:8092 |

### Infrastructure

| Service | Container | Port |
|---------|-----------|------|
| PostgreSQL | `fs-postgres` | 5432 |
| MongoDB | `fs-mongo` | 27017 |
| Redis | `fs-redis` | 6379 |
| Kafka | `fs-kafka` | 9092 / 29092 |
| Zookeeper | `fs-zookeeper` | 2181 |
| Elasticsearch | `fs-elasticsearch` | 9200 |
| MinIO | `fs-minio` | 9000 / 9001 |
| Axon Server | `fs-axonserver` | 8024 (GUI) / 8124 (gRPC) |

### Frontend Apps

| App | Container | Port |
|-----|-----------|------|
| Customer | `fs-customer-fe` | 3000 |
| Seller | `fs-seller-fe` | 3001 |
| Admin | `fs-admin-fe` | 3002 |

### Reverse Proxy

| Service | Container | Port |
|---------|-----------|------|
| Nginx | `fs-reverse-proxy` | 80 |
| Stripe Listener | `fs-stripe-listener` | (dev mode only) |

### Quick Access Links

```
http://localhost:3000    Customer App
http://localhost:3001    Seller App
http://localhost:3002    Admin App
http://localhost:8080    API Gateway
http://localhost:8080/swagger-ui.html  Swagger UI
http://localhost:8761    Eureka Discovery
http://localhost:9001    MinIO Console
http://localhost:8024    Axon Server GUI
```

---

## 9. Container Names

| Category | Containers |
|----------|-----------|
| **Infrastructure** | `fs-postgres`, `fs-mongo`, `fs-redis`, `fs-elasticsearch`, `fs-minio`, `fs-kafka`, `fs-zookeeper`, `fs-axonserver` |
| **Backend** | `fs-discovery`, `fs-gateway`, `fs-identity`, `fs-payment`, `fs-order`, `fs-flashsale`, `fs-product`, `fs-search`, `fs-notification`, `fs-worker` |
| **Frontend** | `fs-customer-fe`, `fs-seller-fe`, `fs-admin-fe` |
| **Special** | `fs-reverse-proxy` (nginx), `fs-stripe-listener` (dev only) |

---

## 10. Database Connections

Connect from your host machine (outside Docker):

| Database | Host | Port | User | Password |
|----------|------|------|------|----------|
| PostgreSQL | localhost | 5432 | postgres | `POSTGRES_PASSWORD` from `.env` |
| MongoDB | localhost | 27017 | fs_mongo_admin | `MONGO_INITDB_ROOT_PASSWORD` from `.env` |
| Redis | localhost | 6379 | (none) | `REDIS_PASSWORD` from `.env` |
| Elasticsearch | localhost | 9200 | (none) | No authentication |
| MinIO | localhost | 9000 | `MINIO_ACCESS_KEY` from `.env` | `MINIO_SECRET_KEY` from `.env` |

---

## 11. Stripe Webhook Setup

### How It Works

| Environment | Mechanism | How to Configure |
|-------------|-----------|-------------------|
| **Local Dev** | Stripe CLI (`fs-stripe-listener`) inside Docker | Started automatically with `dev` command |
| **Production** | Stripe Dashboard sends events directly to your server | Set `STRIPE_WEBHOOK_SECRET_PROD` in production `.env` |

### Local Development

`dev` command automatically starts `fs-stripe-listener` which forwards Stripe webhook events to `payment-service`.

Get the webhook signing secret:

```powershell
docker logs fs-stripe-listener | Select-String whsec_
```

Update `.env` and restart payment-service:

```powershell
# Edit .env: STRIPE_WEBHOOK_SECRET=whsec_xxx
.\flashsale-build.ps1 restart payment    # apply env mới, không rebuild
# hoặc nếu muốn rebuild:
.\flashsale-build.ps1 reset payment
```

### Production

```powershell
# 1. Go to Stripe Dashboard > Developers > Webhooks
# 2. Click "Add endpoint"
# 3. Endpoint URL: https://your-domain.com/api/v1/stripe/webhooks
# 4. Select events: payment_intent.succeeded, payment_intent.payment_failed, charge.refunded, account.updated
# 5. Copy "Signing secret" (whsec_xxx)
# 6. Set in production .env: STRIPE_WEBHOOK_SECRET_PROD=whsec_xxx
```

Deploy:

```bash
IMAGE_PREFIX=your-username/flashsale \
docker-compose -f docker-compose.yml -f docker-compose.prod-pulled.yml up -d
```

---

## 12. Troubleshooting

### Containers Not Starting

```powershell
# Check status
.\flashsale-build.ps1 ps

# View logs
.\flashsale-build.ps1 logs gateway
.\flashsale-build.ps1 logs be

# Check specific container
docker logs fs-payment
```

### Port Already in Use

```powershell
netstat -ano | Select-String ":8080"
taskkill /PID <PID> /F
```

### Out of Disk Space

```powershell
docker system prune -a
docker volume prune
```

### Kafka Not Starting

Kafka needs ~30 seconds to initialize:

```powershell
docker logs fs-kafka
```

### Backend Won't Connect to Database

Check infrastructure health:

```powershell
docker ps --filter "name=fs-" --format "table {{.Names}}\t{{.Status}}"
```

### Maven Build Fails

```powershell
cd backend
mvn clean install -DskipTests
```

### Full Reset

```powershell
.\flashsale-build.ps1 clean
.\flashsale-build.ps1 mvn-all
.\flashsale-build.ps1 dev
```

---

## Test Verification

Tất cả lệnh trong `flashsale-build.ps1` đã được test trên Windows PowerShell 5.1:

| Lệnh | Status |
|------|--------|
| Syntax check (PowerShell parser) | ✅ Pass |
| `help` | ✅ Hiện help đầy đủ với lệnh mới |
| `ps` / `status` | ✅ List containers chạy |
| `mvn` (no target) | ✅ Error có usage hint |
| `npm` (no target) | ✅ Error có usage hint |
| `svc-build` (no target) | ✅ Error |
| `restart` (no target) | ✅ Error có usage hint |
| `reset` (no target) | ✅ Error có usage hint |
| `shell` (no target) | ✅ Error |
| `stop` (no target) | ✅ Error |
| `down` (no target) | ✅ Error (alias works) |
| `fe-down nonexistent` | ✅ Error với valid options |
| Unknown action | ✅ Error → suggest `help` |

Tự test trên máy bạn:
```powershell
.\flashsale-build.ps1 help          # show full help
.\flashsale-build.ps1 ps            # list containers
.\flashsale-build.ps1 unknown       # phải hiện error
.\flashsale-build.ps1 restart       # phải hiện usage error
```

---

## Related Documents

- [DEPLOY.md](DEPLOY.md) — Deployment guide (CD, server setup, GitHub Actions)
- [docs/deployment-architecture.md](docs/deployment-architecture.md) — Production architecture với ASCII diagrams
- [docs/spring-profiles-explained.md](docs/spring-profiles-explained.md) — Cách Spring profiles (`-dev`, `-prod`) hoạt động
- [docs/deployment-port-8080-fix.md](docs/deployment-port-8080-fix.md) — Lịch sử fix bug port 8080
- [docs/00_INDEX.md](docs/00_INDEX.md) — Documentation index
- [docs/01_OVERVIEW.md](docs/01_OVERVIEW.md) — Project architecture
- [docs/02_API.md](docs/02_API.md) — API specification
