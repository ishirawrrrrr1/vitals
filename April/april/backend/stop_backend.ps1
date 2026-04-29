# VITALS MONITOR BACKEND CLEANUP UTILITY
# This script kills all node processes and clears Port 3000 to ensure a clean state.

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "   VITALS MONITOR CLEANUP UTILITY       " -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

# 1. Kill Node processes by Project Path (Targeted)
Write-Host "`n[*] Hunting for lingering Node processes..." -ForegroundColor Yellow
$nodeProcs = Get-Process node -ErrorAction SilentlyContinue
if ($nodeProcs) {
    foreach ($p in $nodeProcs) {
        $path = $p.Path
        if ($path -like "*node.exe*") {
            try {
                $cmdLine = (Get-CimInstance Win32_Process -Filter "ProcessId = $($p.Id)").CommandLine
                if ($cmdLine -like "*index.js*" -or $cmdLine -like "*log_cleaner.js*" -or $cmdLine -like "*monitor_status.js*" -or $cmdLine -like "*hardware_console.js*") {
                    Write-Host ">>> Killing project process: PID $($p.Id) ($($p.ProcessName))" -ForegroundColor Cyan
                    Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
                }
            } catch {
                # Fallback if CimInstance fails
                Write-Host ">>> Stopping Node process: PID $($p.Id)" -ForegroundColor Gray
                Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
            }
        }
    }
}

# 2. Force Clear Port 3000 (Nuclear Option)
Write-Host "`n[*] Ensuring Port 3000 is released..." -ForegroundColor Yellow
$port3000 = Get-NetTCPConnection -LocalPort 3000 -ErrorAction SilentlyContinue
if ($port3000) {
    foreach ($proc in $port3000) {
        $pidToKill = $proc.OwningProcess
        if ($pidToKill -gt 0) {
            Write-Host ">>> Port 3000 occupant found (PID: $pidToKill). Terminating..." -ForegroundColor Red
            Stop-Process -Id $pidToKill -Force -ErrorAction SilentlyContinue
        }
    }
}

# 3. Close orphan CMD windows titled with our scripts (Optional/Best effort)
Write-Host "`n[*] Closing dangling terminal windows..." -ForegroundColor Yellow
$cmdWindows = Get-Process cmd -ErrorAction SilentlyContinue
if ($cmdWindows) {
    foreach ($w in $cmdWindows) {
        if ($w.MainWindowTitle -like "*index.js*" -or $w.MainWindowTitle -like "*log_cleaner.js*" -or $w.MainWindowTitle -like "*monitor_status.js*" -or $w.MainWindowTitle -like "*hardware_console.js*") {
            Stop-Process -Id $w.Id -Force -ErrorAction SilentlyContinue
        }
    }
}

Write-Host "`n[OK] CLEANUP COMPLETE. System is ready for a fresh start." -ForegroundColor Green
Write-Host "=========================================="
Start-Sleep -Seconds 2
