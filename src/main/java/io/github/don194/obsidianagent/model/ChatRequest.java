package io.github.don194.obsidianagent.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天请求DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {
    private String sessionId;
    private String message;
    private String systemPrompt; // 可选的系统提示词
    private Integer maxSteps;    // 可选的最大步骤数
}