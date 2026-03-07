package com.oneask.routing.controller;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent;
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

    private final LlmRoutingAgent llmRoutingAgent;

    // 简单的短期记忆存储：sessionId -> 对话历史
    private final Map<String, List<Map<String, String>>> chatMemory = new ConcurrentHashMap<>();
    private static final int MAX_MEMORY_SIZE = 20;

    public ChatController(@Qualifier("llmRoutingAgent") LlmRoutingAgent llmRoutingAgent) {
        this.llmRoutingAgent = llmRoutingAgent;
    }

    @PostMapping(value = "/chat", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }
        String message = request.getMessage();

        log.info("Received chat request [session={}]: {}", sessionId, message);

        // 记录用户消息到短期记忆
        addToMemory(sessionId, "user", message);

        try {
            // 构建包含上下文的提示
            String contextualPrompt = buildContextualPrompt(sessionId, message);

            // 调用 LlmRoutingAgent
            Optional<OverAllState> result = llmRoutingAgent.invoke(contextualPrompt);

            if (result.isPresent()) {
                OverAllState state = result.get();
                String reply = extractReply(state);
                String routedTo = extractRoutedAgent(state);

                log.info("Agent routed to: {}, reply length: {}", routedTo, reply.length());

                // 记录 AI 回复到短期记忆
                addToMemory(sessionId, "assistant", reply);

                ChatResponse response = new ChatResponse(
                        sessionId,
                        reply,
                        routedTo,
                        true
                );
                return ResponseEntity.ok(response);
            } else {
                log.warn("LlmRoutingAgent returned empty result");
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

    private String buildContextualPrompt(String sessionId, String currentMessage) {
        List<Map<String, String>> history = chatMemory.get(sessionId);
        if (history == null || history.size() <= 1) {
            return currentMessage;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("以下是用户的对话历史（仅供参考上下文）：\n");

        // 只取最近的历史记录（排除最新一条，因为就是当前消息）
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

        // 保持记忆不超过限制
        while (history.size() > MAX_MEMORY_SIZE * 2) {
            history.remove(0);
        }
    }

    private String extractReply(OverAllState state) {
        log.debug("Extracting reply from state, keys: {}", state.data().keySet());
        
        Object output = state.value("output").orElse(null);
        log.debug("output object: {}, type: {}", output, output != null ? output.getClass().getName() : "null");
        if (output != null) {
            String content = extractContentFromObject(output);
            if (content != null && !content.isBlank()) {
                return content;
            }
        }
        
        Object messages = state.value("messages").orElse(null);
        log.debug("messages object: {}, type: {}", messages, messages != null ? messages.getClass().getName() : "null");
        
        if (messages != null) {
            if (messages instanceof List<?> list && !list.isEmpty()) {
                log.debug("messages list size: {}", list.size());
                for (int i = list.size() - 1; i >= 0; i--) {
                    Object item = list.get(i);
                    String className = item.getClass().getName();
                    log.debug("Item {}: type={}", i, className);
                    if (className.contains("AssistantMessage") || className.contains("AIMessage")) {
                        String content = extractContentFromObject(item);
                        if (content != null && !content.isBlank()) {
                            return content;
                        }
                    }
                    if (className.contains("GraphResponse")) {
                        String content = extractContentFromObject(item);
                        if (content != null && !content.isBlank()) {
                            return content;
                        }
                    }
                }
            }
            String content = extractContentFromObject(messages);
            if (content != null && !content.isBlank()) {
                return content;
            }
        }

        for (String key : List.of("result", "response", "writer_output", "reviewer_output")) {
            Object val = state.value(key).orElse(null);
            if (val != null) {
                String content = extractContentFromObject(val);
                if (content != null && !content.isBlank()) {
                    return content;
                }
            }
        }

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
            if (className.contains("GraphResponse")) {
                try {
                    java.lang.reflect.Method outputMethod = obj.getClass().getMethod("output");
                    Object output = outputMethod.invoke(obj);
                    if (output != null) {
                        String content = extractContentFromObject(output);
                        if (content != null && !content.isBlank()) {
                            return content;
                        }
                    }
                } catch (Exception e) {
                    log.debug("Failed to extract from GraphResponse via output: {}", e.getMessage());
                }
                try {
                    java.lang.reflect.Field stateField = obj.getClass().getDeclaredField("state");
                    stateField.setAccessible(true);
                    Object stateObj = stateField.get(obj);
                    if (stateObj != null) {
                        String content = extractContentFromObject(stateObj);
                        if (content != null && !content.isBlank()) {
                            return content;
                        }
                    }
                } catch (Exception e) {
                    log.debug("Failed to extract from GraphResponse via state: {}", e.getMessage());
                }
                try {
                    java.lang.reflect.Method toStringMethod = obj.getClass().getMethod("toString");
                    String str = (String) toStringMethod.invoke(obj);
                    if (str != null && !str.startsWith("com.alibaba.cloud.ai.graph.GraphResponse@")) {
                        return str;
                    }
                } catch (Exception e) {
                    log.debug("Failed to extract from GraphResponse via toString: {}", e.getMessage());
                }
            }
            if (obj instanceof String) {
                return (String) obj;
            }
        } catch (Exception e) {
            log.debug("Failed to extract content from object: {}", e.getMessage());
        }
        return obj.toString();
    }

    private String extractRoutedAgent(OverAllState state) {
        Object routedTo = state.value("_routed_to").orElse(null);
        if (routedTo != null) {
            return routedTo.toString();
        }
        // 检查是否有子 agent 输出 key 来推断路由目标
        if (state.value("messages").isPresent()) {
            return "customer_service_agent";
        }
        return "default_agent";
    }
}
