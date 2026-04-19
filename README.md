# Flash Sale E-Commerce Platform (stealing-from-paradise)

> A high-scale, production-ready e-commerce platform with flash sales, multi-seller marketplace, and comprehensive admin management.

## 🎯 Quick Start

### Option 1: Docker (Recommended - 2 minutes)
```bash
docker-compose up -d
# Wait 3-5 minutes for all services to start
# Access: Customer (3000) | Seller (3001) | Admin (3002)
```

### Option 2: Local Development (Backend)
```bash
cd backend
mvn clean install -DskipTests
# Start each service in separate terminals
mvn spring-boot:run
```

### Option 3: Local Development (Frontend)
```bash
cd frontend/apps/customer
npm install
npm run dev
# Open http://localhost:3000
```

## 📋 Project Overview

| Component | Details |
|-----------|---------|
| **Type** | Microservices E-commerce Platform |
| **Architecture** | Event-Driven (Axon Framework) + Microservices |
| **Backend** | 11 Java/Spring Boot microservices |
| **Frontend** | 3 React apps (Customer, Seller, Admin) |
| **Databases** | PostgreSQL, MongoDB, Redis, Elasticsearch |
| **Message Queue** | Kafka |
| **Deployment** | Docker & Docker Compose |

## 🏗️ Architecture

```
API Gateway (8080)
    ↓
┌─────────────────────────────────────┐
│  Discovery Service (Eureka - 8761)  │
└──────────┬──────────────────────────┘
           ↓
    ┌──────────────────────────────────────────┐
    │        Microservices                      │
    │  ┌─────────────┐  ┌──────────────────┐  │
    │  │ Axon        │  │ Traditional DB   │  │
    │  │ Services    │  │ Services         │  │
    │  │ (4)         │  │ (7)              │  │
    │  └─────────────┘  └──────────────────┘  │
    └──────────────────────────────────────────┘
           ↓
    ┌──────────────────────────────────────────┐
    │        Shared Infrastructure              │
    │  PostgreSQL | MongoDB | Redis | Kafka    │
    └──────────────────────────────────────────┘
```

**Backend Services:**
- **Axon Services**: order, payment, flashsale, worker
- **Traditional Services**: identity, product, cart, search, notification
- **Infrastructure**: discovery, api-gateway, common-lib

## 📚 Documentation

Complete documentation available in `/docs`:

| Document | Purpose |
|----------|---------|
| [PROJECT_OVERVIEW.md](docs/PROJECT_OVERVIEW.md) | **START HERE** - Complete guide |
| [BUSINESS_DOC_v5_3_rts_unified.md](docs/BUSINESS_DOC_v5_3_rts_unified.md) | Business logic & workflows (v5.3) |
| [SYSTEM_POLICY_v3_rts_unified.md](docs/SYSTEM_POLICY_v3_rts_unified.md) | System policies & configuration (v3.0) |
| [DATA_RETENTION_POLICY_v4_rts.md](docs/DATA_RETENTION_POLICY_v4_rts.md) | 22 Cronjobs & retention (v4.0) |
| [BACKEND_GUIDE.md](docs/BACKEND_GUIDE.md) | Backend architecture |
| [FRONTEND_GUIDE.md](docs/FRONTEND_GUIDE.md) | Frontend development |
| [BUILD_AND_DOCKER_GUIDE.md](docs/BUILD_AND_DOCKER_GUIDE.md) | Build & deployment |
| [JAVA_SPRING_BOOT_CONFIG.md](docs/JAVA_SPRING_BOOT_CONFIG.md) | Java 25 configuration |
| [AXON_EXPLANATION.md](docs/AXON_EXPLANATION.md) | Axon Framework guide |
| [DOCUMENTATION_INDEX.md](docs/DOCUMENTATION_INDEX.md) | Documentation index |

**[See complete documentation index →](docs/DOCUMENTATION_INDEX.md)**

## 🛠️ Tech Stack

### Backend
- **Java 25 (LTS)** - Latest Java LTS version
- **Spring Boot 4.0.4** - Latest Spring Boot
- **Spring Cloud 2025.1.1** - Microservices
- **Axon Framework 4.13.0** - Event sourcing & CQRS
- **PostgreSQL 15.4** - SQL database
- **MongoDB 6.0** - NoSQL database
- **Redis 7.0** - Cache
- **Kafka 7.4.0** - Message queue
- **Elasticsearch 8.10** - Search
- **MinIO** - Object storage

### Frontend
- **React 19** - UI library
- **Vite 6.0** - Build tool
- **TypeScript** - Type safety
- **Tailwind CSS** - Styling
- **React Router** - Routing
- **Zustand** - State management
- **Stripe** - Payments

### DevOps
- **Docker** - Containerization
- **Docker Compose** - Orchestration
- **Nginx** - Reverse proxy

## 🚀 Features

- 🛒 **Customer Portal**: Browse, search, checkout
- 🏪 **Seller Portal**: Manage products & orders
- 🔧 **Admin Portal**: Moderation, analytics
- ⚡ **Flash Sales**: Time-limited promotions
- 💳 **Stripe Integration**: Secure payments
- 🔍 **Full-text Search**: Elasticsearch
- 📊 **Event-Driven**: Axon Framework
- 🔐 **Multi-tenant**: Separate Seller & Customer
- 📧 **Notifications**: Email & SMS
- 📈 **Scalable**: Microservices architecture

## 🔧 Setup Requirements

```bash
# System Requirements
- Docker & Docker Compose
- Node.js 18+ (for local frontend)
- Java 25 (for local backend - optional)
- Maven 3.8+ (for backend - optional)
- Git

# Environment
cp .env.example .env
# Edit .env with your settings
```

