package io.github.don194.obsidianagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * 将 application.yml 中的 obsidian.api 配置映射到Java对象。
 * 提供了与Obsidian Local REST API通信所需的所有配置。
 */
@ConfigurationProperties(prefix = "obsidian.api")
@Data
@Validated
public class ObsidianApiProperties {

    /**
     * Obsidian Local REST API 的基础 URL.
     * 例如: "http://127.0.0.1:27123"
     */
    private String baseUrl;

    /**
     * 用于API认证的 Bearer Token.
     */
    private String token;
}

