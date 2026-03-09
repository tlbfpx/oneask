package com.oneask.routing.config;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.a2a.A2aRemoteAgent;
import com.alibaba.cloud.ai.graph.agent.a2a.AgentCardProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfig {

        private static final Logger log = LoggerFactory.getLogger(AgentConfig.class);

        private static final String ROUTING_SYSTEM_PROMPT = """
                        你是一个智能的问答路由Agent。你需要分析用户输入，判断应该路由到哪个Agent。

                        ## 可用的子Agent及其职责

                        ### customer_service_agent
                        - **功能**: 专业客服Agent，处理客户咨询、售后服务、地址查询、经纬度查询等问题
                        - **适用场景**:
                          * 客户咨询产品信息
                          * 售后服务问题
                          * 地址/位置查询（如查询某地经纬度）
                          * 订单相关问题
                          * 投诉建议

                        ### default_agent
                        - **功能**: 通用问题处理Agent
                        - **适用场景**:
                          * 闲聊、打招呼
                          * 通用知识问答
                          * 不属于客服范畴的其他问题

                        ## 决策规则
                        1. **客服任务**: 如果用户问题涉及客服、订单、售后、地址查询、经纬度、位置等，选择 customer_service_agent
                        2. **通用任务**: 其他所有问题，选择 default_agent

                        请直接回答用户的问题，如果问题涉及地址查询、经纬度查询，请明确告知用户相关信息。
                        """;

        @Bean(name = "customerServiceRemoteAgent")
        public A2aRemoteAgent customerServiceRemoteAgent(AgentCardProvider agentCardProvider) {
                log.info("Creating A2A Remote Agent for customer_service_agent via Nacos discovery...");
                return A2aRemoteAgent.builder()
                                .name("customer_service_agent")
                                .agentCardProvider(agentCardProvider)
                                .description("专业客服Agent，处理客户咨询、售后服务、地址查询、经纬度查询等问题")
                                .build();
        }

        @Bean(name = "defaultAgent")
        public ReactAgent defaultAgent(ChatModel chatModel) {
                return ReactAgent.builder()
                                .name("default_agent")
                                .model(chatModel)
                                .description("通用问题处理Agent，处理闲聊、通用知识问答等")
                                .instruction("你是OneAsk智能问答系统的通用助手。你能够回答各种通用问题，进行友好的对话。"
                                                + "请用简洁、专业、友好的语气回答用户的问题。如果用户的问题超出你的能力范围，请诚实告知。")
                                .outputKey("messages")
                                .build();
        }

        @Bean(name = "routingAgent")
        public ReactAgent routingAgent(ChatModel chatModel) {
                log.info("Creating Routing ReactAgent...");
                return ReactAgent.builder()
                                .name("routing_agent")
                                .model(chatModel)
                                .description("智能路由Agent，根据用户输入内容动态选择合适的专家Agent处理")
                                .instruction(ROUTING_SYSTEM_PROMPT)
                                .outputKey("messages")
                                .build();
        }
}
