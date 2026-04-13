#!/bin/bash
# =============================================================================
# FlashSale Server Setup Script
# This script sets up all prerequisites on Ubuntu server for CI/CD deployment
# =============================================================================

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if running as root
if [ "$EUID" -ne 0 ]; then
    log_error "This script must be run as root (use: sudo bash setup-server.sh)"
    exit 1
fi

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║         FlashSale Server Setup - Ubuntu 20.04+              ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""

# =============================================================================
# STEP 1: Update System
# =============================================================================
log_info "Step 1: Updating system packages..."
apt update
apt upgrade -y
apt install -y curl wget git build-essential
log_success "System updated"

# =============================================================================
# STEP 2: Install Java 25
# =============================================================================
log_info "Step 2: Installing Java 25..."
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | head -n 1)
    log_warning "Java already installed: $JAVA_VERSION"
else
    apt install -y openjdk-25-jdk-headless
    log_success "Java 25 installed"
fi

java -version

# =============================================================================
# STEP 3: Install Maven
# =============================================================================
log_info "Step 3: Installing Maven..."
if command -v mvn &> /dev/null; then
    MVN_VERSION=$(mvn --version | head -n 1)
    log_warning "Maven already installed: $MVN_VERSION"
else
    apt install -y maven
    log_success "Maven installed"
fi

mvn --version

# =============================================================================
# STEP 4: Install Node.js 18
# =============================================================================
log_info "Step 4: Installing Node.js 18..."
if command -v node &> /dev/null; then
    NODE_VERSION=$(node --version)
    log_warning "Node.js already installed: $NODE_VERSION"
else
    curl -fsSL https://deb.nodesource.com/setup_18.x | bash -
    apt install -y nodejs
    log_success "Node.js 18 installed"
fi

node --version
npm --version

# =============================================================================
# STEP 5: Install Docker
# =============================================================================
log_info "Step 5: Installing Docker..."
if command -v docker &> /dev/null; then
    DOCKER_VERSION=$(docker --version)
    log_warning "Docker already installed: $DOCKER_VERSION"
else
    curl -fsSL https://get.docker.com -o get-docker.sh
    sh get-docker.sh
    rm get-docker.sh
    log_success "Docker installed"
fi

docker --version

# =============================================================================
# STEP 6: Install Docker Compose
# =============================================================================
log_info "Step 6: Installing Docker Compose..."
if command -v docker-compose &> /dev/null; then
    DC_VERSION=$(docker-compose --version)
    log_warning "Docker Compose already installed: $DC_VERSION"
else
    COMPOSE_VERSION=$(curl -s https://api.github.com/repos/docker/compose/releases/latest | grep 'tag_name' | cut -d'"' -f4)
    curl -L "https://github.com/docker/compose/releases/download/$COMPOSE_VERSION/docker-compose-$(uname -s)-$(uname -m)" \
        -o /usr/local/bin/docker-compose
    chmod +x /usr/local/bin/docker-compose
    log_success "Docker Compose installed"
fi

docker-compose --version

# =============================================================================
# STEP 7: Configure Docker for non-root user
# =============================================================================
log_info "Step 7: Configuring Docker permissions..."
if getent group docker > /dev/null; then
    log_warning "Docker group already exists"
else
    groupadd docker
fi

# Add current user to docker group if it's not root
if [ -n "$SUDO_USER" ]; then
    usermod -aG docker "$SUDO_USER"
    log_success "User $SUDO_USER added to docker group"
fi

# =============================================================================
# STEP 8: Setup project directory
# =============================================================================
log_info "Step 8: Setting up project directory..."
PROJECT_DIR="/opt/flashsale"

if [ -d "$PROJECT_DIR" ]; then
    log_warning "Project directory already exists at $PROJECT_DIR"
else
    mkdir -p "$PROJECT_DIR"
    log_success "Created project directory at $PROJECT_DIR"
fi

# =============================================================================
# STEP 9: Configure firewall
# =============================================================================
log_info "Step 9: Configuring firewall rules..."

if command -v ufw &> /dev/null; then
    # Enable firewall
    ufw --force enable > /dev/null 2>&1 || true

    # Open required ports
    ufw allow 22/tcp     # SSH
    ufw allow 80/tcp     # HTTP
    ufw allow 443/tcp    # HTTPS
    ufw allow 8080/tcp   # API Gateway
    ufw allow 3000/tcp   # Customer App
    ufw allow 3001/tcp   # Seller App
    ufw allow 3002/tcp   # Admin App
    ufw allow 8761/tcp   # Eureka

    log_success "Firewall configured"
else
    log_warning "UFW not available, configure firewall manually"
fi

# =============================================================================
# STEP 10: Create log directory
# =============================================================================
log_info "Step 10: Setting up logging..."
DEPLOY_USER=${SUDO_USER:-$(whoami)}
mkdir -p /var/log/flashsale
chown $DEPLOY_USER:$DEPLOY_USER /var/log/flashsale
log_success "Log directory created at /var/log/flashsale"

# =============================================================================
# STEP 11: Summary
# =============================================================================
echo ""
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║              ✓ SERVER SETUP COMPLETE                        ║"
echo "╠══════════════════════════════════════════════════════════════╣"
echo "║                                                              ║"
echo "║ ✓ Java 25 installed                                         ║"
echo "║ ✓ Maven installed                                           ║"
echo "║ ✓ Node.js 18 installed                                      ║"
echo "║ ✓ Docker installed                                          ║"
echo "║ ✓ Docker Compose installed                                  ║"
echo "║ ✓ Firewall configured                                       ║"
echo "║ ✓ Project directory ready at $PROJECT_DIR                   ║"
echo "║                                                              ║"
echo "╠══════════════════════════════════════════════════════════════╣"
echo "║ NEXT STEPS:                                                  ║"
echo "║                                                              ║"
echo "║ 1. Clone repository:                                         ║"
echo "║    cd $PROJECT_DIR                                     ║"
echo "║    git clone https://github.com/your-repo.git .             ║"
echo "║                                                              ║"
echo "║ 2. Setup environment:                                        ║"
echo "║    cp .env.example .env                                     ║"
echo "║    nano .env  # Edit with your values                       ║"
echo "║                                                              ║"
echo "║ 3. Configure GitHub Secrets:                                 ║"
echo "║    - SERVER_IP: This server's IP                            ║"
echo "║    - SSH_PRIVATE_KEY: Your SSH private key                  ║"
echo "║    - DEPLOY_USER: $DEPLOY_USER                                ║"
echo "║                                                              ║"
echo "║ 4. Push to main branch to trigger CI/CD:                     ║"
echo "║    git push origin main                                      ║"
echo "║                                                              ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""

log_success "Server is ready for deployment!"

