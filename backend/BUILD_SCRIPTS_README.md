# Build and Docker Compose Scripts

Các script tự động hóa quy trình build Maven và khởi động Docker Compose stack cho backend.

## 📋 Scripts Có Sẵn

### 1. **build-and-compose.bat** (Windows Batch)
Script dành cho Windows Command Prompt hoặc PowerShell.

**Cách sử dụng:**
```cmd
cd D:\dev\stealing-from-paradise\backend
build-and-compose.bat
```

### 2. **build-and-compose.ps1** (PowerShell)
Script dành cho PowerShell (modern và colorful hơn).

**Cách sử dụng:**
```powershell
cd D:\dev\stealing-from-paradise\backend
Set-ExecutionPolicy -ExecutionPolicy Bypass -Scope Process -Force
.\build-and-compose.ps1
```

> **Note**: Nếu gặp lỗi `cannot be loaded because running scripts is disabled`, chạy lệnh Set-ExecutionPolicy trên.

## 🔄 Quy Trình Script

Cả hai script đều thực hiện các bước sau:

1. ✅ **Kiểm tra Maven** - Đảm bảo Maven được cài đặt
2. ✅ **Kiểm tra Docker** - Đảm bảo Docker được cài đặt
3. ✅ **Load .env file** - Tải biến môi trường từ `.env` (nếu có)
4. ✅ **Làm sạch build cũ** - Xóa tất cả thư mục `target/` cũ
5. ✅ **Build Maven** - Chạy `mvn clean install -DskipTests -U`
6. ✅ **Dừng containers cũ** - Chạy `docker-compose down -v --remove-orphans`
7. ✅ **Khởi động Docker Compose** - Chạy `docker-compose up -d`
8. ✅ **Hiển thị trạng thái** - Liệt kê tất cả containers và tình trạng của chúng

## 📝 Build Log

Tệp `build.log` sẽ được tạo trong thư mục backend, chứa toàn bộ output Maven build.

Để xem log:
```cmd
type build.log
```

hoặc trong PowerShell:
```powershell
Get-Content build.log
```

## 🌐 Các Services Khả Dụng

Sau khi script chạy xong, có thể truy cập các services tại:

| Service | URL |
|---------|-----|
| API Gateway | http://localhost:8080 |
| Discovery Service (Eureka) | http://localhost:8761 |
| Elasticsearch | http://localhost:9200 |
| Minio Console | http://localhost:9001 |
| AxonServer | http://localhost:8024 |
| Redis | localhost:6379 |
| PostgreSQL | localhost:5432 |
| MongoDB | localhost:27017 |
| Kafka | localhost:9092 |

## 🔧 Các Lệnh Hữu Ích

Sau khi containers đang chạy:

### Xem logs của một service:
```bash
docker-compose logs -f service_name
docker-compose logs -f api-gateway
docker-compose logs -f discovery-service
```

### Dừng toàn bộ stack:
```bash
docker-compose down
```

### Dừng stack và xóa volumes (sẽ xóa dữ liệu):
```bash
docker-compose down -v
```

### Rebuild một service cụ thể:
```bash
docker-compose up -d --build service_name
docker-compose up -d --build api-gateway
```

### Xem trạng thái containers:
```bash
docker-compose ps
```

### Xem logs của tất cả services:
```bash
docker-compose logs
```

### Khởi động một service đã được tắt:
```bash
docker-compose up -d service_name
```

### Tắt một service cụ thể:
```bash
docker-compose stop service_name
```

## 🐛 Troubleshooting

### Maven build thất bại:
1. Kiểm tra file `build.log`
2. Chắc chắn rằng Java 25 được cài đặt: `java -version`
3. Chắc chắn rằng Maven được cài đặt: `mvn -version`

### Docker containers không khởi động:
1. Chắc chắn Docker Desktop đang chạy
2. Kiểm tra logs: `docker-compose logs service_name`
3. Chắc chắn ports không bị chiếm dụng

### Containers khởi động nhưng không healthy:
1. Chờ thêm 10-20 giây (infrastructure cần thời gian khởi động)
2. Kiểm tra health checks: `docker-compose ps`
3. Xem logs chi tiết: `docker-compose logs -f service_name`

## 📦 .env File

Nếu có file `.env` trong thư mục backend, script sẽ tự động load các biến môi trường từ nó.

Ví dụ `.env`:
```env
POSTGRES_USER=postgres
POSTGRES_PASSWORD=yourpassword
REDIS_HOST=redis
DB_HOST=postgres
EUREKA_URI=http://discovery-service:8761/eureka
AXON_SERVER=axonserver:8124
KAFKA_SERVER=kafka:29092
MONGO_HOST=mongo
```

## ⚡ Tối Ưu Hóa

### Nếu build quá lâu:
- Script mặc định bỏ qua tests (`-DskipTests`)
- Nếu muốn chạy tests, chỉnh sửa script và thay `-DskipTests` thành ` ` (xóa)

### Nếu muốn rebuild từ scratch:
```cmd
REM Windows
mvn clean install -DskipTests -U

REM hoặc
build-and-compose.bat
```

### Nếu chỉ muốn khởi động Docker mà không rebuild Maven:
```bash
docker-compose down -v --remove-orphans
docker-compose up -d
```

## 📖 Thêm Thông Tin

- Maven Documentation: https://maven.apache.org/
- Docker Compose Documentation: https://docs.docker.com/compose/
- Spring Boot: https://spring.io/projects/spring-boot

---

**Tạo bởi**: GitHub Copilot  
**Phiên bản**: 1.0  
**Cập nhật lần cuối**: 2026-04-05

