@echo off
:: Check for Administrator privileges
>nul 2>&1 "%SYSTEMROOT%\system32\cacls.exe" "%SYSTEMROOT%\system32\config\system"

if %errorlevel% neq 0 (
    echo [RESTRICTED] Requesting Administrator privileges to clear ports...
    powershell -Command "Start-Process '%~dpnx0' -Verb RunAs"
    exit /b
)

set "SCRIPT_PATH=%~dp0stop_backend.ps1"

:: Check if the PowerShell script exists
if not exist "%SCRIPT_PATH%" (
    echo [ERROR] Cannot find: %SCRIPT_PATH%
    echo.
    echo [PROBABLE CAUSE] Running from inside a ZIP or Temp folder.
    echo Please EXTRACT the project first.
    echo.
    pause
    exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_PATH%"

echo.
echo Operation Finished.
timeout /t 3
