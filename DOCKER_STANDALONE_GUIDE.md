# 📦 Backend & Frontend Standalone Docker Setup

**Ngày**: 2026-04-19  
**Mục Đích**: Hướng dẫn chạy Backend & Frontend độc lập bằng Docker

---

## 📋 Tổng Quan

### Cấu Trúc Mới

```
stealing-from-paradise/
├── .env                         (Main .env - Project wide)
├── backend/
│   ├── .env                     (Backend .env - lấy từ parent)
│   ├── docker-compose-standalone.yml  (Backend + Infrastructure)
│   └── ... (services)
├── frontend/
│   ├── .env                     (Frontend .env - đã có)
│   ├── docker-compose-standalone.yml  (Frontend only - không phụ thuộc backend)
│   └── apps/
└── docker-compose.yml           (Main - tất cả)
```

### Lợi Ích

✅ **Backend độc lập**: Chạy backend + infrastructure riêng  
✅ **Frontend độc lập**: Chạy frontend mà không cần backend  
✅ **Flexibility**: Chọn chạy cái gì cần thiết  
✅ **Development**: Dễ dàng phát triển từng part riêng

---

## 🚀 Cách Sử Dụng

### Option 1: Chạy Tất Cả (Project Root)

```bash
cd stealing-from-paradise

# Chạy từ main docker-compose.yml
docker-compose up -d

# Chờ 3-5 phút
docker-compose ps

# Truy cập:
# - Customer: http://localhost:3000
# - Seller: http://localhost:3001
# - Admin: http://localhost:3002
# - API Gateway: http://localhost:8080
# - Eureka: http://localhost:8761
```

### Option 2: Chạy Chỉ Backend (Standalone)

```bash
cd stealing-from-paradise/backend

# Verify .env exists
ls -la .env  # Phải có

# Chạy backend + infrastructure
docker-compose -f docker-compose-standalone.yml up -d

# Chờ 3-5 phút
docker-compose -f docker-compose-standalone.yml ps

# Truy cập:
# - API Gateway: http://localhost:8080
# - Eureka: http://localhost:8761
# - Axon Server: http://localhost:8124

# Stop
docker-compose -f docker-compose-standalone.yml down
```

### Option 3: Chạy Chỉ Frontend (Standalone)

```bash
cd stealing-from-paradise/frontend

# Chạy 3 frontend apps (KHÔNG cần backend)
docker-compose -f docker-compose-standalone.yml up -d

# Chờ 1-2 phút
docker-compose -f docker-compose-standalone.yml ps

# Truy cập:
# - Customer: http://localhost:3000
# - Seller: http://localhost:3001
# - Admin: http://localhost:3002

# Stop
docker-compose -f docker-compose-standalone.yml down
```

---

## 🔧 Configuration

### Backend .env (`backend/.env`)

```bash
# Tôi đã tạo sẵn từ parent .env
# Chứa:
# - STRIPE keys
# - Database credentials
# - Infrastructure URLs
# - JVM settings

# Nếu muốn override, edit file này
nano backend/.env
```

### Frontend .env (`frontend/.env`)

```bash
# Sử dụng frontend/.env đã tồn tại
# Hoặc tạo mới:

# Standalone mode - không cần backend
VITE_API_URL=http://localhost:8080/api/v1
VITE_MOCK_API=true

# Connected mode - có backend
VITE_API_URL=http://localhost:8080/api/v1
VITE_MOCK_API=false
```

---

## 📊 Use Cases

### Scenario 1: Full Stack Development

```bash
# Terminal 1: Backend
cd backend
docker-compose -f docker-compose-standalone.yml up -d

# Terminal 2: Frontend
cd ../frontend
docker-compose -f docker-compose-standalone.yml up -d

# Test
curl http://localhost:8080/actuator/health  # Backend
# Browser: http://localhost:3000              # Frontend
```

### Scenario 2: Frontend Only Development

```bash
# Frontend developers không cần backend
cd frontend
docker-compose -f docker-compose-standalone.yml up -d

# Chỉnh sửa code, npm run dev có hot reload
# Hoặc dùng mock API trong standalone mode
```

### Scenario 3: Backend Only Development

```bash
# Backend developers test APIs
cd backend
docker-compose -f docker-compose-standalone.yml up -d

# Test API:
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{...}'
```

### Scenario 4: Production Full Stack

```bash
# Từ root project
docker-compose up -d  # Sử dụng main docker-compose.yml

# Tất cả services chạy cùng nhau
```

---

## 📝 Docker Compose Files

### `docker-compose.yml` (Project Root - Main)
- ✅ Chứa tất cả services (backend + frontend + infrastructure)
- ✅ Sử dụng `.env` cha
- ✅ Orchestration đầy đủ

### `backend/docker-compose-standalone.yml`
- ✅ Backend services + infrastructure
- ✅ Độc lập không phụ thuộc frontend
- ✅ Sử dụng `backend/.env`

### `frontend/docker-compose-standalone.yml`
- ✅ Frontend services (3 apps)
- ✅ Độc lập không phụ thuộc backend
- ✅ Sử dụng `frontend/.env`
- ✅ Có fallback mock API

---

## 🔍 Verify Setup

