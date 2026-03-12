package com.oneask.customer.controller;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.MDC;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final ReactAgent customerServiceAgent;

    public AgentController(@Qualifier("customerServiceAgent") ReactAgent customerServiceAgent) {
        this.customerServiceAgent = customerServiceAgent;
    }

    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody Map<String, String> request) {
        String traceId = request.getOrDefault("traceId", UUID.randomUUID().toString().substring(0, 8));
        MDC.put("traceId", traceId);
        long startTime = System.currentTimeMillis();

        String userMessage = request.get("message");

        log.info("[CALL-CHAIN] ========== Customer Service Agent 请求开始 ==========");
        log.info("[CALL-CHAIN] TraceId: {}", traceId);
        log.info("[CALL-CHAIN] 用户输入: {}", userMessage);
        log.info("[CALL-CHAIN] 步骤1: 接收请求 - 耗时: 0ms");

        try {
            long step2Start = System.currentTimeMillis();
            log.info("[CALL-CHAIN] 步骤2: 调用Agent执行 - 开始");
            Optional<OverAllState> result = customerServiceAgent.invoke(userMessage);
            long agentTime = System.currentTimeMillis() - step2Start;
            log.info("[CALL-CHAIN] 步骤2: Agent执行完成 - 耗时: {}ms", agentTime);

            if (result.isPresent()) {
                long step3Start = System.currentTimeMillis();
                OverAllState state = result.get();

                // 首先尝试从 messages 列表中提取最后一个 AssistantMessage
                String responseContent = extractReplyFromState(state);

                // 如果 messages 中没有，再尝试 output 字段
                if (responseContent == null || responseContent.isBlank()) {
                    Object output = state.value("output").orElse(null);
                    responseContent = extractContent(output);
                }
                long extractTime = System.currentTimeMillis() - step3Start;
                log.info("[CALL-CHAIN] 步骤3: 提取响应内容 - 内容长度: {} - 耗时: {}ms",
                        responseContent != null ? responseContent.length() : 0, extractTime);

                long totalTime = System.currentTimeMillis() - startTime;
                log.info("[CALL-CHAIN] ========== Customer Service Agent 请求完成 - 总耗时: {}ms ==========", totalTime);

                return Map.of(
                        "success", true,
                        "response", responseContent != null && !responseContent.isBlank() ? responseContent : "处理完成"
                );
            } else {
                log.warn("[CALL-CHAIN] Agent 返回空结果");
                return Map.of(
                        "success", false,
                        "response", "Agent 返回空结果"
                );
            }
        } catch (Exception e) {
            long totalTime = System.currentTimeMillis() - startTime;
            log.error("[CALL-CHAIN] 请求处理异常 - 总耗时: {}ms - 错误: {}", totalTime, e.getMessage(), e);
            return Map.of(
                    "success", false,
                    "response", "处理出错: " + e.getMessage()
            );
        } finally {
            MDC.remove("traceId");
        }
    }

    private String extractReplyFromState(OverAllState state) {
        Object messages = state.value("messages").orElse(null);
        if (messages != null) {
            String content = extractLastAssistantMessage(messages);
            if (content != null && !content.isBlank()) {
                return content;
            }
        }
        return null;
    }

    private String extractLastAssistantMessage(Object messages) {
        if (messages instanceof List<?> list && !list.isEmpty()) {
            log.debug("Messages list size: {}", list.size());

            for (int i = list.size() - 1; i >= 0; i--) {
                Object item = list.get(i);
                String itemClassName = item.getClass().getName();
                log.debug("Processing message item {} type: {}", i, itemClassName);

                if (itemClassName.contains("AssistantMessage") || itemClassName.contains("AIMessage")) {
                    String content = extractContentFromAssistantMessage(item);
                    if (content != null && !content.isBlank()) {
                        log.debug("Found valid assistant message at index {}: {}", i,
                                content.length() > 200 ? content.substring(0, 200) + "..." : content);
                        return content;
                    }
                }
            }
        }
        return null;
    }

    private String extractContentFromAssistantMessage(Object message) {
        if (message == null) {
            return null;
        }

        try {
            // 尝试调用 getTextContent 方法
            var method = message.getClass().getMethod("getTextContent");
            Object textContent = method.invoke(message);
            if (textContent != null && !textContent.toString().isBlank()) {
                return textContent.toString();
            }
        } catch (Exception ignored) {
        }

        // 备用方案：从 toString() 中提取
        String str = message.toString();
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

    private String extractContent(Object output) {
        if (output == null) {
            return null;
        }

        try {
            String className = output.getClass().getName();

            if (className.contains("AssistantMessage") || className.contains("AIMessage")) {
                return extractContentFromAssistantMessage(output);
            }

            if (output instanceof String str) {
                return str;
            }

            return output.toString();
        } catch (Exception e) {
            log.debug("Failed to extract content: {}", e.getMessage());
            return output.toString();
        }
    }
}
