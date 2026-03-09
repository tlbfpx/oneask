---
name: geocode
description: 地理编码技能，用于地址与经纬度坐标之间的转换。当用户需要查询地址的经纬度坐标，或者根据经纬度查询详细地址时使用此技能。
---

# Geocode Skill

地理编码技能，提供地址与经纬度坐标之间的转换功能。

## 功能描述

本技能封装了百度地图 Geocoding API，提供以下核心功能：

1. **地理编码 (geocode)**: 将详细地址转换为经纬度坐标
2. **逆地理编码 (reverse_geocode)**: 将经纬度坐标转换为详细地址

## 可用工具

执行技能目录下的 Python 脚本：

```
scripts/baidu_geocoding.py
```

### 地理编码

```bash
python3 scripts/baidu_geocoding.py geocode "<address>" "<api_key>"
```

示例：
```bash
python3 scripts/baidu_geocoding.py geocode "北京市天安门" your_api_key
```

### 逆地理编码

```bash
python3 scripts/baidu_geocoding.py reverse_geocode <lat> <lng> "<api_key>"
```

示例：
```bash
python3 scripts/baidu_geocoding.py reverse_geocode 39.908823 116.397470 your_api_key
```

## 配置要求

### 环境变量

| 变量名 | 描述 | 必需 |
|-------|------|------|
| `BAIDU_MAP_AK` | 百度地图 API Key | 是 |

### 百度地图 API Key 配置

1. 访问 [百度地图开放平台](https://lbsyun.baidu.com/)
2. 创建应用，获取 AK
3. 配置 IP 白名单（服务端调用需要配置服务器 IP）

## 参考文档

- API 参考文档：`references/baidu_geocoding_api.md`
- 地理编码示例：`examples/geocode_example.json`
- 逆地理编码示例：`examples/reverse_geocode_example.json`

## 错误处理

| 错误码 | 描述 | 解决方案 |
|-------|------|---------|
| 210 | IP白名单校验失败 | 在百度地图控制台配置服务器IP白名单 |
| 101 | AK不存在 | 检查 API Key 是否正确 |
| 102 | AK已被禁用 | 在百度地图控制台启用 AK |
