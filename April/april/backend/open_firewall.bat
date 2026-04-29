@echo off
echo =======================================================
echo    Node.js Backend - Windows Firewall Setup
echo =======================================================
echo.

:: Check for Administrator privileges
net session >nul 2>&1
if %errorLevel% == 0 (
    echo [OK] Administrator permissions confirmed.
) else (
    echo [ERROR] Administrative permissions are required!
    echo Please Right-Click this file and select "Run as Administrator".
    echo.
    pause
    exit /b
)

echo Adding Firewall Rule for Port 3000 (TCP)...
netsh advfirewall firewall add rule name="NodeJS Backend Port 3000" dir=in action=allow protocol=TCP localport=3000

echo.
echo =======================================================
echo [SUCCESS] Firewall rule added! Your Android app can
echo now connect to this PC on port 3000.
echo =======================================================
pause
