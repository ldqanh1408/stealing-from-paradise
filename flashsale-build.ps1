# ============================================================
# flashsale-build.ps1
# Build & run script for the Flash Sale E-Commerce platform.
#
# SYNTAX:
#   .\flashsale-build.ps1 -Up [-All|-Infra|-Backend|-Frontend] [options]
#   .\flashsale-build.ps1 -Down [-All|-Infra|-Backend|-Frontend] [options]
#   .\flashsale-build.ps1 -Build [-MavenParallel] [-SkipBuild]
#   .\flashsale-build.ps1 -BuildFrontend
#   .\flashsale-build.ps1 -Stop
#   .\flashsale-build.ps1 -Clean [-V] [-Rmi]
#   .\flashsale-build.ps1 -Status | -Logs | -Ports | -Health | -Help
#   .\flashsale-build.ps1 -Restart <service>
#   .\flashsale-build.ps1 -Tail <service> [-Lines <n>]
#   .\flashsale-build.ps1 -Exec <service> <command>
#   .\flashsale-build.ps1 -Menu
#
# EXAMPLES:
#   .\flashsale-build.ps1 -Up -All -D                # Start everything + stripe-listener (dev)
#   .\flashsale-build.ps1 -Up -All -D -NoStripeWebhook  # Start without Stripe CLI (staging/prod)
#   .\flashsale-build.ps1 -Up -Infra                 # Start infrastructure only
#   .\flashsale-build.ps1 -Up -Backend -D           # Start backend + stripe-listener (dev)
#   .\flashsale-build.ps1 -Up -Backend -D -NoStripeWebhook  # Backend only, no stripe-listener
#   .\flashsale-build.ps1 -Up -Frontend              # Start frontend only
#   .\flashsale-build.ps1 -Up -Infra -Backend        # Start infra + backend
#   .\flashsale-build.ps1 -Up -Backend -Frontend     # Start backend + frontend
#   .\flashsale-build.ps1 -Up -All -D -RemoveOrphans # Start all with orphan cleanup
#
#   .\flashsale-build.ps1 -Down -All                 # Stop everything
#   .\flashsale-build.ps1 -Down -All -V             # Stop + remove volumes
#   .\flashsale-build.ps1 -Down -All -V -Rmi        # Stop + volumes + images
#   .\flashsale-build.ps1 -Down -Backend              # Stop backend only
#   .\flashsale-build.ps1 -Down -Frontend            # Stop frontend only
#   .\flashsale-build.ps1 -Down -Infra               # Stop infrastructure only
#
#   .\flashsale-build.ps1 -Build                     # Maven build (no docker)
#   .\flashsale-build.ps1 -Build -MavenParallel      # Parallel Maven build
#   .\flashsale-build.ps1 -BuildFrontend             # NPM build for frontend
#   .\flashsale-build.ps1 -Clean -V -Rmi            # Full nuclear clean
#   .\flashsale-build.ps1 -Stop                     # docker compose stop
#
#   .\flashsale-build.ps1 -Restart fs-payment        # Restart specific container
#   .\flashsale-build.ps1 -Tail fs-gateway -Lines 50 # Tail container logs
#   .\flashsale-build.ps1 -Exec fs-postgres psql -U postgres -d flashsale_platform
#   .\flashsale-build.ps1 -Menu                      # Interactive menu
#
# ============================================================

