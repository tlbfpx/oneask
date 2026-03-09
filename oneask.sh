#!/bin/bash

# OneAsk 服务管理脚本
# 支持：启动、停止、重启、状态查询

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 服务配置
CUSTOMER_SERVICE_NAME="Customer Service Agent"
CUSTOMER_SERVICE_PORT=8081
CUSTOMER_SERVICE_JAR="customer-service-agent/target/customer-service-agent-1.0.0-SNAPSHOT.jar"
CUSTOMER_SERVICE_LOG="logs/customer-service-agent.log"
CUSTOMER_SERVICE_PID_FILE="logs/customer-service-agent.pid"

ROUTING_SERVICE_NAME="LLM Routing Agent"
ROUTING_SERVICE_PORT=8082
ROUTING_SERVICE_JAR="llm-routing-agent/target/llm-routing-agent-1.0.0-SNAPSHOT.jar"
ROUTING_SERVICE_LOG="logs/llm-routing-agent.log"
ROUTING_SERVICE_PID_FILE="logs/llm-routing-agent.pid"

FRONTEND_SERVICE_NAME="Frontend"
FRONTEND_SERVICE_PORT=3000
FRONTEND_DIR="frontend"
FRONTEND_LOG="logs/frontend.log"
FRONTEND_PID_FILE="logs/frontend.pid"

# 脚本目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 加载环境变量
load_env() {
    if [ -f ".env" ]; then
        source .env
        export AI_DASHSCOPE_API_KEY BAIDU_MAP_AK NACOS_ADDR NACOS_USERNAME NACOS_PASSWORD
        echo -e "${GREEN}✓ 环境变量已加载${NC}"
    else
        echo -e "${YELLOW}⚠ 未找到 .env 文件，使用默认配置${NC}"
    fi
}

# 获取进程ID
get_pid() {
    local pid_file=$1
    if [ -f "$pid_file" ]; then
        cat "$pid_file" 2>/dev/null
    else
        echo ""
    fi
}

# 检查进程是否运行
is_running() {
    local pid=$1
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
        return 0
    else
        return 1
    fi
}

# 检查端口是否被占用
check_port() {
    local port=$1
    lsof -ti:$port 2>/dev/null
}

# 打印状态
print_status() {
    local name=$1
    local port=$2
    local pid=$3
    
    if is_running "$pid"; then
        echo -e "${GREEN}● $name${NC}"
        echo -e "  端口: $port"
        echo -e "  进程ID: $pid"
        echo -e "  状态: ${GREEN}运行中${NC}"
    else
        echo -e "${RED}● $name${NC}"
        echo -e "  端口: $port"
        echo -e "  状态: ${RED}已停止${NC}"
    fi
    echo ""
}

# 状态查询
cmd_status() {
    echo -e "${BLUE}=========================================${NC}"
    echo -e "${BLUE}        OneAsk 服务状态查询${NC}"
    echo -e "${BLUE}=========================================${NC}"
    echo ""
    
    local cs_pid=$(get_pid "$CUSTOMER_SERVICE_PID_FILE")
    local lr_pid=$(get_pid "$ROUTING_SERVICE_PID_FILE")
    local fe_pid=$(get_pid "$FRONTEND_PID_FILE")
    
    print_status "$CUSTOMER_SERVICE_NAME" "$CUSTOMER_SERVICE_PORT" "$cs_pid"
    print_status "$ROUTING_SERVICE_NAME" "$ROUTING_SERVICE_PORT" "$lr_pid"
    print_status "$FRONTEND_SERVICE_NAME" "$FRONTEND_SERVICE_PORT" "$fe_pid"
    
    # 检查端口占用
    echo -e "${BLUE}端口占用情况:${NC}"
    for port in 8081 8082 3000; do
        local pid=$(check_port "$port")
        if [ -n "$pid" ]; then
            echo -e "  端口 $port: ${YELLOW}被进程 $pid 占用${NC}"
        else
            echo -e "  端口 $port: ${GREEN}空闲${NC}"
        fi
    done
}

# 启动服务
cmd_start() {
    local service=$1
    
    load_env
    mkdir -p logs
    
    echo -e "${BLUE}=========================================${NC}"
    echo -e "${BLUE}        启动 OneAsk 服务${NC}"
    echo -e "${BLUE}=========================================${NC}"
    echo ""
    
    if [ "$service" == "all" ] || [ "$service" == "customer" ]; then
        start_customer_service
    fi
    
    if [ "$service" == "all" ] || [ "$service" == "routing" ]; then
        start_routing_service
    fi
    
    if [ "$service" == "all" ] || [ "$service" == "frontend" ]; then
        start_frontend_service
    fi
    
    echo ""
    echo -e "${BLUE}=========================================${NC}"
    echo -e "${GREEN}        服务启动完成${NC}"
    echo -e "${BLUE}=========================================${NC}"
    echo ""
    
    sleep 2
    cmd_status
}

