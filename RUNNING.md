# Flash Sale Platform — Running Guide

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Quick Start](#quick-start)
3. [Dev vs Prod Build Strategy](#dev-vs-prod-build-strategy)
4. [Script Flags Reference](#script-flags-reference)
5. [Running Modes](#running-modes)
6. [Common Workflows](#common-workflows)
7. [Running Standalone (Per Module)](#running-standalone-per-module)
8. [Docker Compose Files](#docker-compose-files)
9. [Health Checks](#health-checks)
10. [Service Ports](#service-ports)
11. [CI/CD Pipelines](#cicd-pipelines)
12. [Development Workflows](#development-workflows)
13. [Troubleshooting](#troubleshooting)
14. [File Structure](#file-structure)

---

## Prerequisites

- **Docker** (with Docker Compose v2 — `docker compose`, not `docker-compose`)
- **Java 17+** (for local backend development)
- **Maven 3.8+** (for backend build)
- **Node.js 20+** (for frontend local development)
- **pnpm** (for frontend build)

---

## Quick Start

### 1. Configure Environment

Copy the example env file if you haven't:

```powershell
cp .env.example .env   # or just use the existing .env
```

> **Important:** Fill in real secrets in `.env` before any production deployment. See the `.env` file comments for each section.

### 2. Build Backend JARs (One-Time or After Code Changes)

Before starting the stack, build all JARs on your host machine:

```powershell
.\flashsale-build.ps1 -Build
```

> **Why?** `Dockerfile.dev` copies pre-built JARs from the host's `backend/<service>/target/` directory into containers. Maven runs on your machine, not inside Docker.

### 3. Start Full Stack

```powershell
# From the project root:
.\flashsale-build.ps1 -All -Detach
```

This will:
1. Start infrastructure (Postgres, MongoDB, Redis, Kafka, Elasticsearch, MinIO, AxonServer)
2. Start backend microservices (all 10 services) — JARs already built on host
3. Start frontend apps (customer, seller, admin) in **dev mode** (Vite HMR)

---

## Dev vs Prod Build Strategy

### Backend

| Mode | Dockerfile | Build Location | Requires Host Maven? | Speed |
|------|-----------|----------------|---------------------|-------|
| **Dev** | `Dockerfile.dev` | Host machine (`mvn package`) | Yes | Fast container starts |
| **Prod** | `Dockerfile.prod` | Inside Docker container | No | Slow first build |

**Dev workflow:**
1. You run `mvn package` on your host → JARs land in `backend/<service>/target/`
2. `Dockerfile.dev` copies those JARs into a slim JRE container
3. Container start is fast (no compilation inside Docker)

**Prod workflow:**
1. `docker compose up --build` triggers `Dockerfile.prod`
2. Maven runs inside Docker, compiling source from context
3. Final image is slim (only JRE + JAR), no build tools

### Frontend

| Mode | Dockerfile | Build Location | Requires Host Node? | Hot Reload? |
|------|-----------|----------------|---------------------|-------------|
| **Dev** | `Dockerfile` | Inside Docker container | No | Yes (volume mounts) |
| **Prod** | `Dockerfile.prod` | Inside Docker container | No | No (nginx static) |

**Dev workflow:** Source code is mounted from host into the container via volumes. Vite dev server runs inside Docker, changes auto-reload.

**Prod workflow:** Multi-stage build compiles the app inside Docker, serves via nginx.

---

## Script Flags Reference

Run from the project root: `.\flashsale-build.ps1 [options]`

### Modes (pick one)

| Flag | Description |
|------|-------------|
| `-All` | Start everything (infra + backend + frontend) |
| `-Infra` | Start infrastructure only (databases, queues) |
| `-Backend` | Start backend services only |
| `-Frontend` | Start frontend apps only |
| `-Build` | Maven build only (no Docker) |
| `-BuildBackend` | Maven build backend only |
| `-BuildFrontend` | pnpm build frontend only |
| `-Up` | Alias for `-All` (default — runs if no mode is specified) |

### Up Options

| Flag | Description |
|------|-------------|
| `-Detach` or `-D` | Run in background (docker compose detached mode) |
| `-Watch` | Run in foreground (default — logs stream to terminal) |
| `-SkipBuild` | Skip Maven/pnpm build, just restart containers |
| `-MavenParallel` | Run Maven with parallel threads (`-T 2C`) |
| `-FrontendProd` | Build frontend as production nginx containers (no HMR) |

### Down Options

| Flag | Description |
|------|-------------|
| `-Down` | Stop and remove containers |
| `-Down -V` | Also remove named volumes (**DATA LOSS!**) |
| `-Down -Rmi` | Also remove service images |
| `-Down -RemoveOrphans` | Remove orphaned containers |
| `-Down -V -Rmi` | Full cleanup: containers + volumes + images |
| `-Remove` | Alias for `-Down` (containers only) |

### Cleanup

| Flag | Description |
|------|-------------|
| `-Clean` | Remove ALL containers, volumes, images |
| `-Stop` | Stop all running containers (no removal) |
| `-CleanImages` | Remove all unused images |
| `-CleanVolumes` | Remove all dangling volumes |

### Info

| Flag | Description |
|------|-------------|
| `-Status` | Show container status |
| `-Logs` | Tail logs (Ctrl+C to stop) |
| `-Ports` | Show exposed ports |
| `-Health` | Check service health |
| `-Help` | Show full help |

---

## Running Modes

### Full Stack — Dev Mode (Default)

```powershell
.\flashsale-build.ps1 -All -D
```

- Backend: runs in Docker
- Frontend: runs in Docker with **Vite hot-reload**
- Uses `docker compose.yml` + override automatically

### Full Stack — Foreground (Watch Logs)

```powershell
.\flashsale-build.ps1 -All -Watch
```

- Everything starts in foreground
- Logs stream to terminal (Ctrl+C to stop)

### Infrastructure Only

```powershell
.\flashsale-build.ps1 -Infra -D
```

Start databases and message queues without backend or frontend.

Use this when you want to run backend services **locally** (outside Docker) while using Docker for databases.

### Backend Only

```powershell
.\flashsale-build.ps1 -Backend -D
```

Start backend microservices (requires infrastructure to be running).

### Frontend Only (No Backend)

```powershell
.\flashsale-build.ps1 -Frontend -D
```

Apps run with **mock data** by default — no backend required. Perfect for UI development.

To connect to a real backend instead:

```powershell
$env:VITE_API_URL="http://localhost:8080/api/v1"
$env:VITE_BACKEND_MODE="real"
.\flashsale-build.ps1 -Frontend -D
```

### Production Mode (No Hot Reload)

```powershell
.\flashsale-build.ps1 -All -D -FrontendProd
```

Builds frontend as **nginx containers** (production-ready static assets).

For **full production mode** (backend + frontend both built inside Docker, no host Java/Maven needed):

```powershell
# Option A: Use compose overrides directly
docker compose -f docker compose.yml -f frontend/docker-compose.prod.yml -f backend/docker-compose.prod.yml up --build -d

# Option B: Backend standalone in prod mode
cd backend
docker compose -f docker compose.yml -f docker-compose.prod.yml up --build -d
```

---

## Common Workflows

### Clean Rebuild (Dev)

```powershell
# 1. Full clean — removes all containers, images, and volumes
.\flashsale-build.ps1 -Clean

# 2. Build JARs on host
.\flashsale-build.ps1 -Build

# 3. Start everything
.\flashsale-build.ps1 -All -D
```

Or combined:

```powershell
.\flashsale-build.ps1 -All -Clean -D
```

> **Note:** `-Clean` also removes volumes (data loss). The build step ensures JARs exist for `Dockerfile.dev` to copy.

### Restart Backend Only (After Code Change)

```powershell
# 1. Rebuild JARs on host
.\flashsale-build.ps1 -Build

# 2. Restart containers (JARs are copied from host's target/ dirs)
.\flashsale-build.ps1 -All -SkipBuild -D
```

Or rebuild a specific service via Maven, then restart just that container:

```powershell
cd backend
mvn package -DskipTests -pl identity-service -am
docker compose -f "docker compose.yml" up -d identity-service
```

### Full Maven Build (No Docker)

```powershell
.\flashsale-build.ps1 -Build
```

With parallel threads:

```powershell
.\flashsale-build.ps1 -Build -MavenParallel
```

### Stop Everything

```powershell
.\flashsale-build.ps1 -Stop
```

### Stop + Remove Containers

```powershell
.\flashsale-build.ps1 -Down
```

### Stop + Remove Containers + Volumes (DATA LOSS)

```powershell
.\flashsale-build.ps1 -Down -V
```

### Remove Orphaned Containers

```powershell
.\flashsale-build.ps1 -Down -RemoveOrphans
```

### View Logs

```powershell
# All services (interactive)
.\flashsale-build.ps1 -Logs

# All services (static)
docker compose -f "docker compose.yml" logs -f

# Specific service
docker compose -f "docker compose.yml" logs -f api-gateway
docker compose -f "docker compose.yml" logs -f identity-service
```

### Check Status

```powershell
.\flashsale-build.ps1 -Status
```

### Check Ports

```powershell
.\flashsale-build.ps1 -Ports
```

### Cleanup Unused Images

```powershell
.\flashsale-build.ps1 -CleanImages
```

### Cleanup Dangling Volumes

```powershell
.\flashsale-build.ps1 -CleanVolumes
```

---

## Running Standalone (Per Module)

### Backend Standalone

```powershell
cd backend
docker compose -f docker compose.yml up --build -d
```

### Backend + Infrastructure Only

```powershell
cd backend
docker compose -f docker compose.yml -f docker compose.infra-only.yml up --build -d
```

### Frontend Standalone (Dev Mode)

```powershell
cd frontend
docker compose -f docker compose.yml up --build -d
```

### Frontend Standalone (Production)

```powershell
cd frontend
docker compose -f docker compose.yml -f docker compose.prod.yml up --build -d
```

---

## Docker Compose Files

The project has these compose files (note: `docker compose`, not `docker-compose`):

| File | Purpose |
|------|---------|
| `docker compose.yml` | Full stack (infra + backend + frontend) |
| `docker compose.yml` + override | Auto-merged dev overrides (Vite HMR) |
| `docker compose-infrastructure.yml` | Infrastructure only |
| `docker compose-backend.yml` | Backend services only (dev mode) |
| `backend/docker-compose.prod.yml` | Backend production override (builds JARs inside Docker) |

```powershell
# Dev mode — JARs pre-built on host (fast)
docker compose up --build -d

# Dev mode, explicit
docker compose -f docker compose.yml up --build -d

# Production mode — frontend as nginx, backend builds inside Docker
docker compose -f docker compose.yml -f frontend/docker-compose.prod.yml up --build -d

# Backend standalone, dev mode (requires JARs built on host)
cd backend
docker compose -f docker compose.yml up --build -d

# Backend standalone, prod mode (builds inside Docker)
cd backend
docker compose -f docker compose.yml -f docker-compose.prod.yml up --build -d

# Infrastructure only
docker compose -f docker compose-infrastructure.yml up --build -d
```

---

## Health Checks

### Script

```powershell
.\flashsale-build.ps1 -Health
```

### Manual

```powershell
curl http://localhost:8080/actuator/health     # API Gateway
curl http://localhost:8761/actuator/health     # Eureka Discovery
curl http://localhost:9200                     # Elasticsearch
curl http://localhost:9000/minio/health/live   # MinIO
```

---

## Service Ports

| Port | Service |
|------|---------|
| 3000 | Customer App |
| 3001 | Seller App |
| 3002 | Admin App |
| 8080 | API Gateway / Swagger UI |
| 8761 | Eureka Discovery |
| 8081 | Identity Service |
| 8082 | Payment Service |
| 8083 | Order Service |
| 8085 | Flash Sale Service |
| 8086 | Worker Service |
| 8090 | Product Service |
| 8091 | Search Service |
| 8092 | Notification Service |
| 5432 | PostgreSQL |
| 27017 | MongoDB |
| 6379 | Redis |
| 9092 | Kafka |
| 9200 | Elasticsearch |
| 9000 | MinIO |
| 9001 | MinIO Console |
| 8024 | AxonServer GUI |
| 8124 | AxonServer gRPC |

---

## CI/CD Pipelines

The project uses GitHub Actions workflows defined in `.github/workflows/`.

### ci.yml — PR & Develop Branch

Runs on every push to `develop` and every pull request to `main`/`develop`.

```
Trigger: push to develop | pull_request to main/develop
Jobs:
  backend-lint-and-build  →  backend-tests
  frontend-lint-and-build  →  docker-build-check + docker-build-frontend-check
  security-checks
  ci-summary (comment PR on success)
```

### preview-ci.yml — Preview Branch

Runs on every push to the `preview` branch. Automatically builds all Docker images and merges to `main` on success.

```
Trigger: push to preview
Jobs:
  build-backend-artifacts     →  Maven package, upload JAR artifacts
  build-frontend-artifacts    →  npm ci, upload node_modules (x3 apps)
         ↓ both complete
  build-dev (13 parallel)     →  Build all Dockerfile.dev, push :dev-sha + :dev-latest
         ↓ all pass
  build-prod (13 parallel)    →  Build all Dockerfile.prod, push :prod-sha + :prod-latest
         ↓ all pass
  auto-pr-to-main             →  git merge preview→main + create PR
```

> **Note:** Backend Dockerfile.dev copies pre-built JARs from `target/`, so `build-backend-artifacts` must run first. Frontend Dockerfile.dev installs deps inside the container, but `build-frontend-artifacts` speeds up the process. Dockerfile.prod uses multi-stage builds (Maven/npm run inside Docker), so no pre-built artifacts are needed.

### deploy.yml — Deploy to Server

Runs on push to `main` and `develop`. Builds everything on the target server via SSH.

```
Trigger: push to main/develop | workflow_dispatch
Jobs:
  quick-validation   →  Verify files exist
  deploy-on-server   →  SSH → git pull → mvn → docker build → docker compose up
  notify            →  Report success/failure
```

---

## Development Workflows

### Frontend Development (Hot Reload)

```powershell
# Start full stack in dev mode (frontend runs in Docker with volume mounts)
.\flashsale-build.ps1 -All -D

# Edit code in frontend/apps/customer/src
# Changes auto-reload via Vite HMR
```

Frontend dev images use **Vite dev server with volume mounts** — no build step needed inside Docker. Source code is mounted from host into the container.

### Backend Development (Local Java + Docker Infra)

```powershell
# 1. Start infrastructure in Docker
.\flashsale-build.ps1 -Infra -D

# 2. Wait for services to be healthy (~30s)

# 3. Run backend locally in your IDE
#    or via Maven:
cd backend
mvn spring-boot:run -pl identity-service -Dspring-boot.run.profiles=local
```

### Database Connections (From Host)

| Database | Host | Port | User | Password |
|----------|------|------|------|----------|
| PostgreSQL | localhost | 5432 | postgres | postgres123! |
| MongoDB | localhost | 27017 | fs_mongo_admin | S3cr3t_M0ng0_P@ssw0rd!2024 |
| Redis | localhost | 6379 | — | redis123 |
| Elasticsearch | localhost | 9200 | — | — |
| MinIO | localhost | 9000 | fs_storage_admin | S3cr3t_M1n1o_P@ssw0rd!2024 |

---

## Troubleshooting

### Frontend "[FAIL] Failed to start frontend" — but containers are running

This is a **false positive** in `flashsale-build.ps1`. When `docker compose up --build` runs in foreground mode, it exits after containers start (or when Ctrl+C is pressed), causing PowerShell to record a non-zero `LASTEXITCODE`.

**Check if containers are actually running:**

```powershell
docker ps --filter "name=fe-"
```

If containers are `Up`, the startup succeeded. Use `-Detach` to avoid this:

```powershell
.\flashsale-build.ps1 -Frontend -Detach
```

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

```powershell
# Kafka needs time to initialize. Wait ~30s and check:
docker compose -f "docker compose.yml" logs kafka
```

### Backend Won't Connect to Database

Ensure infrastructure is healthy first:

```powershell
docker compose -f "docker compose.yml" ps   # All infra should be healthy
```

### Maven Build Fails

```powershell
cd backend
mvn clean install -DskipTests -X   # verbose output
```

### Reset Everything

```powershell
.\flashsale-build.ps1 -Clean
.\flashsale-build.ps1 -All -D
```

---

## File Structure

```
project-root/
├── .env                              # Central configuration (single source of truth)
├── docker compose.yml                 # Full stack orchestrator
├── docker compose.yml + override      # Dev mode (Vite HMR) — auto-merged
├── docker compose-infrastructure.yml  # Infrastructure only
├── docker compose-backend.yml         # Backend services only
├── flashsale-build.ps1                # Unified build script
│
├── .github/
│   └── workflows/
│       ├── ci.yml                    # PR & develop branch: build + test + security
│       ├── preview-ci.yml            # Preview branch: docker build + auto-PR to main
│       └── deploy.yml                # Deploy to server via SSH
│
├── backend/
│   ├── .env                          # Minimal — reads from ../.env
│   ├── docker compose.yml             # Backend standalone (infra + services, uses Dockerfile.dev)
│   ├── docker compose.infra-only.yml
│   ├── docker-compose.prod.yml        # Production override (uses Dockerfile.prod — builds in container)
│   └── <service>/
│       ├── Dockerfile.dev            # Copies pre-built JAR from host's target/ (fast)
│       └── Dockerfile.prod           # Multi-stage build: Maven runs inside container
│
└── frontend/
    ├── .env                          # Minimal — reads from ../.env
    ├── docker compose.yml            # Frontend standalone (mock mode, uses Dockerfile.dev)
    ├── docker-compose.prod.yml       # Production override (nginx, built in container)
    └── apps/
        ├── customer/
        ├── seller/
        └── admin/
```

---

## Quick Reference Cheatsheet

```powershell
# START
.\flashsale-build.ps1 -All -D              # Full stack, detached
.\flashsale-build.ps1 -Infra -D            # Infrastructure only
.\flashsale-build.ps1 -Backend -D          # Backend only
.\flashsale-build.ps1 -Frontend -D          # Frontend only (mock)

# RESTART / REBUILD
.\flashsale-build.ps1 -All -SkipBuild -D    # Skip build, just restart
.\flashsale-build.ps1 -Build                 # Maven build only

# STOP
.\flashsale-build.ps1 -Stop                 # Stop containers (keep data)
.\flashsale-build.ps1 -Down                  # Stop + remove containers
.\flashsale-build.ps1 -Down -V               # Stop + remove containers + volumes

# CLEANUP
.\flashsale-build.ps1 -Clean                 # Full clean (containers + volumes + images)
.\flashsale-build.ps1 -CleanImages            # Remove unused images
.\flashsale-build.ps1 -CleanVolumes           # Remove dangling volumes

# INFO
.\flashsale-build.ps1 -Status                # Container status
.\flashsale-build.ps1 -Logs                   # Tail all logs
.\flashsale-build.ps1 -Ports                  # Show exposed ports
.\flashsale-build.ps1 -Health                 # Check service health
.\flashsale-build.ps1 -Help                   # Show all flags

# CI/CD (GitHub Actions)
# develop branch  →  ci.yml        (build + test + security)
# preview branch  →  preview-ci.yml (docker build + auto-PR to main)
# main/develop   →  deploy.yml    (deploy to server)
```
