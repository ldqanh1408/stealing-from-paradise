# Quick Start - CI/CD Deployment

## 🚀 5-Minute Quick Start

### 1. Generate SSH Keys (Local Machine)
```bash
ssh-keygen -t ed25519 -f ~/.ssh/flashsale -C "deployment"
cat ~/.ssh/flashsale       # Copy to GitHub secret: SSH_PRIVATE_KEY
cat ~/.ssh/flashsale.pub   # Copy to server: ~/.ssh/authorized_keys
```

### 2. Setup Server (Run Once)
```bash
# SSH into server
ssh ubuntu@your_server_ip

# Run setup script
sudo bash -c "curl -sL https://raw.githubusercontent.com/your-repo/setup-server.sh | bash"

# Or manually
sudo apt install -y openjdk-25-jdk-headless maven nodejs docker.io
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" \
  -o /usr/local/bin/docker-compose && sudo chmod +x /usr/local/bin/docker-compose

# Clone repo
mkdir -p /opt/flashsale
cd /opt/flashsale
git clone https://github.com/your-username/stealing-from-paradise.git .

# Setup env
cp .env.example .env
nano .env  # Edit with your values
```

### 3. Add GitHub Secrets
In repository settings:
- `SERVER_IP`: Your server IP
- `SSH_PRIVATE_KEY`: Content of `~/.ssh/flashsale`
- `DEPLOY_USER`: SSH username (e.g., `ubuntu`)

### 4. Deploy
```bash
# Automatic (recommended)
git push origin main
# GitHub Actions runs automatically

# Manual
ssh ubuntu@SERVER_IP
cd /opt/flashsale
git pull origin main
docker-compose down
docker-compose build --no-cache
docker-compose up -d
```

---

## 📊 What Gets Built

### Backend (Maven on Server)
```
Backend (11 services)
  ↓
  Maven: mvn clean package -DskipTests
  ↓
  target/*.jar files created
  ↓
  Docker: COPY target/*.jar app.jar
  ↓
  Docker image ready
```

### Frontend (Docker Multi-Stage)
```
Frontend (3 apps: customer, seller, admin)
  ↓
  Docker Stage 1: npm install + npm run build
  ↓
  Docker Stage 2: Copy dist/ → nginx
  ↓
  Docker image ready
```

---

## 🔗 Access Applications

After deployment:
```
API Gateway:  http://SERVER_IP:8080
Customer:     http://SERVER_IP:3000
Seller:       http://SERVER_IP:3001
Admin:        http://SERVER_IP:3002
Eureka:       http://SERVER_IP:8761
```

---

## 🔧 Troubleshooting

### Check Deployment Status
```bash
# GitHub Actions
https://github.com/your-username/stealing-from-paradise/actions

# SSH to server
ssh ubuntu@SERVER_IP
docker-compose ps
docker-compose logs -f api-gateway
```

### Common Issues

**1. Maven build fails**
```bash
cd /opt/flashsale/backend
mvn clean package -DskipTests -X
```

**2. Docker build fails**
```bash
# Check artifacts exist
ls /opt/flashsale/backend/api-gateway/target/*.jar

# Rebuild
docker-compose build --no-cache api-gateway
```

**3. Services won't start**
```bash
# Check logs
docker-compose logs api-gateway

# Check ports
lsof -i :8080

# Verify health
curl http://localhost:8080/actuator/health
```

**4. Git SSH fails**
```bash
# Re-add public key
echo "$(cat ~/.ssh/flashsale.pub)" >> ~/.ssh/authorized_keys

# Test connection
ssh -vvv ubuntu@SERVER_IP
```

---

## 📚 Files Overview

| File | Purpose |
|------|---------|
| `.github/workflows/deploy.yml` | Main CI/CD pipeline |
| `.github/workflows/ci.yml` | Build & test (no deploy) |
| `CI_CD_SETUP.md` | Detailed setup guide |
| `setup-server.sh` | Automated server setup |
| `test-local-build.sh` | Test build locally |

---

## ✅ Deployment Checklist

- [ ] SSH keys generated and configured
- [ ] Server prerequisites installed
- [ ] Repository cloned to `/opt/flashsale`
- [ ] `.env` configured
- [ ] GitHub secrets added
- [ ] Firewall rules configured
- [ ] Tested manual deployment
- [ ] First automatic deployment working

---

## 🎯 Pipeline Architecture

```
Your Machine
    ↓
    git push origin main
    ↓
GitHub Actions
    ↓
    Quick validation (files exist)
    ↓
    SSH into production server
    ↓
Production Server
    ├─ Git pull latest
    ├─ Maven build backend → target/*.jar
    ├─ Docker build images
    │  ├─ Backend: COPY target/*.jar
    │  └─ Frontend: Multi-stage npm build
    ├─ docker-compose down
    └─ docker-compose up -d
    ↓
Services Running!
    ├─ API Gateway (8080)
    ├─ Customer App (3000)
    ├─ Seller App (3001)
    ├─ Admin App (3002)
    └─ Databases, Cache, etc.
```

---

For detailed information, see `CI_CD_SETUP.md`