# 启动 Customer Service Agent
start_customer_service() {
    echo -e "${YELLOW}启动 $CUSTOMER_SERVICE_NAME...${NC}"
    
    local pid=$(get_pid "$CUSTOMER_SERVICE_PID_FILE")
    if is_running "$pid"; then
        echo -e "  ${GREEN}✓ 服务已在运行 (PID: $pid)${NC}"
        return 0
    fi
    
    # 检查端口占用
    local port_pid=$(check_port "$CUSTOMER_SERVICE_PORT")
    if [ -n "$port_pid" ]; then
        echo -e "  ${YELLOW}⚠ 端口 $CUSTOMER_SERVICE_PORT 被进程 $port_pid 占用，正在终止...${NC}"
        kill -9 "$port_pid" 2>/dev/null || true
        sleep 1
    fi
    
    # 检查 JAR 文件
    if [ ! -f "$CUSTOMER_SERVICE_JAR" ]; then
        echo -e "  ${RED}✗ JAR 文件不存在: $CUSTOMER_SERVICE_JAR${NC}"
        echo -e "  ${YELLOW}请先执行: mvn clean package -DskipTests${NC}"
        return 1
    fi
    
    # 启动服务
    nohup java -jar "$CUSTOMER_SERVICE_JAR" > "$CUSTOMER_SERVICE_LOG" 2>&1 &
    local new_pid=$!
    echo "$new_pid" > "$CUSTOMER_SERVICE_PID_FILE"
    
    echo -e "  ${GREEN}✓ 服务已启动 (PID: $new_pid)${NC}"
    echo -e "  ${BLUE}  日志: $CUSTOMER_SERVICE_LOG${NC}"
}

# 启动 LLM Routing Agent
start_routing_service() {
    echo -e "${YELLOW}启动 $ROUTING_SERVICE_NAME...${NC}"
    
    local pid=$(get_pid "$ROUTING_SERVICE_PID_FILE")
    if is_running "$pid"; then
        echo -e "  ${GREEN}✓ 服务已在运行 (PID: $pid)${NC}"
        return 0
    fi
    
    # 检查端口占用
    local port_pid=$(check_port "$ROUTING_SERVICE_PORT")
    if [ -n "$port_pid" ]; then
        echo -e "  ${YELLOW}⚠ 端口 $ROUTING_SERVICE_PORT 被进程 $port_pid 占用，正在终止...${NC}"
        kill -9 "$port_pid" 2>/dev/null || true
        sleep 1
    fi
    
    # 检查 JAR 文件
    if [ ! -f "$ROUTING_SERVICE_JAR" ]; then
        echo -e "  ${RED}✗ JAR 文件不存在: $ROUTING_SERVICE_JAR${NC}"
        echo -e "  ${YELLOW}请先执行: mvn clean package -DskipTests${NC}"
        return 1
    fi
    
    # 启动服务
    nohup java -jar "$ROUTING_SERVICE_JAR" > "$ROUTING_SERVICE_LOG" 2>&1 &
    local new_pid=$!
    echo "$new_pid" > "$ROUTING_SERVICE_PID_FILE"
    
    echo -e "  ${GREEN}✓ 服务已启动 (PID: $new_pid)${NC}"
    echo -e "  ${BLUE}  日志: $ROUTING_SERVICE_LOG${NC}"
}

# 启动 Frontend
start_frontend_service() {
    echo -e "${YELLOW}启动 $FRONTEND_SERVICE_NAME...${NC}"
    
    local pid=$(get_pid "$FRONTEND_PID_FILE")
    if is_running "$pid"; then
        echo -e "  ${GREEN}✓ 服务已在运行 (PID: $pid)${NC}"
        return 0
    fi
    
    # 检查端口占用
    local port_pid=$(check_port "$FRONTEND_SERVICE_PORT")
    if [ -n "$port_pid" ]; then
        echo -e "  ${YELLOW}⚠ 端口 $FRONTEND_SERVICE_PORT 被进程 $port_pid 占用，正在终止...${NC}"
        kill -9 "$port_pid" 2>/dev/null || true
        sleep 1
    fi
    
    # 检查前端目录
    if [ ! -d "$FRONTEND_DIR" ]; then
        echo -e "  ${RED}✗ 前端目录不存在: $FRONTEND_DIR${NC}"
        return 1
    fi
    
    # 检查 node_modules
    if [ ! -d "$FRONTEND_DIR/node_modules" ]; then
        echo -e "  ${YELLOW}⚠ 前端依赖未安装，正在安装...${NC}"
        (cd "$FRONTEND_DIR" && npm install)
    fi
    
    # 启动服务
    (cd "$FRONTEND_DIR" && nohup npm run dev > "../$FRONTEND_LOG" 2>&1 &)
    sleep 2
    local new_pid=$(check_port "$FRONTEND_SERVICE_PORT")
    if [ -n "$new_pid" ]; then
        echo "$new_pid" > "$FRONTEND_PID_FILE"
        echo -e "  ${GREEN}✓ 服务已启动 (PID: $new_pid)${NC}"
        echo -e "  ${BLUE}  日志: $FRONTEND_LOG${NC}"
        echo -e "  ${BLUE}  访问: http://localhost:$FRONTEND_SERVICE_PORT${NC}"
    else
        echo -e "  ${RED}✗ 服务启动失败${NC}"
        return 1
    fi
}

