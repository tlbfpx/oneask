package com.oneask.routing.controller;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.a2a.A2aRemoteAgent;
import com.oneask.routing.model.ChatRequest;
import com.oneask.routing.model.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ReactAgent routingAgent;
    private final ReactAgent defaultAgent;
    private final A2aRemoteAgent customerServiceAgent;

    private final Map<String, List<Map<String, String>>> chatMemory = new ConcurrentHashMap<>();
    private static final int MAX_MEMORY_SIZE = 20;

    private static final Set<String> CUSTOMER_SERVICE_KEYWORDS = Set.of(
            "经纬度", "坐标", "地址", "位置", "在哪里", "查询", "地图",
            "客服", "订单", "售后", "投诉", "建议", "退款", "物流",
            "天安门", "外滩", "西湖", "广场", "街道", "路", "号"
    );

    public ChatController(
            @Qualifier("routingAgent") ReactAgent routingAgent,
            @Qualifier("defaultAgent") ReactAgent defaultAgent,
            @Qualifier("customerServiceRemoteAgent") A2aRemoteAgent customerServiceAgent) {
        this.routingAgent = routingAgent;
        this.defaultAgent = defaultAgent;
        this.customerServiceAgent = customerServiceAgent;
    }

    @PostMapping(value = "/chat", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }
        String message = request.getMessage();
        
        if (message == null || message.isBlank()) {
            message = request.getQuery();
        }

        log.info("Received chat request [session={}]: {}", sessionId, message);

        addToMemory(sessionId, "user", message);

        try {
            String contextualPrompt = buildContextualPrompt(sessionId, message);
            
            String targetAgent = determineTargetAgent(message);
            log.info("Routing to agent: {}", targetAgent);

            Optional<OverAllState> result;
            String routedTo;

            if ("customer_service_agent".equals(targetAgent)) {
                result = customerServiceAgent.invoke(message);
                routedTo = "customer_service_agent";
            } else {
                result = defaultAgent.invoke(contextualPrompt);
                routedTo = "default_agent";
            }

            if (result.isPresent()) {
                OverAllState state = result.get();
                String reply = extractReply(state);

                log.info("Agent {} returned reply length: {}", routedTo, reply.length());

                addToMemory(sessionId, "assistant", reply);

                ChatResponse response = new ChatResponse(
                        sessionId,
                        reply,
                        routedTo,
                        true
                );
                return ResponseEntity.ok(response);
            } else {
                log.warn("Agent {} returned empty result", routedTo);
                ChatResponse response = new ChatResponse(
                        sessionId,
                        "抱歉，系统暂时无法处理您的请求，请稍后再试。",
                        "none",
                        false
                );
                return ResponseEntity.ok(response);
            }
        } catch (Exception e) {
            log.error("Error processing chat request", e);
            ChatResponse response = new ChatResponse(
                    sessionId,
                    "系统处理出错：" + e.getMessage(),
                    "error",
                    false
            );
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "UP");
        status.put("service", "llm-routing-agent");
        return ResponseEntity.ok(status);
    }

    @DeleteMapping("/chat/memory/{sessionId}")
    public ResponseEntity<Map<String, String>> clearMemory(@PathVariable String sessionId) {
        chatMemory.remove(sessionId);
        Map<String, String> result = new HashMap<>();
        result.put("status", "cleared");
        result.put("sessionId", sessionId);
        return ResponseEntity.ok(result);
    }

    private String determineTargetAgent(String message) {
        if (message == null || message.isBlank()) {
            return "default_agent";
        }
        
        String lowerMessage = message.toLowerCase();
        for (String keyword : CUSTOMER_SERVICE_KEYWORDS) {
            if (lowerMessage.contains(keyword)) {
                return "customer_service_agent";
            }
        }
        
        return "default_agent";
    }

    private String buildContextualPrompt(String sessionId, String currentMessage) {
        List<Map<String, String>> history = chatMemory.get(sessionId);
        if (history == null || history.size() <= 1) {
            return currentMessage;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("以下是用户的对话历史（仅供参考上下文）：\n");

        int start = Math.max(0, history.size() - MAX_MEMORY_SIZE - 1);
        for (int i = start; i < history.size() - 1; i++) {
            Map<String, String> entry = history.get(i);
            String role = entry.get("role");
            String content = entry.get("content");
            sb.append(role.equals("user") ? "用户: " : "助手: ").append(content).append("\n");
        }
        sb.append("\n当前用户问题：").append(currentMessage);
        return sb.toString();
    }

    private void addToMemory(String sessionId, String role, String content) {
        chatMemory.computeIfAbsent(sessionId, k -> new ArrayList<>());
        List<Map<String, String>> history = chatMemory.get(sessionId);

        Map<String, String> entry = new HashMap<>();
        entry.put("role", role);
        entry.put("content", content);
        history.add(entry);

        while (history.size() > MAX_MEMORY_SIZE * 2) {
            history.remove(0);
        }
    }

    private String extractReply(OverAllState state) {
        log.debug("Extracting reply from state, keys: {}", state.data().keySet());
        
        // 优先从 messages 列表中获取最后一个助手消息
        Object messages = state.value("messages").orElse(null);
        if (messages != null) {
            String content = extractLastAssistantMessage(messages);
            if (content != null && !content.isBlank()) {
                log.debug("Extracted content from messages: {}", content.length() > 200 ? content.substring(0, 200) + "..." : content);
                return content;
            }
        }
        
        // 尝试从 output 获取
        Object output = state.value("output").orElse(null);
        if (output != null) {
            String content = extractContentFromObject(output);
            if (content != null && !content.isBlank()) {
                return content;
            }
        }

        // 尝试其他字段
        for (String key : List.of("result", "response")) {
            Object val = state.value(key).orElse(null);
            if (val != null) {
                String content = extractContentFromObject(val);
                if (content != null && !content.isBlank()) {
                    return content;
                }
            }
        }

        // 遍历所有数据
        Map<String, Object> allData = state.data();
        if (allData != null && !allData.isEmpty()) {
            for (Map.Entry<String, Object> entry : allData.entrySet()) {
                if (entry.getValue() != null && !entry.getKey().startsWith("_")) {
                    String content = extractContentFromObject(entry.getValue());
                    if (content != null && !content.isBlank()) {
                        return content;
                    }
                }
            }
        }
        return "处理完成，但未获取到具体回复内容。";
    }

    /**
     * 从消息列表中提取最后一个助手消息的内容
     */
    private String extractLastAssistantMessage(Object messages) {
        if (messages instanceof List<?> list && !list.isEmpty()) {
            log.debug("Messages list size: {}", list.size());
            
            // 从后向前遍历，找到最后一个助手消息
            for (int i = list.size() - 1; i >= 0; i--) {
                Object item = list.get(i);
                String itemClassName = item.getClass().getName();
                log.debug("Processing message item {} type: {}", i, itemClassName);
                
                // 只处理助手消息
                if (itemClassName.contains("AssistantMessage") || itemClassName.contains("AIMessage")) {
                    String content = extractContentFromObject(item);
                    if (content != null && !content.isBlank()) {
                        // 过滤掉简单的 "pong" 响应
                        if (!content.trim().equalsIgnoreCase("pong") && !content.trim().equalsIgnoreCase(" pong")) {
                            log.debug("Found valid assistant message at index {}: {}", i, content.length() > 200 ? content.substring(0, 200) + "..." : content);
                            return content;
                        }
                    }
                }
            }
        }
        return null;
    }

    private String extractContentFromObject(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            String className = obj.getClass().getName();
            
            if (className.contains("AssistantMessage") || className.contains("AIMessage")) {
                try {
                    java.lang.reflect.Method getTextContentMethod = obj.getClass().getMethod("getTextContent");
                    Object textContent = getTextContentMethod.invoke(obj);
                    if (textContent != null && !textContent.toString().isBlank()) {
                        return textContent.toString();
                    }
                } catch (NoSuchMethodException ignored) {
                }
                try {
                    java.lang.reflect.Method getContentMethod = obj.getClass().getMethod("getContent");
                    Object content = getContentMethod.invoke(obj);
                    if (content != null && !content.toString().isBlank()) {
                        return content.toString();
                    }
                } catch (NoSuchMethodException ignored) {
                }
                String str = obj.toString();
                int textContentIdx = str.indexOf("textContent=");
                if (textContentIdx > 0) {
                    int start = textContentIdx + "textContent=".length();
                    int end = str.indexOf(", metadata=", start);
                    if (end > start) {
                        return str.substring(start, end);
                    }
                }
                return str;
            }
            
            if (className.contains("UserMessage")) {
                return null;
            }
            
            if (obj instanceof String) {
                return (String) obj;
            }
            
            if (obj instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) obj;
                Object outputVal = map.get("output");
                if (outputVal != null) {
                    return extractContentFromObject(outputVal);
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract content: {}", e.getMessage());
        }
        return null;
    }
}
