# ==================================================================================
# Frontend Build and Docker Compose Script
# PowerShell Version
# This script builds and starts frontend applications
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
Write-Header "STEALING FROM PARADISE - FRONTEND BUILD AND DOCKER COMPOSE"
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
    # Read and set environment variables
    Get-Content ".env" | ForEach-Object {
        if ($_ -match "^([^=]+)=(.*)$") {
            $key = $matches[1]
            $value = $matches[2]
            Set-Item -Path "env:$key" -Value $value
        }
    }
} else {
    Write-Warning-Custom ".env file not found"
}
Write-Host ""

# ==================================================================================
# STEP 3: Start Docker Compose stack
# ==================================================================================
Write-Info "[STEP 3] Starting Docker Compose stack..."
Write-Info "Building and starting frontend applications..."
Write-Host ""

# Stop any existing containers first
Write-Info "Stopping any existing containers..."
docker-compose down --remove-orphans 2>&1 | Out-Null

Write-Host ""
Write-Info "Starting fresh Docker Compose stack..."
docker-compose up -d --build 2>&1

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
# STEP 4: Display stack status
# ==================================================================================
Write-Info "[STEP 4] Checking container health status..."
Write-Host ""

Start-Sleep -Seconds 5
docker-compose ps

Write-Host ""
Write-Header "FRONTEND BUILD AND DOCKER COMPOSE COMPLETE!"
Write-Host ""

Write-Info "Available frontend applications:"
Write-Host "  - Customer App:   http://localhost:3000" -ForegroundColor $colors.Success
Write-Host "  - Seller Center:  http://localhost:3001" -ForegroundColor $colors.Success
Write-Host "  - Admin Portal:   http://localhost:3002" -ForegroundColor $colors.Success
Write-Host ""

Write-Info "Useful commands:"
Write-Host "  - View logs:           docker-compose logs -f [service_name]" -ForegroundColor $colors.Warning
Write-Host "  - Stop stack:          docker-compose down" -ForegroundColor $colors.Warning
Write-Host "  - Rebuild specific app: docker-compose up -d --build [service_name]" -ForegroundColor $colors.Warning
Write-Host ""

Read-Host "Press Enter to exit"

