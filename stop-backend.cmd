@echo off
setlocal

if /i "%~1"=="--no-pause" set "NO_PAUSE=1"

for /f %%p in ('powershell -NoProfile -Command "$p = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue ^| Select-Object -ExpandProperty OwningProcess -Unique; if ($p) { $p }"') do set "BACKEND_PID=%%p"

if not defined BACKEND_PID (
  echo [INFO] No backend process is listening on port 8080.
  goto :end
)

echo [INFO] Stopping backend PID=%BACKEND_PID% ...
taskkill /PID %BACKEND_PID% /F >nul 2>&1
if errorlevel 1 (
  echo [ERROR] Failed to stop PID=%BACKEND_PID%.
  goto :error
)

echo [INFO] Backend stopped.
goto :end

:error
if not defined NO_PAUSE pause
exit /b 1

:end
if not defined NO_PAUSE pause
exit /b 0
