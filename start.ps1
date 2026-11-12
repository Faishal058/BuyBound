Write-Host "===================================================" -ForegroundColor Cyan
Write-Host "Starting BuyBound Platform" -ForegroundColor Cyan
Write-Host "===================================================" -ForegroundColor Cyan

if (-not (Test-Path .env)) {
    if (Test-Path .env.example) {
        Copy-Item .env.example .env
        Write-Host "[+] Created .env from .env.example" -ForegroundColor Green
    }
}

Write-Host "`n[1/3] Checking Docker engine status..." -ForegroundColor Yellow

$dockerRunning = $false
try {
    $null = docker info 2>&1
    if ($LASTEXITCODE -eq 0) {
        $dockerRunning = $true
    }
} catch {
    $dockerRunning = $false
}

if ($dockerRunning) {
    Write-Host "[+] Docker engine is online and ready." -ForegroundColor Green
    Write-Host "`n[2/3] Starting Postgres Database and Spring Boot Backend (Docker Compose)..." -ForegroundColor Yellow
    docker compose up -d
    if ($LASTEXITCODE -eq 0) {
        Write-Host "[+] Database and Spring Boot Backend containers started successfully." -ForegroundColor Green
    } else {
        Write-Host "[!] Docker compose encountered an issue. Check docker logs." -ForegroundColor Yellow
    }
} else {
    Write-Host "[!] Docker engine is not running. Please start Docker Desktop to run backend and PostgreSQL." -ForegroundColor Yellow
}

Write-Host "`n[3/3] Starting Frontend UI (Vite dev server)..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location frontend; npm run dev"

Start-Sleep -Seconds 2
Start-Process "http://localhost:5173"

Write-Host "`n===================================================" -ForegroundColor Green
Write-Host "BuyBound platform is running!" -ForegroundColor Green
Write-Host "Backend API:  http://localhost:8080" -ForegroundColor Green
Write-Host "Frontend UI:  http://localhost:5173" -ForegroundColor Green
Write-Host "===================================================`n" -ForegroundColor Green
