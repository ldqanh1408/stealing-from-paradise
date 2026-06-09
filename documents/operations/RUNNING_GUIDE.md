# Running Guide

> **Source**: documents/operations/RUNNING_GUIDE.md (2026-05-11)
> **Entry Script**: flashsale-build.ps1
> **Generated**: 2026-05-10

---

## Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| Docker Desktop | Latest | Run all services in containers |
| Maven | 3.8+ | Build backend JARs (optional) |
| Java | 25 | Run backend locally (optional) |
| Node.js | 22+ | Run frontend locally (optional) |

> All services can run fully inside Docker.

---

## First-Time Setup

### 1. Copy environment file

```powershell
cp .env.example .env
```

### 2. Fill in secrets in `.env`

```
JWT_SECRET=<generate-a-256bit-base64-string>
POSTGRES_PASSWORD=<strong-password>
REDIS_PASSWORD=<strong-password>
MONGO_INITDB_ROOT_PASSWORD=<strong-password>
MINIO_ACCESS_KEY=<your-access-key>
MINIO_SECRET_KEY=<your-secret-key>
ELASTIC_USERNAME=elastic
ELASTIC_PASSWORD=<strong-password>
STRIPE_SECRET_KEY=sk_test_...
STRIPE_PUBLISHABLE_KEY=pk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...
SPRING_AI_OPENAI_API_KEY=sk-...
```

### 3. Start Docker Desktop

---

## Quick Start

### Full Stack -- DEV mode (recommended)

```powershell
.\flashsale-build.ps1 mvn-all
.\flashsale-build.ps1 dev
```

### Backend Only

```powershell
.\flashsale-build.ps1 be-dev
```

### Frontend Only -- Mock Data

```powershell
# All 3 apps via npm on host (hot-reload, 3 terminal windows)
.\flashsale-build.ps1 fe-dev-all

# All 3 apps via Docker (mock data)
.\flashsale-build.ps1 fe-docker-all
```

### Stop Everything

```powershell
.\flashsale-build.ps1 stop all
```

---

## Script Command Reference

Run from the **project root**: `.\flashsale-build.ps1 <action> [target]`

### Maven -- Backend Build

| Action | Description |
|--------|-------------|
| `mvn-all` | Build ALL Maven modules (clean install, skip tests) |
| `mvn <service>` | Build single Maven module |
| `mvn-clean <service>` | Clean + build single Maven module |

Services: `discovery`, `gateway`, `identity`, `payment`, `order`, `flashsale`, `product`, `search`, `notification`, `chat`, `common-lib`, `dev-data-runner`

### npm -- Frontend Build

| Action | Description |
|--------|-------------|
| `npm-install-all` | Run `npm install` for all frontend apps + shared |
| `npm-install <app>` | Run `npm install` for specific app |
| `npm-all` | Build ALL 3 frontend apps |
| `npm <app>` | Build single frontend app |

Apps: `customer`, `seller`, `admin`, `shared`

### Frontend Dev Modes

| Action | Description |
|--------|-------------|
| `fe-dev <app>` | ONE app via npm on host (hot-reload, mock data) |
| `fe-dev-all` | ALL 3 apps via npm on host (3 terminal windows) |
| `fe-docker <app>` | ONE app via Docker (mock data) |
| `fe-docker-all` | ALL 3 apps via Docker (mock data) |

Apps: `customer` (port 3000), `seller` (port 3001), `admin` (port 3002)

### Backend & Fullstack Modes

| Action | Description |
|--------|-------------|
| `infra-up` | Start ONLY infrastructure |
| `infra-down` | Stop infrastructure |
| `be-dev` | Start infra + backend (no frontend) |
| `be-down` | Stop backend |
| `dev` | Full stack DEV (infra + backend + frontend + stripe-listener) |
| `dev-down` | Stop dev stack |
| `prod` | Full stack PROD (no stripe-listener) |
| `prod-down` | Stop prod stack |

### Single-Service Container Commands

| Action | Description |
|--------|-------------|
| `svc-build <service>` | Build ONE backend service Docker image |
| `svc-run <service>` | Start infra + build + run ONE service |
| `svc-up <service>` | Start ONE service container (already built) |
| `svc-rm <service>` | Remove ONE service container |

Services: `discovery`, `gateway`, `identity`, `payment`, `order`, `flashsale`, `product`, `search`, `notification`, `chat`

### Stop Commands

| Action | Description |
|--------|-------------|
| `stop infra` | Stop infrastructure |
| `stop be` | Stop backend |
| `stop fe` | Stop frontend |
| `stop dev` | Stop dev stack |
| `stop prod` | Stop prod stack |
| `stop all` | Stop ALL containers (keeps volumes) |

### Utility Commands

| Action | Description |
|--------|-------------|
| `logs all` | Stream logs from ALL containers |
| `logs be` | Stream logs from backend |
| `logs fe` | Stream logs from frontend |
| `logs infra` | Stream logs from infrastructure |
| `logs <service>` | Stream logs from a specific container |
| `ps` / `status` | List all running containers |
| `clean` | Stop all + remove volumes (DESTRUCTIVE) |
| `help` | Show help message |

---

## Running Modes

| Mode | Command | Infra | Backend | Frontend | Stripe CLI |
|------|---------|-------|---------|----------|------------|
| **dev** | `dev` | Yes | Yes | Yes | Yes |
| **prod** | `prod` | Yes | Yes | Yes | No |
| **be-dev** | `be-dev` | Yes | Yes | No | No |
| **fe-dev** | `fe-dev <app>` | No | No | Yes | No |
| **infra** | `infra-up` | Yes | No | No | No |

---

## Service Ports & URLs

### Backend Services

