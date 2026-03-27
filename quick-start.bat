@echo off
REM ==================================================================================
REM Quick Start - Opens all necessary terminals for development
REM Mở tất cả các terminal cần thiết để phát triển hệ thống
REM ==================================================================================

setlocal enabledelayedexpansion

echo.
echo ==================================================================================
echo STEALING FROM PARADISE - QUICK START ORCHESTRATOR
echo ==================================================================================
echo.

REM Get the root directory
set ROOT_DIR=%~dp0

echo [INFO] Root directory: %ROOT_DIR%
echo.

echo ==================================================================================
echo SELECT YOUR DEVELOPMENT SETUP:
echo ==================================================================================
echo.
echo 1. INFRASTRUCTURE ONLY (for IDE development)
echo    - Starts databases, caches, message queues
echo    - You develop microservices in IDE
echo.
echo 2. FULL BACKEND STACK (containerized)
echo    - Builds and starts all backend services in Docker
echo    - Best for integration testing
echo.
echo 3. FULL STACK (Backend + Frontend)
echo    - Starts backend, frontend, and infrastructure
echo    - Complete system testing
echo.
echo 4. CUSTOM (choose each component)
echo    - Manually select which layers to start
echo.

set /p CHOICE="Enter your choice (1-4): "

if "%CHOICE%"=="1" goto INFRA_ONLY
if "%CHOICE%"=="2" goto BACKEND_ONLY
if "%CHOICE%"=="3" goto FULL_STACK
if "%CHOICE%"=="4" goto CUSTOM
goto INVALID

REM ==================================================================================
:INFRA_ONLY
echo.
echo [INFO] Starting Infrastructure Only setup...
echo.
cd /d "%ROOT_DIR%infra"
start "Infrastructure Stack" cmd /k start-infrastructure.bat
echo [OK] Infrastructure terminal opened
echo.
echo [INFO] You can now start microservices from your IDE:
echo   - Open IDE project in: %ROOT_DIR%backend
echo   - Run individual Spring Boot applications
echo   - They will connect to infrastructure services
echo.
pause
exit /b 0

REM ==================================================================================
:BACKEND_ONLY
echo.
echo [INFO] Starting Backend Stack (with Infrastructure)...
echo.
cd /d "%ROOT_DIR%backend"
start "Backend Stack" cmd /k build-and-compose.bat
echo [OK] Backend terminal opened
echo.
echo [INFO] Wait for all containers to be healthy before testing
echo.
pause
exit /b 0

REM ==================================================================================
:FULL_STACK
echo.
echo [INFO] Starting Full Stack (Backend + Frontend + Infrastructure)...
echo.
echo [STEP 1] Opening Backend Stack...
cd /d "%ROOT_DIR%backend"
start "Backend Stack" cmd /k build-and-compose.bat

echo [INFO] Waiting for backend to start before frontend...
timeout /t 30

echo [STEP 2] Opening Frontend Stack...
cd /d "%ROOT_DIR%frontend"
start "Frontend Stack" cmd /k build-and-compose.bat

echo.
echo [OK] Both backend and frontend terminals opened
echo [INFO] Frontend will take ~5-10 minutes to build and start
echo.
echo Available at:
echo   - API Gateway: http://localhost:8080
echo   - Customer App: http://localhost:3000
echo   - Seller Center: http://localhost:3001
echo   - Admin Portal: http://localhost:3002
echo.
pause
exit /b 0

REM ==================================================================================
:CUSTOM
echo.
echo ==================================================================================
echo CUSTOM SETUP - Choose which layers to start
echo ==================================================================================
echo.

set /p INFRA="Start Infrastructure? (y/n): "
set /p BACKEND="Start Backend Services? (y/n): "
set /p FRONTEND="Start Frontend Apps? (y/n): "

if /i "%INFRA%"=="y" (
    cd /d "%ROOT_DIR%infra"
    start "Infrastructure Stack" cmd /k start-infrastructure.bat
    timeout /t 10
)

if /i "%BACKEND%"=="y" (
    cd /d "%ROOT_DIR%backend"
    start "Backend Stack" cmd /k build-and-compose.bat
    timeout /t 30
)

if /i "%FRONTEND%"=="y" (
    cd /d "%ROOT_DIR%frontend"
    start "Frontend Stack" cmd /k build-and-compose.bat
)

echo.
echo [OK] Selected components are starting
echo.
pause
exit /b 0

REM ==================================================================================
:INVALID
echo.
echo [ERROR] Invalid choice. Please select 1-4
echo.
pause
goto start

