package com.oneask.routing.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AgentConfig {

    private static final Logger log = LoggerFactory.getLogger(AgentConfig.class);

    private static final String DEFAULT_AGENT_INSTRUCTION = """
            你是OneAsk智能问答系统的通用助手。
            你能够回答各种通用问题，进行友好的对话。
            请用简洁、专业、友好的语气回答用户的问题。
            如果用户的问题超出你的能力范围，请诚实告知。
            """;

    @Bean(name = "defaultAgent")
    public ReactAgent defaultAgent(ChatModel chatModel) {
        log.info("Creating Default ReactAgent...");
        return ReactAgent.builder()
                .name("default_agent")
                .model(chatModel)
                .description("通用问题处理Agent，处理闲聊、通用知识问答等")
                .instruction(DEFAULT_AGENT_INSTRUCTION)
                .outputKey("messages")
                .build();
    }

    @Bean
    @LoadBalanced
    public RestTemplate loadBalancedRestTemplate() {
        log.info("Creating LoadBalanced RestTemplate for service discovery...");
        return new RestTemplate();
    }
}
