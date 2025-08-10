package io.github.don194.obsidianagent.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天响应DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    private String sessionId;
    private String message;
    private String status;       // 可选的状态信息
    private Long timestamp;      // 响应时间戳

    public ChatResponse(String sessionId, String message) {
        this.sessionId = sessionId;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
        this.status = "success";
    }
}