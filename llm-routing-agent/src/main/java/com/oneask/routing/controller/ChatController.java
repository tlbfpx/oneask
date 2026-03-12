package com.oneask.routing.controller;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.oneask.routing.model.ChatRequest;
import com.oneask.routing.model.ChatResponse;
import com.oneask.routing.model.RoutingDecision;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ReactAgent defaultAgent;
    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    private final Map<String, List<Map<String, String>>> chatMemory = new ConcurrentHashMap<>();
    private static final int MAX_MEMORY_SIZE = 20;

    @Value("${customer-service.url:http://localhost:8081}")
    private String customerServiceUrl;

    private static final String ROUTING_PROMPT = """
            你是一个智能路由决策器。分析用户的问题，决定应该路由到哪个Agent处理。
            
            ## 可用的Agent
            
            1. **customer_service_agent** - 专业客服Agent
               - 处理客户咨询、售后服务
               - 地址查询、经纬度查询、位置相关
               - 订单问题、投诉建议、退款物流
            
            2. **default_agent** - 通用助手Agent
               - 闲聊、打招呼
               - 通用知识问答
               - 其他不属于客服范畴的问题
            
            ## 输出格式
            
            请严格按照以下JSON格式输出，不要输出其他内容：
            ```json
            {
                "agent": "customer_service_agent 或 default_agent",
                "reason": "选择该Agent的简短理由"
            }
            ```
            
            ## 用户问题
            %s
            """;

    public ChatController(
            @Qualifier("defaultAgent") ReactAgent defaultAgent,
            ChatModel chatModel) {
        this.defaultAgent = defaultAgent;
        this.chatModel = chatModel;
        this.objectMapper = new ObjectMapper();
        
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30000);
        factory.setReadTimeout(120000);
        this.restTemplate = new RestTemplate(factory);
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
            
            String targetAgent = determineTargetAgentByLLM(message);
            log.info("LLM routing decision: {}", targetAgent);

            String reply;
            String routedTo;

            if ("customer_service_agent".equals(targetAgent)) {
                reply = callCustomerServiceAgent(message);
                routedTo = "customer_service_agent";
            } else {
                Optional<OverAllState> result = defaultAgent.invoke(contextualPrompt);
                if (result.isPresent()) {
                    reply = extractReply(result.get());
                } else {
                    reply = "抱歉，系统暂时无法处理您的请求，请稍后再试。";
                }
                routedTo = "default_agent";
            }

            log.info("Agent {} returned reply length: {}", routedTo, reply.length());

            addToMemory(sessionId, "assistant", reply);

            ChatResponse response = new ChatResponse(
                    sessionId,
                    reply,
                    routedTo,
                    true
            );
            return ResponseEntity.ok(response);
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

    /**
     * 调用 Customer Service Agent
     */
    private String callCustomerServiceAgent(String message) {
        try {
            String url = customerServiceUrl + "/api/agent/chat";
            
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("message", message);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);
            
            log.debug("Calling Customer Service Agent: {}", url);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Object responseContent = body.get("response");
                if (responseContent != null) {
                    return responseContent.toString();
                }
            }
            
            return "客服服务暂时不可用，请稍后再试。";
        } catch (Exception e) {
            log.error("Failed to call Customer Service Agent", e);
            return "调用客服服务失败：" + e.getMessage();
        }
    }

    /**
     * 使用 LLM 智能判断路由目标
     */
    private String determineTargetAgentByLLM(String message) {
        try {
            String promptText = String.format(ROUTING_PROMPT, message);
            Prompt prompt = new Prompt(promptText);
            Object output = chatModel.call(prompt).getResult().getOutput();
            
            String response = extractContentFromObject(output);
            log.debug("LLM routing response: {}", response);
            
            String jsonContent = extractJsonFromResponse(response);
            if (jsonContent != null) {
                RoutingDecision decision = objectMapper.readValue(jsonContent, RoutingDecision.class);
                log.info("Routing decision - Agent: {}, Reason: {}", decision.getAgent(), decision.getReason());
                
                if ("customer_service_agent".equals(decision.getAgent()) || "default_agent".equals(decision.getAgent())) {
                    return decision.getAgent();
                }
            }
        } catch (Exception e) {
            log.warn("LLM routing failed, fallback to default agent: {}", e.getMessage());
        }
        
        return "default_agent";
    }

    /**
     * 从响应中提取 JSON 内容
     */
    private String extractJsonFromResponse(String response) {
        if (response == null || response.isBlank()) {
            return null;
        }
        
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        
        return null;
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
        
        Object messages = state.value("messages").orElse(null);
        if (messages != null) {
            String content = extractLastAssistantMessage(messages);
            if (content != null && !content.isBlank()) {
                log.debug("Extracted content from messages: {}", content.length() > 200 ? content.substring(0, 200) + "..." : content);
                return content;
            }
        }
        
        Object output = state.value("output").orElse(null);
        if (output != null) {
            String content = extractContentFromObject(output);
            if (content != null && !content.isBlank()) {
                return content;
            }
        }

        for (String key : List.of("result", "response")) {
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

    private String extractLastAssistantMessage(Object messages) {
        if (messages instanceof List<?> list && !list.isEmpty()) {
            log.debug("Messages list size: {}", list.size());
            
            for (int i = list.size() - 1; i >= 0; i--) {
                Object item = list.get(i);
                String itemClassName = item.getClass().getName();
                log.debug("Processing message item {} type: {}", i, itemClassName);
                
                if (itemClassName.contains("AssistantMessage") || itemClassName.contains("AIMessage")) {
                    String content = extractContentFromObject(item);
                    if (content != null && !content.isBlank()) {
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