### Kiểm Tra Backend

```bash
cd backend

# Check config
cat .env | grep STRIPE

# Start backend
docker-compose -f docker-compose-standalone.yml up -d

# Verify
curl http://localhost:8080/actuator/health
curl http://localhost:8761/  # Eureka
curl http://localhost:8124/  # Axon

# View logs
docker-compose -f docker-compose-standalone.yml logs api-gateway
```

### Kiểm Tra Frontend

```bash
cd frontend

# Start frontend
docker-compose -f docker-compose-standalone.yml up -d

# Verify
curl http://localhost:3000/  # Customer
curl http://localhost:3001/  # Seller
curl http://localhost:3002/  # Admin

# View logs
docker-compose -f docker-compose-standalone.yml logs customer-app
```

---

## 🛑 Stop & Cleanup

### Stop Services (Giữ Data)

```bash
# Backend
cd backend
docker-compose -f docker-compose-standalone.yml down

# Frontend
cd frontend
docker-compose -f docker-compose-standalone.yml down

# Hoặc từ root
docker-compose down
```

### Complete Cleanup (Xóa Data)

```bash
# Backend
cd backend
docker-compose -f docker-compose-standalone.yml down -v

# Frontend
cd frontend
docker-compose -f docker-compose-standalone.yml down -v

# Hoặc từ root
docker-compose down -v
```

---

## ⚙️ Advanced Usage

### Override Environment Variables

```bash
# Start với custom env
cd backend
STRIPE_SECRET_KEY=sk_test_custom \
docker-compose -f docker-compose-standalone.yml up -d
```

### Custom Project Name

```bash
# Tránh conflict port
COMPOSE_PROJECT_NAME=flashsale_dev_backend \
docker-compose -f docker-compose-standalone.yml up -d
```

### View Logs

```bash
# Tất cả services
docker-compose -f docker-compose-standalone.yml logs -f

# Service cụ thể
docker-compose -f docker-compose-standalone.yml logs -f payment-service

# Tìm lỗi
docker-compose -f docker-compose-standalone.yml logs api-gateway | grep ERROR
```

---

## 📂 File Structure Reference

```
backend/
├── .env  ← NEW: Backend config (from parent .env)
├── docker-compose-standalone.yml  ← NEW: Backend standalone
├── pom.xml
├── docker/
│   ├── postgres/
│   │   └── init/
│   ├── mongo/
│   │   └── init/
│   └── kafka/
├── discovery-service/
├── api-gateway/
├── identity-service/
├── payment-service/
├── order-service/
├── flashsale-service/
├── product-service/
├── cart-service/
├── search-service/
├── notification-service/
└── worker-service/

frontend/
├── .env  ← Existing: Frontend config
├── docker-compose-standalone.yml  ← NEW: Frontend standalone
├── docker-compose.yml  ← Updated: Add support for standalone
├── apps/
│   ├── customer/
│   ├── seller/
│   └── admin/
└── shared/
```

---

## 🆘 Troubleshooting

### Port Already in Use

```bash
# Kiểm tra port
lsof -i :3000
lsof -i :8080

# Kill process
kill -9 <PID>

# Hoặc change port trong docker-compose
# Sửa: ports: ["3000:80"] → ports: ["3000:80"]
```

### Backend không kết nối tới Database

```bash
# Check postgres
docker-compose -f docker-compose-standalone.yml ps | grep postgres

# Verify connection
psql -h localhost -U postgres -d flashsale_platform -c "SELECT 1"

# Check logs
docker-compose -f docker-compose-standalone.yml logs postgres
```

### Frontend không kết nối tới Backend

```bash
# Verify backend running
curl http://localhost:8080/actuator/health

# Check frontend .env
cat frontend/.env | grep VITE_API_URL

# Check browser console (F12) for errors
```

---

## 📊 Quick Command Reference

```bash
# Backend Standalone
cd backend
docker-compose -f docker-compose-standalone.yml up -d
docker-compose -f docker-compose-standalone.yml down

# Frontend Standalone
cd frontend
docker-compose -f docker-compose-standalone.yml up -d
docker-compose -f docker-compose-standalone.yml down

# Full Stack (from root)
docker-compose up -d
docker-compose down

# View status
docker-compose -f docker-compose-standalone.yml ps

# View logs
docker-compose -f docker-compose-standalone.yml logs -f

# Clean everything
docker-compose -f docker-compose-standalone.yml down -v
```

---

## ✅ Summary

| Setup | Command | Notes |
|-------|---------|-------|
| **Full Stack** | `docker-compose up -d` | Từ root, tất cả |
| **Backend Only** | `cd backend && docker-compose -f docker-compose-standalone.yml up -d` | Độc lập |
| **Frontend Only** | `cd frontend && docker-compose -f docker-compose-standalone.yml up -d` | Độc lập, mock API |
| **Clean All** | `docker-compose down -v` | Xóa volumes |

---

**Ready to use! 🚀**

```bash
# Bắt đầu frontend
cd frontend && docker-compose -f docker-compose-standalone.yml up -d

# Hoặc backend
cd backend && docker-compose -f docker-compose-standalone.yml up -d

# Hoặc cả hai
docker-compose up -d
```

