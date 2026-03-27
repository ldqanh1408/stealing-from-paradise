# ==================================================================================
# Build and Docker Compose Script for Stealing from Paradise - Backend
# PowerShell Version
# This script:
# 1. Cleans previous builds
# 2. Builds all Maven projects
# 3. Starts Docker Compose stack
# ==================================================================================

Set-StrictMode -Version Latest
$ErrorActionPreference = "Continue"

# Colors for output
$colors = @{
    Header = "Cyan"
    Success = "Green"
    Error = "Red"
    Warning = "Yellow"
    Info = "White"
}

function Write-Header {
    param([string]$text)
    Write-Host "==================================================================================" -ForegroundColor $colors.Header
    Write-Host $text -ForegroundColor $colors.Header
    Write-Host "==================================================================================" -ForegroundColor $colors.Header
}

function Write-Info {
    param([string]$text)
    Write-Host "[INFO] $text" -ForegroundColor $colors.Info
}

function Write-Success {
    param([string]$text)
    Write-Host "[OK] $text" -ForegroundColor $colors.Success
}

function Write-Error-Custom {
    param([string]$text)
    Write-Host "[ERROR] $text" -ForegroundColor $colors.Error
}

function Write-Warning-Custom {
    param([string]$text)
    Write-Host "[WARNING] $text" -ForegroundColor $colors.Warning
}

Write-Host ""
Write-Header "STEALING FROM PARADISE - BACKEND BUILD AND DOCKER COMPOSE ORCHESTRATION"
Write-Host ""

# Get the script directory
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

Write-Info "Current directory: $(Get-Location)"
Write-Host ""

# ==================================================================================
# STEP 1: Check if Maven is available
# ==================================================================================
Write-Info "[STEP 1] Checking Maven installation..."
$mvnPath = Get-Command mvn -ErrorAction SilentlyContinue
if (-not $mvnPath) {
    Write-Error-Custom "Maven not found in PATH. Please install Maven first."
    Write-Info "Download from: https://maven.apache.org/download.cgi"
    Read-Host "Press Enter to exit"
    exit 1
}
Write-Success "Maven found"
Write-Host ""

# ==================================================================================
# STEP 2: Check if Docker is available
# ==================================================================================
Write-Info "[STEP 2] Checking Docker installation..."
$dockerPath = Get-Command docker -ErrorAction SilentlyContinue
if (-not $dockerPath) {
    Write-Error-Custom "Docker not found in PATH. Please install Docker first."
    Write-Info "Download from: https://www.docker.com/products/docker-desktop"
    Read-Host "Press Enter to exit"
    exit 1
}
Write-Success "Docker found"
Write-Host ""

# ==================================================================================
# STEP 3: Load environment variables from .env file
# ==================================================================================
Write-Info "[STEP 3] Loading environment variables from .env file..."
if (Test-Path ".env") {
    Write-Success ".env file found"
    # Read and set environment variables
    Get-Content ".env" | ForEach-Object {
        if ($_ -match "^([^=]+)=(.*)$") {
            $key = $matches[1]
            $value = $matches[2]
            Set-Item -Path "env:$key" -Value $value
        }
    }
} else {
    Write-Warning-Custom ".env file not found in $scriptDir"
    Write-Info "Using default environment variables"
}
Write-Host ""

# ==================================================================================
# STEP 4: Clean Maven build directories
# ==================================================================================
Write-Info "[STEP 4] Cleaning Maven build directories..."

# Clean root target
$rootTarget = ".\target"
if (Test-Path $rootTarget) {
    Write-Info "Removing root target directory..."
    Remove-Item -Recurse -Force $rootTarget -ErrorAction SilentlyContinue
}

# Clean service targets
$services = @(
    "discovery-service",
    "api-gateway",
    "cart-service",
    "flashsale-service",
    "notification-service",
    "search-service",
    "woker-service"
)

foreach ($service in $services) {
    $targetDir = ".\$service\target"
    if (Test-Path $targetDir) {
        Write-Info "Removing $service\target..."
        Remove-Item -Recurse -Force $targetDir -ErrorAction SilentlyContinue
    }
}

# Clean nested services
$nestedServices = @(
    "identity-service\identity-domain",
    "order-service\order-domain",
    "product-service\product-domain",
    "payment-service\payment-domain"
)

foreach ($service in $nestedServices) {
    $targetDir = ".\$service\target"
    if (Test-Path $targetDir) {
        Write-Info "Removing $service\target..."
        Remove-Item -Recurse -Force $targetDir -ErrorAction SilentlyContinue
    }
}

Write-Success "Maven directories cleaned"
Write-Host ""

# ==================================================================================
# STEP 5: Build all Maven projects
# ==================================================================================
Write-Info "[STEP 5] Building all Maven projects..."
Write-Info "This may take a few minutes..."
Write-Host ""

$buildLog = "build.log"
mvn clean install -DskipTests -U 2>&1 | Tee-Object -FilePath $buildLog

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Error-Custom "Maven build FAILED!"
    Write-Info "Check $buildLog for details"
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host ""
Write-Success "Maven build completed successfully"
Write-Host ""

# ==================================================================================
# STEP 6: Start Docker Compose stack
# ==================================================================================
Write-Info "[STEP 6] Starting Docker Compose stack..."
Write-Info "This will start all containers (infrastructure and microservices)"
Write-Host ""

# Stop any existing containers first
Write-Info "Stopping any existing containers..."
docker-compose down -v --remove-orphans 2>&1 | Out-Null

Write-Host ""
Write-Info "Starting fresh Docker Compose stack..."
docker-compose up -d 2>&1

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Error-Custom "Docker Compose startup FAILED!"
    Write-Info "Run 'docker-compose logs' for details"
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host ""
Write-Success "Docker Compose stack started"
Write-Host ""

# ==================================================================================
# STEP 7: Display stack status
# ==================================================================================
Write-Info "[STEP 7] Checking container health status..."
Write-Host ""

Start-Sleep -Seconds 5
docker-compose ps

Write-Host ""
Write-Header "BUILD AND DOCKER COMPOSE COMPLETE!"
Write-Host ""

Write-Info "Available services:"
Write-Host "  - API Gateway:         http://localhost:8080" -ForegroundColor $colors.Success
Write-Host "  - Discovery Service:   http://localhost:8761" -ForegroundColor $colors.Success
Write-Host "  - Elasticsearch:       http://localhost:9200" -ForegroundColor $colors.Success
Write-Host "  - Minio Console:       http://localhost:9001" -ForegroundColor $colors.Success
Write-Host "  - AxonServer:          http://localhost:8024" -ForegroundColor $colors.Success
Write-Host ""

Write-Info "Useful commands:"
Write-Host "  - View logs:           docker-compose logs -f [service_name]" -ForegroundColor $colors.Warning
Write-Host "  - Stop stack:          docker-compose down" -ForegroundColor $colors.Warning
Write-Host "  - Stop and remove volumes: docker-compose down -v" -ForegroundColor $colors.Warning
Write-Host "  - Rebuild specific service: docker-compose up -d --build [service_name]" -ForegroundColor $colors.Warning
Write-Host ""

Write-Info "Build log saved to: $buildLog"
Write-Host ""

Read-Host "Press Enter to exit"

