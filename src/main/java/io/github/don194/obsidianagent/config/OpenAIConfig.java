package io.github.don194.obsidianagent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAI配置类 - 使用Spring AI M6自动配置
 *
 * Spring AI M6推荐通过application.yml配置，自动创建ChatModel Bean
 * 无需手动创建OpenAiApi和OpenAiChatModel Bean
 */
@Slf4j
@Configuration
public class OpenAIConfig {

    public OpenAIConfig(@Value("${spring.ai.openai.base-url:https://api.openai.com}") String baseUrl,
                        @Value("${spring.ai.openai.api-key:not-set}") String apiKey) {
        log.info("=== OpenAI 配置信息 ===");
        log.info("Base URL: {}", baseUrl);
        log.info("API Key: {}...{}",
                apiKey.length() > 10 ? apiKey.substring(0, 10) : "短密钥",
                apiKey.length() > 10 ? apiKey.substring(apiKey.length() - 4) : "");
        log.info("OpenAI Config loaded - using Spring AI auto-configuration");
    }


}