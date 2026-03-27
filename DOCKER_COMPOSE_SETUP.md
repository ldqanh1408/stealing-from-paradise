# Docker Compose Setup - Hướng Dẫn Hoàn Chỉnh

Hệ thống microservices "Stealing from Paradise" được tổ chức thành 3 layer Docker Compose riêng biệt.

## 📋 Cấu Trúc Docker Compose

### 1. **Infrastructure Layer** (`infra/docker-compose.yml`)
Chứa tất cả các dịch vụ hạ tầng, không chứa ứng dụng backend:
- **Databases**: PostgreSQL, MongoDB
- **Cache**: Redis
- **Search**: Elasticsearch
- **Object Storage**: Minio
- **Message Queue**: Kafka + Zookeeper
- **Event Sourcing**: AxonServer

**Khi nào sử dụng?**
- Khi muốn phát triển microservices từ IDE (chạy trực tiếp)
- Khi chỉ cần infrastructure mà không chạy containerized backend
- Để giảm tải máy tính

### 2. **Backend Layer** (`backend/docker-compose.yml`)
Chứa tất cả microservices backend + infrastructure:
- Tất cả các dịch vụ từ infra
- API Gateway
- Discovery Service
- Tất cả các microservices (Identity, Product, Order, Payment, etc.)

**Khi nào sử dụng?**
- Khi muốn chạy toàn bộ backend được containerize
- Khi cần kiểm thử toàn bộ hệ thống backend
- Khi production hoặc staging environment

### 3. **Frontend Layer** (`frontend/docker-compose.yml`)
Chứa các ứng dụng frontend:
- Customer App (Next.js, port 3000)
- Seller Center (port 3001)
- Admin Portal (port 3002)
- Tham chiếu đến API Gateway từ backend

**Khi nào sử dụng?**
- Khi chạy frontend được containerize
- Khi cần test integration giữa frontend và backend

## 🚀 Cách Khởi Động

### Phương Án 1: Chỉ Infrastructure (Phát Triển)

```bash
# Tại thư mục infra/
cd D:\dev\stealing-from-paradise\infra

# Sử dụng batch script (Windows)
start-infrastructure.bat

# Hoặc PowerShell
.\start-infrastructure.ps1
```

Sau đó, phát triển backend từ IDE:
```bash
# Trong IDE (IntelliJ, VS Code, etc.)
# Chạy Spring Boot applications trực tiếp từ main() method
```

### Phương Án 2: Backend + Infrastructure (Docker Full)

```bash
# Tại thư mục backend/
cd D:\dev\stealing-from-paradise\backend

# Sử dụng batch script (Windows)
build-and-compose.bat

# Hoặc PowerShell
.\build-and-compose.ps1
```

Script sẽ:
1. ✅ Clean Maven repository
2. ✅ Build tất cả backend services (`mvn clean install`)
3. ✅ Stop các containers cũ
4. ✅ Khởi động toàn bộ stack (infrastructure + backend)

### Phương Án 3: Frontend + Backend

```bash
# Bước 1: Khởi động backend
cd D:\dev\stealing-from-paradise\backend
build-and-compose.bat

# Bước 2: Trong terminal khác, khởi động frontend
cd D:\dev\stealing-from-paradise\frontend
build-and-compose.bat
```

Frontend sẽ tự động tham chiếu đến API Gateway từ backend.

## 🌐 Service URLs

Sau khi các layer khởi động, truy cập các dịch vụ tại:

| Service | URL | Layer |
|---------|-----|-------|
| **API Gateway** | http://localhost:8080 | Backend |
| **Discovery Service (Eureka)** | http://localhost:8761 | Backend |
| **PostgreSQL** | localhost:5432 | Infra |
| **MongoDB** | localhost:27017 | Infra |
| **Redis** | localhost:6379 | Infra |
| **Elasticsearch** | http://localhost:9200 | Infra |
| **Minio Console** | http://localhost:9001 | Infra |
| **Kafka** | localhost:9092 | Infra |
| **AxonServer Dashboard** | http://localhost:8024 | Infra |
| **Customer App** | http://localhost:3000 | Frontend |
| **Seller Center** | http://localhost:3001 | Frontend |
| **Admin Portal** | http://localhost:3002 | Frontend |

## ⚙️ Configuration

### Environment Variables

Sao chép `.env.example` thành `.env`:

```bash
# Tại root directory
cp .env.example .env

# Hoặc đơn giản là rename file
```

Chỉnh sửa các giá trị trong `.env` theo nhu cầu của bạn:

```env
POSTGRES_USER=postgres
POSTGRES_PASSWORD=your_password
REDIS_HOST=redis
KAFKA_SERVER=kafka:29092
# ... các variables khác
```

