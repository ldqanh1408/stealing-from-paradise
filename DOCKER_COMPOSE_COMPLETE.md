# Stealing from Paradise - Docker Compose Complete Setup ✅

## 📦 Các File Mới Tạo

Tôi đã tạo ra một hệ thống Docker Compose hoàn chỉnh với các script tự động hóa. Dưới đây là danh sách các file:

### Root Directory (`D:\dev\stealing-from-paradise\`)

| File | Mục Đích |
|------|---------|
| `quick-start.bat` | **START HERE** - Script mở giao diện chọn lựa cho tất cả setups |
| `quick-start.ps1` | PowerShell version của quick-start |
| `.env.example` | Template file biến môi trường (sao chép thành `.env`) |
| `DOCKER_COMPOSE_SETUP.md` | Hướng dẫn chi tiết về Docker Compose setup |

### Backend Directory (`backend/`)

| File | Mục Đích |
|------|---------|
| `build-and-compose.bat` | Build Maven + start Docker Compose backend |
| `build-and-compose.ps1` | PowerShell version |
| `BUILD_SCRIPTS_README.md` | Hướng dẫn chi tiết về build scripts |

### Frontend Directory (`frontend/`)

| File | Mục Đích |
|------|---------|
| `build-and-compose.bat` | Build + start Docker Compose frontend |
| `build-and-compose.ps1` | PowerShell version |

### Infra Directory (`infra/`)

| File | Mục Đích |
|------|---------|
| `docker-compose.yml` | Infrastructure-only compose (databases, caches, etc.) |
| `start-infrastructure.bat` | Start chỉ infrastructure |
| `start-infrastructure.ps1` | PowerShell version |

---

## 🚀 Quick Start (3 Cách)

### Cách 1: Sử Dụng Interactive Quick Start (Dễ Nhất) ⭐

```bash
# Windows Command Prompt
D:\dev\stealing-from-paradise\quick-start.bat

# Hoặc PowerShell
.\quick-start.ps1
```

Sau đó chọn setup mong muốn:
- **Option 1**: Infrastructure Only (chạy microservices từ IDE)
- **Option 2**: Full Backend Stack (Docker)
- **Option 3**: Full Stack (Backend + Frontend)
- **Option 4**: Custom (chọn từng phần)

### Cách 2: Infrastructure Only (Cho Phát Triển)

Nếu muốn chạy microservices trực tiếp từ IDE:

```bash
# Windows
cd D:\dev\stealing-from-paradise\infra
start-infrastructure.bat

# PowerShell
.\start-infrastructure.ps1
```

Sau đó, mở IDE và chạy Spring Boot apps trực tiếp. Các services sẽ kết nối với infrastructure.

### Cách 3: Full Backend Stack (Docker)

Để chạy tất cả microservices trong Docker:

```bash
# Windows
cd D:\dev\stealing-from-paradise\backend
build-and-compose.bat

# PowerShell
.\build-and-compose.ps1
```

Script sẽ:
1. ✅ Clean old builds
2. ✅ Build tất cả Maven projects
3. ✅ Start Docker Compose stack

### Cách 4: Full Stack (Backend + Frontend)

```bash
# Terminal 1: Backend
cd D:\dev\stealing-from-paradise\backend
build-and-compose.bat

# Terminal 2 (chạy sau ~30 giây): Frontend
cd D:\dev\stealing-from-paradise\frontend
build-and-compose.bat
```

---

## 🌐 Service URLs

| Service | URL | Làu |
|---------|-----|-----|
| API Gateway | http://localhost:8080 | Backend |
| Discovery Service | http://localhost:8761 | Backend |
| **Frontend:**
| Customer App | http://localhost:3000 | Frontend |
| Seller Center | http://localhost:3001 | Frontend |
| Admin Portal | http://localhost:3002 | Frontend |
| **Infrastructure:**
| PostgreSQL | localhost:5432 | Infra |
| MongoDB | localhost:27017 | Infra |
| Redis | localhost:6379 | Infra |
| Elasticsearch | http://localhost:9200 | Infra |
| Minio | http://localhost:9001 | Infra |
| Kafka | localhost:9092 | Infra |
| AxonServer | http://localhost:8024 | Infra |

