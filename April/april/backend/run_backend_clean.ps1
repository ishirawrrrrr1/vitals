# VITALS MONITOR BACKEND - PRE-FLIGHT INTEGRITY CHECK
# CAPTURE THE ACTUAL SCRIPT DIRECTORY
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
Set-Location $ScriptDir

Clear-Host
Write-Host "===========================================" -ForegroundColor Cyan
Write-Host "   VITALS HUB - PRE-FLIGHT INTEGRITY CHECK " -ForegroundColor Cyan
Write-Host "===========================================" -ForegroundColor Cyan

# --- 1. DEPENDENCY CHECKS ---
Write-Host "[*] Checking Node.js..." -NoNewline
if (!(Get-Command node -ErrorAction SilentlyContinue)) {
    Write-Host " FAIL" -ForegroundColor Red
    Write-Host "`nERROR: Node.js is missing. Please install it from https://nodejs.org/" -ForegroundColor Yellow
    pause; exit
}
$nodeVer = node -v
Write-Host " OK ($nodeVer)" -ForegroundColor Green

Write-Host "[*] Checking node_modules..." -NoNewline
if (!(Test-Path "$ScriptDir\node_modules")) {
    Write-Host " MISSING" -ForegroundColor Yellow
    Write-Host ">>> Attempting automatic 'npm install'..." -ForegroundColor Gray
    npm install
    if ($LASTEXITCODE -ne 0) {
        Write-Host "FATAL: npm install failed. check internet." -ForegroundColor Red
        pause; exit
    }
}
Write-Host " OK" -ForegroundColor Green

# --- 2. DATABASE READINESS ---
Write-Host "[*] Checking MySQL (XAMPP)..." -NoNewline
$tcpConnection = New-Object System.Net.Sockets.TcpClient
$wait = $tcpConnection.BeginConnect("localhost", 3306, $null, $null)
$success = $wait.AsyncWaitHandle.WaitOne(1000, $false)
if (!$success) {
    Write-Host " WARNING (PORT 3306 CLOSED)" -ForegroundColor Yellow
    Write-Host ">>> ADVICE: Ensure XAMPP MySQL is started." -ForegroundColor Gray
} else {
    $tcpConnection.EndConnect($wait)
    Write-Host " OK" -ForegroundColor Green
}
$tcpConnection.Close()

# --- 3. PORT 3000 CLEANUP ---
Write-Host "[*] Clearing Port 3000..." -NoNewline
$port3000 = Get-NetTCPConnection -LocalPort 3000 -ErrorAction SilentlyContinue
if ($port3000) {
    foreach ($proc in $port3000) {
        if ($proc.OwningProcess -gt 0) {
            Stop-Process -Id $proc.OwningProcess -Force -ErrorAction SilentlyContinue
        }
    }
    Start-Sleep -Seconds 1
    Write-Host " CLEANED" -ForegroundColor Green
} else {
    Write-Host " ALREADY CLEAR" -ForegroundColor Green
}

# --- 4. NETWORK / FIREWALL ---
$currentPrincipal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
$isAdmin = $currentPrincipal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

if ($isAdmin) {
    Write-Host "[*] Admin Mode detected. Applying Firewall Fixes..." -ForegroundColor Cyan
    if (!(Get-NetFirewallRule -DisplayName "Vitals Node Backend" -ErrorAction SilentlyContinue)) {
        New-NetFirewallRule -DisplayName "Vitals Node Backend" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 3000 -Profile Any -Description "Allows Vitals App to connect to Laptop"
        Write-Host " Firewall exception applied." -ForegroundColor Green
    }
}

# --- 5. START SERVER ---
$hostname = [System.Net.Dns]::GetHostName()
$ipList = Get-NetIPAddress | Where-Object { $_.AddressFamily -eq 'IPv4' -and $_.PrefixOrigin -eq 'Dhcp' }

Write-Host "`nSTARTING MASTER SERVER..." -ForegroundColor Magenta
# Start in a new window so the user can see the console logs
Start-Process cmd.exe -ArgumentList "/k cd /d `"$ScriptDir`" && title Vitals Server Console && node index.js" -WindowStyle Normal

# --- 6. VALIDATION LOOP (Wait for response) ---
Write-Host "[*] Waiting for server to wake up..." -NoNewline
$retries = 0
$serverReady = $false
while ($retries -lt 10 -and !$serverReady) {
    try {
        $check = Invoke-WebRequest -Uri "http://localhost:3000" -Method Head -UseBasicParsing -TimeoutSec 1 -ErrorAction SilentlyContinue
        if ($check.StatusCode -eq 200) { $serverReady = $true }
    } catch {}
    if (!$serverReady) {
        Write-Host "." -NoNewline
        Start-Sleep -Seconds 1
        $retries++
    }
}

if ($serverReady) {
    Write-Host " ONLINE!" -ForegroundColor Green
    Start-Process "http://localhost:3000"
    Write-Host "`n===========================================" -ForegroundColor Cyan
    Write-Host "   SYSTEM IS RUNNING AND ASSURED!          " -ForegroundColor Green
    Write-Host "===========================================" -ForegroundColor Cyan
    Write-Host "PC Name: $hostname.local"
    foreach ($ip in $ipList) {
        Write-Host "App IP:  http://$($ip.IPAddress):3000" -ForegroundColor Yellow
    }
    Write-Host "-------------------------------------------"
    Write-Host "Keep this window open for your IP reference."
    Write-Host "The Server Logs are in the OTHER window."
} else {
    Write-Host " TIMEOUT" -ForegroundColor Red
    Write-Host "The server took too long to start. Check the Server Console window for errors." -ForegroundColor Yellow
}

# Keep window open for IP reference
Write-Host "`nPress any key to close this monitor..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