param(
    # --- ACTIONS (mutually exclusive) ---
    [switch]$Up,
    [switch]$Down,
    [switch]$Build,
    [switch]$BuildFrontend,
    [switch]$Stop,
    [switch]$Clean,
    [switch]$Status,
    [switch]$Logs,
    [switch]$Ports,
    [switch]$Health,
    [switch]$Help,
    [switch]$Menu,
    [switch]$Restart,
    [switch]$Exec,
    [switch]$Tail,

    # --- TARGETS (composable, default = all) ---
    [switch]$All,
    [switch]$Infra,
    [switch]$Backend,
    [switch]$Frontend,

    # --- UP OPTIONS ---
    [switch]$D,
    [switch]$Detach,
    [switch]$SkipBuild,
    [switch]$MavenParallel,
    [switch]$FrontendProd,

    # --- DOWN / CLEAN OPTIONS ---
    [switch]$V,
    [switch]$RemoveVolumes,
    [switch]$Rmi,
    [switch]$RemoveImages,
    [switch]$RemoveOrphans,
    [switch]$Remove,

    # --- UP OPTIONS ---
    [switch]$NoStripeWebhook,

    # --- RESTART / TAIL / EXEC ---
    [string]$Service,
    [string]$Command,
    [int]$Lines = 30
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = if ($PSScriptRoot) { $PSScriptRoot } else { $PWD.Path }

# --- Alias resolution ---
if ($D)           { $Detach = $true }
if ($V)           { $RemoveVolumes = $true }
if ($Rmi)         { $RemoveImages = $true }
if ($Remove)      { $Down = $true }

# ==============================================================================
# VALIDATION
# ==============================================================================

function Test-DockerRunning {
    try {
        $null = docker info 2>&1
        return $true
    } catch {
        Write-Host "[FAIL] Docker is not running. Start Docker Desktop and try again." -ForegroundColor Red
        return $false
    }
}

function Test-EnvFile {
    $envPath = Join-Path $ProjectRoot '.env'
    if (-not (Test-Path $envPath)) {
        Write-Host "[WARN] .env not found at project root. Copy .env.example to .env first." -ForegroundColor Yellow
        return $false
    }
    $content = Get-Content $envPath -Raw
    $required = @(
        @{Name='STRIPE_SECRET_KEY';       Display='Stripe Secret Key'},
        @{Name='STRIPE_PUBLISHABLE_KEY'; Display='Stripe Publishable Key'}
    )
    $allOk = $true
    foreach ($var in $required) {
        if ($content -notmatch "(?m)^$($var.Name)=") {
            Write-Host "[WARN] Missing $($var.Display) in .env" -ForegroundColor Yellow
            $allOk = $false
        }
    }
    return $allOk
}

function Test-ServiceExists {
    param([string]$Svc)
    $all = Get-AllServices
    if ($Svc -notin $all) {
        Write-Host "[WARN] Unknown service: $Svc" -ForegroundColor Yellow
        Write-Host "Valid services:" -ForegroundColor Yellow
        $all | ForEach-Object { Write-Host "  $_" -ForegroundColor DarkGray }
        return $false
    }
    return $true
}

# ==============================================================================
# HELP TEXT
# ==============================================================================

if ($Help) {
    $fg = 'Cyan'
    Write-Host @"

FLASH SALE BUILD SCRIPT
============================================================

DEPLOYMENT MODES (run from project root):

  .dev   → Stripe CLI + Backend + Frontend (local dev)
           .\flashsale-build.ps1 -Up -All -D
           docker compose -f docker-compose.yml -f docker-compose.dev.yml up --build -d

  .prod  → Backend + Frontend (no Stripe CLI — Stripe Dashboard sends webhooks)
           .\flashsale-build.ps1 -Up -All -D -NoStripeWebhook
           docker compose -f docker-compose.yml up --build -d

  mock  → Frontend only, no backend (uses mock data)
           .\flashsale-build.ps1 -Up -Frontend -D
           cd frontend && docker compose -f docker-compose.yml up --build -d

ACTIONS  (choose one):
  -Up           Start services          -Down         Stop services
  -Build        Maven build only       -BuildFrontend NPM build only
  -Stop         docker compose stop    -Clean         Nuclear clean
  -Status       Container status       -Logs          Tail all logs
  -Ports        Show exposed ports     -Health        Check health
  -Restart      Restart a container    -Tail          Tail container logs
  -Exec         Run command in container
  -Menu         Interactive menu

UP OPTIONS:
  -Detach (-D)    Background mode (docker compose -d)
  -SkipBuild      Skip Maven/NPM build step
  -MavenParallel  Maven -T 2C (parallel threads)
  -FrontendProd   Use nginx production containers
  -NoStripeWebhook Skip Stripe CLI listener (.prod / staging mode)

TARGETS  (composable, default = all):
  -All           Everything             -Infra        Infrastructure
  -Backend       Backend microservices  -Frontend     Frontend apps

  Combine targets freely:
    -Up -Infra -Backend          Start infra + backend
    -Up -Backend -Frontend       Start backend + frontend
    -Down -Frontend             Stop frontend only

DOWN / CLEAN OPTIONS:
  -V (-RemoveVolumes)      Remove named volumes  [DATA LOSS]
  -Rmi (-RemoveImages)     Remove service images
  -RemoveOrphans          Remove orphaned containers

  Common combos:
    -Down -All -V             Stop + remove volumes
    -Down -All -V -Rmi       Stop + volumes + images
    -Clean -V -Rmi           Nuclear clean

RESTART / TAIL / EXEC:
  -Restart <service>  Restart a specific container
  -Tail <service> [-Lines <n>]  Tail logs from a container (default 30 lines)
  -Exec <service> <cmd> Run command inside container
    Example: -Exec fs-postgres psql -U postgres -d flashsale_platform
    Example: -Exec fs-redis redis-cli -a redis123
    Example: -Exec fs-gateway sh

SERVICE NAMES:
  INFRA:  fs-postgres  fs-mongo  fs-redis  fs-elasticsearch
          fs-minio  fs-kafka  fs-zookeeper  fs-axonserver
  BACKEND: fs-discovery  fs-gateway  fs-identity  fs-payment  fs-order
           fs-flashsale  fs-product  fs-search  fs-notification  fs-worker
           fs-stripe-listener (only in .dev mode)
  FRONTEND: fs-customer-fe  fs-seller-fe  fs-admin-fe

EXAMPLES:
  .\flashsale-build.ps1 -Up -All -D             # .dev mode (Stripe CLI on)
  .\flashsale-build.ps1 -Up -All -D -NoStripeWebhook  # .prod mode (Stripe CLI off)
  .\flashsale-build.ps1 -Up -Frontend -D         # frontend mock only
  .\flashsale-build.ps1 -Up -Infra -Backend -D  # infra + backend
  .\flashsale-build.ps1 -Down -All -V -Rmi
  .\flashsale-build.ps1 -Build -MavenParallel
  .\flashsale-build.ps1 -Restart fs-payment
  .\flashsale-build.ps1 -Tail fs-gateway -Lines 50
  .\flashsale-build.ps1 -Menu

"@ -ForegroundColor $fg
    exit 0
}

# ==============================================================================
# COMPOSE FILE DEFINITIONS
# ==============================================================================
#
# Compose file layout (project root):
#   docker-compose.yml                  ← full stack (root, .prod mode)
#   docker-compose.dev.yml            ← .dev mode override (adds stripe-listener)
#   docker-compose-infrastructure.yml   ← infra only (root)
#   docker-compose-backend.yml           ← backend services only (root)
#
# Compose file layout (backend/):
#   backend/docker-compose.yml           ← standalone backend (infra + services)
#   backend/docker-compose.infra-only.yml
#   backend/docker-compose.prod.yml      ← prod override (builds inside Docker)
#   backend/docker-compose.prod-pulled.yml ← prod override (pulls from GHCR)
#
# Compose file layout (frontend/):
#   frontend/docker compose.yml          ← standalone frontend (space in name)
#   frontend/docker-compose.prod.yml    ← prod override (nginx)
#   frontend/docker-compose.prod-pulled.yml ← prod override (pulls from GHCR)
#
# THREE DEPLOYMENT MODES:
#
#   .dev  (local dev with Stripe CLI):
#     docker compose -f docker-compose.yml -f docker-compose.dev.yml up --build -d
#     Script: .\flashsale-build.ps1 -Up -All -D
#     → infra + backend + frontend + stripe-listener
#
#   .prod (staging/prod, no Stripe CLI):
#     docker compose -f docker-compose.yml up --build -d
#     Script: .\flashsale-build.ps1 -Up -All -D -NoStripeWebhook
#     → infra + backend + frontend (Stripe events from Dashboard/Servers)
#
#   Frontend mock (no backend):
#     cd frontend && docker compose -f docker-compose.yml up --build -d
#     Script: .\flashsale-build.ps1 -Up -Frontend -D
#     → 3 frontend apps with mock data (VITE_BACKEND_MODE=mock)

$RootComposeFiles = @('docker-compose.yml')

# Dev-only compose file (contains stripe-listener — NOT used in production)
$DevComposeFiles = @('docker-compose.dev.yml')

# Root-level partial compose files
$InfraComposeFiles   = @('docker-compose-infrastructure.yml')
$BackendComposeFiles = @('docker-compose-backend.yml')

# Frontend standalone (space in filename, runs from frontend/ dir)
$FrontendComposeDir   = Join-Path $ProjectRoot 'frontend'
$FrontendComposeFiles = @('docker compose.yml')

# Backend standalone (runs from backend/ dir)
$BackendDirComposeFiles = @('docker-compose.yml')
$BackendDir             = Join-Path $ProjectRoot 'backend'

# ==============================================================================
# HELPERS
# ==============================================================================

function Get-ScriptDir {
    if ($PSScriptRoot) { $PSScriptRoot } else { $PWD.Path }
}

function Test-File {
    param([string]$Path, [string]$Name)
    if (-not (Test-Path $Path)) {
        Write-Host "[WARN] $Name not found: $Path" -ForegroundColor Yellow
        return $false
    }
    $true
}

function Run-Dc {
    param(
        [string[]] $Files,
        [string]   $DcArgs,
        [string]   $WorkingDir = (Get-ScriptDir),
        [switch]   $Quiet,
        [switch]   $IgnoreErrors
    )
    $fileArg = ($Files | ForEach-Object { "-f", $_ }) -join ' '
    Write-Host "  > docker compose $fileArg $DcArgs" -ForegroundColor DarkGray
    Push-Location $WorkingDir -PassThru | Out-Null
    try {
        if ($Quiet) {
            $null = docker compose $fileArg $DcArgs 2>&1
        } else {
            docker compose $fileArg $DcArgs
        }
        if (-not $IgnoreErrors -and $LASTEXITCODE -ne 0) {
            Write-Host "[FAIL] docker compose exited with code $LASTEXITCODE" -ForegroundColor Red
        }
        return $LASTEXITCODE
    } finally {
        Pop-Location
    }
}

function Write-Step { param([string]$m) Write-Host "`n>>> $m" -ForegroundColor Cyan }
function Write-Success { param([string]$m) Write-Host "[OK] $m" -ForegroundColor Green }
function Write-Warn { param([string]$m) Write-Host "[WARN] $m" -ForegroundColor Yellow }
function Write-Fail { param([string]$m) Write-Host "[FAIL] $m" -ForegroundColor Red }

function Get-UpFlags {
    $flags = @('up', '--build')
    if ($Detach) { $flags += '-d' }
    if ($RemoveOrphans) { $flags += '--remove-orphans' }
    if ($RemoveVolumes) { $flags += '-V' }
    return $flags
}

function Get-DownFlags {
    $flags = @('down')
    if ($RemoveVolumes)  { $flags += '-v' }
    if ($RemoveImages)   { $flags += '--rmi', 'local' }
    if ($RemoveOrphans)  { $flags += '--remove-orphans' }
    return $flags
}

function Get-AllServices {
    @(
        'fs-postgres', 'fs-mongo', 'fs-redis', 'fs-elasticsearch',
        'fs-minio', 'fs-kafka', 'fs-zookeeper', 'fs-axonserver',
        'fs-discovery', 'fs-gateway', 'fs-identity', 'fs-payment',
        'fs-order', 'fs-flashsale', 'fs-product', 'fs-search',
        'fs-notification', 'fs-worker',
        'fs-customer-fe', 'fs-seller-fe', 'fs-admin-fe'
    )
}

# ==============================================================================
# TARGET RESOLUTION
# ==============================================================================

function Resolve-Targets {
    if (-not ($All -or $Infra -or $Backend -or $Frontend)) {
        $script:All = $true
    }

    $targets = @()

    if ($All) {
        $targets += @{
            Name='infra';
            Files=$InfraComposeFiles;
            Dir=$ProjectRoot;
            Block='none';
            Desc='Infrastructure (databases, queues)'
        }
        $targets += @{
            Name='backend';
            Files=$BackendComposeFiles;
            Dir=$ProjectRoot;
            Block='maven';
            Desc='Backend microservices'
        }
        $targets += @{
            Name='frontend';
            Files=$FrontendComposeFiles;
            Dir=$FrontendComposeDir;
            Block='npm';
            Desc='Frontend apps'
        }
    } else {
        if ($Infra) {
            $targets += @{
                Name='infra';
                Files=$InfraComposeFiles;
                Dir=$ProjectRoot;
                Block='none';
                Desc='Infrastructure'
            }
        }
        if ($Backend) {
            $targets += @{
                Name='backend';
                Files=$BackendComposeFiles;
                Dir=$ProjectRoot;
                Block='maven';
                Desc='Backend microservices'
            }
        }
        if ($Frontend) {
            $targets += @{
                Name='frontend';
                Files=$FrontendComposeFiles;
                Dir=$FrontendComposeDir;
                Block='npm';
                Desc='Frontend apps'
            }
        }
    }

    return $targets
}

# ==============================================================================
# BUILD FUNCTIONS
# ==============================================================================

function Invoke-MavenBuild {
    $BackendDir = Join-Path $ProjectRoot 'backend'
    $pomPath    = Join-Path $BackendDir 'pom.xml'
    if (-not (Test-File $pomPath 'backend/pom.xml')) {
        Write-Warn 'Skipping Maven build.'
        return
    }
    Write-Step 'Building backend with Maven...'
    Push-Location $BackendDir
    try {
        $mvnArgs = @('clean', 'install', '-DskipTests')
        if ($MavenParallel) { $mvnArgs += '-T', '2C' }
        Write-Host "  > mvn $($mvnArgs -join ' ')" -ForegroundColor DarkGray
        mvn $mvnArgs
        if ($LASTEXITCODE -ne 0) {
            Write-Fail 'Maven build failed'
            exit 1
        }
        Write-Success 'Maven build completed.'
    } finally {
        Pop-Location
    }
}

function Invoke-NpmBuild {
    $feDir   = Join-Path $ProjectRoot 'frontend'
    $pkgJson = Join-Path $feDir 'package.json'

    # Frontend doesn't have a root package.json — each app has its own.
    # The frontend Dockerfile.dev installs deps inside the container.
    # This function checks if individual app packages exist.
    $apps = @('customer', 'seller', 'admin')
    $missing = $true
    foreach ($app in $apps) {
        $appPkg = Join-Path $feDir "apps/$app/package.json"
        if (Test-Path $appPkg) { $missing = $false }
    }

    if ($missing) {
        Write-Host '[SKIP] Frontend package.json not found. Build happens inside Docker.' -ForegroundColor DarkGray
        return
    }

    Write-Step 'Building frontend apps with npm...'
    Push-Location $feDir
    try {
        # Each app builds independently inside its own container via Dockerfile.dev.
        # This runs a lightweight check / shared build if needed.
        Write-Host '  Note: Frontend builds are run inside Docker containers.' -ForegroundColor DarkGray
        Write-Host '  To build in Docker: docker compose -f docker-compose.yml up --build' -ForegroundColor DarkGray
    } finally {
        Pop-Location
    }
}

# ==============================================================================
# UP / DOWN
# ==============================================================================

function Start-Target {
    param(
        [string[]] $Files,
        [string]   $WorkingDir,
        [string]   $Block,
        [string]   $Name,
        [string]   $Desc,
        [switch]   $Detach
    )
    $flags = Get-UpFlags
    $fileStr = ($Files -join ' + ')
    Write-Step "Starting $Name ($fileStr) - $Desc..."
    $code = Run-Dc -Files $Files -DcArgs ($flags -join ' ') -WorkingDir $WorkingDir
    if ($code -ne 0) {
        Write-Fail "Failed to start $Name (docker compose exit code: $code)"
        exit 1
    }
}

function Stop-Target {
    param(
        [string[]] $Files,
        [string]   $WorkingDir,
        [string]   $Name
    )
    $flags = Get-DownFlags
    $fileStr = ($Files -join ' + ')
    Write-Step "Stopping $Name ($fileStr)..."
    $code = Run-Dc -Files $Files -DcArgs ($flags -join ' ') -WorkingDir $WorkingDir -Quiet -IgnoreErrors
    if ($code -ne 0) {
        Write-Warn "docker compose down for $Name returned exit code $code (may be OK if not running)"
    }
}

function Invoke-Up {
    $targets = Resolve-Targets

    if (-not (Test-DockerRunning)) { exit 1 }

    $runDetach = $false
    if ($Detach -or $D) { $runDetach = $true }

    $needsMaven = $targets | Where-Object { $_.Block -eq 'maven' }
    $needsNpm   = $targets | Where-Object { $_.Block -eq 'npm' }

    if (-not $SkipBuild) {
        if ($needsMaven) {
            Invoke-MavenBuild
        }
        if ($needsNpm) {
            Invoke-NpmBuild
        }
    } else {
        Write-Host '[SKIP] Build skipped (-SkipBuild)' -ForegroundColor DarkGray
    }

    foreach ($t in $targets) {
        Start-Target -Files $t.Files -WorkingDir $t.Dir -Block $t.Block -Name $t.Name -Desc $t.Desc -Detach:$runDetach
    }

    # --- Start stripe-listener (dev only) ---
    $wantsBackend = $targets | Where-Object { $_.Name -eq 'backend' }
    if ($wantsBackend -and -not $NoStripeWebhook) {
        Write-Step "Starting stripe-listener (dev only)..."
        $code = Run-Dc -Files $DevComposeFiles -DcArgs 'up -d' -WorkingDir $ProjectRoot -Quiet
        if ($LASTEXITCODE -ne 0) {
            Write-Warn "stripe-listener may have failed. Check: docker ps fs-stripe-listener"
        } else {
            Write-Success "stripe-listener started (fs-stripe-listener)"
        }
    } elseif ($wantsBackend -and $NoStripeWebhook) {
        Write-Host '[SKIP] Stripe CLI listener disabled (-NoStripeWebhook)' -ForegroundColor DarkGray
        Write-Host '  Prod: Stripe Dashboard sends webhooks directly to your server.' -ForegroundColor DarkGray
    }

    Write-Host ''
    Write-Host 'View logs:    docker compose logs -f' -ForegroundColor Yellow
    Write-Host 'Check status: .\flashsale-build.ps1 -Status' -ForegroundColor Yellow
    Write-Host 'View ports:   .\flashsale-build.ps1 -Ports' -ForegroundColor Yellow
    Write-Host 'Check health: .\flashsale-build.ps1 -Health' -ForegroundColor Yellow
    Write-Host 'Stop:         .\flashsale-build.ps1 -Down' -ForegroundColor Yellow
    Write-Host ''
    Write-Host 'Access points:' -ForegroundColor Yellow
    Write-Host '  http://localhost:8080  API Gateway / Swagger UI'
    Write-Host '  http://localhost:8761  Eureka Discovery'
    Write-Host '  http://localhost:3000  Customer App'
    Write-Host '  http://localhost:3001  Seller App'
    Write-Host '  http://localhost:3002  Admin App'
    Write-Host '  http://localhost:9001  MinIO Console'
    Write-Host '  http://localhost:8024  Axon Server GUI'
    Write-Host ''
    if ($wantsBackend -and -not $NoStripeWebhook) {
        Write-Host '  Stripe CLI: fs-stripe-listener is running automatically (dev mode).' -ForegroundColor Yellow
        Write-Host '  Check secret: docker logs fs-stripe-listener | Select-String whsec_' -ForegroundColor DarkGray
    }
}

function Invoke-Down {
    $targets = Resolve-Targets

    if ($targets.Count -gt 1) {
        [array]::Reverse($targets)
    }

    foreach ($t in $targets) {
        Stop-Target -Files $t.Files -WorkingDir $t.Dir -Name $t.Name
    }

    Write-Success 'Down complete.'
}

# ==============================================================================
# INFO / CLEAN
# ==============================================================================

function Show-Status {
    Write-Host "`n=== CONTAINER STATUS ===" -ForegroundColor Cyan
    $code = Run-Dc -Files $RootComposeFiles -DcArgs 'ps' -WorkingDir $ProjectRoot -Quiet
    if ($LASTEXITCODE -ne 0) {
        docker ps --format 'table {{.Names}}	{{.Status}}	{{.Ports}}' 2>$null
    }
}

function Show-Ports {
    Write-Host "`n=== EXPOSED PORTS ===" -ForegroundColor Cyan
    $running = docker ps --format '{{.Names}}' 2>$null
    if ($running) {
        $running | ForEach-Object {
            $name  = $_
            $ports = docker port $name 2>$null
            if ($ports) {
                Write-Host "$name" -ForegroundColor Yellow
                $ports -split "`n" | ForEach-Object { Write-Host "  $_" }
            }
        }
    } else {
        Write-Host '(No containers running)' -ForegroundColor DarkGray
    }
    Write-Host ''
    Write-Host 'PORT REFERENCE:' -ForegroundColor Cyan
    Write-Host '  BACKEND SERVICES'
    Write-Host '  8080 API Gateway   | 8761 Eureka       | 8081 Identity'
    Write-Host '  8082 Payment      | 8083 Order        | 8085 FlashSale'
    Write-Host '  8086 Worker       | 8090 Product      | 8091 Search'
    Write-Host '  8092 Notification | 8024 Axon GUI     | 8124 Axon gRPC'
    Write-Host ''
    Write-Host '  INFRASTRUCTURE'
    Write-Host '  9200 Elasticsearch | 9000 MinIO        | 9001 MinIO Console'
    Write-Host '  5432 PostgreSQL   | 27017 MongoDB     | 6379 Redis'
    Write-Host '  9092 Kafka       | 2181 Zookeeper    | 29092 Kafka Internal'
    Write-Host ''
    Write-Host '  FRONTEND APPS'
    Write-Host '  3000 Customer App | 3001 Seller App   | 3002 Admin App'
}

function Show-Health {
    Write-Host "`n=== SERVICE HEALTH ===" -ForegroundColor Cyan
    $svcList = @(
        @{Name='API Gateway';   Url='http://localhost:8080/actuator/health'},
        @{Name='Discovery';     Url='http://localhost:8761/actuator/health'},
        @{Name='Elasticsearch'; Url='http://localhost:9200'},
        @{Name='MinIO';        Url='http://localhost:9000/minio/health/live'},
        @{Name='Axon Server';  Url='http://localhost:8024/actuator/health'}
    )
    foreach ($svc in $svcList) {
        try {
            $resp = Invoke-WebRequest -Uri $svc.Url -UseBasicParsing -TimeoutSec 3 -ErrorAction SilentlyContinue
            $ok = ($resp.StatusCode -eq 200)
        } catch { $ok = $false }
        $status = if ($ok) { 'UP' } else { 'DOWN' }
        $color  = if ($ok) { 'Green' } else { 'Red' }
        $padded = $svc.Name.PadRight(16)
        Write-Host "  $padded : " -NoNewline
        Write-Host $status -ForegroundColor $color
    }
    $infraList = @(
        @{Name='PostgreSQL';  Container='fs-postgres'},
        @{Name='Redis';       Container='fs-redis'},
        @{Name='MongoDB';     Container='fs-mongo'},
        @{Name='Kafka';       Container='fs-kafka'},
        @{Name='Zookeeper';   Container='fs-zookeeper'},
        @{Name='AxonServer';  Container='fs-axonserver'}
    )
    foreach ($svc in $infraList) {
        $padded = $svc.Name.PadRight(16)
        $st = docker inspect --format='{{.State.Health.Status}}' $svc.Container 2>$null
        if ($st -eq 'healthy') {
            Write-Host "  $padded : " -NoNewline
            Write-Host 'UP (healthy)' -ForegroundColor Green
        } elseif ($st -eq 'unhealthy') {
            Write-Host "  $padded : " -NoNewline
            Write-Host 'DOWN (unhealthy)' -ForegroundColor Red
        } elseif ($st -eq 'starting') {
            Write-Host "  $padded : " -NoNewline
            Write-Host 'STARTING' -ForegroundColor Yellow
        } else {
            Write-Host "  $padded : " -NoNewline
            Write-Host 'NOT FOUND' -ForegroundColor DarkGray
        }
    }
}

function Show-Logs {
    Write-Host "`n=== TAILING LOGS (Ctrl+C to stop) ===" -ForegroundColor Cyan
    Run-Dc -Files $RootComposeFiles -DcArgs 'logs -f' -WorkingDir $ProjectRoot
}

function Invoke-Stop {
    Write-Step 'Stopping all containers (docker compose stop)...'
    $allComposeFiles = @(
        @{Files=$InfraComposeFiles;   Dir=$ProjectRoot},
        @{Files=$BackendComposeFiles; Dir=$ProjectRoot},
        @{Files=$FrontendComposeFiles; Dir=$FrontendComposeDir}
    )
    foreach ($entry in $allComposeFiles) {
        Run-Dc -Files $entry.Files -DcArgs 'stop' -WorkingDir $entry.Dir -Quiet -IgnoreErrors
    }
    Write-Success 'All containers stopped.'
}

function Invoke-Clean {
    Write-Step 'FULL CLEAN'
    Write-Warn 'This will DELETE ALL DATA (Postgres, MongoDB, Redis, Kafka, Elasticsearch)!'
    $volFlag = if ($RemoveVolumes) { '-v' } else { '' }
    $rmiFlag = if ($RemoveImages)  { '--rmi local' } else { '' }

    # Root compose (full stack)
    Run-Dc -Files $RootComposeFiles -DcArgs "down $volFlag $rmiFlag" -WorkingDir $ProjectRoot -Quiet -IgnoreErrors
    # Backend standalone compose
    Run-Dc -Files $BackendDirComposeFiles -DcArgs "down $volFlag $rmiFlag" -WorkingDir $BackendDir -Quiet -IgnoreErrors
    # Frontend compose
    Run-Dc -Files $FrontendComposeFiles -DcArgs "down $volFlag $rmiFlag" -WorkingDir $FrontendComposeDir -Quiet -IgnoreErrors

    if ($RemoveVolumes) {
        docker volume prune -f 2>$null
    }
    if ($RemoveImages) {
        docker image prune -f 2>$null
    }
    Write-Success 'Full clean complete.'
}

# ==============================================================================
# RESTART / TAIL / EXEC
# ==============================================================================

function Resolve-ServiceName {
    param([string]$Name)
    $n = $Name.Trim()
    # Auto-prefix fs- if missing
    if ($n -notmatch '^fs-') {
        $n = "fs-$n"
    }
    return $n
}

function Invoke-Restart {
    if ([string]::IsNullOrWhiteSpace($Service)) {
        Write-Fail "-Restart requires a service name."
        Write-Host "Example: .\flashsale-build.ps1 -Restart payment" -ForegroundColor Yellow
        Write-Host "Services: $((Get-AllServices) -join ', ')" -ForegroundColor DarkGray
        exit 1
    }
    $svc = Resolve-ServiceName $Service
    if (-not (Test-ServiceExists $svc)) { exit 1 }

    $running = docker ps --format '{{.Names}}' | Select-String "^$([regex]::Escape($svc))$"
    if (-not $running) {
        Write-Warn "Container '$svc' is not running."
        exit 0
    }
    Write-Step "Restarting $svc..."
    docker restart $svc
    Write-Success "$svc restarted."
    Write-Host "Use '.\flashsale-build.ps1 -Tail $svc' to watch its logs." -ForegroundColor DarkGray
}

function Invoke-Tail {
    if ([string]::IsNullOrWhiteSpace($Service)) {
        Write-Fail "-Tail requires a service name."
        Write-Host "Example: .\flashsale-build.ps1 -Tail gateway -Lines 50" -ForegroundColor Yellow
        Write-Host "Services: $((Get-AllServices) -join ', ')" -ForegroundColor DarkGray
        exit 1
    }
    $svc = Resolve-ServiceName $Service
    if (-not (Test-ServiceExists $svc)) { exit 1 }

    $exists = docker ps -a --format '{{.Names}}' | Select-String "^$([regex]::Escape($svc))$"
    if (-not $exists) {
        Write-Fail "Container '$svc' not found."
        exit 1
    }
    Write-Host "`n=== Tailing $svc (last $Lines lines, Ctrl+C to stop) ===" -ForegroundColor Cyan
    docker logs "$svc" --tail $Lines -f
}

function Invoke-Exec {
    if ([string]::IsNullOrWhiteSpace($Service) -or [string]::IsNullOrWhiteSpace($Command)) {
        Write-Fail "-Exec requires both <service> and <command>."
        Write-Host "Example: .\flashsale-build.ps1 -Exec postgres psql -U postgres -d flashsale_platform" -ForegroundColor Yellow
        Write-Host "Example: .\flashsale-build.ps1 -Exec mongo mongosh -u admin -p" -ForegroundColor Yellow
        Write-Host "Example: .\flashsale-build.ps1 -Exec redis redis-cli -a redis123" -ForegroundColor Yellow
        Write-Host "Example: .\flashsale-build.ps1 -Exec kafka kafka-topics --list --bootstrap-server localhost:9092" -ForegroundColor Yellow
        Write-Host "Example: .\flashsale-build.ps1 -Exec gateway sh" -ForegroundColor Yellow
        exit 1
    }
    $svc = Resolve-ServiceName $Service
    if (-not (Test-ServiceExists $svc)) { exit 1 }

    $running = docker ps --format '{{.Names}}' | Select-String "^$([regex]::Escape($svc))$"
    if (-not $running) {
        Write-Fail "Container '$svc' is not running. Start it first with -Up."
        exit 1
    }
    Write-Host "[EXEC] $svc > $Command" -ForegroundColor DarkGray
    docker exec -it "$svc" sh -c $Command
}

# ==============================================================================
# INTERACTIVE MENU
# ==============================================================================

function Show-Menu {
    while ($true) {
        Clear-Host
        Write-Host @"

============================================================
  FLASH SALE PLATFORM  —  Interactive Menu
============================================================
  NOTE: fs-stripe-listener starts automatically when backend starts.
        Use -NoStripeWebhook to disable it.

  [1] Start All Services (docker compose up -d)
  [2] Start Infrastructure Only
  [3] Start Backend Only
  [4] Start Frontend Only
  [5] Start Infra + Backend

  [6] Stop All Services
  [7] Stop Backend
  [8] Stop Frontend

  [9]  Restart Service
  [10] Tail Logs
  [11] Run Exec Command

  [12] Status
  [13] Health Check
  [14] Ports
  [15] Maven Build
  [16] Clean (remove volumes + images)

  [0] Exit

"@ -ForegroundColor Cyan

        $choice = Read-Host "Select an option"
        switch ($choice) {
            '1' { & $PSCommandPath -Up -All -D }
            '2' { & $PSCommandPath -Up -Infra -D }
            '3' { & $PSCommandPath -Up -Backend -D }
            '4' { & $PSCommandPath -Up -Frontend -D }
            '5' { & $PSCommandPath -Up -Infra -Backend -D }
            '6' { & $PSCommandPath -Down -All }
            '7' { & $PSCommandPath -Down -Backend }
            '8' { & $PSCommandPath -Down -Frontend }
            '9' {
                $svc = Read-Host "Enter service name (e.g. payment, gateway)"
                & $PSCommandPath -Restart $svc
            }
            '10' {
                $svc = Read-Host "Enter service name (e.g. gateway, order)"
                & $PSCommandPath -Tail $svc
            }
            '11' {
                $svc = Read-Host "Enter service name"
                $cmd = Read-Host "Enter command"
                & $PSCommandPath -Exec $svc -Command $cmd
            }
            '12' { & $PSCommandPath -Status }
            '13' { & $PSCommandPath -Health }
            '14' { & $PSCommandPath -Ports }
            '15' { & $PSCommandPath -Build }
            '16' {
                Write-Host "This will DELETE ALL DATA. Are you sure? (y/n) " -ForegroundColor Yellow -NoNewline
                $confirm = Read-Host
                if ($confirm -eq 'y') { & $PSCommandPath -Clean -V -Rmi }
            }
            '0' { Write-Host "Goodbye!" -ForegroundColor Green; exit 0 }
        }
        if ($choice -notin @('9', '10', '11')) {
            Write-Host "`nPress Enter to return to menu..." -ForegroundColor DarkGray
            Read-Host
        }
    }
}

# ==============================================================================
# MAIN DISPATCH
# ==============================================================================

Set-Location (Get-ScriptDir)

if ($Status)  { Show-Status;  exit 0 }
if ($Ports)   { Show-Ports;   exit 0 }
if ($Health)  { Show-Health;  exit 0 }
if ($Logs)    { Show-Logs;    exit 0 }
if ($Menu)    { Show-Menu;    exit 0 }

if ($Restart) { Invoke-Restart; exit 0 }
if ($Tail)    { Invoke-Tail;   exit 0 }
if ($Exec)    { Invoke-Exec;   exit 0 }

if ($Stop)    { Invoke-Stop;  exit 0 }

if ($Clean) {
    Invoke-Clean
    exit 0
}

if ($Build) {
    Invoke-MavenBuild
    exit 0
}

if ($BuildFrontend) {
    Invoke-NpmBuild
    exit 0
}

if ($Down) {
    Invoke-Down
    exit 0
}

if ($Up) {
    Invoke-Up
    exit 0
}

Write-Host 'No action specified. Run .\flashsale-build.ps1 -Help for usage.' -ForegroundColor Yellow
exit 0
