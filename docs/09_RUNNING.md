# Flash Sale Platform — Running Guide

**Project**: stealing-from-paradise  
**Date**: 2026-04-23  
**Version**: v1  
**Language**: English

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Environment Setup](#environment-setup)
3. [Quick Start](#quick-start)
4. [Docker Compose File Architecture](#docker-compose-file-architecture)
5. [Build Strategy (Dev vs Prod)](#build-strategy-dev-vs-prod)
6. [Script Reference](#script-reference)
7. [Running Modes](#running-modes)
8. [Container Names](#container-names)
9. [Service Ports](#service-ports)
10. [Database Connections](#database-connections)
11. [Stripe Webhook Setup](#stripe-webhook-setup)
12. [Troubleshooting](#troubleshooting)
13. [CI/CD Pipelines](#cicd-pipelines)
14. [Quick Reference Cheatsheet](#quick-reference-cheatsheet)

---

## Prerequisites

| Tool | Version | Required For | Notes |
|------|---------|-------------|-------|
| **Docker** | Latest | All | Docker Desktop on Windows. Must be running. |
| **Maven** | 3.8+ | Backend build | For `Dockerfile.dev` (JARs pre-built on host) |
| **Java** | 25 | Local backend dev | Optional — can run all services in Docker |
| **Node.js** | 18+ | Local frontend dev | Optional — frontend builds inside Docker |

> **Note**: This project uses `docker compose` (v2, space-separated). Not `docker-compose` (v1, hyphen).

---

## Environment Setup

### 1. Verify `.env` exists

```powershell
# From the project root:
ls .env
```

If missing, copy from the example:

```powershell
cp .env.example .env
```

### 2. Fill in secrets

Open `.env` and fill in:

```
STRIPE_SECRET_KEY=sk_test_...        # Stripe dashboard > Developers > API keys
STRIPE_PUBLISHABLE_KEY=pk_test_...  # Stripe dashboard > Developers > API keys
STRIPE_WEBHOOK_SECRET=whsec_...     # From: stripe listen (local dev)
STRIPE_WEBHOOK_SECRET_PROD=whsec_... # From: Stripe Dashboard > Webhooks (production)
```

### 3. Start Docker Desktop

Make sure Docker Desktop is running before using the script.

---

## Quick Start

### Full Stack (Dev Mode — Recommended)

> **Important**: Open a **new terminal** first to run the Stripe webhook listener (see [Stripe Webhook Setup](#stripe-webhook-setup)).

```powershell
# Terminal 1: Start Stripe webhook listener BEFORE starting the stack
.\stripe-webhook.ps1 -Mode Start

# Terminal 0: Build backend JARs on your machine (one-time or after code changes)
.\flashsale-build.ps1 -Build

# Terminal 0: Start everything (infrastructure + backend + frontend)
.\flashsale-build.ps1 -Up -All -D
```

This starts:
- Infrastructure: Postgres, MongoDB, Redis, Elasticsearch, MinIO, Kafka, Zookeeper, AxonServer
- Backend: 10 microservices (pre-built JARs from host)
- Frontend: 3 apps in dev mode (Vite hot-reload via volume mounts)

Wait ~60 seconds, then access:
- Customer App: http://localhost:3000
- Seller App: http://localhost:3001
- Admin App: http://localhost:3002
- API Gateway: http://localhost:8080
- Eureka: http://localhost:8761

### Infrastructure Only (Run Backend Locally)

```powershell
# Start only databases and queues
.\flashsale-build.ps1 -Up -Infra -D

# Then run backend locally in your IDE:
cd backend
mvn spring-boot:run -pl identity-service
```

### Frontend Only (Mock Data)

```powershell
# Frontend runs with mock data — no backend needed
.\flashsale-build.ps1 -Up -Frontend -D
```

To connect frontend to a real backend:

```powershell
$env:VITE_API_URL = "http://localhost:8080/api/v1"
$env:VITE_BACKEND_MODE = "real"
.\flashsale-build.ps1 -Up -Frontend -D
```

### Stop Everything

```powershell
.\flashsale-build.ps1 -Down -All
```

---

## Docker Compose File Architecture

```
project-root/
│
├── docker-compose.yml                   # Full stack (all services, reads ../.env)
├── docker-compose-infrastructure.yml    # Infrastructure only (root level)
├── docker-compose-backend.yml           # Backend services only (root level)
│
├── backend/
│   ├── docker-compose.yml               # Standalone: infra + backend (dev mode)
│   ├── docker-compose.infra-only.yml    # Standalone: infra only
│   ├── docker-compose.prod.yml          # Prod override: builds JARs inside Docker
│   └── docker-compose.prod-pulled.yml   # Prod override: pulls from GHCR
│
└── frontend/
    ├── docker compose.yml               # Standalone: 3 apps (DEV, space in filename!)
    ├── docker-compose.prod.yml         # Prod override: nginx containers
    └── docker-compose.prod-pulled.yml  # Prod override: pulls from GHCR
```

### How Compose Files Work Together

| Command | Compose Files | Result |
|---------|-------------|--------|
| `.\flashsale-build.ps1 -Up -All` | `docker-compose.yml` + `docker-compose-infrastructure.yml` + `docker-compose-backend.yml` + `docker compose.yml` | Full stack, detached |
| `.\flashsale-build.ps1 -Up -Infra` | `docker-compose-infrastructure.yml` | Only databases/queues |
| `.\flashsale-build.ps1 -Up -Backend` | `docker-compose-backend.yml` | Only backend services |
| `.\flashsale-build.ps1 -Up -Frontend` | `docker compose.yml` (in `frontend/`) | Only frontend apps |

### Direct Docker Commands (without the script)

```powershell
# Full stack — run from project root
docker compose -f docker-compose.yml up --build -d

# Frontend standalone — run from frontend/ directory
cd frontend
docker compose -f "docker compose.yml" up --build -d
```

> **Important**: Frontend compose file has a **space** in its name: `docker compose.yml` (not `docker-compose.yml`).

---

## Build Strategy (Dev vs Prod)

### Backend

| Mode | Dockerfile | Build Location | Needs Host Maven? | Speed |
|------|-----------|---------------|-------------------|-------|
| **Dev** | `Dockerfile.dev` | Host machine (`mvn package`) | Yes | Fast container starts |
| **Prod** | `Dockerfile.prod` | Inside Docker container | No | Slow first build |

**Dev workflow** (default):
1. You run `mvn package` on your host → JARs in `backend/<service>/target/`
2. `Dockerfile.dev` copies those JARs into a slim JRE container
3. Container start is fast (no Maven inside Docker)

**Prod workflow** (when using `docker-compose.prod.yml`):
1. `docker compose up --build` triggers `Dockerfile.prod`
2. Maven runs inside Docker
3. Final image is slim (JRE + JAR only)

### Frontend

| Mode | Dockerfile | Hot Reload? | Build Location |
|------|-----------|-------------|---------------|
| **Dev** | `Dockerfile.dev` | Yes (via volume mounts) | Inside Docker |
| **Prod** | `Dockerfile.prod` | No (nginx static) | Inside Docker |

**Dev workflow**: Source code is mounted from host into container via volumes. Vite HMR runs inside Docker.

---

## Script Reference

Run from the **project root**: `.\flashsale-build.ps1 [options]`

### Actions (pick one)

| Flag | Description |
|------|-------------|
| `-Up` | Start services (combine with targets below) |
| `-Down` | Stop and remove containers |
| `-Build` | Maven build backend JARs (no Docker) |
| `-BuildFrontend` | Build frontend apps |
| `-Stop` | Stop containers (no removal, keeps data) |
| `-Clean` | Nuclear clean: containers + volumes + images |
| `-Status` | Show container status |
| `-Logs` | Tail all logs (Ctrl+C to stop) |
| `-Ports` | Show exposed ports |
| `-Health` | Check service health |
| `-Help` | Show full help message |
| `-Menu` | Interactive menu |
| `-Restart <svc>` | Restart a specific container |
| `-Tail <svc>` | Tail logs from a specific container |
| `-Exec <svc> <cmd>` | Run command inside a container |

### Targets (composable, default = all)

| Flag | Description |
|------|-------------|
| `-All` | Everything (infra + backend + frontend) |
| `-Infra` | Infrastructure only (databases, queues) |
| `-Backend` | Backend microservices only |
| `-Frontend` | Frontend apps only |

Combine targets: `-Up -Infra -Backend` starts infra + backend.

### Up Options

| Flag | Description |
|------|-------------|
| `-D` or `-Detach` | Background mode (docker compose `-d`) |
| `-SkipBuild` | Skip Maven/NPM build step |
| `-MavenParallel` | Maven `-T 2C` (parallel threads) |
| `-FrontendProd` | Frontend as nginx production containers |

### Down / Clean Options

| Flag | Description |
|------|-------------|
| `-V` or `-RemoveVolumes` | Remove named volumes (**DATA LOSS**) |
| `-Rmi` or `-RemoveImages` | Remove service images |
| `-RemoveOrphans` | Remove orphaned containers |

### Restart / Tail / Exec

```powershell
# Restart a container
.\flashsale-build.ps1 -Restart payment        # auto-prefixes fs- → fs-payment

# Tail logs from a container (last 50 lines)
.\flashsale-build.ps1 -Tail gateway -Lines 50

# Run command inside container
.\flashsale-build.ps1 -Exec postgres psql -U postgres -d flashsale_platform
.\flashsale-build.ps1 -Exec redis redis-cli -a redis123
.\flashsale-build.ps1 -Exec gateway sh
```

---

## Running Modes

### Dev Mode (Default)

```powershell
# Full stack, dev mode
.\flashsale-build.ps1 -Up -All -D

# Backend in Docker, frontend in Docker with HMR
```

### Production Mode (Nginx)

```powershell
# Frontend as nginx containers (no HMR)
.\flashsale-build.ps1 -Up -All -D -FrontendProd
```

### Backend Standalone (from `backend/` directory)

```powershell
cd backend

# Dev mode (needs JARs built on host first)
docker compose -f docker-compose.yml up --build -d

# Prod mode (builds inside Docker, no host Maven needed)
docker compose -f docker-compose.yml -f docker-compose.prod.yml up --build -d

# Infra only (databases + queues)
docker compose -f docker-compose.yml -f docker-compose.infra-only.yml up --build -d
```

### Frontend Standalone (from `frontend/` directory)

```powershell
cd frontend

# Dev mode (HMR, mock data by default)
docker compose -f "docker compose.yml" up --build -d

# Prod mode (nginx)
docker compose -f "docker compose.yml" -f docker-compose.prod.yml up --build -d
```

---

## Container Names

### Infrastructure

| Container | Service | Ports |
|-----------|---------|-------|
| `fs-postgres` | PostgreSQL | 5432 |
| `fs-mongo` | MongoDB | 27017 |
| `fs-redis` | Redis | 6379 |
| `fs-elasticsearch` | Elasticsearch | 9200 |
| `fs-minio` | MinIO (S3) | 9000, 9001 |
| `fs-kafka` | Kafka | 9092, 29092 |
| `fs-zookeeper` | Zookeeper | 2181 |
| `fs-axonserver` | AxonServer | 8024, 8124 |

### Backend Services

| Container | Service | Port |
|-----------|---------|------|
| `fs-discovery` | Eureka Discovery | 8761 |
| `fs-gateway` | API Gateway | 8080 |
| `fs-identity` | Identity Service | 8081 |
| `fs-payment` | Payment Service | 8082 |
| `fs-order` | Order Service | 8083 |
| `fs-flashsale` | Flash Sale Service | 8085 |
| `fs-product` | Product Service | 8090 |
| `fs-search` | Search Service | 8091 |
| `fs-notification` | Notification Service | 8092 |
| `fs-worker` | Worker Service | 8086 |

### Frontend Apps

| Container | App | Port |
|-----------|-----|------|
| `fs-customer-fe` | Customer App | 3000 |
| `fs-seller-fe` | Seller App | 3001 |
| `fs-admin-fe` | Admin App | 3002 |

---

## Service Ports

| Port | Service | Notes |
|------|---------|-------|
| 3000 | Customer App | React app |
| 3001 | Seller App | React app |
| 3002 | Admin App | React app |
| 8080 | API Gateway | Swagger UI at `/swagger-ui.html` |
| 8081 | Identity Service | User auth, JWT |
| 8082 | Payment Service | Stripe integration |
| 8083 | Order Service | Order management |
| 8085 | Flash Sale Service | Promotions |
| 8086 | Worker Service | Scheduled jobs |
| 8090 | Product Service | Product catalog |
| 8091 | Search Service | Elasticsearch |
| 8092 | Notification Service | Email/SMS |
| 8761 | Eureka Discovery | Service registry |
| 5432 | PostgreSQL | Main SQL database |
| 27017 | MongoDB | NoSQL database |
| 6379 | Redis | Cache |
| 9092 | Kafka | Message queue (external) |
| 29092 | Kafka | Message queue (internal) |
| 2181 | Zookeeper | Kafka coordination |
| 9200 | Elasticsearch | Search engine |
| 9000 | MinIO | Object storage (S3-compatible) |
| 9001 | MinIO Console | MinIO admin UI |
| 8024 | Axon Server | GUI |
| 8124 | Axon Server | gRPC |

---

## Database Connections

Connect from your host machine (outside Docker):

| Database | Host | Port | User | Password |
|----------|------|------|------|----------|
| PostgreSQL | localhost | 5432 | postgres | (from `.env` POSTGRES_PASSWORD) |
| MongoDB | localhost | 27017 | fs_mongo_admin | (from `.env` MONGO_INITDB_ROOT_PASSWORD) |
| Redis | localhost | 6379 | — | (from `.env` REDIS_PASSWORD) |
| Elasticsearch | localhost | 9200 | — | No auth |
| MinIO | localhost | 9000 | fs_storage_admin | (from `.env` MINIO_ACCESS/SECRET_KEY) |

---

## Stripe Webhook Setup

> **Important**: `flashsale-build.ps1` only starts the stack. Stripe webhook requires a **separate terminal** running `stripe-webhook.ps1`.

### Step 0 — Open a New Terminal FIRST

Before running the stack, open a **new terminal** to run the Stripe webhook listener. This runs alongside the stack.

```powershell
# Terminal 1: Run this BEFORE starting the stack, or in a separate terminal
.\stripe-webhook.ps1 -Mode Start
```

### Step 1 — Start the Stack

```powershell
# Terminal 0 (or after starting stripe-webhook): Start the platform
.\flashsale-build.ps1 -Up -All -D
```

### Step 2 — Configure Webhook Secret

After Stripe CLI starts, it prints the signing secret:

```
Ready! Your webhook signing secret is whsec_xxx
```

Copy it to `.env`:

```
STRIPE_WEBHOOK_SECRET=whsec_xxx
```

Then restart payment-service:

```powershell
docker restart fs-payment
```

### Step 3 — Trigger Test Events

```powershell
# From a new terminal, trigger Stripe test events:
stripe trigger payment_intent.succeeded
stripe trigger charge.refunded
```

### Production (Real Server)

> No Stripe CLI needed on production. Stripe Dashboard sends events directly to your server.

```powershell
# 1. Go to Stripe Dashboard > Developers > Webhooks
# 2. Click "Add endpoint"
# 3. Endpoint URL: https://your-domain.com/api/v1/stripe/webhooks
# 4. Select events:
#      - payment_intent.succeeded
#      - payment_intent.payment_failed
#      - charge.refunded
#      - account.updated
# 5. Copy the "Signing secret" (whsec_xxx)
# 6. Set in production .env:
#      STRIPE_WEBHOOK_SECRET_PROD=whsec_xxx

# Or show the full guide:
.\stripe-webhook.ps1 -Mode ProdGuide
```

---

## Troubleshooting

### Frontend "[FAIL]" — but containers are running

This is a false positive. When `docker compose up --build` runs in foreground mode, it exits after containers start (or on Ctrl+C), causing PowerShell to record a non-zero exit code.

**Check if containers are actually running:**

```powershell
docker ps --filter "name=fs-"
```

If containers are `Up`, the startup succeeded. Use `-D` (detach) to avoid this.

### Port Already in Use

```powershell
# Find what's using a port
netstat -ano | Select-String ":8080"
# Kill by PID:
taskkill /PID <PID> /F
```

### Out of Disk Space

```powershell
docker system prune -a
docker volume prune
```

### Kafka Not Starting

Kafka needs ~30 seconds to initialize. Check logs:

```powershell
docker compose -f docker-compose.yml logs kafka
```

### Backend Won't Connect to Database

Ensure infrastructure is healthy first:

```powershell
.\flashsale-build.ps1 -Health
```

### Maven Build Fails

```powershell
cd backend
mvn clean install -DskipTests -X   # verbose output
```

### Reset Everything

```powershell
.\flashsale-build.ps1 -Clean -V -Rmi
.\flashsale-build.ps1 -Build
.\flashsale-build.ps1 -Up -All -D
```

---

## CI/CD Pipelines

### ci.yml — PR & Develop Branch

Runs on every push to `develop` and every pull request.

```
Trigger: push to develop | pull_request to main/develop
Jobs:
  backend-lint-and-build  →  backend-tests
  frontend-lint-and-build  →  docker-build-check + docker-build-frontend-check
  security-checks
  ci-summary (comment PR on success)
```

### preview-ci.yml — Preview Branch

Builds all Docker images and auto-merges to `main` on success.

```
Trigger: push to preview
Jobs:
  build-backend-artifacts     →  Maven package, upload JAR artifacts
  build-frontend-artifacts    →  npm ci, upload node_modules (x3 apps)
         ↓ both complete
  build-dev (13 parallel)     →  Build all Dockerfile.dev, push :dev-sha + :dev-latest
         ↓ all pass
  build-prod (13 parallel)   →  Build all Dockerfile.prod, push :prod-sha + :prod-latest
         ↓ all pass
  auto-pr-to-main            →  git merge preview→main + create PR
```

### deploy.yml — Deploy to Server

Runs on push to `main` and `develop`. Builds on the target server via SSH.

```
Trigger: push to main/develop | workflow_dispatch
Jobs:
  quick-validation   →  Verify files exist
  deploy-on-server  →  SSH → git pull → mvn → docker build → docker compose up
  notify           →  Report success/failure
```

---

## Quick Reference Cheatsheet

```powershell
# ============================================================
# STRIPE WEBHOOK — RUN FIRST (dev only, separate terminal)
# ============================================================
.\stripe-webhook.ps1 -Mode Start              # Start Stripe CLI listener
.\stripe-webhook.ps1 -Mode ProdGuide         # Show prod webhook setup

# ============================================================
# START
# ============================================================
.\flashsale-build.ps1 -Up -All -D              # Full stack, detached
.\flashsale-build.ps1 -Up -Infra -D            # Infrastructure only
.\flashsale-build.ps1 -Up -Backend -D          # Backend only
.\flashsale-build.ps1 -Up -Frontend -D        # Frontend only (mock)

# REBUILD
.\flashsale-build.ps1 -Build                    # Maven build
.\flashsale-build.ps1 -Build -MavenParallel    # Parallel Maven build
.\flashsale-build.ps1 -Up -All -SkipBuild -D  # Restart without rebuilding

# STOP
.\flashsale-build.ps1 -Stop                    # Stop (keep data)
.\flashsale-build.ps1 -Down                    # Remove containers
.\flashsale-build.ps1 -Down -V               # Remove containers + volumes

# CLEAN
.\flashsale-build.ps1 -Clean -V -Rmi          # Nuclear: containers + volumes + images

# INFO
.\flashsale-build.ps1 -Status                  # Container status
.\flashsale-build.ps1 -Logs                   # Tail all logs
.\flashsale-build.ps1 -Ports                   # Exposed ports
.\flashsale-build.ps1 -Health                  # Service health
.\flashsale-build.ps1 -Help                   # Full help

# CONTAINER OPS
.\flashsale-build.ps1 -Restart payment         # Restart fs-payment
.\flashsale-build.ps1 -Tail gateway -Lines 50 # Tail fs-gateway logs
.\flashsale-build.ps1 -Exec postgres psql -U postgres -d flashsale_platform

# INTERACTIVE
.\flashsale-build.ps1 -Menu                   # Interactive menu

# STANDALONE (from subdirectory)
cd backend
docker compose -f docker-compose.yml up --build -d
docker compose -f docker-compose.yml -f docker-compose.infra-only.yml up --build -d
cd ../frontend
docker compose -f "docker compose.yml" up --build -d
```

---

## Related Documents

- [00_INDEX.md](00_INDEX.md) — Documentation index
- [01_OVERVIEW.md](01_OVERVIEW.md) — Project architecture & setup
- [02_API.md](02_API.md) — API specification
- [06_PAYMENT_SAGA_FLOW.md](06_PAYMENT_SAGA_FLOW.md) — Payment flow & Stripe integration

---

**Last Updated**: 2026-04-23
**Status**: Production-Ready
