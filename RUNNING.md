# Flash Sale Platform — Running Guide (Quick Reference)

> **Full documentation: [docs/09_RUNNING.md](docs/09_RUNNING.md)**
> This file is a quick reference. See the full guide for detailed instructions.
> **Note**: `stripe-webhook.ps1` (separate terminal) is required for local Stripe webhook.

---

## Quick Start

```powershell
# Terminal 1: Start Stripe webhook listener (run FIRST, keep this terminal open)
.\stripe-webhook.ps1 -Mode Start

# Terminal 0: Build backend JARs (one-time or after code changes)
.\flashsale-build.ps1 -Build

# Terminal 0: Start everything
.\flashsale-build.ps1 -Up -All -D
```

---

## Script Reference

Run from the **project root**: `.\flashsale-build.ps1 [options]`

### Actions

| Flag | Description |
|------|-------------|
| `-Up -All -D` | Start everything (infra + backend + frontend) |
| `-Up -Infra -D` | Infrastructure only |
| `-Up -Backend -D` | Backend services only |
| `-Up -Frontend -D` | Frontend apps only (mock data) |
| `-Down -All` | Stop and remove containers |
| `-Build` | Maven build backend JARs |
| `-BuildFrontend` | Build frontend apps |
| `-Stop` | Stop containers (keep data) |
| `-Clean -V -Rmi` | Nuclear clean (DATA LOSS) |
| `-Status` | Container status |
| `-Logs` | Tail logs |
| `-Ports` | Show exposed ports |
| `-Health` | Check service health |
| `-Restart <svc>` | Restart a container |
| `-Tail <svc>` | Tail container logs |
| `-Exec <svc> <cmd>` | Run command in container |
| `-Menu` | Interactive menu |

### Container Names

```
INFRA:     fs-postgres  fs-mongo  fs-redis  fs-elasticsearch
            fs-minio  fs-kafka  fs-zookeeper  fs-axonserver
BACKEND:   fs-discovery  fs-gateway  fs-identity  fs-payment  fs-order
            fs-flashsale  fs-product  fs-search  fs-notification  fs-worker
FRONTEND:  fs-customer-fe  fs-seller-fe  fs-admin-fe
```

### Access Points

```
http://localhost:3000  Customer App
http://localhost:3001  Seller App
http://localhost:3002  Admin App
http://localhost:8080  API Gateway / Swagger UI
http://localhost:8761  Eureka Discovery
http://localhost:9001  MinIO Console
http://localhost:8024  Axon Server GUI
```

---

## Docker Compose Files

```
project-root/
  docker-compose.yml                   ← full stack
  docker-compose-infrastructure.yml    ← infra only
  docker-compose-backend.yml          ← backend only
  flashsale-build.ps1                 ← unified script

backend/
  docker-compose.yml                  ← standalone backend (dev)
  docker-compose.infra-only.yml
  docker-compose.prod.yml             ← prod: builds inside Docker
  docker-compose.prod-pulled.yml     ← prod: pulls from GHCR

frontend/
  docker compose.yml                  ← standalone frontend (space in name!)
  docker-compose.prod.yml            ← prod: nginx
  docker-compose.prod-pulled.yml      ← prod: pulls from GHCR
```

> **Note**: Frontend compose file is `docker compose.yml` (with a **space**).

---

## Common Tasks

```powershell
# Restart after code change
.\flashsale-build.ps1 -Build
.\flashsale-build.ps1 -Up -All -SkipBuild -D

# Reset everything
.\flashsale-build.ps1 -Clean -V -Rmi
.\flashsale-build.ps1 -Build
.\flashsale-build.ps1 -Up -All -D

# Tail specific service
.\flashsale-build.ps1 -Tail gateway -Lines 50

# Restart payment service
.\flashsale-build.ps1 -Restart payment

# Exec into postgres
.\flashsale-build.ps1 -Exec postgres psql -U postgres -d flashsale_platform

# Stripe webhook (local dev)
.\stripe-webhook.ps1 -Mode Start
.\stripe-webhook.ps1 -Mode ProdGuide
```

---

**Full guide: [docs/09_RUNNING.md](docs/09_RUNNING.md)**
