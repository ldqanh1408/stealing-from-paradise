# Windows Quick Setup Guide for CI/CD

## Prerequisites (Windows)

### 1. Install Git for Windows
- Download: https://git-scm.com/download/win
- Use Git Bash for SSH commands

### 2. Generate SSH Keys (Git Bash)
```bash
ssh-keygen -t ed25519 -f ~/.ssh/flashsale -C "deployment"
```

This creates:
- `~/.ssh/flashsale` (private key)
- `~/.ssh/flashsale.pub` (public key)

### 3. Copy Keys

**Private Key** (for GitHub):
```bash
cat ~/.ssh/flashsale
# Copy entire content to GitHub secret: SSH_PRIVATE_KEY
```

**Public Key** (for Server):
```bash
cat ~/.ssh/flashsale.pub
# Copy to server: ssh ubuntu@SERVER_IP
# Then: echo "KEY_CONTENT" >> ~/.ssh/authorized_keys
```

---

## GitHub Setup

1. Go to: Repository → Settings → Secrets and Variables → Actions

2. Add these secrets:
   - **SERVER_IP**: Your production server IP
   - **SSH_PRIVATE_KEY**: Content of `~/.ssh/flashsale`
   - **DEPLOY_USER**: SSH username (e.g., `ubuntu`)

---

## Deploy (Windows)

### Option 1: Using Git Bash
```bash
# Navigate to project
cd D:\dev\stealing-from-paradise

# Push to main
git add .
git commit -m "feat: your feature"
git push origin main

# Watch deployment
# Open: https://github.com/your-username/stealing-from-paradise/actions
```

### Option 2: Using PowerShell
```powershell
# Navigate to project
cd D:\dev\stealing-from-paradise

# Push to main
git add .
git commit -m "feat: your feature"
git push origin main

# Open browser to watch
Start-Process "https://github.com/your-username/stealing-from-paradise/actions"
```

---

## SSH to Server (Windows)

### Option 1: Git Bash
```bash
# Add to ~/.ssh/config
Host flashsale-prod
  HostName your_server_ip
  User ubuntu
  IdentityFile ~/.ssh/flashsale

# Connect
ssh flashsale-prod

# Navigate
cd /opt/flashsale
docker-compose ps
docker-compose logs -f
```

### Option 2: PowerShell (OpenSSH)
```powershell
# Check if OpenSSH installed (Windows 10+)
Get-WindowsCapability -Online | Where-Object Name -like 'OpenSSH*'

# Connect
ssh -i $env:USERPROFILE\.ssh\flashsale ubuntu@your_server_ip

# Check services
docker-compose ps
```

### Option 3: PuTTY/Terminals
1. Download PuTTY: https://www.putty.org/
2. Convert key: PuTTYgen → Import `~/.ssh/flashsale`
3. Connect and use SSH

---

## Monitor Deployment (Windows)

### GitHub Actions (Web)
1. Open: https://github.com/your-username/stealing-from-paradise/actions
2. Click latest workflow run
3. Watch real-time logs

### Server Logs (SSH)
```bash
ssh ubuntu@your_server_ip
cd /opt/flashsale

# Check services
docker-compose ps

# View logs
docker-compose logs -f api-gateway

# Specific service
docker logs -f flashsale-api-gateway

# Exit logs
# Press Ctrl+C
```

---

## Troubleshooting (Windows)

### SSH Key Permission Error
```bash
# In Git Bash, fix permissions
chmod 600 ~/.ssh/flashsale
chmod 700 ~/.ssh
```

### SSH Connection Refused
```bash
# Test connection with verbose output
ssh -vvv -i ~/.ssh/flashsale ubuntu@your_server_ip

# Check public key on server
ssh ubuntu@your_server_ip
cat ~/.ssh/authorized_keys
```

### GitHub Secret Issues
1. Verify `SSH_PRIVATE_KEY` doesn't have extra whitespace
2. Verify `SERVER_IP` is correct
3. Verify `DEPLOY_USER` matches server username

---

## Common Commands

### Git Operations
```bash
# Clone repo
git clone https://github.com/your-username/stealing-from-paradise.git

# Create feature branch
git checkout -b feature/my-feature

# Commit and push
git add .
git commit -m "feat: description"
git push origin feature/my-feature

# Create Pull Request on GitHub (web)

# After approval, merge to main
git checkout main
git merge feature/my-feature
git push origin main
# CI/CD automatically triggers
```

### Docker Commands (on server)
```bash
# List running containers
docker-compose ps

# View logs
docker-compose logs
docker-compose logs -f api-gateway

# Restart service
docker-compose restart api-gateway

# Stop all services
docker-compose down

# Start services
docker-compose up -d

# Remove everything (caution!)
docker-compose down -v
```

### Check Services (from Windows)
```bash
# From PowerShell or Git Bash
curl http://SERVER_IP:8080/actuator/health
curl http://SERVER_IP:3000
```

---

## Windows Tools Recommendation

### Essential
- **Git Bash**: SSH and Git commands
- **Visual Studio Code**: Code editor with Git integration
- **Windows Terminal**: Modern terminal

### Optional
- **PuTTY**: SSH client
- **WinSCP**: SFTP file transfer
- **Docker Desktop for Windows**: Test Docker locally

---

## Workflow Summary

```
Developer (Windows)
  ↓
Code locally in VS Code
  ↓
git add . && git commit && git push
  ↓
GitHub
  ↓
GitHub Actions Workflow
  ↓
SSH to Production Server
  ↓
Maven build
Docker build
docker-compose up
  ↓
Production Services Running
  ↓
Access: http://SERVER_IP:3000, :3001, :3002, etc.
```

---

## Advanced: Local Docker Testing

If you have Docker Desktop for Windows:

```bash
# Build locally
docker-compose build

# Start locally
docker-compose up -d

# Check services
docker-compose ps

# View logs
docker-compose logs

# Stop
docker-compose down
```

---

## Useful Links

- GitHub Actions: https://github.com/your-username/stealing-from-paradise/actions
- Production Server: http://your_server_ip:8080
- Documentation: See `CI_CD_SETUP.md`
- Quick Start: See `QUICKSTART_CICD.md`

---

## Quick Checklist

- [ ] Git installed on Windows
- [ ] SSH keys generated
- [ ] GitHub secrets added
- [ ] Server setup complete
- [ ] First deployment successful
- [ ] Can access services
- [ ] Can SSH to server
- [ ] Monitor logs working

