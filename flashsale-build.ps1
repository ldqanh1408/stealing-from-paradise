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
#
# EXAMPLES:
#   .\flashsale-build.ps1 -Up -All -D                # Start everything, detached
#   .\flashsale-build.ps1 -Up -Infra                 # Start infrastructure only
#   .\flashsale-build.ps1 -Up -Backend -D             # Start backend only
#   .\flashsale-build.ps1 -Up -Frontend              # Start frontend only (foreground)
#   .\flashsale-build.ps1 -Up -Infra -Backend         # Start infra + backend
#   .\flashsale-build.ps1 -Up -Backend -Frontend     # Start backend + frontend
#   .\flashsale-build.ps1 -Up -All -D -RemoveOrphans  # Start all with orphan cleanup
#   .\flashsale-build.ps1 -Up -Frontend -D -V         # Recreate frontend volumes
#
#   .\flashsale-build.ps1 -Down -All                  # Stop everything
#   .\flashsale-build.ps1 -Down -All -V               # Stop + remove volumes
#   .\flashsale-build.ps1 -Down -All -V -Rmi          # Stop + volumes + images
#   .\flashsale-build.ps1 -Down -All -V -Rmi -RemoveOrphans
#   .\flashsale-build.ps1 -Down -Backend              # Stop backend only
#   .\flashsale-build.ps1 -Down -Frontend             # Stop frontend only
#   .\flashsale-build.ps1 -Down -Infra                # Stop infrastructure only
#
#   .\flashsale-build.ps1 -Build                      # Maven build (no docker)
#   .\flashsale-build.ps1 -Build -MavenParallel        # Parallel Maven build
#   .\flashsale-build.ps1 -BuildFrontend
#   .\flashsale-build.ps1 -Clean -V -Rmi               # Full nuclear clean
#   .\flashsale-build.ps1 -Stop                        # docker compose stop
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

    # --- TARGETS (composable, default = all) ---
    [switch]$All,
    [switch]$Infra,
    [switch]$Backend,
    [switch]$Frontend,

    # --- UP OPTIONS ---
    [switch]$D,           # shorthand for -Detach
    [switch]$Detach,
    [switch]$SkipBuild,
    [switch]$MavenParallel,
    [switch]$FrontendProd,

    # --- DOWN / CLEAN OPTIONS ---
    [switch]$V,           # shorthand for -RemoveVolumes
    [switch]$RemoveVolumes,
    [switch]$Rmi,         # shorthand for -RemoveImages
    [switch]$RemoveImages,
    [switch]$RemoveOrphans,
    [switch]$Remove      # alias for -Down
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = if ($PSScriptRoot) { $PSScriptRoot } else { $PWD.Path }

# --- Alias resolution ---
if ($D)             { $Detach = $true }
if ($V)             { $RemoveVolumes = $true }
if ($Rmi)            { $RemoveImages = $true }
if ($Remove)         { $Down = $true }

# ==============================================================================
# HELP TEXT
# ==============================================================================

if ($Help) {
    $fg = 'Cyan'
    Write-Host @"

FLASH SALE BUILD SCRIPT
============================================================

ACTIONS  (choose one):
  -Up           Start services          -Down         Stop services
  -Build        Maven build only        -BuildFrontend  Npm build only
  -Stop         docker compose stop     -Clean         Nuclear clean
  -Status       Container status       -Logs          Tail logs
  -Ports        Show exposed ports     -Health        Check health

TARGETS  (composable, default = all):
  -All           Everything              -Infra        Infrastructure
  -Backend       Backend microservices  -Frontend     Frontend apps

  Combine targets freely:
    -Up -Infra -Backend         Start infra + backend
    -Up -Backend -Frontend      Start backend + frontend
    -Down -Frontend             Stop frontend only

UP OPTIONS:
  -Detach (-D)    Background mode (docker compose -d)
  -SkipBuild      Skip Maven build step
  -MavenParallel  Maven -T 2C (parallel threads)
  -FrontendProd   Use nginx production containers

DOWN / CLEAN OPTIONS:
  -V (-RemoveVolumes)     Remove named volumes  [DATA LOSS]
  -Rmi (-RemoveImages)    Remove service images
  -RemoveOrphans         Remove orphaned containers

  Common combos:
    -Down -All -V              Stop + remove volumes
    -Down -All -V -Rmi         Stop + volumes + images
    -Down -All -V -Rmi -RemoveOrphans   Full cleanup
    -Clean -V -Rmi             Nuclear clean

STOP:
  -Stop          docker compose stop (no removal)

EXAMPLES:
  .\flashsale-build.ps1 -Up -All -D
  .\flashsale-build.ps1 -Up -Infra -D
  .\flashsale-build.ps1 -Up -Backend -D
  .\flashsale-build.ps1 -Up -Frontend -D
  .\flashsale-build.ps1 -Up -Infra -Backend -D
  .\flashsale-build.ps1 -Up -All -D -RemoveOrphans
  .\flashsale-build.ps1 -Up -All -D -V
  .\flashsale-build.ps1 -Down -All -V -Rmi
  .\flashsale-build.ps1 -Down -Backend
  .\flashsale-build.ps1 -Down -Frontend
  .\flashsale-build.ps1 -Build -MavenParallel
  .\flashsale-build.ps1 -Clean -V -Rmi
  .\flashsale-build.ps1 -Status
  .\flashsale-build.ps1 -Logs

"@ -ForegroundColor $fg
    exit 0
}

