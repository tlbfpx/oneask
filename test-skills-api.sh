#!/bin/bash

# 测试 Skills API

echo "========================================="
echo "测试 Customer Service Agent Skills 功能"
echo "========================================="

BASE_URL="http://localhost:8081"

echo ""
echo "1. 测试健康检查..."
curl -s $BASE_URL/actuator/health
echo ""

echo ""
echo "2. 测试基本对话（查询技能列表）..."
curl -s -X POST $BASE_URL/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "你好，请介绍一下你有哪些技能"}' | head -200
echo ""

echo ""
echo "3. 测试地理编码技能（地址转经纬度）..."
curl -s -X POST $BASE_URL/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "请查询北京天安门的经纬度坐标"}' | head -500
echo ""

echo ""
echo "4. 测试逆地理编码技能（经纬度转地址）..."
curl -s -X POST $BASE_URL/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "请问坐标39.908823, 116.397470是什么地方"}' | head -500
echo ""

echo ""
echo "========================================="
echo "测试完成"
echo "========================================="
