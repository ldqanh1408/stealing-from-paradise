@echo off
REM ==================================================================================
REM Frontend Build and Docker Compose Script
REM This script builds and starts frontend applications
REM ==================================================================================

setlocal enabledelayedexpansion

echo.
echo ==================================================================================
echo STEALING FROM PARADISE - FRONTEND BUILD AND DOCKER COMPOSE
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
    REM Read .env file and set variables
    for /f "eol=# delims=" %%x in (.env) do (
        set "%%x"
    )
) else (
    echo [WARNING] .env file not found
)
echo.

REM ==================================================================================
REM STEP 3: Start Docker Compose stack
REM ==================================================================================
echo [STEP 3] Starting Docker Compose stack...
echo [INFO] Building and starting frontend applications...
echo.

REM Stop any existing containers first
echo [INFO] Stopping any existing containers...
docker-compose down --remove-orphans 2>&1

echo.
echo [INFO] Starting fresh Docker Compose stack...
docker-compose up -d --build 2>&1

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
REM STEP 4: Display stack status
REM ==================================================================================
echo [STEP 4] Checking container health status...
echo.

timeout /t 5 /nobreak
docker-compose ps

echo.
echo ==================================================================================
echo FRONTEND BUILD AND DOCKER COMPOSE COMPLETE!
echo ==================================================================================
echo.
echo [INFO] Available frontend applications:
echo   - Customer App:   http://localhost:3000
echo   - Seller Center:  http://localhost:3001
echo   - Admin Portal:   http://localhost:3002
echo.
echo [INFO] Useful commands:
echo   - View logs:           docker-compose logs -f [service_name]
echo   - Stop stack:          docker-compose down
echo   - Rebuild specific app: docker-compose up -d --build [service_name]
echo.
pause

