@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion

REM OneAsk 服务管理脚本 (Windows 版本)
REM 支持：启动、停止、重启、状态查询

REM 颜色定义
set "RED=[31m"
set "GREEN=[32m"
set "YELLOW=[33m"
set "BLUE=[34m"
set "NC=[0m"

REM 服务配置
set "CUSTOMER_SERVICE_NAME=Customer Service Agent"
set "CUSTOMER_SERVICE_PORT=8081"
set "CUSTOMER_SERVICE_JAR=customer-service-agent\target\customer-service-agent-1.0.0-SNAPSHOT.jar"
set "CUSTOMER_SERVICE_LOG=logs\customer-service-agent.log"
set "CUSTOMER_SERVICE_PID_FILE=logs\customer-service-agent.pid"

set "ROUTING_SERVICE_NAME=LLM Routing Agent"
set "ROUTING_SERVICE_PORT=8082"
set "ROUTING_SERVICE_JAR=llm-routing-agent\target\llm-routing-agent-1.0.0-SNAPSHOT.jar"
set "ROUTING_SERVICE_LOG=logs\llm-routing-agent.log"
set "ROUTING_SERVICE_PID_FILE=logs\llm-routing-agent.pid"

REM 切换到脚本所在目录
cd /d "%~dp0"

REM 主函数
if "%~1"=="" goto :help
if /i "%~1"=="start" goto :start
if /i "%~1"=="stop" goto :stop
if /i "%~1"=="restart" goto :restart
if /i "%~1"=="status" goto :status
if /i "%~1"=="logs" goto :logs
if /i "%~1"=="test" goto :test
if /i "%~1"=="help" goto :help
if /i "%~1"=="--help" goto :help
if /i "%~1"=="-h" goto :help
echo [31m未知命令: %~1[0m
echo.
goto :help

REM ==================== 启动服务 ====================
:start
call :load_env
if not exist "logs" mkdir logs

echo [34m=========================================[0m
echo [34m        启动 OneAsk 服务[0m
echo [34m=========================================[0m
echo.

set "service=%~2"
if "!service!"=="" set "service=all"

if /i "!service!"=="all" (
    call :start_customer_service
    call :start_routing_service
) else if /i "!service!"=="customer" (
    call :start_customer_service
) else if /i "!service!"=="routing" (
    call :start_routing_service
) else (
    echo [31m未知服务: !service![0m
    echo 可用选项: all, customer, routing
    exit /b 1
)

echo.
echo [34m=========================================[0m
echo [32m        服务启动完成[0m
echo [34m=========================================[0m
echo.
timeout /t 2 /nobreak >nul
call :status
goto :eof

REM 启动 Customer Service Agent
:start_customer_service
echo [33m启动 %CUSTOMER_SERVICE_NAME%...[0m

REM 检查是否已在运行
call :get_pid "%CUSTOMER_SERVICE_PID_FILE%"
if defined PID (
    tasklist /FI "PID eq !PID!" 2>nul | findstr "!PID!" >nul
    if !errorlevel! equ 0 (
        echo   [32m✓ 服务已在运行 (PID: !PID!)[0m
        goto :eof
    )
)

REM 检查端口占用
call :check_port %CUSTOMER_SERVICE_PORT%
if defined PORT_PID (
    echo   [33m⚠ 端口 %CUSTOMER_SERVICE_PORT% 被进程 !PORT_PID! 占用，正在终止...[0m
    taskkill /F /PID !PORT_PID! >nul 2>&1
    timeout /t 1 /nobreak >nul
)

REM 检查 JAR 文件
if not exist "%CUSTOMER_SERVICE_JAR%" (
    echo   [31m✗ JAR 文件不存在: %CUSTOMER_SERVICE_JAR%[0m
    echo   [33m请先执行: mvn clean package -DskipTests[0m
    exit /b 1
)

REM 启动服务
start /B javaw -jar "%CUSTOMER_SERVICE_JAR%" > "%CUSTOMER_SERVICE_LOG%" 2>&1
set "NEW_PID=!ERRORLEVEL!"
for /f "tokens=2" %%a in ('tasklist ^| findstr "javaw"') do (
    echo %%a > "%CUSTOMER_SERVICE_PID_FILE%"
    set "NEW_PID=%%a"
)

echo   [32m✓ 服务已启动[0m
echo   [34m  日志: %CUSTOMER_SERVICE_LOG%[0m
goto :eof

REM 启动 LLM Routing Agent
:start_routing_service
echo [33m启动 %ROUTING_SERVICE_NAME%...[0m

REM 检查是否已在运行
call :get_pid "%ROUTING_SERVICE_PID_FILE%"
if defined PID (
    tasklist /FI "PID eq !PID!" 2>nul | findstr "!PID!" >nul
    if !errorlevel! equ 0 (
        echo   [32m✓ 服务已在运行 (PID: !PID!)[0m
        goto :eof
    )
)

REM 检查端口占用
call :check_port %ROUTING_SERVICE_PORT%
if defined PORT_PID (
    echo   [33m⚠ 端口 %ROUTING_SERVICE_PORT% 被进程 !PORT_PID! 占用，正在终止...[0m
    taskkill /F /PID !PORT_PID! >nul 2>&1
    timeout /t 1 /nobreak >nul
)

REM 检查 JAR 文件
if not exist "%ROUTING_SERVICE_JAR%" (
    echo   [31m✗ JAR 文件不存在: %ROUTING_SERVICE_JAR%[0m
    echo   [33m请先执行: mvn clean package -DskipTests[0m
    exit /b 1
)

REM 启动服务
start /B javaw -jar "%ROUTING_SERVICE_JAR%" > "%ROUTING_SERVICE_LOG%" 2>&1
for /f "tokens=2" %%a in ('tasklist ^| findstr "javaw"') do (
    echo %%a > "%ROUTING_SERVICE_PID_FILE%"
    set "NEW_PID=%%a"
)

echo   [32m✓ 服务已启动[0m
echo   [34m  日志: %ROUTING_SERVICE_LOG%[0m
goto :eof

REM ==================== 停止服务 ====================
:stop
echo [34m=========================================[0m
echo [34m        停止 OneAsk 服务[0m
echo [34m=========================================[0m
echo.

set "service=%~2"
if "!service!"=="" set "service=all"

if /i "!service!"=="all" (
    call :stop_customer_service
    call :stop_routing_service
) else if /i "!service!"=="customer" (
    call :stop_customer_service
) else if /i "!service!"=="routing" (
    call :stop_routing_service
) else (
    echo [31m未知服务: !service![0m
    echo 可用选项: all, customer, routing
    exit /b 1
)

echo.
echo [34m=========================================[0m
echo [32m        服务停止完成[0m
echo [34m=========================================[0m
goto :eof

REM 停止 Customer Service Agent
:stop_customer_service
echo [33m停止 %CUSTOMER_SERVICE_NAME%...[0m

call :get_pid "%CUSTOMER_SERVICE_PID_FILE%"
if defined PID (
    tasklist /FI "PID eq !PID!" 2>nul | findstr "!PID!" >nul
    if !errorlevel! equ 0 (
        taskkill /PID !PID! >nul 2>&1
        timeout /t 2 /nobreak >nul
        taskkill /F /PID !PID! >nul 2>&1
    )
    del /F /Q "%CUSTOMER_SERVICE_PID_FILE%" >nul 2>&1
    echo   [32m✓ 服务已停止[0m
) else (
    echo   [33m⚠ 服务未运行[0m
)

REM 清理端口占用
call :check_port %CUSTOMER_SERVICE_PORT%
if defined PORT_PID (
    taskkill /F /PID !PORT_PID! >nul 2>&1
    echo   [32m✓ 清理端口 %CUSTOMER_SERVICE_PORT% 占用[0m
)
goto :eof

REM 停止 LLM Routing Agent
:stop_routing_service
echo [33m停止 %ROUTING_SERVICE_NAME%...[0m

call :get_pid "%ROUTING_SERVICE_PID_FILE%"
if defined PID (
    tasklist /FI "PID eq !PID!" 2>nul | findstr "!PID!" >nul
    if !errorlevel! equ 0 (
        taskkill /PID !PID! >nul 2>&1
        timeout /t 2 /nobreak >nul
        taskkill /F /PID !PID! >nul 2>&1
    )
    del /F /Q "%ROUTING_SERVICE_PID_FILE%" >nul 2>&1
    echo   [32m✓ 服务已停止[0m
) else (
    echo   [33m⚠ 服务未运行[0m
)

REM 清理端口占用
call :check_port %ROUTING_SERVICE_PORT%
if defined PORT_PID (
    taskkill /F /PID !PORT_PID! >nul 2>&1
    echo   [32m✓ 清理端口 %ROUTING_SERVICE_PORT% 占用[0m
)
goto :eof

REM ==================== 重启服务 ====================
:restart
echo [34m=========================================[0m
echo [34m        重启 OneAsk 服务[0m
echo [34m=========================================[0m
echo.

set "service=%~2"
if "!service!"=="" set "service=all"

call :stop !service!
timeout /t 2 /nobreak >nul
call :start !service!
goto :eof

REM ==================== 状态查询 ====================
:status
echo [34m=========================================[0m
echo [34m        OneAsk 服务状态查询[0m
echo [34m=========================================[0m
echo.

call :get_pid "%CUSTOMER_SERVICE_PID_FILE%"
set "CS_PID=!PID!"
call :print_status "%CUSTOMER_SERVICE_NAME%" "%CUSTOMER_SERVICE_PORT%" "!CS_PID!"

call :get_pid "%ROUTING_SERVICE_PID_FILE%"
set "LR_PID=!PID!"
call :print_status "%ROUTING_SERVICE_NAME%" "%ROUTING_SERVICE_PORT%" "!LR_PID!"

echo [34m端口占用情况:[0m
call :check_port %CUSTOMER_SERVICE_PORT%
if defined PORT_PID (
    echo   端口 %CUSTOMER_SERVICE_PORT%: [33m被进程 !PORT_PID! 占用[0m
) else (
    echo   端口 %CUSTOMER_SERVICE_PORT%: [32m空闲[0m
)

call :check_port %ROUTING_SERVICE_PORT%
if defined PORT_PID (
    echo   端口 %ROUTING_SERVICE_PORT%: [33m被进程 !PORT_PID! 占用[0m
) else (
    echo   端口 %ROUTING_SERVICE_PORT%: [32m空闲[0m
)
goto :eof

REM 打印状态
:print_status
set "name=%~1"
set "port=%~2"
set "pid=%~3"

if defined pid (
    tasklist /FI "PID eq %pid%" 2>nul | findstr "%pid%" >nul
    if !errorlevel! equ 0 (
        echo [32m● %name%[0m
        echo   端口: %port%
        echo   进程ID: %pid%
        echo   状态: [32m运行中[0m
    ) else (
        echo [31m● %name%[0m
        echo   端口: %port%
        echo   状态: [31m已停止[0m
    )
) else (
    echo [31m● %name%[0m
    echo   端口: %port%
    echo   状态: [31m已停止[0m
)
echo.
goto :eof

REM ==================== 查看日志 ====================
:logs
set "service=%~2"
set "lines=%~3"
if "!lines!"=="" set "lines=100"

if /i "!service!"=="customer" (
    if exist "%CUSTOMER_SERVICE_LOG%" (
        type "%CUSTOMER_SERVICE_LOG%" 2>nul | findstr /N "^" | findstr "^[!lines!-9]" >nul
        if !errorlevel! equ 0 (
            powershell -Command "Get-Content '%CUSTOMER_SERVICE_LOG%' -Tail !lines!"
        ) else (
            type "%CUSTOMER_SERVICE_LOG%"
        )
    ) else (
        echo [31m日志文件不存在: %CUSTOMER_SERVICE_LOG%[0m
    )
) else if /i "!service!"=="routing" (
    if exist "%ROUTING_SERVICE_LOG%" (
        powershell -Command "Get-Content '%ROUTING_SERVICE_LOG%' -Tail !lines!"
    ) else (
        echo [31m日志文件不存在: %ROUTING_SERVICE_LOG%[0m
    )
) else (
    echo [31m请指定服务: customer 或 routing[0m
)
goto :eof

REM ==================== 测试服务 ====================
:test
echo [34m=========================================[0m
echo [34m        测试 OneAsk 服务[0m
echo [34m=========================================[0m
echo.

echo [33m测试 Customer Service Agent (8081)...[0m
powershell -Command "try { $resp = Invoke-RestMethod -Uri 'http://localhost:8081/api/agent/chat' -Method POST -ContentType 'application/json' -Body '{\"message\": \"你好，请介绍你的技能\"}' -TimeoutSec 10; Write-Host '[32m✓ 服务响应正常[0m'; Write-Host \"响应: $resp\" } catch { Write-Host '[31m✗ 服务无响应[0m' }"

echo.
echo [33m测试 LLM Routing Agent (8082)...[0m
powershell -Command "try { $resp = Invoke-RestMethod -Uri 'http://localhost:8082/api/chat' -Method POST -ContentType 'application/json' -Body '{\"message\": \"测试消息\", \"sessionId\": \"test\"}' -TimeoutSec 10; Write-Host '[32m✓ 服务响应正常[0m'; Write-Host \"响应: $resp\" } catch { Write-Host '[31m✗ 服务无响应[0m' }"
goto :eof

REM ==================== 帮助 ====================
:help
echo [34mOneAsk 服务管理脚本 (Windows 版本)[0m
echo.
echo 用法: oneask.bat [命令] [参数]
echo.
echo 命令:
echo   start [all^|customer^|routing]  启动服务 (默认: all)
echo   stop [all^|customer^|routing]   停止服务 (默认: all)
echo   restart [all^|customer^|routing] 重启服务 (默认: all)
echo   status                        查看服务状态
echo   logs ^<service^> [lines]        查看日志 (service: customer^|routing)
echo   test                          测试服务
echo   help                          显示帮助
echo.
echo 示例:
echo   oneask.bat start              启动所有服务
echo   oneask.bat start customer     只启动 Customer Service Agent
echo   oneask.bat stop               停止所有服务
echo   oneask.bat status             查看服务状态
echo   oneask.bat logs customer 50   查看 Customer Service Agent 最近 50 行日志
echo   oneask.bat test               测试服务
goto :eof

REM ==================== 工具函数 ====================

REM 加载环境变量
:load_env
if exist ".env" (
    for /f "usebackq tokens=1,* delims==" %%a in (".env") do (
        set "%%a=%%b"
    )
    echo [32m✓ 环境变量已加载[0m
) else (
    echo [33m⚠ 未找到 .env 文件，使用默认配置[0m
)
goto :eof

REM 获取进程ID
:get_pid
set "PID="
if exist "%~1" (
    set /p PID=<"%~1"
)
goto :eof

REM 检查端口占用
:check_port
set "PORT_PID="
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":%~1" ^| findstr "LISTENING"') do (
    set "PORT_PID=%%a"
)
goto :eof
