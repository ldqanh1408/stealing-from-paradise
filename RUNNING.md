# Flash Sale Platform — Running Guide

## Prerequisites

- **Docker** (with Docker Compose v2)
- **Java 17+** (for local backend development)
- **Maven 3.8+** (for backend build)
- **Node.js 20+** (for frontend local development)

---

## Quick Start

### 1. Configure Environment

Copy the example env file if you haven't:

```powershell
cp .env.example .env   # or just use the existing .env
```

> **Important:** Fill in real secrets in `.env` before any production deployment. See the `.env` file comments for each section.

### 2. Full Stack (One Command)

```powershell
# From the project root:
.\flashsale-build.ps1 -All -Detach
```

This will:
1. Build all backend services with Maven
2. Start infrastructure (Postgres, MongoDB, Redis, Kafka, Elasticsearch, MinIO, AxonServer)
3. Start backend microservices (all 10 services)
4. Start frontend apps (customer, seller, admin) in **dev mode** (Vite HMR)

---

## Running Modes

### Full Stack (Default)

```powershell
.\flashsale-build.ps1 -All -Detach
```

- Backend: runs in Docker
- Frontend: runs in Docker with **Vite hot-reload**
- Uses `docker-compose.override.yml` automatically

### Infrastructure Only

Start databases and message queues without backend or frontend:

```powershell
.\flashsale-build.ps1 -Infra -Detach
```

Use this when you want to run backend services **locally** (outside Docker) while using Docker for databases.

### Backend Only

Start backend microservices (requires infrastructure):

```powershell
.\flashsale-build.ps1 -Backend -Detach
```

### Frontend Only (No Backend)

```powershell
.\flashsale-build.ps1 -Frontend -Detach
```

Apps run with **mock data** by default — no backend required. Perfect for UI development.

To connect to a real backend instead:

```powershell
$VITE_API_URL=http://localhost:8080/api/v1
$VITE_BACKEND_MODE=real
.\flashsale-build.ps1 -Frontend -Detach
```

### Production Mode (No Hot Reload)

```powershell
.\flashsale-build.ps1 -All -Detach -FrontendProd
```

Builds frontend as **nginx containers** (production-ready static assets).

---

## Common Workflows

### Clean Rebuild

```powershell
# Full clean — removes all containers, images, and volumes
.\flashsale-build.ps1 -All -Clean -Detach
```

### Restart Backend Only (After Code Change)

```powershell
# Skip Maven build, just restart containers
.\flashsale-build.ps1 -All -SkipBuild -Detach
```

Or rebuild a specific service:

```powershell
docker-compose up --build -d identity-service
```

### View Logs

```powershell
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f api-gateway
docker-compose logs -f identity-service
```

### Stop Everything

```powershell
.\flashsale-build.ps1 -Stop
```

---

## Running Standalone (Per Module)

### Backend Standalone

```powershell
cd backend
docker-compose up --build -d
```

### Backend + Infrastructure Only

```powershell
cd backend
docker-compose -f docker-compose.yml -f docker-compose.infra-only.yml up --build -d
```

### Frontend Standalone

```powershell
cd frontend
docker-compose -f docker-compose.yml up --build -d
```

### Frontend Production Build

```powershell
cd frontend
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up --build -d
```

---

## Using Docker Compose Directly

The project has several compose files:

| File | Purpose |
|------|---------|
| `docker-compose.yml` | Full stack (infra + backend + frontend) |
| `docker-compose.override.yml` | Auto-merged dev overrides (Vite HMR) |
| `docker-compose-infrastructure.yml` | Infrastructure only |
| `docker-compose-backend.yml` | Backend services only |

```powershell
# Full stack, ignore override (production mode)
docker-compose -f docker-compose.yml up --build -d

# Full stack, with override (dev mode)
docker-compose up --build -d

# Infrastructure only
docker-compose -f docker-compose-infrastructure.yml up --build -d

# Backend only (requires flashsale-net to exist)
docker-compose -f docker-compose-backend.yml up --build -d
```

---

## Health Checks

### Script

```powershell
.\flashsale-build.ps1 -Health
```

### Manual

```bash
curl http://localhost:8080/actuator/health     # API Gateway
curl http://localhost:8761/eureka/status        # Eureka Discovery
curl http://localhost:9200                      # Elasticsearch
curl http://localhost:9000/minio/health/live    # MinIO
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

## Development Workflows

### Frontend Development (Hot Reload)

```powershell
# Start full stack in dev mode
.\flashsale-build.ps1 -All -Detach

# Edit code in frontend/apps/customer/src
# Changes auto-reload via Vite HMR
```

### Backend Development (Local Java + Docker Infra)

```powershell
# 1. Start infrastructure in Docker
.\flashsale-build.ps1 -Infra -Detach

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
docker-compose logs kafka
```

### Backend Won't Connect to Database

Ensure infrastructure is healthy first:

```powershell
docker-compose ps   # All infra should be healthy
```

### Maven Build Fails

```powershell
cd backend
mvn clean install -DskipTests -X   # verbose output
```

### Reset Everything

```powershell
.\flashsale-build.ps1 -Clean
.\flashsale-build.ps1 -All -Detach
```

---

## File Structure

```
project-root/
├── .env                          # Central configuration (single source of truth)
├── docker-compose.yml             # Full stack orchestrator
├── docker-compose.override.yml    # Dev mode (Vite HMR) — auto-merged
├── docker-compose-infrastructure.yml  # Infrastructure only
├── docker-compose-backend.yml     # Backend services only
├── flashsale-build.ps1            # Unified build script
│
├── backend/
│   ├── .env                      # Minimal — reads from ../.env
│   ├── docker-compose.yml         # Backend standalone (infra + services)
│   ├── docker-compose.infra-only.yml
│   └── <service>/
│
└── frontend/
    ├── .env                      # Minimal — reads from ../.env
    ├── docker-compose.yml         # Frontend standalone (mock mode)
    ├── docker-compose.prod.yml    # Production override (nginx)
    └── apps/
        ├── customer/
        ├── seller/
        └── admin/
```