# ==============================================================================
# COMPOSE FILES
# ==============================================================================

# Root-level compose files use hyphen: docker-compose.yml
$RootComposeFiles = @(
    'docker-compose.yml'
)
$InfraComposeFiles   = @('docker-compose-infrastructure.yml')
$BackendComposeFiles = @('docker-compose-backend.yml')
# Frontend uses "docker compose.yml" (space) and lives in frontend/ subdir
$FrontendComposeDir = Join-Path $ProjectRoot 'frontend'
$FrontendComposeFiles = @(
    'docker compose.yml'
)
if ($FrontendProd) {
    $FrontendComposeFiles += 'docker compose.prod.yml'
}

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
        [string]   $Args,
        [string]   $WorkingDir = (Get-ScriptDir),
        [switch]   $Quiet,
        [switch]   $IgnoreErrors
    )
    $fileArg = ($Files | ForEach-Object { "-f", $_ }) -join ' '
    Write-Host "  > docker compose $fileArg $Args" -ForegroundColor DarkGray
    Push-Location $WorkingDir -PassThru | Out-Null
    try {
        if ($Quiet) {
            $null = docker compose $fileArg $Args 2>&1
        } else {
            docker compose $fileArg $Args
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
    param([switch]$Detach)
    $flags = @('up', '--build')
    if ($Detach) { $flags += '-d' }
    if ($RemoveOrphans) { $flags += '--remove-orphans' }
    if ($RemoveVolumes) { $flags += '-V' }
    return $flags
}

function Get-DownFlags {
    $flags = @('down')
    if ($RemoveVolumes)   { $flags += '-v' }
    if ($RemoveImages)    { $flags += '--rmi', 'local' }
    if ($RemoveOrphans)  { $flags += '--remove-orphans' }
    return $flags
}

# ==============================================================================
# TARGET RESOLUTION
# ==============================================================================

# Returns an array of target objects: @{ Name; Files[]; WorkingDir; BuildBlock }
# Block values: 'maven', 'npm', 'none'
function Resolve-Targets {
    param([switch]$IsUp)

    # If no specific target is given, default to All
    if (-not ($All -or $Infra -or $Backend -or $Frontend)) {
        $script:All = $true
    }

    $targets = @()

    if ($All -or (-not $Infra -and -not $Backend -and -not $Frontend)) {
        # Everything
        $targets += @{ Name='infra';    Files=$InfraComposeFiles;   Dir=$ProjectRoot;       Block='none' }
        $targets += @{ Name='backend';  Files=$BackendComposeFiles; Dir=$ProjectRoot;       Block='maven' }
        $targets += @{ Name='frontend'; Files=$FrontendComposeFiles; Dir=$FrontendComposeDir; Block='npm' }
    } else {
        if ($All -or $Infra) {
            $targets += @{ Name='infra';    Files=$InfraComposeFiles;   Dir=$ProjectRoot;       Block='none' }
        }
        if ($All -or $Backend) {
            $targets += @{ Name='backend';  Files=$BackendComposeFiles; Dir=$ProjectRoot;       Block='maven' }
        }
        if ($All -or $Frontend) {
            $targets += @{ Name='frontend'; Files=$FrontendComposeFiles; Dir=$FrontendComposeDir; Block='npm' }
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
    if (-not (Test-File $feDir 'frontend/') -or -not (Test-File $pkgJson 'frontend/package.json')) {
        Write-Warn 'Skipping frontend build.'
        return
    }
    Write-Step 'Building frontend apps...'
    Push-Location $feDir
    try {
        Write-Host "  > pnpm install" -ForegroundColor DarkGray
        pnpm install
        Write-Host "  > pnpm build" -ForegroundColor DarkGray
        pnpm build
        if ($LASTEXITCODE -ne 0) {
            Write-Fail 'Frontend build failed'
            exit 1
        }
        Write-Success 'Frontend build completed.'
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
        [switch]   $Detach
    )
    $flags = Get-UpFlags -Detach:$Detach
    $fileStr = ($Files -join ' + ')
    Write-Step "Starting $Name ($fileStr)..."
    $code = Run-Dc -Files $Files -Args ($flags -join ' ') -WorkingDir $WorkingDir
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
    $code = Run-Dc -Files $Files -Args ($flags -join ' ') -WorkingDir $WorkingDir -Quiet
    if ($code -ne 0) {
        Write-Warn "docker compose down for $Name returned exit code $code (may be OK if not running)"
    }
}

function Invoke-Up {
    $targets = Resolve-Targets -IsUp

    $runDetach = $false
    if ($Detach -or $D) { $runDetach = $true }

    # --- Build phase ---
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
        Write-Host '[SKIP] Maven/NPM build skipped (-SkipBuild)' -ForegroundColor DarkGray
    }

    # --- Start phase (in order: infra -> backend -> frontend) ---
    foreach ($t in $targets) {
        Start-Target -Files $t.Files -WorkingDir $t.Dir -Block $t.Block -Name $t.Name -Detach:$runDetach
    }

    # --- Summary ---
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
    Write-Host ''
}

function Invoke-Down {
    $targets = Resolve-Targets

    # Stop in reverse order: frontend -> backend -> infra
    [array]::Reverse($targets)

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
    $code = Run-Dc -Files $RootComposeFiles -Args 'ps' -WorkingDir $ProjectRoot -Quiet
    if ($LASTEXITCODE -ne 0) {
        docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' 2>$null
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
    Write-Host '  8080 API Gateway   | 8761 Eureka      | 8081 Identity'
    Write-Host '  8082 Payment      | 8083 Order       | 8085 FlashSale'
    Write-Host '  8086 Worker       | 8090 Product     | 8091 Search'
    Write-Host '  8092 Notification | 3000 Customer    | 3001 Seller'
    Write-Host '  3002 Admin        | 9200 Elastic      | 9000 MinIO'
    Write-Host '  9001 MinIO Console| 5432 Postgres     | 27017 Mongo'
    Write-Host '  6379 Redis        | 9092 Kafka        | 2181 Zookeeper'
    Write-Host '  8024 Axon GUI     | 8124 Axon gRPC'
}

function Show-Health {
    Write-Host "`n=== SERVICE HEALTH ===" -ForegroundColor Cyan
    $svcList = @(
        @{Name='API Gateway';   Url='http://localhost:8080/actuator/health'},
        @{Name='Discovery';     Url='http://localhost:8761/actuator/health'},
        @{Name='Elasticsearch'; Url='http://localhost:9200'},
        @{Name='MinIO';         Url='http://localhost:9000/minio/health/live'}
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
        @{Name='PostgreSQL'; Container='fs-postgres'},
        @{Name='Redis';      Container='fs-redis'},
        @{Name='MongoDB';    Container='fs-mongo'}
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
    Run-Dc -Files $RootComposeFiles -Args 'logs -f' -WorkingDir $ProjectRoot
}

function Invoke-Stop {
    Write-Step 'Stopping all containers (docker compose stop)...'
    foreach ($files in @($InfraComposeFiles, $BackendComposeFiles, $FrontendComposeFiles)) {
        $dir = if ($files[0] -match 'frontend') { $FrontendComposeDir } else { $ProjectRoot }
        Run-Dc -Files $files -Args 'stop' -WorkingDir $dir -Quiet -IgnoreErrors
    }
    Write-Success 'All containers stopped.'
}

function Invoke-Clean {
    Write-Step 'FULL CLEAN'
    Write-Warn 'This will DELETE ALL DATA (Postgres, MongoDB, Redis, Kafka, Elasticsearch)!'
    $volFlag  = if ($RemoveVolumes)   { '-v' } else { '' }
    $rmiFlag  = if ($RemoveImages)      { '--rmi local' } else { '' }

    # Root compose
    Run-Dc -Files $RootComposeFiles -Args "down $volFlag $rmiFlag" -WorkingDir $ProjectRoot -Quiet -IgnoreErrors
    # Backend
    Run-Dc -Files $BackendComposeFiles -Args "down $volFlag $rmiFlag" -WorkingDir $ProjectRoot -Quiet -IgnoreErrors
    # Frontend
    Run-Dc -Files $FrontendComposeFiles -Args "down $volFlag $rmiFlag" -WorkingDir $FrontendComposeDir -Quiet -IgnoreErrors

    if ($RemoveVolumes) {
        docker volume prune -f 2>$null
    }
    if ($RemoveImages) {
        docker image prune -f 2>$null
    }
    Write-Success 'Full clean complete.'
}

# ==============================================================================
# MAIN DISPATCH
# ==============================================================================

Set-Location (Get-ScriptDir)

if ($Status)  { Show-Status;  exit 0 }
if ($Ports)   { Show-Ports;   exit 0 }
if ($Health)  { Show-Health;  exit 0 }
if ($Logs)    { Show-Logs;    exit 0 }

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

# Nothing specified — show help
Write-Host 'No action specified. Run .\flashsale-build.ps1 -Help for usage.' -ForegroundColor Yellow
exit 0
