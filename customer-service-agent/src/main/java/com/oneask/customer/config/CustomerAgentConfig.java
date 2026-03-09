package com.oneask.customer.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.shelltool.ShellToolAgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.skills.SkillsAgentHook;
import com.alibaba.cloud.ai.graph.agent.tools.ShellTool2;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.classpath.ClasspathSkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.filesystem.FileSystemSkillRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Configuration
public class CustomerAgentConfig {

    private static final Logger log = LoggerFactory.getLogger(CustomerAgentConfig.class);

    @Value("${app.baidu.map-ak:}")
    private String baiduMapAk;

    @Bean
    public SkillRegistry skillRegistry() {
        log.info("Initializing SkillRegistry for skills directory");
        
        // 首先尝试从文件系统加载（开发模式）
        Path projectSkillsPath = Path.of("customer-service-agent/src/main/resources/skills").toAbsolutePath();
        if (Files.exists(projectSkillsPath)) {
            log.info("Found skills directory in project: {}", projectSkillsPath);
            return FileSystemSkillRegistry.builder()
                    .projectSkillsDirectory(projectSkillsPath.toString())
                    .build();
        }
        
        // 否则从 classpath 加载（JAR 模式）
        log.info("Using classpath skills directory");
        return ClasspathSkillRegistry.builder()
                .classpathPath("BOOT-INF/classes/skills")
                .build();
    }

    @Bean
    public SkillsAgentHook skillsAgentHook(SkillRegistry skillRegistry) {
        log.info("Creating SkillsAgentHook with skill registry");
        return SkillsAgentHook.builder()
                .skillRegistry(skillRegistry)
                .autoReload(true)
                .build();
    }

    @Bean
    public ShellToolAgentHook shellToolAgentHook() {
        String workDir = System.getProperty("user.dir");
        log.info("Creating ShellToolAgentHook with work directory: {}", workDir);
        return ShellToolAgentHook.builder()
                .shellTool2(ShellTool2.builder(workDir).build())
                .build();
    }

    @Bean(name = "customerServiceAgent")
    public ReactAgent customerServiceAgent(
            ChatModel chatModel,
            SkillsAgentHook skillsAgentHook,
            ShellToolAgentHook shellToolAgentHook) {

        log.info("Creating Customer Service ReactAgent with skills support...");

        return ReactAgent.builder()
                .name("customer_service_agent")
                .model(chatModel)
                .description("专业客服Agent，处理客户咨询、售后服务、地址查询、经纬度查询等问题")
                .instruction("""
                        你是客服助手，拥有多种技能来帮助用户解决问题。
                        
                        当用户需要查询地址的经纬度坐标，或者根据经纬度查询详细地址时，请使用 geocode 技能。
                        
                        使用技能时：
                        1. 先调用 read_skill 加载技能详情
                        2. 根据技能说明执行相应的 Python 脚本
                        3. 百度地图 API Key: %s
                        
                        注意：如果 API Key 未配置，请告知用户需要配置 BAIDU_MAP_AK 环境变量。
                        """.formatted(baiduMapAk != null && !baiduMapAk.isBlank() ? baiduMapAk : "未配置"))
                .hooks(List.of(skillsAgentHook, shellToolAgentHook))
                .saver(new MemorySaver())
                .enableLogging(true)
                .outputKey("messages")
                .build();
    }
}
