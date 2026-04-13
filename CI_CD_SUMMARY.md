# CI/CD Pipeline - Complete Setup Summary

## 📋 What Has Been Created

### 1. GitHub Actions Workflows

#### `.github/workflows/deploy.yml` (Main Pipeline)
- **Triggers**: Push to `main` or `develop` branch, manual trigger
- **Flow**:
  1. Quick validation (check files exist)
  2. SSH into production server
  3. On server: Git pull → Maven build → Docker build → docker-compose up
  4. Verify deployment
  5. Send notification

**Key Features**:
- ✅ Backend built with Maven on server (produces JAR files)
- ✅ Docker images built with pre-compiled artifacts
- ✅ Frontend uses multi-stage Docker build
- ✅ Comprehensive logging and error handling
- ✅ Health check verification

#### `.github/workflows/ci.yml` (Existing - for PR/develop)
- Runs on pull requests and develop branch
- Tests only, no deployment
- Validates code quality

---

### 2. Setup & Configuration Files

#### `CI_CD_SETUP.md` (Detailed Guide)
- Complete setup instructions
- Server prerequisites
- GitHub secrets configuration
- Troubleshooting guide
- Security best practices

#### `QUICKSTART_CICD.md` (Quick Reference)
- 5-minute quick start
- Common commands
- Troubleshooting shortcuts
- Architecture overview

#### `.env.example` (Environment Template)
- Pre-configured template
- All required variables
- Comments for each setting

---

### 3. Helper Scripts

#### `setup-server.sh` (Automated Setup)
Installs all prerequisites on Ubuntu server:
- Java 25
- Maven
- Node.js 18
- Docker
- Docker Compose
- Configures firewall
- Creates project directory

**Usage**:
```bash
sudo bash setup-server.sh
```

#### `test-local-build.sh` (Local Testing)
Tests build process locally without full CI/CD:
- Checks prerequisites
- Builds backend with Maven
- Builds Docker images
- Shows results

**Usage**:
```bash
bash test-local-build.sh
```

---

### 4. Updated Dockerfiles

#### Backend Dockerfiles
- ✅ Already configured to use `COPY target/*.jar`
- No changes needed
- Expects pre-built JAR from Maven

#### Frontend Dockerfiles (Kept as-is)
- ✅ Multi-stage build maintained
- Build happens inside Docker
- Original structure preserved

---

## 🔄 How It Works

### Deployment Flow

```
Developer commits & pushes to main
         ↓
GitHub Actions triggered
         ↓
validate: Check files exist
         ↓
SSH key authentication
         ↓
SSH to production server
         ↓
Execute deployment.sh on server:
  ├─ Git pull
  ├─ Maven: mvn clean package -DskipTests
  │  └─ Creates: backend/*/target/*.jar
  ├─ Docker build (uses JAR + multi-stage frontend)
  │  ├─ Backend: COPY target/*.jar app.jar
  │  └─ Frontend: Multi-stage npm build + nginx
  ├─ docker-compose down (stop old services)
  ├─ docker-compose up -d (start new services)
  ├─ Wait 30 seconds
  └─ Health check verification
         ↓
Services running on production server
```

### Build Artifacts

```
Maven Build (Server)
├─ backend/discovery-service/target/discovery-service-1.0.0-SNAPSHOT.jar
├─ backend/api-gateway/target/api-gateway-1.0.0-SNAPSHOT.jar
├─ backend/identity-service/target/identity-service-1.0.0-SNAPSHOT.jar
├─ backend/product-service/target/product-service-1.0.0-SNAPSHOT.jar
├─ backend/order-service/target/order-service-1.0.0-SNAPSHOT.jar
├─ backend/payment-service/target/payment-service-1.0.0-SNAPSHOT.jar
├─ backend/cart-service/target/cart-service-1.0.0-SNAPSHOT.jar
├─ backend/flashsale-service/target/flashsale-service-1.0.0-SNAPSHOT.jar
├─ backend/search-service/target/search-service-1.0.0-SNAPSHOT.jar
├─ backend/notification-service/target/notification-service-1.0.0-SNAPSHOT.jar
└─ backend/worker-service/target/worker-service-1.0.0-SNAPSHOT.jar

Docker Images (from artifacts)
├─ flashsale_discovery-service:latest
├─ flashsale_api-gateway:latest
├─ flashsale_identity-service:latest
├─ flashsale_product-service:latest
├─ flashsale_order-service:latest
├─ flashsale_payment-service:latest
├─ flashsale_cart-service:latest
├─ flashsale_flashsale-service:latest
├─ flashsale_search-service:latest
├─ flashsale_notification-service:latest
├─ flashsale_worker-service:latest
├─ flashsale_customer_app:latest
├─ flashsale_seller_app:latest
└─ flashsale_admin_app:latest
```

---

## 🚀 Quick Start (3 Steps)

### Step 1: Generate SSH Keys
```bash
ssh-keygen -t ed25519 -f ~/.ssh/flashsale -C "deployment"
```

