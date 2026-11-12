@echo off
setlocal enabledelayedexpansion

echo ===================================================
echo Starting BuyBound Platform
echo ===================================================

if not exist .env (
    if exist .env.example (
        copy .env.example .env >nul
        echo [+] Created .env from .env.example
    )
)

echo [1/3] Checking Docker status...
docker info >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo [+] Docker engine is online and ready.
    echo.
    echo [2/3] Starting Postgres Database and Spring Boot Backend (Docker Compose)...
    docker compose up -d
    if %ERRORLEVEL% NEQ 0 (
        echo [!] Docker compose returned an issue. Check docker logs.
    ) else (
        echo [+] Database and Spring Boot Backend containers are running.
    )
) else (
    echo [!] Docker engine is not running. Please start Docker Desktop to run the backend and database.
)

echo.
echo [3/3] Starting Frontend UI (Vite dev server)...
cd frontend
start cmd /k "npm run dev"
cd ..

echo.
echo ===================================================
echo BuyBound platform is running!
echo Backend API:  http://localhost:8080
echo Frontend UI:  http://localhost:5173
echo ===================================================
echo.
timeout /t 2 /nobreak >nul
start http://localhost:5173
