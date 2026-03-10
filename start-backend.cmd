@echo off
setlocal

if /i "%~1"=="--no-pause" set "NO_PAUSE=1"

cd /d "%~dp0"

set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"

set "MAVEN_CMD=%ROOT%\.tools\apache-maven-3.9.12\bin\mvn.cmd"
set "MAVEN_REPO=%ROOT%\.m2\repository"
set "LOG_DIR=%ROOT%\logs"

if not exist "%MAVEN_CMD%" (
  echo [ERROR] Maven not found: "%MAVEN_CMD%"
  goto :error
)

for /f %%p in ('powershell -NoProfile -Command "$p = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue ^| Select-Object -ExpandProperty OwningProcess -Unique; if ($p) { $p }"') do set "BACKEND_PID=%%p"

if defined BACKEND_PID (
  echo [INFO] Backend is already running on port 8080. PID=%BACKEND_PID%
  echo [INFO] URL: http://127.0.0.1:8080
  goto :end
)

if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"
for /f %%t in ('powershell -NoProfile -Command "(Get-Date).ToString('yyyyMMdd-HHmmss')"') do set "STAMP=%%t"

set "OUT_LOG=%LOG_DIR%\backend-%STAMP%.out.log"
set "ERR_LOG=%LOG_DIR%\backend-%STAMP%.err.log"

echo [INFO] Starting backend...
echo [INFO] Output log: "%OUT_LOG%"
echo [INFO] Error log:  "%ERR_LOG%"
echo [INFO] Press Ctrl+C in this window to stop it.
echo.

call "%MAVEN_CMD%" -Dmaven.repo.local="%MAVEN_REPO%" spring-boot:run 1>>"%OUT_LOG%" 2>>"%ERR_LOG%"
set "EXIT_CODE=%ERRORLEVEL%"

if "%EXIT_CODE%"=="0" (
  echo.
  echo [INFO] Backend stopped normally.
  goto :end
)

echo.
echo [ERROR] Backend exited with code %EXIT_CODE%.
echo [ERROR] Check logs:
echo [ERROR]   %OUT_LOG%
echo [ERROR]   %ERR_LOG%
goto :error

:error
if not defined NO_PAUSE pause
exit /b 1

:end
if not defined NO_PAUSE pause
exit /b 0
