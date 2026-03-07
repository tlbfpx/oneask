package com.oneask.customer.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 百度地图地址转经纬度工具
 * 通过执行 Python 脚本调用百度地图 Geocoding API
 */
public class GeocodingTool {

    private static final Logger log = LoggerFactory.getLogger(GeocodingTool.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.baidu.map-ak:}")
    private String baiduMapAk;

    @Value("${app.python.path:python3}")
    private String pythonPath;

    @Tool(description = "查询地址的经纬度坐标。输入一个中国地址（如'北京市天安门'），返回该地址的经度(lng)和纬度(lat)。适用于用户需要查找某个地点位置坐标的场景。")
    public String geocode(
            @ToolParam(description = "要查询经纬度的地址，如'北京市天安门广场'、'上海市外滩'等") String address) {

        log.info("Geocoding request for address: {}", address);

        if (baiduMapAk == null || baiduMapAk.isBlank() || baiduMapAk.equals("your-baidu-map-ak")) {
            log.warn("Baidu Map AK not configured, using mock response");
            return mockGeocode(address);
        }

        try {
            // 获取 Python 脚本路径
            String scriptPath = getScriptPath();
            log.info("Executing Python script: {} {} {}", pythonPath, scriptPath, address);

            ProcessBuilder pb = new ProcessBuilder(
                    pythonPath, scriptPath, address, baiduMapAk);
            pb.redirectErrorStream(true);

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }

            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "{\"error\": \"Python script execution timed out\"}";
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.error("Python script exited with code {}: {}", exitCode, output);
                return "{\"error\": \"Python script failed with exit code " + exitCode + "\"}";
            }

            String result = output.toString().trim();
            log.info("Geocoding result: {}", result);

            // 验证响应是合法 JSON
            JsonNode node = objectMapper.readTree(result);
            return result;

        } catch (Exception e) {
            log.error("Error executing geocoding script", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private String getScriptPath() {
        // 尝试从 classpath 找脚本
        try {
            var resource = getClass().getClassLoader().getResource("scripts/baidu_geocoding.py");
            if (resource != null) {
                return resource.getPath();
            }
        } catch (Exception e) {
            log.warn("Cannot resolve script from classpath", e);
        }
        // 回退到相对路径
        return "src/main/resources/scripts/baidu_geocoding.py";
    }

    private String mockGeocode(String address) {
        // 一些常见地址的模拟经纬度
        if (address != null) {
            if (address.contains("天安门")) {
                return "{\"address\": \"" + address
                        + "\", \"lng\": 116.397470, \"lat\": 39.908823, \"note\": \"模拟数据(百度Map AK未配置)\"}";
            } else if (address.contains("外滩")) {
                return "{\"address\": \"" + address
                        + "\", \"lng\": 121.490317, \"lat\": 31.236305, \"note\": \"模拟数据(百度Map AK未配置)\"}";
            } else if (address.contains("西湖")) {
                return "{\"address\": \"" + address
                        + "\", \"lng\": 120.148732, \"lat\": 30.242870, \"note\": \"模拟数据(百度Map AK未配置)\"}";
            }
        }
        return "{\"address\": \"" + address
                + "\", \"lng\": 116.404, \"lat\": 39.915, \"note\": \"模拟数据(百度Map AK未配置，默认返回北京坐标)\"}";
    }
}
