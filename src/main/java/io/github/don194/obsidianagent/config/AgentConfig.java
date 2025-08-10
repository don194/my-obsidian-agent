package io.github.don194.obsidianagent.config;

import io.github.don194.obsidianagent.agent.ObsidianAgent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel; // 导入 ChatModel
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * 负责配置Agent本身。
 */
@Configuration
public class AgentConfig {

    @Bean
    @Scope("prototype") // 确保每个请求获取独立的Agent实例
    public ObsidianAgent obsidianAgent(
            ToolCallback[] allTools,
            ChatModel chatModel
    ) {

        var agent = new ObsidianAgent(allTools, chatModel);

        // 但构造时需要ChatModel来初始化内部默认的ChatClient
        return agent;
    }
}