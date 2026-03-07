package com.oneask.routing;

import com.alibaba.cloud.ai.a2a.autoconfigure.server.A2aServerAgentCardAutoConfiguration;
import com.alibaba.cloud.ai.a2a.autoconfigure.server.A2aServerAutoConfiguration;
import com.alibaba.cloud.ai.a2a.autoconfigure.server.A2aServerHandlerAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = {
    A2aServerAutoConfiguration.class,
    A2aServerHandlerAutoConfiguration.class,
    A2aServerAgentCardAutoConfiguration.class
})
public class LlmRoutingAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(LlmRoutingAgentApplication.class, args);
    }
}