# 停止服务
cmd_stop() {
    local service=$1
    
    echo -e "${BLUE}=========================================${NC}"
    echo -e "${BLUE}        停止 OneAsk 服务${NC}"
    echo -e "${BLUE}=========================================${NC}"
    echo ""
    
    if [ "$service" == "all" ] || [ "$service" == "customer" ]; then
        stop_customer_service
    fi
    
    if [ "$service" == "all" ] || [ "$service" == "routing" ]; then
        stop_routing_service
    fi
    
    if [ "$service" == "all" ] || [ "$service" == "frontend" ]; then
        stop_frontend_service
    fi
    
    echo ""
    echo -e "${BLUE}=========================================${NC}"
    echo -e "${GREEN}        服务停止完成${NC}"
    echo -e "${BLUE}=========================================${NC}"
}

# 停止 Customer Service Agent
stop_customer_service() {
    echo -e "${YELLOW}停止 $CUSTOMER_SERVICE_NAME...${NC}"
    
    local pid=$(get_pid "$CUSTOMER_SERVICE_PID_FILE")
    if is_running "$pid"; then
        kill "$pid" 2>/dev/null || true
        sleep 2
        
        # 强制终止
        if is_running "$pid"; then
            kill -9 "$pid" 2>/dev/null || true
        fi
        
        rm -f "$CUSTOMER_SERVICE_PID_FILE"
        echo -e "  ${GREEN}✓ 服务已停止${NC}"
    else
        echo -e "  ${YELLOW}⚠ 服务未运行${NC}"
    fi
    
    # 清理端口占用
    local port_pid=$(check_port "$CUSTOMER_SERVICE_PORT")
    if [ -n "$port_pid" ]; then
        kill -9 "$port_pid" 2>/dev/null || true
        echo -e "  ${GREEN}✓ 清理端口 $CUSTOMER_SERVICE_PORT 占用${NC}"
    fi
}

# 停止 LLM Routing Agent
stop_routing_service() {
    echo -e "${YELLOW}停止 $ROUTING_SERVICE_NAME...${NC}"
    
    local pid=$(get_pid "$ROUTING_SERVICE_PID_FILE")
    if is_running "$pid"; then
        kill "$pid" 2>/dev/null || true
        sleep 2
        
        # 强制终止
        if is_running "$pid"; then
            kill -9 "$pid" 2>/dev/null || true
        fi
        
        rm -f "$ROUTING_SERVICE_PID_FILE"
        echo -e "  ${GREEN}✓ 服务已停止${NC}"
    else
        echo -e "  ${YELLOW}⚠ 服务未运行${NC}"
    fi
    
    # 清理端口占用
    local port_pid=$(check_port "$ROUTING_SERVICE_PORT")
    if [ -n "$port_pid" ]; then
        kill -9 "$port_pid" 2>/dev/null || true
        echo -e "  ${GREEN}✓ 清理端口 $ROUTING_SERVICE_PORT 占用${NC}"
    fi
}

# 停止 Frontend
stop_frontend_service() {
    echo -e "${YELLOW}停止 $FRONTEND_SERVICE_NAME...${NC}"
    
    local pid=$(get_pid "$FRONTEND_PID_FILE")
    if is_running "$pid"; then
        kill "$pid" 2>/dev/null || true
        sleep 2
        
        # 强制终止
        if is_running "$pid"; then
            kill -9 "$pid" 2>/dev/null || true
        fi
        
        rm -f "$FRONTEND_PID_FILE"
        echo -e "  ${GREEN}✓ 服务已停止${NC}"
    else
        echo -e "  ${YELLOW}⚠ 服务未运行${NC}"
    fi
    
    # 清理端口占用
    local port_pid=$(check_port "$FRONTEND_SERVICE_PORT")
    if [ -n "$port_pid" ]; then
        kill -9 "$port_pid" 2>/dev/null || true
        echo -e "  ${GREEN}✓ 清理端口 $FRONTEND_SERVICE_PORT 占用${NC}"
    fi
}

