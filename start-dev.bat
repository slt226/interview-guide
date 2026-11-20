@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

echo ============================================================
echo  interview-guide launcher  (Docker 依赖版)
echo ============================================================
echo.

REM ---------- 0. 校验 Docker 是否已安装并运行 ----------
where docker >nul 2>&1
if errorlevel 1 (
  echo [X] 未检测到 Docker CLI。
  echo     请先安装 Docker Desktop: https://www.docker.com/products/docker-desktop/
  echo     安装后确认系统托盘出现鲸鱼图标（守护进程已启动）再运行本脚本。
  pause
  exit /b 1
)
docker info >nul 2>&1
if errorlevel 1 (
  echo [X] Docker 已安装，但守护进程未运行。
  echo     请启动 Docker Desktop，等图标变绿后再重试。
  pause
  exit /b 1
)
echo [OK] Docker 守护进程运行中

REM ---------- 1. 预检 5432 是否被 CentOS 虚拟机占用 ----------
netstat -ano | findstr ":5432" | findstr LISTENING >nul
if not errorlevel 1 (
  echo.
  echo [!] 检测到 5432 端口已被占用。
  echo     若这是 VMware 里的 CentOS 虚拟机 PostgreSQL，请先关机该虚拟机，
  echo     否则 Docker 的 postgres 容器会绑定端口失败。
  echo     已占用情况：
  netstat -ano | findstr ":5432" | findstr LISTENING
  echo.
  echo     确认虚拟机已关机后，按任意键继续；或关闭本窗口取消。
  pause
)

REM ---------- 2. 停掉原生 Redis，避免与 docker redis 抢 6379 ----------
echo [*] 检查原生 Redis (redis-server.exe) ...
tasklist | findstr /i "redis-server.exe" >nul 2>&1
if not errorlevel 1 (
  taskkill /F /IM redis-server.exe >nul 2>&1
  echo     -> 已停止原生 Redis（6379 已释放，交由 Docker 提供）
) else (
  echo     -> 未发现原生 Redis 进程
)

REM ---------- 3. 启动依赖服务 ----------
echo [*] 启动依赖服务 (PostgreSQL / Redis / RustFS) ...
docker compose -f docker-compose.dev.yml up -d
if errorlevel 1 (
  echo [X] docker compose 启动失败，请检查端口冲突或镜像拉取是否成功。
  pause
  exit /b 1
)

REM ---------- 4. 等待依赖端口就绪 ----------
call :wait_port 5432 PostgreSQL
call :wait_port 6379 Redis
call :wait_port 9000 RustFS

echo.
echo [OK] 依赖服务全部就绪。
echo      提示：RustFS 的 interview-guide bucket 由应用启动时自动创建，无需手动操作。
echo.

REM ---------- 5. 启动后端 ----------
echo [*] 启动后端 (gradlew :app:bootRun) ...
netstat -ano | findstr ":8080" | findstr LISTENING >nul
if errorlevel 1 (
  start "interview-backend" cmd /k "cd /d %~dp0 && .\gradlew.bat :app:bootRun"
  echo     -> 后端窗口已打开
) else (
  echo     -> 8080 已被占用，后端似乎已在运行，跳过
)

REM ---------- 6. 启动前端 ----------
echo [*] 启动前端 (pnpm dev) ...
netstat -ano | findstr ":5173" | findstr LISTENING >nul
if errorlevel 1 (
  start "interview-frontend" cmd /k "cd /d %~dp0frontend && pnpm dev"
  echo     -> 前端窗口已打开
) else (
  echo     -> 5173 已被占用，前端似乎已在运行，跳过
)

echo.
echo ============================================================
echo  Backend  http://localhost:8080
echo  Swagger  http://localhost:8080/swagger-ui.html
echo  Frontend http://localhost:5173
echo ============================================================
echo  等后端窗口出现 "Started App in X seconds" 后再使用。
echo.
echo  停止依赖服务：docker compose -f docker-compose.dev.yml down
echo  清空全部数据：docker compose -f docker-compose.dev.yml down -v
echo ============================================================
timeout /t 6 >nul
endlocal
exit /b 0

:wait_port
set "PORT=%~1"
set "NAME=%~2"
set "TRY=0"
:wait_loop
powershell -NoProfile -Command "Test-NetConnection -ComputerName localhost -Port %PORT% -InformationLevel Quiet" 2>nul | findstr /i "True" >nul
if not errorlevel 1 (
  echo     -> %NAME% (端口 %PORT%) 已就绪
  goto :eof
)
set /a TRY+=1
if %TRY% geq 40 (
  echo     [警告] %NAME% (端口 %PORT%) 等待超时，请检查 'docker compose -f docker-compose.dev.yml ps' 与容器日志。
  goto :eof
)
echo     .. 等待 %NAME% (端口 %PORT%) %TRY%/40
timeout /t 2 >nul
goto :wait_loop