### Step 2: Add GitHub Secrets
Go to: Repository Settings → Secrets and Variables → Actions

Add:
- `SERVER_IP`: Your server IP
- `SSH_PRIVATE_KEY`: Content of `~/.ssh/flashsale`
- `DEPLOY_USER`: SSH username (e.g., `ubuntu`)

### Step 3: Deploy
```bash
git push origin main
```

Then watch: https://github.com/your-username/stealing-from-paradise/actions

---

## 📁 File Structure

```
stealing-from-paradise/
├── .github/
│   └── workflows/
│       ├── deploy.yml          ← Main CI/CD pipeline (CREATED)
│       └── ci.yml              ← PR validation (already exists)
├── CI_CD_SETUP.md              ← Detailed guide (CREATED)
├── QUICKSTART_CICD.md          ← Quick reference (CREATED)
├── setup-server.sh             ← Server setup script (CREATED)
├── test-local-build.sh         ← Local test script (CREATED)
├── .env.example                ← Environment template (exists)
├── docker-compose.yml          ← Orchestration (exists)
├── backend/
│   ├── pom.xml
│   ├── */Dockerfile            ← Uses COPY target/*.jar
│   └── */src/
├── frontend/
│   ├── apps/*/Dockerfile       ← Multi-stage build (kept as-is)
│   └── apps/*/src/
└── ...
```

---

## ✅ Server Requirements

Must have installed:
- Java 25
- Maven 3.8+
- Node.js 18+
- npm
- Docker
- Docker Compose
- Git
- curl

**Recommended**:
- Ubuntu 20.04+
- 8GB+ RAM
- 50GB+ storage
- SSH key authentication

---

## 🔒 Security Setup

### SSH Keys
1. Generate: `ssh-keygen -t ed25519 -f ~/.ssh/flashsale`
2. Add public key to server: `~/.ssh/authorized_keys`
3. Add private key to GitHub: `SSH_PRIVATE_KEY` secret

### Environment Variables
1. Create `.env` from `.env.example`
2. Update with actual values (passwords, API keys)
3. **Never commit** `.env` to git
4. Copy to server: `/opt/flashsale/.env`

### GitHub Secrets
1. `SERVER_IP`: Server hostname/IP
2. `SSH_PRIVATE_KEY`: Private SSH key content
3. `DEPLOY_USER`: SSH username

---

## 🔄 Update Cycle

### For Regular Updates
```bash
# Make changes locally
git add .
git commit -m "feat: your feature"

# Push to main
git push origin main

# GitHub Actions automatically:
# - Validates code
# - Builds backend
# - Builds Docker images
# - Deploys to server
# - Verifies health
```

### Check Deployment Status
```bash
# Option 1: GitHub UI
https://github.com/your-repo/actions

# Option 2: SSH to server
ssh ubuntu@SERVER_IP
docker-compose ps
docker-compose logs -f
```

---

## 🆘 Troubleshooting

### Issue: SSH Connection Failed
```bash
# Check GitHub secret: SSH_PRIVATE_KEY
# Check server: SSH public key in ~/.ssh/authorized_keys
# Verify: Server IP in GitHub secret: SERVER_IP
```

### Issue: Maven Build Fails
```bash
# SSH to server
ssh ubuntu@SERVER_IP
cd /opt/flashsale/backend
mvn clean compile -X  # Verbose output
```

### Issue: Docker Build Fails
```bash
# Check JAR files exist
ls -la /opt/flashsale/backend/api-gateway/target/

# Rebuild
docker-compose build --no-cache api-gateway
```

### Issue: Services Won't Start
```bash
ssh ubuntu@SERVER_IP
docker-compose logs
docker-compose restart
```

---

## 📚 Documentation

| File | Purpose |
|------|---------|
| `CI_CD_SETUP.md` | Complete setup and troubleshooting |
| `QUICKSTART_CICD.md` | Quick reference and common commands |
| `setup-server.sh` | Automated server setup |
| `test-local-build.sh` | Local build testing |
| `.github/workflows/deploy.yml` | GitHub Actions pipeline |
| `docker-compose.yml` | Service orchestration |

---

## 🎯 Next Steps

1. **Setup Server**
   - Run: `sudo bash setup-server.sh`
   - Or manual: Follow `CI_CD_SETUP.md`

2. **Configure GitHub**
   - Add SSH secrets to repository

3. **Test Deployment**
   - Push to main branch
   - Monitor GitHub Actions
   - Access deployed services

4. **Monitor Logs**
   - GitHub Actions: https://github.com/your-repo/actions
   - Server: `docker-compose logs -f`

---

## 📞 Support

For issues or questions:
1. Check `CI_CD_SETUP.md` troubleshooting section
2. Check server logs: `docker-compose logs`
3. Check GitHub Actions logs
4. SSH to server and debug manually

---

**Created**: 2026-04-13  
**Status**: ✅ Complete and Ready for Production  
**System**: GitHub Actions + Docker + Server Deployment

