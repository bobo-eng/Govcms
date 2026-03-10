@echo off
setlocal

if /i "%~1"=="--no-pause" set "NO_PAUSE=1"

cd /d "%~dp0"

echo [INFO] Restarting backend on port 8080...
call "%~dp0stop-backend.cmd" --no-pause
if errorlevel 1 (
  echo [WARN] Stop step returned non-zero. Continuing to start...
)

echo.
if defined NO_PAUSE (
  call "%~dp0start-backend.cmd" --no-pause
) else (
  call "%~dp0start-backend.cmd"
)

exit /b %ERRORLEVEL%
