# 百度地图 Geocoding API v3.0 参考文档

## 概述

百度地图 Geocoding API 是一项免费的 Web 服务接口，提供地理编码和逆地理编码服务。

## 地理编码接口

### 请求 URL

```
https://api.map.baidu.com/geocoding/v3/
```

### 请求参数

| 参数名 | 类型 | 必需 | 描述 |
|-------|------|------|------|
| address | string | 是 | 待解析的地址 |
| ak | string | 是 | 用户申请的 API Key |
| output | string | 否 | 输出格式，可选 json 或 xml，默认 json |
| ret_coordtype | string | 否 | 返回坐标类型，可选 gcj02ll、bd09ll、bd09mc |
| city | string | 否 | 指定查询的城市 |

### 响应示例

```json
{
    "status": 0,
    "result": {
        "location": {
            "lng": 116.397470,
            "lat": 39.908823
        },
        "precise": 1,
        "confidence": 80,
        "comprehension": 100,
        "level": "道路"
    }
}
```

## 逆地理编码接口

### 请求 URL

```
https://api.map.baidu.com/reverse_geocoding/v3/
```

### 请求参数

| 参数名 | 类型 | 必需 | 描述 |
|-------|------|------|------|
| location | string | 是 | 经纬度坐标，格式：lat,lng |
| ak | string | 是 | 用户申请的 API Key |
| output | string | 否 | 输出格式，默认 json |
| coordtype | string | 否 | 坐标类型，可选 gcj02ll、bd09ll、bd09mc |
| extensions_poi | string | 否 | 是否返回 POI 信息，0 不返回，1 返回 |

### 响应示例

```json
{
    "status": 0,
    "result": {
        "formatted_address": "北京市东城区东华门街道天安门广场",
        "business": "天安门",
        "addressComponent": {
            "province": "北京市",
            "city": "北京市",
            "district": "东城区",
            "street": "广场东侧路",
            "street_number": ""
        }
    }
}
```

## 状态码说明

| 状态码 | 描述 |
|-------|------|
| 0 | 正常 |
| 1 | 服务器内部错误 |
| 2 | 请求参数非法 |
| 3 | 权限校验失败 |
| 4 | 配额校验失败 |
| 5 | AK不存在或被禁止 |
| 101 | AK不存在 |
| 102 | AK已被禁用 |
| 200-203 | AK被禁止使用该服务 |
| 210-211 | IP白名单校验失败 |
| 220 | Referer白名单校验失败 |
| 230 | APP白名单校验失败 |
| 240-252 | 请求参数非法 |
| 300-302 | 配额校验失败 |
| 401-403 | 服务内部错误 |
| 500-502 | 服务内部错误 |

## 官方文档

- [地理编码 API 文档](https://lbsyun.baidu.com/index.php?title=webapi/guide/webservice-geocoding)
- [逆地理编码 API 文档](https://lbsyun.baidu.com/index.php?title=webapi/guide/webservice-reversegeocoding)