| Service | Container | Port | URL |
|---------|-----------|------|-----|
| API Gateway | `fs-gateway` | 8080 | http://localhost:8080 |
| Discovery (Eureka) | `fs-discovery` | 8761 | http://localhost:8761 |
| Identity | `fs-identity` | 8081 | http://localhost:8081 |
| Payment | `fs-payment` | 8082 | http://localhost:8082 |
| Order | `fs-order` | 8083 | http://localhost:8083 |
| Flash Sale | `fs-flashsale` | 8086 | http://localhost:8086 |
| Product | `fs-product` | 8084 | http://localhost:8084 |
| Search | `fs-search` | 8087 | http://localhost:8087 |
| Notification | `fs-notification` | 8092 | http://localhost:8092 |
| AI Chat | `fs-chat` | 8093 | http://localhost:8093 |

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

## Container Names

| Category | Containers |
|----------|------------|
| **Infrastructure** | `fs-postgres`, `fs-mongo`, `fs-redis`, `fs-elasticsearch`, `fs-minio`, `fs-kafka`, `fs-zookeeper`, `fs-axonserver` |
| **Backend** | `fs-discovery`, `fs-gateway`, `fs-identity`, `fs-payment`, `fs-order`, `fs-flashsale`, `fs-product`, `fs-search`, `fs-notification`, `fs-chat` |
| **Frontend** | `fs-customer-fe`, `fs-seller-fe`, `fs-admin-fe` |
| **Special** | `fs-reverse-proxy` (nginx), `fs-stripe-listener` (dev only) |

---

## Database Connections

Connect from host machine (outside Docker):

| Database | Host | Port | User | Password |
|----------|------|------|------|----------|
| PostgreSQL | localhost | 5432 | postgres | `POSTGRES_PASSWORD` from `.env` |
| MongoDB | localhost | 27017 | fs_mongo_admin | `MONGO_INITDB_ROOT_PASSWORD` from `.env` |
| Redis | localhost | 6379 | (none) | `REDIS_PASSWORD` from `.env` |
| Elasticsearch | localhost | 9200 | elastic | `ELASTIC_PASSWORD` from `.env` (x-pack security; disabled by default in dev, must be enabled in production) |
| MinIO | localhost | 9000 | `MINIO_ACCESS_KEY` | `MINIO_SECRET_KEY` |

---

## Docker Compose File Architecture

```
project-root/
|-- docker-compose.yml                    # Base: infra + backend + frontend + nginx
|-- docker-compose.dev.yml               # DEV override: adds fs-stripe-listener
|-- docker-compose-infrastructure.yml    # Infrastructure only
|-- docker-compose-backend.yml            # Backend services only
|-- flashsale-build.ps1                # Unified build & run script
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
|   |-- ai-chat-service/
|   |-- common-lib/
|   |-- docker/
|       |-- postgres/init/
|       |-- mongo/init/
|       |-- kafka/create-topics.sh
```

### Compose File Combinations

| Command | Compose Files | Result |
|---------|--------------|--------|
| `dev` | `docker-compose.yml` + `docker-compose.dev.yml` | Full stack + stripe-listener |
| `prod` | `docker-compose.yml` | Full stack (no stripe-listener) |
| `be-dev` | `docker-compose.yml` + `docker-compose-backend.yml` | Backend + stripe-listener |
| `infra-up` | `docker-compose.yml` + `docker-compose-infrastructure.yml` | Infrastructure only |

---

## Stripe Webhook Setup

### Local Development

`dev` command automatically starts `fs-stripe-listener` which forwards Stripe webhook events to `payment-service`.

Get the webhook signing secret:

```powershell
docker logs fs-stripe-listener | Select-String whsec_
```

Update `.env` and restart:

```powershell
.\flashsale-build.ps1 svc-rm payment
.\flashsale-build.ps1 svc-up payment
```

### Production

1. Stripe Dashboard > Developers > Webhooks > Add endpoint
2. Endpoint URL: `https://your-domain.com/api/v1/stripe/webhooks`
3. Events: `payment_intent.succeeded`, `payment_intent.payment_failed`, `charge.refunded`, `account.updated`
4. Copy signing secret (whsec_xxx)
5. Set `STRIPE_WEBHOOK_SECRET_PROD=whsec_xxx` in production `.env`

---

## Development Workflows

### Full-Stack (daily dev)

```powershell
.\flashsale-build.ps1 dev

# After backend changes:
.\flashsale-build.ps1 mvn-all
.\flashsale-build.ps1 stop be
.\flashsale-build.ps1 be-dev

# Full reset:
.\flashsale-build.ps1 stop all
.\flashsale-build.ps1 mvn-all
.\flashsale-build.ps1 dev
```

### Backend-Only

```powershell
# Option A: Backend in Docker
.\flashsale-build.ps1 be-dev

# Option B: Backend in IDE, infra in Docker
.\flashsale-build.ps1 infra-up
cd backend
mvn spring-boot:run -pl identity-service
```

### Frontend-Only

```powershell
# npm on host (recommended)
.\flashsale-build.ps1 fe-dev-all

# Docker (sandboxed)
.\flashsale-build.ps1 fe-docker-all
```

### Single Service

```powershell
.\flashsale-build.ps1 svc-run order
curl http://localhost:8080/api/v1/orders/...
```

---

## Troubleshooting

### Containers Not Starting

```powershell
.\flashsale-build.ps1 ps
.\flashsale-build.ps1 logs gateway
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

Kafka needs ~30 seconds to initialize. Check: `docker logs fs-kafka`

### Full Reset

```powershell
.\flashsale-build.ps1 clean
.\flashsale-build.ps1 mvn-all
.\flashsale-build.ps1 dev
```
