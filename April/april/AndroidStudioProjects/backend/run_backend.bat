@echo off
setlocal
:: Change to the backend directory
cd /d "%~dp0"

:: Run the PowerShell integrity and startup script
powershell -ExecutionPolicy Bypass -File "backend_check.ps1"

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Backend check failed. 
    pause
)
endlocal
