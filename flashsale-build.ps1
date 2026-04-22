# ============================================================
# flashsale-build.ps1
# Universal build & run script for the Flash Sale E-Commerce platform.
#
# Run from the project root: .\flashsale-build.ps1 [options]
# ============================================================

param(
    [switch]$All,
    [switch]$Infra,
    [switch]$Backend,
    [switch]$Frontend,
    [switch]$Build,
    [switch]$Up,
    [switch]$Down,
    [switch]$SkipBuild,
    [switch]$NoMaven,
    [switch]$MavenParallel,
    [switch]$Detach,
    [switch]$D,
    [switch]$FrontendProd,
    [switch]$Watch,
    [switch]$RemoveVolumes,
    [switch]$V,
    [switch]$RemoveImages,
    [switch]$Rmi,
    [switch]$RemoveOrphans,
    [switch]$Remove,
    [switch]$Clean,
    [switch]$CleanImages,
    [switch]$CleanVolumes,
    [switch]$Stop,
    [switch]$BuildBackend,
    [switch]$BuildFrontend,
    [switch]$Status,
    [switch]$Logs,
    [switch]$Ports,
    [switch]$Health,
    [switch]$Help
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = if ($PSScriptRoot) { $PSScriptRoot } else { $PWD.Path }

if ($D)            { $Detach = $true }
if ($NoMaven)      { $SkipBuild = $true }
if ($V)            { $RemoveVolumes = $true }
if ($Rmi)          { $RemoveImages = $true }

$noModeGiven = -not ($Up -or $All -or $Infra -or $Backend -or $Frontend -or $Build -or $Down -or $Stop -or $Clean -or $CleanImages -or $CleanVolumes -or $Status -or $Logs -or $Ports -or $Health -or $Help -or $BuildBackend -or $BuildFrontend)
if ($noModeGiven) { $All = $true }

if ($Help) {
    Write-Host "FLASH SALE BUILD SCRIPT USAGE"
    Write-Host "================================"
    Write-Host ""
    Write-Host "MODES:"
    Write-Host "  -All           Start everything [default]"
    Write-Host "  -Infra         Start infrastructure only"
    Write-Host "  -Backend       Start backend services only"
    Write-Host "  -Frontend      Start frontend apps only"
    Write-Host "  -Build         Maven build only (no docker)"
    Write-Host "  -BuildBackend  Maven build backend only"
    Write-Host "  -BuildFrontend Maven build frontend only"
    Write-Host ""
    Write-Host "UP OPTIONS:"
    Write-Host "  -Detach (-D)   Run in background (docker compose -d)"
    Write-Host "  -Watch         Run in foreground (default)"
    Write-Host "  -SkipBuild     Skip Maven build"
    Write-Host "  -MavenParallel Run Maven with parallel threads (-T 2C)"
    Write-Host "  -FrontendProd  Build frontend as production nginx"
    Write-Host ""
    Write-Host "DOWN OPTIONS:"
    Write-Host "  -Down          Stop and remove containers"
    Write-Host "  -Down -V       Also remove named volumes (DATA LOSS!)"
    Write-Host "  -Down -Rmi     Also remove service images"
    Write-Host "  -Down -RemoveOrphans  Remove orphaned containers"
    Write-Host "  -Down -V -Rmi  Full cleanup: containers + volumes + images"
    Write-Host "  -Remove        Alias for -Down (containers only)"
    Write-Host ""
    Write-Host "CLEANUP:"
    Write-Host "  -Clean         Remove ALL containers, volumes, images"
    Write-Host "  -Stop          Stop all running containers (no removal)"
    Write-Host "  -CleanImages   Remove all unused images"
    Write-Host "  -CleanVolumes  Remove all dangling volumes"
    Write-Host ""
    Write-Host "INFO:"
    Write-Host "  -Status        Show container status"
    Write-Host "  -Logs          Tail logs"
    Write-Host "  -Ports         Show exposed ports"
    Write-Host "  -Health        Check service health"
    Write-Host ""
    Write-Host "EXAMPLES:"
    Write-Host "  .\flashsale-build.ps1 -All -Detach"
    Write-Host "  .\flashsale-build.ps1 -Build"
    Write-Host "  .\flashsale-build.ps1 -Down -V"
    Write-Host "  .\flashsale-build.ps1 -Status"
    exit 0
}

function Get-ScriptDir {
    if ($PSScriptRoot) { $PSScriptRoot } else { $PWD.Path }
}

function Test-EnvFile {
    $envPath = Join-Path (Get-ScriptDir) ".env"
    if (-not (Test-Path $envPath)) {
        Write-Host "[WARN] .env not found at project root." -ForegroundColor Yellow
        return $false
    }
    return $true
}

function Test-File {
    param([string]$Path, [string]$Name)
    if (-not (Test-Path $Path)) {
        Write-Host "[WARN] $Name not found: $Path" -ForegroundColor Yellow
        return $false
    }
    return $true
}

function Run-DockerCompose {
    param(
        [string]$File,
        [string]$Args,
        [string]$WorkingDir = (Get-ScriptDir),
        [switch]$Quiet
    )
    Write-Host "  > docker compose -f `"$File`" $Args" -ForegroundColor DarkGray
    Push-Location $WorkingDir -PassThru | Out-Null
    try {
        if ($Quiet) {
            $null = docker compose -f $File $Args 2>&1
        } else {
            docker compose -f $File $Args
        }
        return $LASTEXITCODE
    } finally {
        Pop-Location
    }
}

function Get-DownFlags {
    $flags = "down"
    if ($RemoveVolumes)   { $flags = $flags + " -v" }
    if ($RemoveImages)    { $flags = $flags + " --rmi local" }
    if ($RemoveOrphans)  { $flags = $flags + " --remove-orphans" }
    return $flags.Trim()
}

function Write-Step { param([string]$msg) Write-Host "`n>>> $msg" -ForegroundColor Cyan }
function Write-Success { param([string]$msg) Write-Host "[OK] $msg" -ForegroundColor Green }
function Write-Warn { param([string]$msg) Write-Host "[WARN] $msg" -ForegroundColor Yellow }
function Write-Fail { param([string]$msg) Write-Host "[FAIL] $msg" -ForegroundColor Red }

function Stop-AllContainers {
    Write-Step "Stopping all containers..."
    Run-DockerCompose -File "docker compose.yml" -Args "down" -Quiet
    Run-DockerCompose -File "docker compose-infrastructure.yml" -Args "down" -Quiet
    Run-DockerCompose -File "docker compose-backend.yml" -Args "down" -Quiet
    Write-Success "All containers stopped."
}

function Show-Status {
    Write-Host "`n=== CONTAINER STATUS ===" -ForegroundColor Cyan
    $code = Run-DockerCompose -File "docker compose.yml" -Args "ps" -Quiet
    if ($LASTEXITCODE -ne 0) {
        docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" 2>$null
    }
}

function Show-Ports {
    Write-Host "`n=== EXPOSED PORTS ===" -ForegroundColor Cyan
    $running = docker ps --format "{{.Names}}" 2>$null
    if ($running) {
        $running | ForEach-Object {
            $name = $_
            $ports = docker port $name 2>$null
            if ($ports) {
                Write-Host "$name" -ForegroundColor Yellow
                $ports -split "`n" | ForEach-Object { Write-Host "  $_" }
            }
        }
    } else {
        Write-Host "(No containers running)" -ForegroundColor DarkGray
    }
    Write-Host ""
    Write-Host "PORT REFERENCE:" -ForegroundColor Cyan
    Write-Host "  8080 API Gateway    | 8761 Eureka     | 8081 Identity"
    Write-Host "  8082 Payment       | 8083 Order      | 8085 FlashSale"
    Write-Host "  8086 Worker        | 8090 Product    | 8091 Search"
    Write-Host "  8092 Notification  | 3000 Customer   | 3001 Seller"
    Write-Host "  3002 Admin         | 9200 Elastic    | 9000 MinIO"
    Write-Host "  9001 MinIO Console | 5432 Postgres   | 27017 Mongo"
    Write-Host "  6379 Redis         | 9092 Kafka     | 2181 Zookeeper"
    Write-Host "  8024 Axon GUI      | 8124 Axon gRPC"
}

function Show-Health {
    Write-Host "`n=== SERVICE HEALTH ===" -ForegroundColor Cyan
    $svcList = @(
        @{Name="API Gateway";    Url="http://localhost:8080/actuator/health"},
        @{Name="Discovery";       Url="http://localhost:8761/actuator/health"},
        @{Name="Elasticsearch";   Url="http://localhost:9200"},
        @{Name="MinIO";          Url="http://localhost:9000/minio/health/live"}
    )
    foreach ($svc in $svcList) {
        try {
            $resp = Invoke-WebRequest -Uri $svc.Url -UseBasicParsing -TimeoutSec 3 -ErrorAction SilentlyContinue
            $ok = ($resp.StatusCode -eq 200)
        } catch { $ok = $false }
        $status = if ($ok) { "UP" } else { "DOWN" }
        $color = if ($ok) { "Green" } else { "Red" }
        $padded = $svc.Name.PadRight(16)
        Write-Host "  $padded : " -NoNewline
        Write-Host $status -ForegroundColor $color
    }
    $infraList = @(
        @{Name="PostgreSQL"; Container="fs-postgres"},
        @{Name="Redis";      Container="fs-redis"},
        @{Name="MongoDB";    Container="fs-mongo"}
    )
    foreach ($svc in $infraList) {
        $padded = $svc.Name.PadRight(16)
        $st = docker inspect --format="{{.State.Health.Status}}" $svc.Container 2>$null
        if ($st -eq "healthy") {
            Write-Host "  $padded : " -NoNewline
            Write-Host "UP (healthy)" -ForegroundColor Green
        } elseif ($st -eq "unhealthy") {
            Write-Host "  $padded : " -NoNewline
            Write-Host "DOWN (unhealthy)" -ForegroundColor Red
        } elseif ($st -eq "starting") {
            Write-Host "  $padded : " -NoNewline
            Write-Host "STARTING" -ForegroundColor Yellow
        } else {
            Write-Host "  $padded : " -NoNewline
            Write-Host "NOT FOUND" -ForegroundColor DarkGray
        }
    }
}

function Show-Logs {
    Write-Host "`n=== TAILING LOGS (Ctrl+C to stop) ===" -ForegroundColor Cyan
    docker compose -f "docker compose.yml" logs -f
}

function Invoke-MavenBuild {
    param([switch]$Parallel)
    $BackendDir = Join-Path (Get-ScriptDir) "backend"
    $pomPath = Join-Path $BackendDir "pom.xml"
    if (-not (Test-File $pomPath "backend/pom.xml")) {
        Write-Warn "Skipping Maven build."
        return
    }
    Write-Step "Building backend with Maven..."
    Push-Location $BackendDir
    try {
        $mvnArgs = @("clean", "install", "-DskipTests")
        if ($Parallel) { $mvnArgs = $mvnArgs + @("-T", "2C") }
        Write-Host "  > mvn $($mvnArgs -join ' ')" -ForegroundColor DarkGray
        mvn $mvnArgs
        if ($LASTEXITCODE -ne 0) {
            Write-Fail "Maven build failed"
            exit 1
        }
        Write-Success "Maven build completed."
    } finally {
        Pop-Location
    }
}

function Invoke-NpmBuild {
    $FeDir = Join-Path (Get-ScriptDir) "frontend"
    $pkgJson = Join-Path $FeDir "package.json"
    if (-not (Test-Path $FeDir)) {
        Write-Warn "frontend/ directory not found. Skipping."
        return
    }
    if (-not (Test-Path $pkgJson)) {
        Write-Warn "frontend/package.json not found. Skipping."
        return
    }
    Write-Step "Building frontend apps..."
    Push-Location $FeDir
    try {
        Write-Host "  > pnpm install" -ForegroundColor DarkGray
        pnpm install
        Write-Host "  > pnpm build" -ForegroundColor DarkGray
        pnpm build
        if ($LASTEXITCODE -ne 0) {
            Write-Fail "Frontend build failed"
            exit 1
        }
        Write-Success "Frontend build completed."
    } finally {
        Pop-Location
    }
}

function Start-AllServices {
    param([switch]$Detach)
    $args = @("up", "--build")
    if ($Detach) { $args += "-d" }
    if ($FrontendProd) { Write-Host "  (frontend: production nginx mode)" -ForegroundColor DarkGray }
    Write-Step "Starting all services..."
    $code = docker compose @args
    if ($code -ne 0) {
        Write-Fail "docker compose up failed"
        exit 1
    }
}

function Start-InfraServices {
    param([switch]$Detach)
    $args = @("-f", "docker compose-infrastructure.yml", "up", "--build")
    if ($Detach) { $args += "-d" }
    Write-Step "Starting infrastructure services..."
    Push-Location (Get-ScriptDir)
    try {
        $code = docker compose @args
        if ($code -ne 0) {
            Write-Fail "Failed to start infrastructure."
            exit 1
        }
    } finally {
        Pop-Location
    }
}

function Start-BackendServices {
    param([switch]$Detach)
    $args = @("-f", "docker compose-backend.yml", "up", "--build")
    if ($Detach) { $args += "-d" }
    Write-Step "Starting backend services..."
    Push-Location (Get-ScriptDir)
    try {
        $code = docker compose @args
        if ($code -ne 0) {
            Write-Warn "Trying backend standalone..."
            $code = docker compose -f "backend/docker compose.yml" @args
            if ($code -ne 0) {
                Write-Fail "Failed to start backend."
                exit 1
            }
        }
    } finally {
        Pop-Location
    }
}

function Start-FrontendServices {
    param([switch]$Detach)
    $FeDir = Join-Path (Get-ScriptDir) "frontend"
    if (-not (Test-Path $FeDir)) {
        Write-Fail "frontend/ directory not found"
        exit 1
    }
    Write-Step "Starting frontend apps..."
    Push-Location $FeDir
    try {
        if ($FrontendProd) {
            $args = @("-f", "docker compose.yml", "-f", "docker compose.prod.yml", "up", "--build")
            if ($Detach) { $args += "-d" }
            Write-Host "  (production mode: nginx containers)" -ForegroundColor DarkGray
            $code = docker compose @args
        } else {
            $args = @("-f", "docker compose.yml", "up", "--build")
            if ($Detach) { $args += "-d" }
            Write-Host "  (dev mode: Vite HMR)" -ForegroundColor DarkGray
            $code = docker compose @args
        }
        if ($code -ne 0) {
            Write-Fail "Failed to start frontend."
            exit 1
        }
    } finally {
        Pop-Location
    }
}

function Stop-Down {
    $flags = Get-DownFlags
    Write-Step "Running: docker compose $flags"
    Push-Location (Get-ScriptDir)
    try {
        docker compose -f "docker compose.yml" $flags 2>$null
        docker compose -f "docker compose-infrastructure.yml" $flags 2>$null
        docker compose -f "docker compose-backend.yml" $flags 2>$null
    } finally {
        Pop-Location
    }
    Write-Success "Down complete."
}

function Clean-All {
    Write-Step "FULL CLEAN - removing ALL containers, volumes, images..."
    Write-Warn "This will DELETE ALL DATA (Postgres, MongoDB, Redis, Kafka, Elasticsearch)!"
    Push-Location (Get-ScriptDir)
    try {
        docker compose -f "docker compose.yml" down -v --rmi local 2>$null
        docker compose -f "docker compose-infrastructure.yml" down -v 2>$null
        docker compose -f "docker compose-backend.yml" down -v --rmi local 2>$null
    } finally {
        Pop-Location
    }
    $backendDir = Join-Path (Get-ScriptDir) "backend"
    Push-Location $backendDir
    try {
        docker compose -f "docker compose.yml" down -v --rmi local 2>$null
    } finally {
        Pop-Location
    }
    $frontendDir = Join-Path (Get-ScriptDir) "frontend"
    Push-Location $frontendDir
    try {
        docker compose -f "docker compose.yml" down -v --rmi local 2>$null
    } finally {
        Pop-Location
    }
    docker network rm flashsale-net 2>$null
    docker image prune -f 2>$null
    docker volume prune -f 2>$null
    Write-Success "Full clean complete."
}

function Show-AccessPoints {
    Write-Host ""
    Write-Host "View logs:    docker compose logs -f" -ForegroundColor Yellow
    Write-Host "Check status: .\flashsale-build.ps1 -Status" -ForegroundColor Yellow
    Write-Host "View ports:   .\flashsale-build.ps1 -Ports" -ForegroundColor Yellow
    Write-Host "Check health: .\flashsale-build.ps1 -Health" -ForegroundColor Yellow
    Write-Host "Stop all:     .\flashsale-build.ps1 -Down" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Access points:" -ForegroundColor Yellow
    Write-Host "  http://localhost:8080  API Gateway / Swagger UI"
    Write-Host "  http://localhost:8761  Eureka Discovery"
    Write-Host "  http://localhost:3000  Customer App"
    Write-Host "  http://localhost:3001  Seller App"
    Write-Host "  http://localhost:3002  Admin App"
    Write-Host "  http://localhost:9001  MinIO Console"
    Write-Host ""
}

function Show-FrontendAccess {
    Write-Host ""
    Write-Host "Access apps at:" -ForegroundColor Yellow
    Write-Host "  http://localhost:3000  Customer App"
    Write-Host "  http://localhost:3001  Seller App"
    Write-Host "  http://localhost:3002  Admin App"
    Write-Host ""
}

# ======================================================================
# MAIN
# ======================================================================

Set-Location (Get-ScriptDir)

if ($Status)       { Show-Status; exit 0 }
if ($Ports)        { Show-Ports; exit 0 }
if ($Health)       { Show-Health; exit 0 }
if ($Logs)         { Show-Logs; exit 0 }

$envOk = Test-EnvFile

if ($Stop)         { Stop-AllContainers; exit 0 }

if ($Clean) {
    Clean-All
    exit 0
}
if ($CleanImages) {
    Write-Step "Removing unused images..."
    docker image prune -a -f 2>$null
    Write-Success "Done."
    exit 0
}
if ($CleanVolumes) {
    Write-Step "Removing dangling volumes..."
    docker volume prune -f 2>$null
    Write-Success "Done."
    exit 0
}

if ($Down -or $Remove) {
    Stop-Down
    exit 0
}

if ($Build -or $BuildBackend) {
    Invoke-MavenBuild -Parallel:$MavenParallel
    exit 0
}

if ($BuildFrontend) {
    Invoke-NpmBuild
    exit 0
}

if (-not $SkipBuild -and ($All -or $Backend)) {
    Invoke-MavenBuild -Parallel:$MavenParallel
}

$runDetach = -not $Watch
if ($Detach) { $runDetach = $true }
if ($Watch) { $runDetach = $false }

if ($All) {
    Start-AllServices -Detach:$runDetach
    if ($runDetach) { Show-AccessPoints }
    exit 0
}

if ($Infra) {
    Start-InfraServices -Detach:$runDetach
    exit 0
}

if ($Backend) {
    Start-BackendServices -Detach:$runDetach
    exit 0
}

if ($Frontend) {
    Start-FrontendServices -Detach:$runDetach
    if ($runDetach) { Show-FrontendAccess }
    exit 0
}