## 📊 Project Statistics

| Metric | Value |
|--------|-------|
| Backend Services | 11 |
| Frontend Apps | 3 |
| Cronjobs | 22 |
| Documentation Files | 13 |
| Documentation Lines | 6,000+ |
| Code Examples | 150+ |
| SQL Queries | 100+ |

## 🎓 How to Get Started

### By Role:

**New Developer:**
1. Read [PROJECT_OVERVIEW.md](docs/PROJECT_OVERVIEW.md)
2. Read [BACKEND_GUIDE.md](docs/BACKEND_GUIDE.md) or [FRONTEND_GUIDE.md](docs/FRONTEND_GUIDE.md)
3. Setup & run locally
4. Reference [DOCUMENTATION_INDEX.md](docs/DOCUMENTATION_INDEX.md)

**Backend Developer:**
1. [PROJECT_OVERVIEW.md](docs/PROJECT_OVERVIEW.md) - Backend Architecture
2. [BACKEND_GUIDE.md](docs/BACKEND_GUIDE.md)
3. [AXON_EXPLANATION.md](docs/AXON_EXPLANATION.md)
4. [JAVA_SPRING_BOOT_CONFIG.md](docs/JAVA_SPRING_BOOT_CONFIG.md)

**Frontend Developer:**
1. [PROJECT_OVERVIEW.md](docs/PROJECT_OVERVIEW.md) - Frontend Architecture
2. [FRONTEND_GUIDE.md](docs/FRONTEND_GUIDE.md)
3. [BUILD_AND_DOCKER_GUIDE.md](docs/BUILD_AND_DOCKER_GUIDE.md)

**DevOps Engineer:**
1. [BUILD_AND_DOCKER_GUIDE.md](docs/BUILD_AND_DOCKER_GUIDE.md)
2. [DATA_RETENTION_POLICY_v4_rts.md](docs/DATA_RETENTION_POLICY_v4_rts.md)
3. [JAVA_SPRING_BOOT_CONFIG.md](docs/JAVA_SPRING_BOOT_CONFIG.md)

## 🔗 Important Links

- **API Gateway**: http://localhost:8080
- **Eureka Dashboard**: http://localhost:8761
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **Customer App**: http://localhost:3000
- **Seller App**: http://localhost:3001
- **Admin App**: http://localhost:3002
- **MongoDB Compass**: mongodb://localhost:27017
- **Redis CLI**: `redis-cli -h localhost`
- **PostgreSQL**: `psql -h localhost -U flashsale`

## 📦 Folder Structure

```
stealing-from-paradise/
├── backend/                   # Java microservices
│   ├── api-gateway/
│   ├── discovery-service/
│   ├── identity-service/
│   ├── product-service/
│   ├── order-service/
│   ├── payment-service/
│   ├── flashsale-service/
│   ├── cart-service/
│   ├── search-service/
│   ├── notification-service/
│   ├── worker-service/
│   ├── common-lib/
│   └── docker/
├── frontend/                  # React apps
│   ├── apps/
│   │   ├── customer/
│   │   ├── seller/
│   │   └── admin/
│   └── shared/
├── docs/                      # Comprehensive documentation
├── docker-compose.yml         # Main compose file
└── README.md
```

## 🚀 Quick Commands

```bash
# Start all services
docker-compose up -d

# Stop all services
docker-compose down

# View logs
docker-compose logs -f api-gateway

# Run specific service locally
cd backend/{service-name}
mvn spring-boot:run

# Build frontend
cd frontend/apps/customer
npm run build

# Health check
curl http://localhost:8080/actuator/health
```

## 🔍 Troubleshooting

### Port Already in Use
```bash
lsof -i :8080
kill -9 {PID}
```

### Docker Issues
```bash
docker-compose down -v  # Remove volumes
docker system prune -a   # Clean up
docker-compose up -d --build
```

### Build Errors
```bash
cd backend
mvn clean install -DskipTests -X  # Verbose mode
```

See [BUILD_AND_DOCKER_GUIDE.md](docs/BUILD_AND_DOCKER_GUIDE.md) for more troubleshooting.

## 📖 Documentation

**Comprehensive documentation is available in the [/docs](docs/) folder:**

- ✅ Architecture & Setup
- ✅ Backend & Frontend Development
- ✅ Business Logic (v5.3)
- ✅ System Policies (v3.0)
- ✅ Data Retention & Cronjobs (v4.0)
- ✅ Deployment & Operations

[👉 See DOCUMENTATION_INDEX.md for complete guide](docs/DOCUMENTATION_INDEX.md)

## 🤝 Contributing

1. Create a feature branch: `git checkout -b feature/my-feature`
2. Make changes and test locally
3. Commit: `git commit -m "feat(service): description"`
4. Push: `git push origin feature/my-feature`
5. Create Pull Request

## 📜 License

MIT License - See LICENSE file

## ✨ Summary

This is a **production-ready, fully-documented e-commerce platform** with:

- ✅ Event-driven microservices architecture
- ✅ 11 backend services + 3 frontend apps
- ✅ Advanced features (Flash Sales, Stripe, Search)
- ✅ Comprehensive documentation (13 files, 6,000+ lines)
- ✅ 22 automated cronjobs for operations
- ✅ Docker containerization & deployment ready

**Ready for production deployment! 🚀**

---

**Last Updated**: 2026-04-14  
**Status**: ✅ Production-Ready  
**Documentation Version**: Complete v5.3 RTS Unified

