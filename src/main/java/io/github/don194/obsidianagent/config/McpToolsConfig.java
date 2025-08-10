package io.github.don194.obsidianagent.config;

import io.modelcontextprotocol.client.McpSyncClient; // 确保导入正确
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.List;

/**
 * - 负责从MCP客户端获取所有外部工具。
 * 手动注入McpSyncClient列表来构建Provider。
 */
@Configuration
public class McpToolsConfig {

    /**
     * 创建一个只包含MCP外部工具回调的列表Bean。
     *
     * @param mcpSyncClients Spring Boot会自动配置并注入一个包含所有McpSyncClient的列表。
     * @return 只包含外部工具的ToolCallback列表。
     */
    @Bean
    public List<ToolCallback> mcpToolCallbacks(List<McpSyncClient> mcpSyncClients) {
        if (mcpSyncClients == null || mcpSyncClients.isEmpty()) {
            return Collections.emptyList();
        }

        // 手动创建SyncMcpToolCallbackProvider实例
        SyncMcpToolCallbackProvider provider = new SyncMcpToolCallbackProvider(mcpSyncClients);

        // 从创建的provider实例中获取工具回调
        return List.of(provider.getToolCallbacks());
    }
}