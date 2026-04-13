# CI/CD Deployment Guide

## 📋 Overview

Hệ thống CI/CD này được thiết kế để:

1. **Build Backend trên Server**: Maven build → target/*.jar
2. **Build Frontend trong Docker**: Multi-stage build → dist/
3. **Build Docker Images**: Copy artifacts và run services
4. **Deploy tự động**: Git push → GitHub Actions → Server

---

## 🔧 Required Setup

### 1. Generate SSH Keys

```bash
# On your local machine
ssh-keygen -t ed25519 -f ~/.ssh/flashsale_deploy -C "deployment"
cat ~/.ssh/flashsale_deploy           # Private key (copy to GitHub)
cat ~/.ssh/flashsale_deploy.pub       # Public key (copy to server)
```

### 2. Server Setup

```bash
# SSH vào server
ssh ubuntu@your_server_ip

# Cài đặt Java 25
sudo apt update && sudo apt install -y openjdk-25-jdk-headless

# Cài đặt Maven
sudo apt install -y maven

# Cài đặt Node.js & npm
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt install -y nodejs

# Cài đặt Docker
curl -fsSL https://get.docker.com -o get-docker.sh && sudo sh get-docker.sh

# Cài đặt Docker Compose
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" \
  -o /usr/local/bin/docker-compose && sudo chmod +x /usr/local/bin/docker-compose

# Add SSH public key
mkdir -p ~/.ssh
echo "your_public_key_content" >> ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys
chmod 700 ~/.ssh

# Clone repository
mkdir -p /opt/flashsale
cd /opt/flashsale
git clone https://github.com/your-username/stealing-from-paradise.git .

# Setup .env
cp .env.example .env
nano .env  # Edit with your values
```

### 3. GitHub Secrets Setup

Go to: **GitHub Repository** → **Settings** → **Secrets and variables** → **Actions**

Add these secrets:

| Secret | Value |
|--------|-------|
| **SERVER_IP** | Your server IP or hostname |
| **SSH_PRIVATE_KEY** | Full content of `~/.ssh/flashsale_deploy` |
| **DEPLOY_USER** | SSH username (e.g., `ubuntu`) |

---

## 🚀 How It Works

### Pipeline Flow

```
1. You push to main/develop branch
            ↓
2. GitHub Actions triggered
            ↓
3. Quick validation (check files exist)
            ↓
4. SSH into production server
            ↓
5. On server, execute deployment:
   - Git pull latest code
   - Maven build backend → target/*.jar
   - Docker build images (multi-stage)
   - docker-compose up -d
            ↓
6. Services running on production
```

### Build Process Details

#### Backend
```
Maven build on server:
  source/ → compile → package → target/*.jar
  
Docker build:
  Dockerfile: COPY target/*.jar app.jar
  Result: JRE image with JAR (no build in Docker)
```

#### Frontend
```
Docker multi-stage build:
  Stage 1 (builder):
    - npm install
    - npm run build → dist/
  
  Stage 2 (production):
    - Copy dist/ → nginx
    - Serve with nginx
```

---

## 📌 Deployment Instructions

### Automatic Deployment (Recommended)

```bash
# 1. Make changes locally
git add .
git commit -m "feat: your feature"

# 2. Push to main or develop
git push origin main

# 3. Watch GitHub Actions
# Go to: https://github.com/your-username/stealing-from-paradise/actions

# 4. Access deployed services
http://SERVER_IP:8080        # API Gateway
http://SERVER_IP:3000        # Customer App
http://SERVER_IP:3001        # Seller App
http://SERVER_IP:3002        # Admin App
http://SERVER_IP:8761        # Eureka
```

### Manual Deployment (SSH)

```bash
# SSH to server
ssh $DEPLOY_USER@$SERVER_IP

cd /opt/flashsale

# Update code
git fetch origin
git reset --hard origin/main

# Build backend
cd backend
mvn clean package -DskipTests
cd ..

# Build Docker images
docker-compose build --no-cache

# Restart services
docker-compose down
docker-compose up -d

# Verify
docker-compose ps
curl http://localhost:8080/actuator/health
```

---

## 🔍 Monitoring & Troubleshooting

### Check GitHub Actions Status

```bash
# View logs in real-time
https://github.com/your-username/stealing-from-paradise/actions
```

### SSH into Server & Check Logs

```bash
ssh $DEPLOY_USER@$SERVER_IP
cd /opt/flashsale

# View running containers
docker-compose ps

# Check service logs
docker-compose logs -f api-gateway
docker-compose logs -f discovery-service

# Check health
curl http://localhost:8080/actuator/health
```

### Common Issues

#### 1. Maven build fails
```bash
cd /opt/flashsale/backend
mvn clean compile -X   # Verbose mode for debugging
```

#### 2. Docker build fails
```bash
# Check if service directory exists
ls -la backend/api-gateway/target/

# Rebuild from scratch
docker-compose build --no-cache api-gateway
```

#### 3. Services won't start
```bash
# Check port conflicts
lsof -i :8080
lsof -i :3000

# Check resource usage
docker stats

# View full logs
docker-compose logs
```

#### 4. Git authentication fails
```bash
# Re-add SSH public key on server
echo "$(cat ~/.ssh/flashsale_deploy.pub)" >> ~/.ssh/authorized_keys

# Test SSH connection
ssh -vvv $DEPLOY_USER@$SERVER_IP
```

---

## 📁 Directory Structure

```
/opt/flashsale/
├── .git/                    # Git repository
├── .github/
│   └── workflows/
│       └── deploy.yml       # CI/CD pipeline
├── backend/                 # Maven project
│   ├── pom.xml
│   ├── discovery-service/
│   ├── api-gateway/
│   └── ... other services
├── frontend/                # Frontend apps
│   ├── apps/
│   │   ├── customer/
│   │   ├── seller/
│   │   └── admin/
│   └── shared/
├── docker-compose.yml       # Service orchestration
├── .env                     # Environment variables
└── .env.example             # Template
```

---

## 🔐 Security Best Practices

1. **SSH Keys**
   - Use Ed25519 keys (more secure than RSA)
   - Never commit private keys to git
   - Rotate keys periodically

2. **Environment Variables**
   - Store sensitive data in `.env` (not in git)
   - Use `.env.example` as template
   - Update passwords in `.env` on production

3. **Server Access**
   - Use firewall to limit port access
   - Enable SSH key-based auth only
   - Disable password login
   - Use strong passwords in `.env`

4. **GitHub Secrets**
   - Rotate secrets periodically
   - Audit secret access logs
   - Use least privilege approach

---

## 📊 Performance Tips

### Speed up Maven builds
```bash
# Use local Maven cache
export MAVEN_OPTS="-Xmx2g -XX:+TieredCompilation -XX:TieredStopAtLevel=1"

# Skip tests in CI/CD
mvn clean package -DskipTests
```

### Speed up Docker builds
```bash
# Enable BuildKit
export DOCKER_BUILDKIT=1

# Use BuildKit cache
docker build --build-arg BUILDKIT_INLINE_CACHE=1 .
```

### Reduce image sizes
- Backend: Use eclipse-temurin JRE (not JDK)
- Frontend: Use nginx:alpine (not debian)

---

## 🆘 Emergency Procedures

### Rollback to Previous Version

```bash
ssh $DEPLOY_USER@$SERVER_IP
cd /opt/flashsale

# Get commit history
git log --oneline -n 10

# Rollback to previous commit
git checkout COMMIT_HASH

# Rebuild and deploy
docker-compose down
docker-compose build --no-cache
docker-compose up -d
```

### Force Redeploy

```bash
# Push empty commit to trigger CI/CD
git commit --allow-empty -m "chore: force redeploy"
git push origin main
```

---

## 📚 Additional Resources

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Docker Compose Documentation](https://docs.docker.com/compose/)
- [Maven Documentation](https://maven.apache.org/guides/)
- [Project README](./README.md)
- [Project Overview](./PROJECT_OVERVIEW.md)

---

## ✅ Deployment Checklist

Before first deployment:
- [ ] Server prerequisites installed (Java, Maven, Node.js, Docker)
- [ ] SSH keys generated and configured
- [ ] GitHub secrets added (SERVER_IP, SSH_PRIVATE_KEY, DEPLOY_USER)
- [ ] Repository cloned to /opt/flashsale
- [ ] .env configured with actual values
- [ ] Firewall rules configured
- [ ] Database volumes created
- [ ] Tested manual deployment first

---

**Created**: 2026-04-13  
**Last Updated**: 2026-04-13  
**Maintainer**: DevOps Team

