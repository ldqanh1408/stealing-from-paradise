# 🚀 Stealing from Paradise - Docker Compose Setup Complete

> **Status**: ✅ READY TO USE

Hệ thống Docker Compose hoàn chỉnh với 3 layer độc lập và scripts tự động hóa.

## 🎯 Quick Start (Chọn 1 cái)

### 1. Interactive Menu (Easiest) ⭐
```bash
D:\dev\stealing-from-paradise\quick-start.bat
```
Sau đó chọn setup mong muốn:
- Option 1: Infrastructure Only (IDE dev)
- Option 2: Full Backend Stack (Docker)
- Option 3: Full Stack (Backend + Frontend)
- Option 4: Custom

### 2. Infrastructure Only (For IDE Development)
```bash
cd infra
start-infrastructure.bat
# Then run microservices from IDE
```

### 3. Full Backend Stack (Docker Containers)
```bash
cd backend
build-and-compose.bat
# Maven build + Docker start
```

### 4. Full System (Backend + Frontend)
```bash
# Terminal 1
cd backend && build-and-compose.bat

# Terminal 2 (wait 30 seconds)
cd frontend && build-and-compose.bat
```

## 📦 Architecture

```
3 Independent Docker Compose Layers:
├── infra/           (Infrastructure only - databases, caches, queues)
├── backend/         (Microservices + infrastructure)
└── frontend/        (Next.js applications)
```

## 🌐 Available Services

| Service | URL |
|---------|-----|
| API Gateway | http://localhost:8080 |
| Customer App | http://localhost:3000 |
| Seller Center | http://localhost:3001 |
| Admin Portal | http://localhost:3002 |
| Eureka Discovery | http://localhost:8761 |
| PostgreSQL | localhost:5432 |
| MongoDB | localhost:27017 |
| Redis | localhost:6379 |
| Elasticsearch | http://localhost:9200 |
| Minio | http://localhost:9001 |

## 📚 Documentation

| File | Purpose |
|------|---------|
| **DOCKER_COMPOSE_SETUP.md** | Comprehensive guide (20+ pages) |
| **BUILD_SCRIPTS_README.md** | Build scripts documentation |
| **SETUP_CHECKLIST.md** | Implementation details |
| **.env.example** | Configuration template |

## ⚡ Useful Commands

```bash
# View container status
docker-compose ps

# View logs
docker-compose logs -f [service_name]

# Stop everything
docker-compose down

# Stop & remove data
docker-compose down -v

# Restart one service
docker-compose restart [service_name]

# Rebuild & restart
docker-compose up -d --build [service_name]
```

## 🔧 Configuration

1. Copy `.env.example` to `.env`
2. Edit values (optional, defaults work)
3. Scripts automatically load `.env`

```bash
cp .env.example .env
# Edit as needed
```

## 📖 Where to Start Reading

1. **First Time?** → DOCKER_COMPOSE_SETUP.md
2. **Building Backend?** → BUILD_SCRIPTS_README.md
3. **Quick Reference?** → DOCKER_COMPOSE_COMPLETE.md
4. **Verification?** → SETUP_CHECKLIST.md

## ✨ Key Features

✅ 3 independent Docker Compose configurations  
✅ Automated Maven build + Docker start  
✅ Interactive quick-start menu  
✅ Health checks for all services  
✅ Volume persistence  
✅ Network isolation  
✅ Environment management  
✅ Comprehensive logging  
✅ Production-ready setup  

## 🐛 Troubleshooting

### Port Already in Use
```bash
netstat -ano | findstr :5432
taskkill /PID <PID> /F
```

### Container Won't Start
```bash
docker-compose logs [service_name]
```

### Build Failed
```bash
mvn clean install -U -DskipTests
```

### Fresh Start
```bash
docker-compose down -v --remove-orphans
docker system prune -a
docker-compose up -d --build
```

For more: See DOCKER_COMPOSE_SETUP.md → Troubleshooting

## 🎓 Development Workflows

### Fast Development (Hot Reload)
1. Run `infra/start-infrastructure.bat`
2. Run microservices from IDE
3. Changes reload instantly ⚡

### Integration Testing (Docker)
1. Run `backend/build-and-compose.bat`
2. Test microservices in containers
3. Mirrors production environment

### Complete System Testing
1. Run `backend/build-and-compose.bat`
2. Run `frontend/build-and-compose.bat`
3. Test end-to-end scenarios

## 📊 Services Summary

**Infrastructure (8 services):**
PostgreSQL, MongoDB, Redis, Elasticsearch, Minio, Kafka, Zookeeper, AxonServer

**Backend (13+ microservices):**
API Gateway, Discovery Service, Identity, Product, Order, Payment, FlashSale, Search, Notification, Worker, Cart, etc.

**Frontend (3 applications):**
Customer App, Seller Center, Admin Portal

## 🚀 Next Steps

1. ✅ Run `quick-start.bat`
2. ✅ Choose your setup
3. ✅ Wait for "healthy" status
4. ✅ Access services
5. ✅ Read documentation

## 📝 Files Created

- ✅ `quick-start.bat` & `quick-start.ps1` (Interactive starter)
- ✅ `backend/build-and-compose.bat` & `.ps1` (Backend automation)
- ✅ `frontend/build-and-compose.bat` & `.ps1` (Frontend automation)
- ✅ `infra/start-infrastructure.bat` & `.ps1` (Infrastructure only)
- ✅ `infra/docker-compose.yml` (Infrastructure compose)
- ✅ `.env.example` (Configuration template)
- ✅ 4 comprehensive documentation files

## ⚠️ Prerequisites

- ✅ Docker Desktop (running)
- ✅ Java 25 or higher
- ✅ Maven 3.6+
- ✅ 8GB+ RAM recommended
- ✅ 30GB+ disk space

## 🎉 Ready to Go!

Everything is set up and ready to use. Start with:

```bash
D:\dev\stealing-from-paradise\quick-start.bat
```

---

**Version**: 1.0  
**Created**: 2026-04-05  
**Status**: ✅ Production Ready

For detailed information, see [DOCKER_COMPOSE_SETUP.md](./DOCKER_COMPOSE_SETUP.md)

