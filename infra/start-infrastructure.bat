@echo off
REM ==================================================================================
REM Infrastructure-Only Docker Compose Script
REM Khởi động chỉ các dịch vụ hạ tầng (databases, message queues, etc.)
REM ==================================================================================

setlocal enabledelayedexpansion

echo.
echo ==================================================================================
echo STEALING FROM PARADISE - INFRASTRUCTURE STACK
echo ==================================================================================
echo.

REM Get the directory where the script is located
set SCRIPT_DIR=%~dp0
cd /d "%SCRIPT_DIR%"

echo [INFO] Current directory: %cd%
echo.

REM ==================================================================================
REM STEP 1: Check if Docker is available
REM ==================================================================================
echo [STEP 1] Checking Docker installation...
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
REM STEP 2: Load environment variables from .env file
REM ==================================================================================
echo [STEP 2] Loading environment variables...
if exist ".env" (
    echo [OK] .env file found
    for /f "eol=# delims=" %%x in (.env) do (
        set "%%x"
    )
) else (
    echo [WARNING] .env file not found in parent directory
)
echo.

REM ==================================================================================
REM STEP 3: Start Infrastructure Stack
REM ==================================================================================
echo [STEP 3] Starting Infrastructure stack...
echo [INFO] This will start all databases, caches, and message queues...
echo.

REM Stop any existing infrastructure containers
echo [INFO] Stopping any existing infrastructure containers...
docker-compose down -v --remove-orphans 2>&1

echo.
echo [INFO] Starting Infrastructure stack...
docker-compose up -d 2>&1

if errorlevel 1 (
    echo.
    echo [ERROR] Docker Compose startup FAILED!
    echo [INFO] Run 'docker-compose logs' for details
    pause
    exit /b 1
)

echo.
echo [OK] Infrastructure stack started
echo.

REM ==================================================================================
REM STEP 4: Display stack status
REM ==================================================================================
echo [STEP 4] Checking container health status...
echo.

timeout /t 5 /nobreak
docker-compose ps

echo.
echo ==================================================================================
echo INFRASTRUCTURE STACK READY!
echo ==================================================================================
echo.
echo [INFO] Available services:
echo   - PostgreSQL:    localhost:5432
echo   - MongoDB:       localhost:27017
echo   - Redis:         localhost:6379
echo   - Elasticsearch: localhost:9200
echo   - Minio:         localhost:9000 (API), http://localhost:9001 (Console)
echo   - Kafka:         localhost:9092
echo   - Zookeeper:     localhost:2181
echo   - AxonServer:    localhost:8024 (Dashboard), localhost:8124 (gRPC)
echo.
echo [INFO] Next steps:
echo   1. Start backend services: cd ..\backend && build-and-compose.bat
echo   2. Or start frontend: cd ..\frontend && build-and-compose.bat
echo   3. Or develop from IDE using this infrastructure
echo.
echo [INFO] Useful commands:
echo   - View logs:     docker-compose logs -f [service_name]
echo   - Stop stack:    docker-compose down
echo   - Stop and reset volumes: docker-compose down -v
echo.
pause

