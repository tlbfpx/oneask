#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
百度地图 Geocoding API v3.0 - 地址转经纬度
Usage: python3 baidu_geocoding.py <address> <ak>
"""

import requests
import json
import sys
import urllib.parse


def geocode(address: str, ak: str) -> dict:
    """
    调用百度地图 Geocoding API 将地址转换为经纬度

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
                "address": address,
                "lng": location["lng"],
                "lat": location["lat"],
                "confidence": data["result"].get("confidence", 0),
                "level": data["result"].get("level", "unknown")
            }
        else:
            return {
                "error": data.get("message", "Unknown error"),
                "status": data.get("status"),
                "address": address
            }

    except requests.exceptions.Timeout:
        return {"error": "Request timed out", "address": address}
    except requests.exceptions.RequestException as e:
        return {"error": f"Request failed: {str(e)}", "address": address}
    except (KeyError, ValueError) as e:
        return {"error": f"Response parsing error: {str(e)}", "address": address}


if __name__ == "__main__":
    if len(sys.argv) < 3:
        print(json.dumps({"error": "Usage: python3 baidu_geocoding.py <address> <ak>"}, ensure_ascii=False))
        sys.exit(1)

    addr = sys.argv[1]
    api_key = sys.argv[2]
    result = geocode(addr, api_key)
    print(json.dumps(result, ensure_ascii=False))
