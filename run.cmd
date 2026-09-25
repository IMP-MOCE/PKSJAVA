@echo off
chcp 65001 >nul
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0project.ps1" %*
set "result=%errorlevel%"
if "%~1"=="" pause
exit /b %result%
