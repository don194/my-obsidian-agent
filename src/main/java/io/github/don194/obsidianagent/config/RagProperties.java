package io.github.don194.obsidianagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 将 application.yml 中的 obsidian.rag 配置映射到Java对象。
 */
@Configuration
@ConfigurationProperties(prefix = "obsidian.rag")
@Data
public class RagProperties {

    /**
     * 向量数据库文件的存储路径。
     */
    private String vectorStorePath;
}

