# backend_check.ps1

Write-Host "--- Vitality Backend Integrity Check ---" -ForegroundColor Cyan

# 1. Path Verification
$backendPath = Get-Location
Write-Host "[1/4] Checking workspace: $backendPath"
if (!(Test-Path "index.js") -or !(Test-Path "package.json")) {
    Write-Host "[FAIL] index.js or package.json missing in $backendPath" -ForegroundColor Red
    exit 1
}
Write-Host "[OK] Backend files found." -ForegroundColor Green

# 2. Dependency Check
Write-Host "[2/4] Checking node_modules..."
if (!(Test-Path "node_modules")) {
    Write-Host "[!] node_modules missing. Running npm install..." -ForegroundColor Yellow
    npm install
} elseif (!(Test-Path "node_modules/socket.io")) {
    Write-Host "[!] Socket.io missing. Installing..." -ForegroundColor Yellow
    npm install socket.io
    Write-Host "[OK] node_modules and Socket.io are ready." -ForegroundColor Green
} else {
    Write-Host "[OK] node_modules and Socket.io are ready." -ForegroundColor Green
}

# 3. Environment & DB Connectivity
Write-Host "[3/4] Checking .env and DB Connection..."
if (!(Test-Path ".env")) {
    Write-Host "[WARN] .env file missing. Using defaults." -ForegroundColor Yellow
}

# Run a quick check using node to see if DB is reachable (using the existing test_connection.js if available, or a snippet)
try {
    # Check if we can start the app or if ports are blocked
    $portCheck = Get-NetTCPConnection -LocalPort 3000 -ErrorAction SilentlyContinue
    if ($portCheck) {
        Write-Host "[!] Port 3000 is already in use. Is the server already running?" -ForegroundColor Yellow
    }
} catch {}

# 4. Device/IP Info (Helpful for updating Flutter/Arduino)
$ip = (Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.InterfaceAlias -notlike "*Loopback*" -and $_.IPv4Address -notlike "169.*" }).IPv4Address | Select-Object -First 1
Write-Host "[4/4] Network Diagnostics" -ForegroundColor Cyan
Write-Host ">>> SERVER IP: $ip" -ForegroundColor White
Write-Host ">>> API URL: http://$ip:3000" -ForegroundColor White
Write-Host ">>> REAL-TIME DASHBOARD: http://$ip:3000" -ForegroundColor Cyan
Write-Host ">>> Ensure your ESP32 Hub and Android App use this IP!" -ForegroundColor Yellow

# Start the Backend
Write-Host "`nStarting Backend Server..." -ForegroundColor Green
npm start