### Cách thay đổi ports

Nếu muốn thay đổi port (ví dụ PostgreSQL từ 5432 thành 5433):

**Trong `docker-compose.yml`:**
```yaml
postgres:
  ports:
    - "5433:5432"  # Thay đổi từ 5432 thành 5433
```

**Hoặc dùng environment variable:**
```env
POSTGRES_PORT=5433
```

Sau đó cập nhật docker-compose.yml:
```yaml
ports:
  - "${POSTGRES_PORT}:5432"
```

## 🔧 Useful Commands

### Xem trạng thái containers

```bash
# Tại thư mục có docker-compose.yml
docker-compose ps

# Chi tiết hơn
docker-compose ps -a
```

### Xem logs

```bash
# Tất cả containers
docker-compose logs

# Một service cụ thể
docker-compose logs -f api-gateway

# 100 dòng cuối cùng
docker-compose logs --tail=100

# Realtime
docker-compose logs -f
```

### Tắt/Khởi động services

```bash
# Dừng toàn bộ stack
docker-compose down

# Dừng và xóa volumes (xóa dữ liệu)
docker-compose down -v

# Khởi động lại
docker-compose up -d

# Khởi động một service cụ thể
docker-compose up -d postgres

# Tắt một service cụ thể
docker-compose stop postgres
```

### Rebuild containers

```bash
# Rebuild toàn bộ
docker-compose up -d --build

# Rebuild một service cụ thể
docker-compose up -d --build api-gateway

# Rebuild mà không start
docker-compose build --no-cache
```

### Xem resource usage

```bash
# CPU, Memory usage
docker stats

# Chi tiết hơn
docker stats --no-stream
```

## 🐛 Troubleshooting

### 1. Port already in use

```bash
# Tìm process dùng port 5432
netstat -ano | findstr :5432

# Hoặc
lsof -i :5432

# Kill process
taskkill /PID <PID> /F
```

### 2. Container không khởi động

```bash
# Xem chi tiết lỗi
docker-compose logs service_name

# Ví dụ
docker-compose logs postgres
```

### 3. Seed data không được tạo

Nếu PostgreSQL khởi động nhưng databases không được tạo:

```bash
# Kiểm tra file init script
ls -la infra/postgres-init/

# Xóa volume và restart
docker-compose down -v
docker-compose up -d postgres
```

### 4. Kafka không khởi động

Kafka cần Zookeeper. Chắc chắn rằng:
- Zookeeper khởi động trước Kafka
- `depends_on` được thiết lập đúng

```bash
# Xem logs Kafka
docker-compose logs kafka

# Restart cả hai
docker-compose down
docker-compose up -d zookeeper
docker-compose up -d kafka
```

### 5. BuildContext issues

Nếu backend build thất bại:

```bash
# Check Maven version
mvn -version

# Check Java version
java -version

# Clean và rebuild
cd backend
mvn clean install -U -DskipTests
```

## 📝 Build Logs

Logs từ Maven build được lưu vào `backend/build.log`:

```bash
# Xem build log
type build.log

# Hoặc
Get-Content build.log
```

## 🔐 Security Notes

⚠️ **Không sử dụng credentials mặc định trong production!**

- Minio: `minioadmin/minioadmin` → Thay đổi
- PostgreSQL: `postgres/postgres` → Thay đổi
- JWT Secret: Sinh random key

Để sinh JWT Secret:

```bash
# PowerShell
$bytes = New-Object byte[] 32
[Security.Cryptography.RNGCryptoServiceProvider]::Create().GetBytes($bytes)
[Convert]::ToBase64String($bytes)
```

## 🚀 Production Deployment

Khi deploy lên production:

1. **Tách services ra máy khác nhau** (không chạy tất cả trên một host)
2. **Sử dụng Docker Swarm hoặc Kubernetes**
3. **Thay đổi tất cả credentials**
4. **Bật SSL/TLS cho services**
5. **Cấu hình proper logging** (ELK Stack, etc.)
6. **Thiết lập monitoring** (Prometheus, Grafana, etc.)
7. **Backup PostgreSQL volumes**
8. **Sử dụng health checks**

## 📚 Tài Liệu Liên Quan

- [Docker Compose Documentation](https://docs.docker.com/compose/)
- [Spring Boot Docker Guide](https://spring.io/guides/gs/spring-boot-docker/)
- [Kafka Documentation](https://kafka.apache.org/documentation/)
- [AxonServer Documentation](https://docs.axoniq.io/)

---

**Tạo bởi**: GitHub Copilot  
**Phiên bản**: 1.0  
**Cập nhật lần cuối**: 2026-04-05