---

## 🔧 Useful Commands

### View container status
```bash
docker-compose ps
```

### View logs
```bash
# All containers
docker-compose logs

# Specific service
docker-compose logs -f api-gateway

# Last 100 lines
docker-compose logs --tail=100
```

### Stop/Start
```bash
# Stop everything
docker-compose down

# Stop + remove volumes (xóa dữ liệu)
docker-compose down -v

# Start again
docker-compose up -d

# Restart one service
docker-compose restart postgres
```

### Rebuild
```bash
# Rebuild all
docker-compose up -d --build

# Rebuild one service
docker-compose up -d --build api-gateway
```

---

## 📝 Configuration

Sao chép `.env.example` thành `.env` và chỉnh sửa:

```bash
cp .env.example .env
```

Các biến quan trọng:
```env
POSTGRES_USER=postgres
POSTGRES_PASSWORD=your_password_here
REDIS_HOST=redis
KAFKA_SERVER=kafka:29092
```

---

## 🎯 Recommended Workflow

### Phát triển (Development)
1. Chạy `start-infrastructure.bat` ở `infra/`
2. Mở IDE, chạy từng Spring Boot app trực tiếp
3. Tận dụng hot reload, debugging native

### Testing/Demo
1. Chạy `build-and-compose.bat` ở `backend/`
2. Chạy `build-and-compose.bat` ở `frontend/`
3. Test toàn bộ stack containerized

### Production/CI-CD
1. Build backend tại CI/CD server
2. Push images đến Docker Registry
3. Deploy bằng Kubernetes/Docker Swarm

---

## ✨ Key Features

✅ **3 Independent Docker Compose Files**
- Infra only (cho IDE development)
- Backend + Infra (Docker microservices)
- Frontend (Next.js apps)

✅ **Automated Scripts**
- Maven build + Docker start (one command)
- Health checks cho tất cả services
- Auto cleanup & volume management

✅ **Environment Management**
- `.env.example` template
- Easy to customize ports, credentials

✅ **Detailed Logging**
- Build logs saved to `build.log`
- Docker compose logs accessible

✅ **Comprehensive Documentation**
- `DOCKER_COMPOSE_SETUP.md` - Chi tiết
- `BUILD_SCRIPTS_README.md` - Scripts guide
- Comments in mỗi file

---

## 🐛 Troubleshooting

### Port already in use
```bash
# Find process
netstat -ano | findstr :5432

# Kill it
taskkill /PID <PID> /F
```

### Container won't start
```bash
docker-compose logs service_name
```

### Build fails
```bash
# Clean and retry
mvn clean install -U -DskipTests
```

### Fresh start
```bash
# Remove everything
docker-compose down -v --remove-orphans
docker system prune -a

# Rebuild
docker-compose up -d --build
```

---

## 📚 Files to Read

1. **Quick Reference**: `DOCKER_COMPOSE_SETUP.md` (ở root)
2. **Backend Scripts**: `BUILD_SCRIPTS_README.md` (ở backend)
3. **All docker-compose.yml files** (comments inside)

---

## 🎉 Next Steps

1. ✅ Copy `.env.example` → `.env` (optional, defaults work)
2. ✅ Run `quick-start.bat` (hoặc chọn setup trực tiếp)
3. ✅ Wait for containers to be healthy
4. ✅ Access services via URLs above
5. ✅ Read `DOCKER_COMPOSE_SETUP.md` để hiểu rõ hơn

---

**Tạo bởi**: GitHub Copilot  
**Phiên bản**: 1.0  
**Cập nhật lần cuối**: 2026-04-05

Hệ thống Docker Compose đã được hoàn thiện! 🚀

