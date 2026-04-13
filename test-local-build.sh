#!/bin/bash
# =============================================================================
# Local Test Deployment Script
# This simulates the CI/CD deployment process locally
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

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║         Local Build & Docker Test (Non-Docker Build)         ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

log_info "Project directory: $PROJECT_DIR"

# =============================================================================
# STEP 1: Check Prerequisites
# =============================================================================
log_info "Step 1: Checking prerequisites..."

if ! command -v java &> /dev/null; then
    log_error "Java not found"
    exit 1
fi
log_success "Java found: $(java -version 2>&1 | head -n 1)"

if ! command -v mvn &> /dev/null; then
    log_error "Maven not found"
    exit 1
fi
log_success "Maven found: $(mvn --version | head -n 1)"

if ! command -v node &> /dev/null; then
    log_error "Node.js not found"
    exit 1
fi
log_success "Node.js found: $(node --version)"

if ! command -v docker &> /dev/null; then
    log_error "Docker not found"
    exit 1
fi
log_success "Docker found: $(docker --version)"

# =============================================================================
# STEP 2: Build Backend with Maven
# =============================================================================
log_info "Step 2: Building backend with Maven..."
cd "$PROJECT_DIR/backend"
log_info "Running: mvn clean package -DskipTests -q"
mvn clean package -DskipTests -q
log_success "Backend JAR files created"

# List JAR files
log_info "Backend artifacts:"
find "$PROJECT_DIR/backend" -name "*.jar" -type f | grep -v ".jar.original" | head -n 5
cd "$PROJECT_DIR"

# =============================================================================
# STEP 3: Build Docker Images (from local artifacts)
# =============================================================================
log_info "Step 3: Building Docker images..."

# Backend services
SERVICES=(
    "discovery-service"
    "api-gateway"
)

for service in "${SERVICES[@]}"; do
    SERVICE_DIR="backend/$service"
    if [ -f "$SERVICE_DIR/Dockerfile" ] && [ -d "$SERVICE_DIR/target" ]; then
        log_info "Building $service Docker image..."
        docker build -t flashsale_${service}:test "$SERVICE_DIR"
        log_success "$service image built"
    fi
done

# Frontend apps (multi-stage)
for app in customer seller admin; do
    if [ -f "frontend/apps/$app/Dockerfile" ]; then
        log_info "Building $app-app Docker image..."
        docker build -t flashsale_${app}_app:test \
            -f ./frontend/apps/$app/Dockerfile \
            --build-arg APP_PATH=apps/$app \
            ./frontend
        log_success "$app-app image built"
    fi
done

# =============================================================================
# STEP 4: Show Results
# =============================================================================
log_info "Step 4: Showing build results..."

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Local Build Test Complete!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

log_info "Built JAR files:"
find "$PROJECT_DIR/backend" -name "*.jar" -type f | grep -v ".jar.original" | while read jar; do
    SIZE=$(du -h "$jar" | cut -f1)
    echo "  $SIZE  $(basename $jar)"
done

echo ""
log_info "Built Docker images:"
docker images | grep flashsale | awk '{printf "  %-30s %-15s %s\n", $1, $2, $3}'

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✓ Local build test successful!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "Next steps:"
echo "  1. Test deployment: docker-compose up -d"
echo "  2. Check services: docker-compose ps"
echo "  3. View logs: docker-compose logs -f api-gateway"
echo "  4. Stop services: docker-compose down"
echo ""

