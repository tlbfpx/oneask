package com.oneask.routing.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.a2a.A2aRemoteAgent;
import com.alibaba.cloud.ai.graph.agent.a2a.AgentCardProvider;
import com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

@Configuration
public class AgentConfig {

        private static final Logger log = LoggerFactory.getLogger(AgentConfig.class);

        private static final String ROUTING_SYSTEM_PROMPT = """
                        你是一个智能的问答路由Agent，负责根据用户需求将任务路由到最合适的专家Agent。

                        ## 你的职责
                        1. 仔细分析用户输入的意图和需求
                        2. 根据任务特性，选择最合适的专家Agent
                        3. 确保路由决策准确、高效

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
                        1. **客服任务**: 如果用户问题涉及客服、订单、售后、地址查询等，选择 customer_service_agent
                        2. **通用任务**: 其他所有问题，选择 default_agent
                        """;

        /**
         * 远程客服 Agent（通过 Nacos 发现）
         */
        @Bean(name = "customerServiceRemoteAgent")
        public A2aRemoteAgent customerServiceRemoteAgent(AgentCardProvider agentCardProvider) {
                log.info("Creating A2A Remote Agent for customer_service_agent via Nacos discovery...");
                return A2aRemoteAgent.builder()
                                .name("customer_service_agent")
                                .agentCardProvider(agentCardProvider)
                                .description("专业客服Agent，处理客户咨询、售后服务、地址查询、经纬度查询等问题")
                                .build();
        }

        /**
         * 本地默认 Agent（Fallback）
         */
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

        /**
         * LLM 智能路由 Agent
         */
        @Bean(name = "llmRoutingAgent")
        @Primary
        public LlmRoutingAgent llmRoutingAgent(
                        ChatModel chatModel,
                        A2aRemoteAgent customerServiceRemoteAgent,
                        ReactAgent defaultAgent) {

                log.info("Creating LlmRoutingAgent with sub-agents: customer_service_agent, default_agent");
                return LlmRoutingAgent.builder()
                                .name("llm_routing_agent")
                                .description("智能路由Agent，根据用户输入内容动态选择合适的专家Agent处理")
                                .model(chatModel)
                                .systemPrompt(ROUTING_SYSTEM_PROMPT)
                                .subAgents(List.of(customerServiceRemoteAgent, defaultAgent))
                                .build();
        }
}
