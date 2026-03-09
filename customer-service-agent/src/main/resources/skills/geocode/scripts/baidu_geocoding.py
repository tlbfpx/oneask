#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
百度地图 Geocoding API v3.0 - 地址转经纬度工具

功能：
1. 地理编码：将地址转换为经纬度坐标
2. 逆地理编码：将经纬度转换为详细地址

Usage:
    python3 baidu_geocoding.py geocode <address> <ak>
    python3 baidu_geocoding.py reverse_geocode <lat> <lng> <ak>

示例：
    python3 baidu_geocoding.py geocode "北京市天安门" your_api_key
    python3 baidu_geocoding.py reverse_geocode 39.908823 116.397470 your_api_key
"""

import requests
import json
import sys
import urllib.parse


def geocode(address: str, ak: str) -> dict:
    """
    地理编码：将地址转换为经纬度坐标
    
    Args:
        address: 要查询的地址，如"北京市天安门广场"
        ak: 百度地图开放平台 API Key
    
    Returns:
        包含经纬度的字典
    """
    url = "https://api.map.baidu.com/geocoding/v3/"
    params = {
        "address": address,
        "output": "json",
        "ak": ak,
        "ret_coordtype": "gcj02ll"
    }
    
    try:
        response = requests.get(url, params=params, timeout=10)
        response.raise_for_status()
        data = response.json()
        
        if data.get("status") == 0:
            location = data["result"]["location"]
            return {
                "success": True,
                "address": address,
                "lng": location["lng"],
                "lat": location["lat"],
                "confidence": data["result"].get("confidence", 0),
                "level": data["result"].get("level", "unknown"),
                "precise": data["result"].get("precise", 0),
                "message": f"查询成功：{address} 的坐标为经度 {location['lng']}，纬度 {location['lat']}"
            }
        else:
            error_msg = {
                1: "服务器内部错误",
                2: "请求参数非法",
                3: "权限校验失败",
                4: "配额校验失败",
                5: "AK不存在或被禁止",
                101: "AK不存在",
                102: "AK已被禁用",
                200: "AK不存在或被禁止",
                201: "AK被禁止使用该服务",
                202: "AK被禁止使用该服务",
                203: "AK被禁止使用该服务",
                210: "IP白名单校验失败",
                211: "IP白名单校验失败",
                220: "Referer白名单校验失败",
                230: "APP白名单校验失败",
                240: "请求参数非法",
                250: "请求参数非法",
                251: "请求参数非法",
                252: "请求参数非法",
                300: "配额校验失败",
                301: "配额校验失败",
                302: "配额校验失败",
                401: "服务内部错误",
                402: "服务内部错误",
                403: "服务内部错误",
                500: "服务内部错误",
                501: "服务内部错误",
                502: "服务内部错误"
            }
            return {
                "success": False,
                "error": error_msg.get(data.get("status"), f"未知错误，状态码: {data.get('status')}"),
                "status": data.get("status"),
                "address": address,
                "message": f"查询失败：{error_msg.get(data.get('status'), '未知错误')}"
            }
    
    except requests.exceptions.Timeout:
        return {
            "success": False,
            "error": "请求超时",
            "address": address,
            "message": "查询失败：请求百度地图API超时，请稍后重试"
        }
    except requests.exceptions.RequestException as e:
        return {
            "success": False,
            "error": f"网络请求失败: {str(e)}",
            "address": address,
            "message": f"查询失败：网络请求异常 - {str(e)}"
        }
    except (KeyError, ValueError) as e:
        return {
            "success": False,
            "error": f"响应解析错误: {str(e)}",
            "address": address,
            "message": f"查询失败：响应数据解析异常"
        }


def reverse_geocode(lat: float, lng: float, ak: str) -> dict:
    """
    逆地理编码：将经纬度转换为详细地址
    
    Args:
        lat: 纬度
        lng: 经度
        ak: 百度地图开放平台 API Key
    
    Returns:
        包含详细地址的字典
    """
    url = "https://api.map.baidu.com/reverse_geocoding/v3/"
    params = {
        "location": f"{lat},{lng}",
        "output": "json",
        "ak": ak,
        "coordtype": "gcj02ll",
        "extensions_poi": "1"
    }
    
    try:
        response = requests.get(url, params=params, timeout=10)
        response.raise_for_status()
        data = response.json()
        
        if data.get("status") == 0:
            result = data["result"]
            formatted_address = result.get("formatted_address", "")
            business = result.get("business", "")
            address_component = result.get("addressComponent", {})
            
            return {
                "success": True,
                "lat": lat,
                "lng": lng,
                "formatted_address": formatted_address,
                "business": business,
                "province": address_component.get("province", ""),
                "city": address_component.get("city", ""),
                "district": address_component.get("district", ""),
                "street": address_component.get("street", ""),
                "street_number": address_component.get("street_number", ""),
                "message": f"坐标 ({lat}, {lng}) 对应的地址是：{formatted_address}"
            }
        else:
            return {
                "success": False,
                "error": f"API返回错误，状态码: {data.get('status')}",
                "lat": lat,
                "lng": lng,
                "message": f"逆地理编码查询失败"
            }
    
    except requests.exceptions.Timeout:
        return {
            "success": False,
            "error": "请求超时",
            "lat": lat,
            "lng": lng,
            "message": "查询失败：请求百度地图API超时"
        }
    except requests.exceptions.RequestException as e:
        return {
            "success": False,
            "error": f"网络请求失败: {str(e)}",
            "lat": lat,
            "lng": lng,
            "message": f"查询失败：网络请求异常"
        }
    except (KeyError, ValueError) as e:
        return {
            "success": False,
            "error": f"响应解析错误: {str(e)}",
            "lat": lat,
            "lng": lng,
            "message": f"查询失败：响应数据解析异常"
        }


def print_usage():
    print(json.dumps({
        "error": "参数错误",
        "usage": {
            "geocode": "python3 baidu_geocoding.py geocode <address> <ak>",
            "reverse_geocode": "python3 baidu_geocoding.py reverse_geocode <lat> <lng> <ak>"
        },
        "examples": [
            "python3 baidu_geocoding.py geocode \"北京市天安门\" your_api_key",
            "python3 baidu_geocoding.py reverse_geocode 39.908823 116.397470 your_api_key"
        ]
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print_usage()
        sys.exit(1)
    
    command = sys.argv[1].lower()
    
    if command == "geocode":
        if len(sys.argv) < 4:
            print_usage()
            sys.exit(1)
        addr = sys.argv[2]
        api_key = sys.argv[3]
        result = geocode(addr, api_key)
        print(json.dumps(result, ensure_ascii=False))
    
    elif command == "reverse_geocode":
        if len(sys.argv) < 5:
            print_usage()
            sys.exit(1)
        try:
            latitude = float(sys.argv[2])
            longitude = float(sys.argv[3])
            api_key = sys.argv[4]
            result = reverse_geocode(latitude, longitude, api_key)
            print(json.dumps(result, ensure_ascii=False))
        except ValueError:
            print(json.dumps({"error": "纬度和经度必须是数字", "success": False}, ensure_ascii=False))
            sys.exit(1)
    
    else:
        print_usage()
        sys.exit(1)
