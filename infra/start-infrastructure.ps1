# ==================================================================================
# Infrastructure-Only Docker Compose Script
# PowerShell Version
# Khởi động chỉ các dịch vụ hạ tầng (databases, message queues, etc.)
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
Write-Header "STEALING FROM PARADISE - INFRASTRUCTURE STACK"
Write-Host ""

# Get the script directory
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

Write-Info "Current directory: $(Get-Location)"
Write-Host ""

# ==================================================================================
# STEP 1: Check if Docker is available
# ==================================================================================
Write-Info "[STEP 1] Checking Docker installation..."
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
# STEP 2: Load environment variables from .env file
# ==================================================================================
Write-Info "[STEP 2] Loading environment variables..."
if (Test-Path ".env") {
    Write-Success ".env file found"
    Get-Content ".env" | ForEach-Object {
        if ($_ -match "^([^=]+)=(.*)$") {
            $key = $matches[1]
            $value = $matches[2]
            Set-Item -Path "env:$key" -Value $value
        }
    }
} else {
    Write-Warning-Custom ".env file not found in parent directory"
}
Write-Host ""

# ==================================================================================
# STEP 3: Start Infrastructure Stack
# ==================================================================================
Write-Info "[STEP 3] Starting Infrastructure stack..."
Write-Info "This will start all databases, caches, and message queues..."
Write-Host ""

# Stop any existing infrastructure containers
Write-Info "Stopping any existing infrastructure containers..."
docker-compose down -v --remove-orphans 2>&1 | Out-Null

Write-Host ""
Write-Info "Starting Infrastructure stack..."
docker-compose up -d 2>&1

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Error-Custom "Docker Compose startup FAILED!"
    Write-Info "Run 'docker-compose logs' for details"
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host ""
Write-Success "Infrastructure stack started"
Write-Host ""

# ==================================================================================
# STEP 4: Display stack status
# ==================================================================================
Write-Info "[STEP 4] Checking container health status..."
Write-Host ""

Start-Sleep -Seconds 5
docker-compose ps

Write-Host ""
Write-Header "INFRASTRUCTURE STACK READY!"
Write-Host ""

Write-Info "Available services:"
Write-Host "  - PostgreSQL:    localhost:5432" -ForegroundColor $colors.Success
Write-Host "  - MongoDB:       localhost:27017" -ForegroundColor $colors.Success
Write-Host "  - Redis:         localhost:6379" -ForegroundColor $colors.Success
Write-Host "  - Elasticsearch: localhost:9200" -ForegroundColor $colors.Success
Write-Host "  - Minio:         localhost:9000 (API), http://localhost:9001 (Console)" -ForegroundColor $colors.Success
Write-Host "  - Kafka:         localhost:9092" -ForegroundColor $colors.Success
Write-Host "  - Zookeeper:     localhost:2181" -ForegroundColor $colors.Success
Write-Host "  - AxonServer:    localhost:8024 (Dashboard), localhost:8124 (gRPC)" -ForegroundColor $colors.Success
Write-Host ""

Write-Info "Next steps:"
Write-Host "  1. Start backend services: cd ..\backend; .\build-and-compose.ps1" -ForegroundColor $colors.Warning
Write-Host "  2. Or start frontend: cd ..\frontend; .\build-and-compose.ps1" -ForegroundColor $colors.Warning
Write-Host "  3. Or develop from IDE using this infrastructure" -ForegroundColor $colors.Warning
Write-Host ""

Write-Info "Useful commands:"
Write-Host "  - View logs:     docker-compose logs -f [service_name]" -ForegroundColor $colors.Warning
Write-Host "  - Stop stack:    docker-compose down" -ForegroundColor $colors.Warning
Write-Host "  - Stop and reset volumes: docker-compose down -v" -ForegroundColor $colors.Warning
Write-Host ""

Read-Host "Press Enter to exit"

