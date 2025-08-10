package io.github.don194.obsidianagent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import java.util.ArrayList;
import java.util.List;

/**
 *负责将所有工具组合成一个最终的工具包，供Agent使用。
 */
@Slf4j
@Configuration
public class AgentToolsConfig {

    @Bean
    @Primary
    public ToolCallback[] allTools(
            @Qualifier("localToolCallbacks") List<ToolCallback> localToolCallbacks,
            @Qualifier("mcpToolCallbacks") List<ToolCallback> mcpToolCallbacks
    ) {
        List<ToolCallback> allToolCallbacks = new ArrayList<>();
        log.debug("found {} local tool callbacks", localToolCallbacks.size());
        allToolCallbacks.addAll(localToolCallbacks);
        allToolCallbacks.addAll(mcpToolCallbacks);
        return allToolCallbacks.toArray(new ToolCallback[0]);
    }
}