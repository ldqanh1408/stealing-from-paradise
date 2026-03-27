@echo off
REM ==================================================================================
REM Build and Docker Compose Script for Stealing from Paradise - Backend
REM This script:
REM 1. Cleans previous builds
REM 2. Builds all Maven projects
REM 3. Starts Docker Compose stack
REM ==================================================================================

setlocal enabledelayedexpansion

echo.
echo ==================================================================================
echo STEALING FROM PARADISE - BACKEND BUILD AND DOCKER COMPOSE ORCHESTRATION
echo ==================================================================================
echo.

REM Get the directory where the script is located
set SCRIPT_DIR=%~dp0
cd /d "%SCRIPT_DIR%"

echo [INFO] Current directory: %cd%
echo.

REM ==================================================================================
REM STEP 1: Check if Maven is available
REM ==================================================================================
echo [STEP 1] Checking Maven installation...
where mvn >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Maven not found in PATH. Please install Maven first.
    echo [INFO] Download from: https://maven.apache.org/download.cgi
    pause
    exit /b 1
)
echo [OK] Maven found
echo.

REM ==================================================================================
REM STEP 2: Check if Docker is available
REM ==================================================================================
echo [STEP 2] Checking Docker installation...
where docker >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker not found in PATH. Please install Docker first.
    echo [INFO] Download from: https://www.docker.com/products/docker-desktop
    pause
    exit /b 1
)
echo [OK] Docker found
echo.

REM ==================================================================================
REM STEP 3: Load environment variables from .env file
REM ==================================================================================
echo [STEP 3] Loading environment variables from .env file...
if exist ".env" (
    echo [OK] .env file found
    REM Read .env file and set variables
    for /f "eol=# delims=" %%x in (.env) do (
        set "%%x"
    )
) else (
    echo [WARNING] .env file not found in %SCRIPT_DIR%
    echo [INFO] Using default environment variables
)
echo.

REM ==================================================================================
REM STEP 4: Clean Maven repository (optional - removes old artifacts)
REM ==================================================================================
echo [STEP 4] Cleaning Maven build directories...
if exist ".\target" (
    echo [INFO] Removing root target directory...
    rmdir /s /q ".\target" >nul 2>&1
)

REM Clean all service target directories
for /d %%D in (discovery-service, api-gateway, cart-service, flashsale-service, notification-service, search-service, worker-service) do (
    if exist ".\%%D\target" (
        echo [INFO] Removing %%D\target...
        rmdir /s /q ".\%%D\target" >nul 2>&1
    )
)

REM Clean nested service structures
if exist ".\identity-service\identity-domain\target" (
    echo [INFO] Removing identity-service\identity-domain\target...
    rmdir /s /q ".\identity-service\identity-domain\target" >nul 2>&1
)

if exist ".\order-service\order-domain\target" (
    echo [INFO] Removing order-service\order-domain\target...
    rmdir /s /q ".\order-service\order-domain\target" >nul 2>&1
)

if exist ".\product-service\product-domain\target" (
    echo [INFO] Removing product-service\product-domain\target...
    rmdir /s /q ".\product-service\product-domain\target" >nul 2>&1
)

if exist ".\payment-service\payment-domain\target" (
    echo [INFO] Removing payment-service\payment-domain\target...
    rmdir /s /q ".\payment-service\payment-domain\target" >nul 2>&1
)

echo [OK] Maven directories cleaned
echo.

REM ==================================================================================
REM STEP 5: Build all Maven projects
REM ==================================================================================
echo [STEP 5] Building all Maven projects...
echo [INFO] This may take a few minutes...
echo.

call mvn clean install -DskipTests -U 2>&1 | tee build.log

REM Check if build succeeded
if errorlevel 1 (
    echo.
    echo [ERROR] Maven build FAILED!
    echo [INFO] Check build.log for details
    pause
    exit /b 1
)

echo.
echo [OK] Maven build completed successfully
echo.

REM ==================================================================================
REM STEP 6: Start Docker Compose stack
REM ==================================================================================
echo [STEP 6] Starting Docker Compose stack...
echo [INFO] This will start all containers (infrastructure and microservices)
echo.

REM Stop any existing containers first
echo [INFO] Stopping any existing containers...
docker-compose down -v --remove-orphans 2>&1

echo.
echo [INFO] Starting fresh Docker Compose stack...
docker-compose up -d 2>&1

REM Check if docker-compose succeeded
if errorlevel 1 (
    echo.
    echo [ERROR] Docker Compose startup FAILED!
    echo [INFO] Run 'docker-compose logs' for details
    pause
    exit /b 1
)

echo.
echo [OK] Docker Compose stack started
echo.

REM ==================================================================================
REM STEP 7: Display stack status
REM ==================================================================================
echo [STEP 7] Checking container health status...
echo.

timeout /t 5 /nobreak
docker-compose ps

echo.
echo ==================================================================================
echo BUILD AND DOCKER COMPOSE COMPLETE!
echo ==================================================================================
echo.
echo [INFO] Available services:
echo   - API Gateway:         http://localhost:8080
echo   - Discovery Service:   http://localhost:8761
echo   - Elasticsearch:       http://localhost:9200
echo   - Minio Console:       http://localhost:9001
echo   - AxonServer:          http://localhost:8024
echo.
echo [INFO] Useful commands:
echo   - View logs:           docker-compose logs -f [service_name]
echo   - Stop stack:          docker-compose down
echo   - Stop and remove volumes: docker-compose down -v
echo   - Rebuild specific service: docker-compose up -d --build [service_name]
echo.
echo [INFO] Build log saved to: build.log
echo.
pause

