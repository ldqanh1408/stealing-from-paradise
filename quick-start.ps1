# ==================================================================================
# Quick Start - Opens all necessary terminals for development
# PowerShell Version
# Mở tất cả các terminal cần thiết để phát triển hệ thống
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
    Choice = "Magenta"
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

Write-Host ""
Write-Header "STEALING FROM PARADISE - QUICK START ORCHESTRATOR"
Write-Host ""

# Get root directory
$rootDir = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Info "Root directory: $rootDir"
Write-Host ""

# Display menu
Write-Host ""
Write-Host "==================================================================================" -ForegroundColor $colors.Choice
Write-Host "SELECT YOUR DEVELOPMENT SETUP:" -ForegroundColor $colors.Choice
Write-Host "==================================================================================" -ForegroundColor $colors.Choice
Write-Host ""
Write-Host "1. INFRASTRUCTURE ONLY (for IDE development)" -ForegroundColor $colors.Warning
Write-Host "   - Starts databases, caches, message queues" -ForegroundColor $colors.Info
Write-Host "   - You develop microservices in IDE" -ForegroundColor $colors.Info
Write-Host ""
Write-Host "2. FULL BACKEND STACK (containerized)" -ForegroundColor $colors.Warning
Write-Host "   - Builds and starts all backend services in Docker" -ForegroundColor $colors.Info
Write-Host "   - Best for integration testing" -ForegroundColor $colors.Info
Write-Host ""
Write-Host "3. FULL STACK (Backend + Frontend)" -ForegroundColor $colors.Warning
Write-Host "   - Starts backend, frontend, and infrastructure" -ForegroundColor $colors.Info
Write-Host "   - Complete system testing" -ForegroundColor $colors.Info
Write-Host ""
Write-Host "4. CUSTOM (choose each component)" -ForegroundColor $colors.Warning
Write-Host "   - Manually select which layers to start" -ForegroundColor $colors.Info
Write-Host ""

$choice = Read-Host "Enter your choice (1-4)"

switch ($choice) {
    "1" { Start-InfrastructureOnly }
    "2" { Start-BackendOnly }
    "3" { Start-FullStack }
    "4" { Start-Custom }
    default {
        Write-Error-Custom "Invalid choice. Please select 1-4"
        exit 1
    }
}

# ==================================================================================
# INFRASTRUCTURE ONLY
# ==================================================================================
function Start-InfrastructureOnly {
    Write-Host ""
    Write-Info "Starting Infrastructure Only setup..."
    Write-Host ""

    $infraPath = Join-Path $rootDir "infra"
    Set-Location $infraPath

    # Open new PowerShell window
    Start-Process PowerShell -ArgumentList "-NoExit", "-Command", "& '.\start-infrastructure.ps1'"

    Write-Success "Infrastructure terminal opened"
    Write-Host ""
    Write-Info "You can now start microservices from your IDE:"
    Write-Host "  - Open IDE project in: $($rootDir)backend" -ForegroundColor $colors.Success
    Write-Host "  - Run individual Spring Boot applications" -ForegroundColor $colors.Success
    Write-Host "  - They will connect to infrastructure services" -ForegroundColor $colors.Success
    Write-Host ""
}

# ==================================================================================
# BACKEND ONLY
# ==================================================================================
function Start-BackendOnly {
    Write-Host ""
    Write-Info "Starting Backend Stack (with Infrastructure)..."
    Write-Host ""

    $backendPath = Join-Path $rootDir "backend"
    Set-Location $backendPath

    # Open new PowerShell window
    Start-Process PowerShell -ArgumentList "-NoExit", "-Command", "& '.\build-and-compose.ps1'"

    Write-Success "Backend terminal opened"
    Write-Host ""
    Write-Info "Wait for all containers to be healthy before testing"
    Write-Host ""
}

# ==================================================================================
# FULL STACK
# ==================================================================================
function Start-FullStack {
    Write-Host ""
    Write-Info "Starting Full Stack (Backend + Frontend + Infrastructure)..."
    Write-Host ""

    Write-Info "Opening Backend Stack..."
    $backendPath = Join-Path $rootDir "backend"
    Set-Location $backendPath
    Start-Process PowerShell -ArgumentList "-NoExit", "-Command", "& '.\build-and-compose.ps1'"

    Write-Info "Waiting for backend to initialize..."
    Start-Sleep -Seconds 30

    Write-Info "Opening Frontend Stack..."
    $frontendPath = Join-Path $rootDir "frontend"
    Set-Location $frontendPath
    Start-Process PowerShell -ArgumentList "-NoExit", "-Command", "& '.\build-and-compose.ps1'"

    Write-Host ""
    Write-Success "Both backend and frontend terminals opened"
    Write-Host ""
    Write-Info "Frontend will take ~5-10 minutes to build and start"
    Write-Host ""
    Write-Host "Available at:" -ForegroundColor $colors.Success
    Write-Host "  - API Gateway: http://localhost:8080" -ForegroundColor $colors.Success
    Write-Host "  - Customer App: http://localhost:3000" -ForegroundColor $colors.Success
    Write-Host "  - Seller Center: http://localhost:3001" -ForegroundColor $colors.Success
    Write-Host "  - Admin Portal: http://localhost:3002" -ForegroundColor $colors.Success
    Write-Host ""
}

# ==================================================================================
# CUSTOM
# ==================================================================================
function Start-Custom {
    Write-Host ""
    Write-Header "CUSTOM SETUP - Choose which layers to start"
    Write-Host ""

    $infraChoice = Read-Host "Start Infrastructure? (y/n)"
    $backendChoice = Read-Host "Start Backend Services? (y/n)"
    $frontendChoice = Read-Host "Start Frontend Apps? (y/n)"

    if ($infraChoice -eq "y" -or $infraChoice -eq "Y") {
        Write-Info "Starting Infrastructure..."
        $infraPath = Join-Path $rootDir "infra"
        Set-Location $infraPath
        Start-Process PowerShell -ArgumentList "-NoExit", "-Command", "& '.\start-infrastructure.ps1'"
        Start-Sleep -Seconds 10
    }

    if ($backendChoice -eq "y" -or $backendChoice -eq "Y") {
        Write-Info "Starting Backend..."
        $backendPath = Join-Path $rootDir "backend"
        Set-Location $backendPath
        Start-Process PowerShell -ArgumentList "-NoExit", "-Command", "& '.\build-and-compose.ps1'"
        Start-Sleep -Seconds 30
    }

    if ($frontendChoice -eq "y" -or $frontendChoice -eq "Y") {
        Write-Info "Starting Frontend..."
        $frontendPath = Join-Path $rootDir "frontend"
        Set-Location $frontendPath
        Start-Process PowerShell -ArgumentList "-NoExit", "-Command", "& '.\build-and-compose.ps1'"
    }

    Write-Host ""
    Write-Success "Selected components are starting"
    Write-Host ""
}

