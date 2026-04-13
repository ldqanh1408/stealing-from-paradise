# 🚀 CI/CD Pipeline - START HERE

## ✅ What's Done

Hệ thống CI/CD hoàn chỉnh đã được tạo để:

1. **Build backend trên server** với Maven → `target/*.jar`
2. **Build Docker images** từ các artifacts
3. **Deploy tự động** khi push code lên `main` branch
4. **Health check** sau deployment

---

## 📋 Files Created

| File | Purpose | Size |
|------|---------|------|
| `.github/workflows/deploy.yml` | Main CI/CD pipeline | 348 lines |
| `CI_CD_SETUP.md` | Detailed setup guide | 800+ lines |
| `QUICKSTART_CICD.md` | 5-minute quick start | 200 lines |
| `CI_CD_SUMMARY.md` | Complete overview | 400 lines |
| `WINDOWS_SETUP.md` | Windows guide | 300 lines |
| `setup-server.sh` | Server setup script | 200 lines |
| `test-local-build.sh` | Local test script | 150 lines |

---

## 🎯 Quick Start (3 Bước)

### Bước 1: Tạo SSH Keys (Local Machine)

**Linux/Mac:**
```bash
ssh-keygen -t ed25519 -f ~/.ssh/flashsale -C "deployment"
```

**Windows (Git Bash):**
```bash
ssh-keygen -t ed25519 -f ~/.ssh/flashsale -C "deployment"
```

### Bước 2: Add GitHub Secrets

Đi tới: Repository → Settings → Secrets and variables → Actions

Thêm 3 secrets:
```
SERVER_IP = Your server IP/hostname
SSH_PRIVATE_KEY = (copy nội dung file ~/.ssh/flashsale)
DEPLOY_USER = ubuntu (hoặc username của bạn)
```

### Bước 3: Deploy

```bash
# Push code lên main branch
git push origin main

# GitHub Actions tự động chạy
# Mở: https://github.com/your-username/stealing-from-paradise/actions
# để xem tiến trình
```

✅ **Xong!** Services sẽ chạy trong 5-10 phút

---

## 📖 Documentation

### Bạn là người mới?
→ Đọc: **`QUICKSTART_CICD.md`** (5 phút)

### Bạn cần chi tiết?
→ Đọc: **`CI_CD_SETUP.md`** (20 phút)

### Bạn dùng Windows?
→ Đọc: **`WINDOWS_SETUP.md`** (10 phút)

### Bạn muốn overview?
→ Đọc: **`CI_CD_SUMMARY.md`** (10 phút)

---

## 🔧 Server Setup

### Tự động (Recommended)
```bash
ssh ubuntu@YOUR_SERVER_IP
sudo bash -c "$(curl -sL https://raw.githubusercontent.com/your-repo/setup-server.sh)"
```

### Thủ công
Chi tiết xem trong: `CI_CD_SETUP.md` → "Server Setup" section

---

## 🚀 How It Works

```
1. Developer push code
              ↓
2. GitHub Actions triggered
              ↓
3. SSH vào server
              ↓
4. Trên server:
   - Git pull
   - Maven: mvn clean package → target/*.jar
   - Docker: docker build (using target/*.jar)
   - docker-compose up
              ↓
5. Services running!
   - API Gateway: 8080
   - Customer App: 3000
   - Seller App: 3001
   - Admin App: 3002
   - ... + databases & services
```

---

## ✨ Features

✅ Tự động deploy khi git push  
✅ Build backend trên server (Maven)  
✅ Docker images từ artifacts  
✅ Health check tự động  
✅ SSH key authentication  
✅ Error handling & logging  
✅ Rollback support  

---

## 📊 Build Process

### Backend (Maven on Server)
```
Java Source
  ↓
mvn clean package -DskipTests
  ↓
target/
├─ discovery-service-1.0.0-SNAPSHOT.jar
├─ api-gateway-1.0.0-SNAPSHOT.jar
├─ ... (11 services total)
  ↓
Docker build: COPY target/*.jar
  ↓
Docker image ready
```

### Frontend (Docker Multi-Stage)
```
TypeScript + React Source
  ↓
Docker Stage 1: npm build → dist/
Docker Stage 2: COPY dist/ → nginx
  ↓
Docker image ready
```

---

## 🔍 Check Deployment Status

### GitHub Actions (Real-time)
```
https://github.com/your-username/stealing-from-paradise/actions
```

### SSH to Server
```bash
ssh ubuntu@YOUR_SERVER_IP
docker-compose ps
docker-compose logs -f api-gateway
```

### Check Services
```bash
# API Gateway
curl http://YOUR_SERVER_IP:8080/actuator/health

# Customer App
curl http://YOUR_SERVER_IP:3000

# Seller App
curl http://YOUR_SERVER_IP:3001
```

---

## 🐛 Troubleshooting

### SSH Connection Failed
→ Check: `WINDOWS_SETUP.md` → SSH section

### Maven Build Fails
→ Check: `CI_CD_SETUP.md` → Troubleshooting

### Docker Issues
→ Check: `CI_CD_SETUP.md` → Common Issues

### Services Won't Start
→ Check: `CI_CD_SETUP.md` → Monitoring

---

## 📚 All Documentation

```
├── QUICKSTART_CICD.md ........ START HERE (5 min)
├── CI_CD_SETUP.md ............ Complete guide (20 min)
├── CI_CD_SUMMARY.md .......... Overview (10 min)
├── WINDOWS_SETUP.md .......... Windows guide (10 min)
├── FILES_SUMMARY.md .......... Files overview
└── CI_CD_COMPLETE.md ......... Completion status
```

---

## ✅ Checklist

- [ ] SSH keys generated
- [ ] GitHub secrets added
- [ ] Server setup complete
- [ ] First deployment working
- [ ] Can access services
- [ ] Can view logs
- [ ] Ready for production

---

## 🎯 Next Steps

1. **Bây giờ**: Đọc `QUICKSTART_CICD.md` (5 phút)
2. **Tiếp theo**: Setup server (10 phút)
3. **Sau đó**: Add GitHub secrets (2 phút)
4. **Cuối cùng**: Push code để test (1 phút)

---

## 💡 Pro Tips

```bash
# Test build locally (optional)
bash test-local-build.sh

# Check setup
ssh ubuntu@YOUR_SERVER_IP
ls -la /opt/flashsale

# Manual deploy
cd /opt/flashsale
git pull && docker-compose down
docker-compose build && docker-compose up -d

# View all services
docker-compose ps

# Clear everything (be careful!)
docker-compose down -v
```

---

## 📞 Quick Links

- GitHub Actions: https://github.com/your-repo/actions
- Docker Hub: https://hub.docker.com/
- GitHub Docs: https://docs.github.com/en/actions
- Docker Docs: https://docs.docker.com/

---

**Status**: ✅ READY TO DEPLOY

**Bạn đã sẵn sàng!** 🎉

Hãy bắt đầu từ `QUICKSTART_CICD.md` →

