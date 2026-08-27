@echo off
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0BUILD_JAR_WINDOWS.ps1"
echo.
pause
