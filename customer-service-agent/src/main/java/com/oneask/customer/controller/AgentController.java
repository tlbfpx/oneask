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

import java.util.Map;
import java.util.Optional;

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
        String userMessage = request.get("message");
        log.info("Received chat request: {}", userMessage);

        try {
            Optional<OverAllState> result = customerServiceAgent.invoke(userMessage);

            if (result.isPresent()) {
                OverAllState state = result.get();
                Object output = state.value("output").orElse(null);

                String responseContent = extractContent(output);

                return Map.of(
                        "success", true,
                        "response", responseContent != null ? responseContent : "处理完成"
                );
            } else {
                return Map.of(
                        "success", false,
                        "response", "Agent 返回空结果"
                );
            }
        } catch (Exception e) {
            log.error("Error processing chat request", e);
            return Map.of(
                    "success", false,
                    "response", "处理出错: " + e.getMessage()
            );
        }
    }

    private String extractContent(Object output) {
        if (output == null) {
            return null;
        }

        try {
            String className = output.getClass().getName();

            if (className.contains("AssistantMessage") || className.contains("AIMessage")) {
                try {
                    var method = output.getClass().getMethod("getTextContent");
                    Object textContent = method.invoke(output);
                    if (textContent != null && !textContent.toString().isBlank()) {
                        return textContent.toString();
                    }
                } catch (Exception ignored) {
                }

                String str = output.toString();
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
