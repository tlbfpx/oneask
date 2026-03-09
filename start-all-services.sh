#!/bin/bash

# 启动所有服务脚本

cd /Users/muxi/Documents/workspace/git_project/oneask

# 加载环境变量
source .env
export AI_DASHSCOPE_API_KEY BAIDU_MAP_AK NACOS_ADDR NACOS_USERNAME NACOS_PASSWORD

echo "========================================="
echo "启动 OneAsk 服务集群"
echo "========================================="

# 检查端口占用
echo ""
echo "检查端口占用情况..."
for port in 8081 8082; do
    pid=$(lsof -ti:$port 2>/dev/null)
    if [ -n "$pid" ]; then
        echo "  端口 $port 被进程 $pid 占用，正在终止..."
        kill -9 $pid 2>/dev/null
        sleep 1
    fi
done
echo "  端口检查完成"

# 启动 Customer Service Agent
echo ""
echo "启动 Customer Service Agent (端口: 8081)..."
nohup java -jar customer-service-agent/target/customer-service-agent-1.0.0-SNAPSHOT.jar > logs/customer-service-agent.log 2>&1 &
CSPID=$!
echo "  进程ID: $CSPID"

# 启动 LLM Routing Agent
echo ""
echo "启动 LLM Routing Agent (端口: 8082)..."
nohup java -jar llm-routing-agent/target/llm-routing-agent-1.0.0-SNAPSHOT.jar > logs/llm-routing-agent.log 2>&1 &
LRPID=$!
echo "  进程ID: $LRPID"

# 等待服务启动
echo ""
echo "等待服务启动..."
sleep 10

# 检查服务状态
echo ""
echo "检查服务状态..."
if curl -s http://localhost:8081/actuator/health 2>/dev/null | grep -q "UP"; then
    echo "  ✅ Customer Service Agent 已启动"
else
    echo "  ⚠️ Customer Service Agent 可能未完全启动"
fi

if curl -s http://localhost:8082/actuator/health 2>/dev/null | grep -q "UP"; then
    echo "  ✅ LLM Routing Agent 已启动"
else
    echo "  ⚠️ LLM Routing Agent 可能未完全启动"
fi

echo ""
echo "========================================="
echo "服务启动完成"
echo "========================================="
echo ""
echo "日志文件:"
echo "  - Customer Service Agent: logs/customer-service-agent.log"
echo "  - LLM Routing Agent: logs/llm-routing-agent.log"
echo ""
echo "API 端点:"
echo "  - Customer Service Agent: http://localhost:8081/api/agent/chat"
echo "  - LLM Routing Agent: http://localhost:8082/api/chat"
echo ""
echo "停止服务:"
echo "  kill $CSPID $LRPID"
echo ""

# 保存进程ID
echo $CSPID > logs/customer-service-agent.pid
echo $LRPID > logs/llm-routing-agent.pid
