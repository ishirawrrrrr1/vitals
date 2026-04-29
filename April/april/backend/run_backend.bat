@echo off
setlocal enabledelayedexpansion
title VITALS HUB - LAUNCHER

:: 1. ADMINISTRATOR ELEVATION
>nul 2>&1 "%SYSTEMROOT%\system32\cacls.exe" "%SYSTEMROOT%\system32\config\system"
if %errorlevel% neq 0 (
    echo [PROMPT] Requesting Administrator privileges for Network/Firewall config...
    powershell -Command "Start-Process '%~dpnx0' -Verb RunAs"
    exit /b
)

:: 2. RUN INTEGRITY SCRIPT
set "SCRIPT_PATH=%~dp0run_backend.ps1"

if not exist "%SCRIPT_PATH%" (
    echo [FATAL ERROR] Cannot find: %SCRIPT_PATH%
    echo Make sure you extracted the files!
    pause
    exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_PATH%"

if %errorlevel% neq 0 (
    echo.
    echo [LAUNCHER ERROR] The integrity check failed.
    pause
    exit /b %errorlevel%
)
