package io.github.don194.obsidianagent.config;

import io.github.don194.obsidianagent.tool.TerminateTool;
import io.github.don194.obsidianagent.tool.TimeTools;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;

/**
 *负责注册所有项目内部的工具Bean。
 */
@Configuration
public class LocalToolsConfig {

    @Bean public TimeTools timeTools() { return new TimeTools(); }
    @Bean public TerminateTool terminateTool() { return new TerminateTool(); }

    @Bean
    public List<ToolCallback> localToolCallbacks(TimeTools timeTools, TerminateTool terminateTool
                                                  )  {
        return List.of(ToolCallbacks.from(timeTools, terminateTool));
    }
}