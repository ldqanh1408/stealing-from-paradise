# 🚀 Stealing from Paradise - E-Commerce Flash Sale Platform

> **Status**: ✅ Docker Compose Setup Complete - Ready to Use
>
> A comprehensive microservices e-commerce platform with Flash Sale capabilities, fully containerized and automated.

## 🎯 Quick Start

### Option 1: Interactive Setup (Recommended) ⭐
```bash
D:\dev\stealing-from-paradise\quick-start.bat
```
Then select your preferred setup (Infrastructure, Backend, Frontend, or Custom).

### Option 2: Infrastructure Only (For IDE Development)
```bash
cd infra
start-infrastructure.bat
# Then run microservices from your IDE
```

### Option 3: Full Backend Stack (Docker)
```bash
cd backend
build-and-compose.bat
# Automatically builds Maven + starts Docker
```

### Option 4: Complete System (Backend + Frontend)
```bash
# Terminal 1
cd backend && build-and-compose.bat

# Terminal 2 (after 30 seconds)
cd frontend && build-and-compose.bat
```

## 📦 Architecture

This project is organized into **3 independent Docker Compose layers**:

### 1. **Infrastructure Layer** (`infra/docker-compose.yml`)
- PostgreSQL, MongoDB, Redis
- Elasticsearch, Minio
- Kafka, Zookeeper
- AxonServer
- **Best for**: IDE development (run microservices directly)

### 2. **Backend Layer** (`backend/docker-compose.yml`)
- All infrastructure services
- 13+ microservices (API Gateway, Discovery, Identity, Product, Order, etc.)
- **Best for**: Docker-based integration testing

### 3. **Frontend Layer** (`frontend/docker-compose.yml`)
- Customer App (Next.js, port 3000)
- Seller Center (port 3001)
- Admin Portal (port 3002)
- **Best for**: Frontend + backend integration

## 🌐 Service Endpoints

| Service | URL | Layer |
|---------|-----|-------|
| **API Gateway** | http://localhost:8080 | Backend |
| **Customer App** | http://localhost:3000 | Frontend |
| **Seller Center** | http://localhost:3001 | Frontend |
| **Admin Portal** | http://localhost:3002 | Frontend |
| **Eureka Discovery** | http://localhost:8761 | Backend |
| **PostgreSQL** | localhost:5432 | Infra |
| **MongoDB** | localhost:27017 | Infra |
| **Redis** | localhost:6379 | Infra |
| **Elasticsearch** | http://localhost:9200 | Infra |
| **Minio Console** | http://localhost:9001 | Infra |
| **Kafka** | localhost:9092 | Infra |
| **AxonServer** | http://localhost:8024 | Infra |

## 📚 Documentation

| Document | Purpose | Read Time |
|----------|---------|-----------|
| **[DOCKER_START.md](./DOCKER_START.md)** | Quick reference & getting started | 5 min |
| **[DOCKER_COMPOSE_SETUP.md](./DOCKER_COMPOSE_SETUP.md)** | Comprehensive setup guide | 30 min |
| **[BUILD_SCRIPTS_README.md](./backend/BUILD_SCRIPTS_README.md)** | Build automation details | 15 min |
| **[FILE_MANIFEST.md](./FILE_MANIFEST.md)** | Complete file listing | 10 min |
| **[SETUP_CHECKLIST.md](./SETUP_CHECKLIST.md)** | Implementation & verification | 20 min |

**Start here**: [DOCKER_START.md](./DOCKER_START.md) - One-page quick reference

## ⚡ Common Commands

```bash
# Check container status
docker-compose ps

# View logs
docker-compose logs -f [service_name]

# Stop all
docker-compose down

# Stop + remove data
docker-compose down -v

# Rebuild & restart
docker-compose up -d --build [service_name]
```

## 🔧 Configuration

### Environment Variables
```bash
# Copy template
cp .env.example .env

# Edit as needed
nano .env
```

### Available Variables
```env
# Database
POSTGRES_USER=postgres
POSTGRES_PASSWORD=your_password

# Services
EUREKA_URI=http://discovery-service:8761/eureka
KAFKA_SERVER=kafka:29092

# Storage
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin
```

## 🎓 Development Workflows

### Fast Development (Hot Reload) ⚡
```bash
cd infra
start-infrastructure.bat
# Then: Run Spring Boot apps from IDE
# → Changes reload instantly
# → Full debugging support
```

### Integration Testing (Docker)
```bash
cd backend
build-and-compose.bat
# → All services containerized
# → Mirrors production environment
# → Test microservices together
```

### End-to-End Testing
```bash
cd backend && build-and-compose.bat
cd frontend && build-and-compose.bat
# → Complete system testing
# → Frontend + Backend integration
```

## 📋 Project Structure