# 重启服务
cmd_restart() {
    local service=$1
    
    echo -e "${BLUE}=========================================${NC}"
    echo -e "${BLUE}        重启 OneAsk 服务${NC}"
    echo -e "${BLUE}=========================================${NC}"
    echo ""
    
    cmd_stop "$service"
    sleep 2
    cmd_start "$service"
}

# 查看日志
cmd_logs() {
    local service=$1
    local lines=${2:-100}
    
    case "$service" in
        customer)
            if [ -f "$CUSTOMER_SERVICE_LOG" ]; then
                tail -n "$lines" "$CUSTOMER_SERVICE_LOG"
            else
                echo -e "${RED}日志文件不存在: $CUSTOMER_SERVICE_LOG${NC}"
            fi
            ;;
        routing)
            if [ -f "$ROUTING_SERVICE_LOG" ]; then
                tail -n "$lines" "$ROUTING_SERVICE_LOG"
            else
                echo -e "${RED}日志文件不存在: $ROUTING_SERVICE_LOG${NC}"
            fi
            ;;
        frontend)
            if [ -f "$FRONTEND_LOG" ]; then
                tail -n "$lines" "$FRONTEND_LOG"
            else
                echo -e "${RED}日志文件不存在: $FRONTEND_LOG${NC}"
            fi
            ;;
        *)
            echo -e "${RED}请指定服务: customer, routing 或 frontend${NC}"
            ;;
    esac
}

# 测试服务
cmd_test() {
    echo -e "${BLUE}=========================================${NC}"
    echo -e "${BLUE}        测试 OneAsk 服务${NC}"
    echo -e "${BLUE}=========================================${NC}"
    echo ""
    
    echo -e "${YELLOW}测试 Customer Service Agent (8081)...${NC}"
    local cs_response=$(curl -s -X POST http://localhost:8081/api/agent/chat \
        -H "Content-Type: application/json" \
        -d '{"message": "你好，请介绍你的技能"}' 2>/dev/null)
    
    if [ -n "$cs_response" ]; then
        echo -e "${GREEN}✓ 服务响应正常${NC}"
        echo "响应: $cs_response"
    else
        echo -e "${RED}✗ 服务无响应${NC}"
    fi
    
    echo ""
    echo -e "${YELLOW}测试 LLM Routing Agent (8082)...${NC}"
    local lr_response=$(curl -s -X POST http://localhost:8082/api/chat \
        -H "Content-Type: application/json" \
        -d '{"message": "测试消息", "sessionId": "test"}' 2>/dev/null)
    
    if [ -n "$lr_response" ]; then
        echo -e "${GREEN}✓ 服务响应正常${NC}"
        echo "响应: $lr_response"
    else
        echo -e "${RED}✗ 服务无响应${NC}"
    fi
    
    echo ""
    echo -e "${YELLOW}测试 Frontend (3000)...${NC}"
    local fe_response=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:3000 2>/dev/null)
    
    if [ "$fe_response" == "200" ]; then
        echo -e "${GREEN}✓ 前端服务正常 (HTTP 200)${NC}"
    else
        echo -e "${RED}✗ 前端服务无响应${NC}"
    fi
}

# 显示帮助
cmd_help() {
    echo -e "${BLUE}OneAsk 服务管理脚本${NC}"
    echo ""
    echo "用法: ./oneask.sh [命令] [参数]"
    echo ""
    echo "命令:"
    echo "  start [all|customer|routing|frontend]  启动服务 (默认: all)"
    echo "  stop [all|customer|routing|frontend]   停止服务 (默认: all)"
    echo "  restart [all|customer|routing|frontend] 重启服务 (默认: all)"
    echo "  status                                 查看服务状态"
    echo "  logs <service> [lines]                 查看日志 (service: customer|routing|frontend)"
    echo "  test                                   测试服务"
    echo "  help                                   显示帮助"
    echo ""
    echo "示例:"
    echo "  ./oneask.sh start                      启动所有服务"
    echo "  ./oneask.sh start frontend             只启动前端服务"
    echo "  ./oneask.sh stop                       停止所有服务"
    echo "  ./oneask.sh status                     查看服务状态"
    echo "  ./oneask.sh logs customer 50           查看 Customer Service Agent 最近 50 行日志"
    echo "  ./oneask.sh test                       测试服务"
}

# 主函数
main() {
    local command=${1:-help}
    local service=${2:-all}
    local param3=$3
    
    case "$command" in
        start)
            cmd_start "$service"
            ;;
        stop)
            cmd_stop "$service"
            ;;
        restart)
            cmd_restart "$service"
            ;;
        status)
            cmd_status
            ;;
        logs)
            cmd_logs "$service" "$param3"
            ;;
        test)
            cmd_test
            ;;
        help|--help|-h)
            cmd_help
            ;;
        *)
            echo -e "${RED}未知命令: $command${NC}"
            echo ""
            cmd_help
            exit 1
            ;;
    esac
}

main "$@"
