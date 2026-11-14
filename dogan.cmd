@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0dogan.ps1" %*
exit /b %ERRORLEVEL%