```
stealing-from-paradise/
├── backend/                          # Microservices backend
│   ├── api-gateway/                  # API Gateway (Spring Cloud)
│   ├── discovery-service/            # Eureka Service Registry
│   ├── identity-service/             # Authentication & Authorization
│   ├── product-service/              # Product Catalog
│   ├── order-service/                # Order Management
│   ├── payment-service/              # Payment Processing
│   ├── cart-service/                 # Shopping Cart
│   ├── search-service/               # Elasticsearch Integration
│   ├── notification-service/         # Email/SMS Notifications
│   ├── flashsale-service/            # Flash Sale Engine
│   ├── worker-service/               # Background Jobs
│   ├── build-and-compose.bat         # ← Run this to start backend
│   ├── BUILD_SCRIPTS_README.md       # Build documentation
│   └── docker-compose.yml            # Backend composition
│
├── frontend/                         # Frontend Applications
│   ├── apps/customer-app/            # Customer Portal (Next.js)
│   ├── apps/seller-center/           # Seller Management
│   ├── apps/admin-portal/            # Admin Dashboard
│   ├── build-and-compose.bat         # ← Run this to start frontend
│   └── docker-compose.yml            # Frontend composition
│
├── infra/                            # Infrastructure Services
│   ├── docker-compose.yml            # ← Infrastructure only (NEW)
│   ├── start-infrastructure.bat      # ← Run this for IDE dev
│   ├── postgres-init/                # Database initialization
│   ├── axon-init/                    # AxonServer setup
│   └── kafka-init/                   # Kafka setup
│
├── quick-start.bat                   # ⭐ START HERE (Interactive menu)
├── quick-start.ps1                   # PowerShell version
├── DOCKER_START.md                   # Quick reference
├── DOCKER_COMPOSE_SETUP.md           # Comprehensive guide
├── FILE_MANIFEST.md                  # File listing
├── SETUP_CHECKLIST.md                # Verification
└── .env.example                      # Configuration template
```

## ✨ Key Features

✅ **3 Independent Docker Compose Configurations**
- Choose the setup that fits your workflow

✅ **Fully Automated Build & Deploy Scripts**
- Maven compilation
- Docker image building
- Container orchestration

✅ **Interactive Quick Start Menu**
- Guides through available options
- Opens correct terminal windows

✅ **Comprehensive Documentation**
- Quick reference (1 page)
- Complete guide (20+ pages)
- Troubleshooting included

✅ **Production Ready**
- Health checks for all services
- Volume persistence
- Network isolation
- Auto-restart policies
- Comprehensive logging

## 🐛 Troubleshooting

### Port Already in Use
```bash
netstat -ano | findstr :5432
taskkill /PID <PID> /F
```

### Container Won't Start
```bash
docker-compose logs service_name
```

### Build Failed
```bash
mvn clean install -U -DskipTests
```

### Fresh Start (Delete Everything)
```bash
docker-compose down -v --remove-orphans
docker system prune -a
docker-compose up -d --build
```

For detailed troubleshooting, see [DOCKER_COMPOSE_SETUP.md](./DOCKER_COMPOSE_SETUP.md#troubleshooting)

## 📋 Prerequisites

- ✅ Docker Desktop (running)
- ✅ Java 25 or higher
- ✅ Maven 3.6+
- ✅ 8GB+ RAM recommended
- ✅ 30GB+ disk space

## 🚀 Getting Started Steps

1. **Clone or navigate to project**
   ```bash
   cd D:\dev\stealing-from-paradise
   ```

2. **Copy environment configuration**
   ```bash
   cp .env.example .env
   ```

3. **Start the system**
   ```bash
   quick-start.bat
   ```
   Or choose specific layer:
   - `infra/start-infrastructure.bat` (Infrastructure only)
   - `backend/build-and-compose.bat` (Backend services)
   - `frontend/build-and-compose.bat` (Frontend apps)

4. **Wait for "healthy" status**
   ```bash
   docker-compose ps
   ```

5. **Access services**
   - API Gateway: http://localhost:8080
   - Frontend: http://localhost:3000
   - Other URLs listed above

## 📚 Additional Resources

- [DOCKER_START.md](./DOCKER_START.md) - Quick start guide
- [DOCKER_COMPOSE_SETUP.md](./DOCKER_COMPOSE_SETUP.md) - Comprehensive documentation
- [BUILD_SCRIPTS_README.md](./backend/BUILD_SCRIPTS_README.md) - Build automation guide
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Docker Compose Documentation](https://docs.docker.com/compose/)

## 🎯 Recommended Next Steps

### If You're New
→ Read [DOCKER_START.md](./DOCKER_START.md) (one page)

### If You Want Quick Development
→ Run `infra/start-infrastructure.bat` and use IDE

### If You Want Full Testing
→ Run `backend/build-and-compose.bat`

### If You Want Everything
→ Run `quick-start.bat` and select option 3

## 🤝 Contributing

When adding new services:
1. Update relevant `docker-compose.yml` file
2. Add environment variables to `.env.example`
3. Update documentation
4. Test with scripts

## 📝 Version Info

- **Version**: 1.0
- **Created**: 2026-04-05
- **Status**: ✅ Production Ready
- **Last Updated**: 2026-04-05

## 📞 Support

For issues:
1. Check [DOCKER_COMPOSE_SETUP.md](./DOCKER_COMPOSE_SETUP.md#troubleshooting)
2. View service logs: `docker-compose logs [service]`
3. Check build.log: `cat build.log`

---

**Made with ❤️ by GitHub Copilot**

*"Complete microservices e-commerce platform with automated Docker deployment"*
