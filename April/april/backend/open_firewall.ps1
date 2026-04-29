<#
.SYNOPSIS
Opens Windows Firewall for Port 3000 to allow Android App connections
.DESCRIPTION
This script automatically runs as Administrator and configures the Windows Defender Firewall 
to allow incoming TCP traffic on port 3000, which is required for the Node.js backend.
#>

if (-Not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Warning "Restarting script with Administrator privileges..."
    Start-Process powershell -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`"" -Verb RunAs
    exit
}

Write-Host "=======================================================" -ForegroundColor Cyan
Write-Host "    Node.js Backend - Windows Firewall Setup" -ForegroundColor Cyan
Write-Host "=======================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "[*] Administrator permissions confirmed." -ForegroundColor Green
Write-Host "[*] Adding Firewall Rule for Port 3000 (TCP)..." -ForegroundColor Yellow

# Add Firewall Rule
New-NetFirewallRule -DisplayName "NodeJS Backend Port 3000" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 3000 -ErrorAction SilentlyContinue | Out-Null

Write-Host ""
Write-Host "=======================================================" -ForegroundColor Green
Write-Host "[SUCCESS] Firewall rule added! Your Android app can" -ForegroundColor Green
Write-Host "          now connect to this PC on port 3000." -ForegroundColor Green
Write-Host "=======================================================" -ForegroundColor Green
Write-Host ""

Write-Host "Press any key to exit..."
$Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown") | Out-Null
