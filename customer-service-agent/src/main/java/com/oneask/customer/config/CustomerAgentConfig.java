package com.oneask.customer.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.oneask.customer.tools.GeocodingTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class CustomerAgentConfig {

        private static final Logger log = LoggerFactory.getLogger(CustomerAgentConfig.class);

        @Bean
        public GeocodingTool geocodingTool() {
                return new GeocodingTool();
        }

        @Bean(name = "customerServiceAgent")
        public ReactAgent customerServiceAgent(
                        ChatModel chatModel,
                        GeocodingTool geocodingTool) {

                log.info("Creating Customer Service ReactAgent with geocoding tool...");

                ToolCallback[] callbacks = MethodToolCallbackProvider.builder().toolObjects(geocodingTool).build()
                                .getToolCallbacks();

                return ReactAgent.builder()
                                .name("customer_service_agent")
                                .model(chatModel)
                                .description("专业客服Agent，处理客户咨询、售后服务、地址查询、经纬度查询等问题")
                                .instruction("""
                                                你是OneAsk智能问答系统的专业客服助手。你的职责包括：
                                                
                                                1. **客户咨询**: 回答关于产品、服务的各类问题
                                                2. **售后服务**: 处理退换货、维修、投诉等售后相关问题
                                                3. **地址/位置查询**: 当用户询问地址、位置、地点时，必须使用 geocode 工具查询经纬度坐标
                                                4. **订单咨询**: 协助查询订单状态、物流信息等

                                                ## 重要规则
                                                - 当用户提到任何地址、地点、位置时（如"中州华庭"、"天安门"等），立即使用 geocode 工具查询经纬度
                                                - 不要询问用户是否需要查询经纬度，直接查询并返回结果
                                                - 用简洁、友好、专业的语气回答
                                                - 查询地址后，清晰展示地址名称和经纬度坐标
                                                """)
                                .tools(List.of(callbacks))
                                .outputKey("messages")
                                .build();
        }
}
