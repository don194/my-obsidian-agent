package io.github.don194.obsidianagent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA 审计配置
 * 启用 @CreatedDate 和 @LastModifiedDate 自动填充
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditConfig {
    // Spring Boot 会自动处理审计功能
}