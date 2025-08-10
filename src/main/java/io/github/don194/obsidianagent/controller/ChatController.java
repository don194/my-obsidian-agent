package io.github.don194.obsidianagent.controller;

import io.github.don194.obsidianagent.entity.ChatSession;
import io.github.don194.obsidianagent.model.ChatRequest;
import io.github.don194.obsidianagent.service.ChatService;
import io.github.don194.obsidianagent.memory.SqliteChatMemory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 聊天控制器
 * 处理所有与聊天相关的HTTP请求
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatService chatService;
    private final SqliteChatMemory sqliteChatMemory;

    /**
     * 发送消息并获取流式响应
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody ChatRequest request) {
        log.info("Received stream chat request for session: {}", request.getSessionId());

        // 验证请求
        if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            SseEmitter emitter = new SseEmitter();
            try {
                emitter.send("错误：消息不能为空");
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }

        // 如果没有提供sessionId，生成新的
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = UUID.randomUUID().toString();
        }

        return chatService.streamChat(sessionId, request.getMessage());
    }



    /**
     * 创建新的聊天会话
     */
    @PostMapping("/sessions")
    public ResponseEntity<Map<String, String>> createSession(@RequestBody(required = false) Map<String, String> body) {
        try {
            String sessionId = chatService.createNewSession();
            String title = body != null ? body.get("title") : null;

            if (title != null && !title.trim().isEmpty()) {
                chatService.updateSessionTitle(sessionId, title);
            }

            log.info("Created new session: {}", sessionId);
            return ResponseEntity.ok(Map.of("sessionId", sessionId));
        } catch (Exception e) {
            log.error("Error creating session", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "创建会话失败: " + e.getMessage()));
        }
    }

    /**
     * 获取所有会话列表
     */
    @GetMapping("/sessions")
    public ResponseEntity<List<Map<String, Object>>> getAllSessions() {
        try {
            List<Map<String, Object>> sessions = chatService.getAllSessions();
            return ResponseEntity.ok(sessions);
        } catch (Exception e) {
            log.error("Error getting sessions", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取指定会话的详情
     */
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<Map<String, Object>> getSession(@PathVariable String sessionId) {
        try {
            Map<String, Object> sessionDetails = chatService.getSessionDetails(sessionId);
            if (sessionDetails == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(sessionDetails);
        } catch (Exception e) {
            log.error("Error getting session: {}", sessionId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 更新会话标题
     */
    @PutMapping("/sessions/{sessionId}/title")
    public ResponseEntity<Map<String, String>> updateSessionTitle(
            @PathVariable String sessionId,
            @RequestBody Map<String, String> body) {
        try {
            String title = body.get("title");
            if (title == null || title.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "标题不能为空"));
            }

            chatService.updateSessionTitle(sessionId, title);
            return ResponseEntity.ok(Map.of("message", "标题更新成功"));
        } catch (Exception e) {
            log.error("Error updating session title: {}", sessionId, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "更新标题失败: " + e.getMessage()));
        }
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Map<String, String>> deleteSession(@PathVariable String sessionId) {
        try {
            chatService.deleteSession(sessionId);
            log.info("Deleted session: {}", sessionId);
            return ResponseEntity.ok(Map.of("message", "会话删除成功"));
        } catch (Exception e) {
            log.error("Error deleting session: {}", sessionId, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "删除会话失败: " + e.getMessage()));
        }
    }

    /**
     * 获取会话的聊天历史
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<List<Map<String, Object>>> getSessionMessages(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "50") int limit) {
        try {
            // 需要在ChatService中添加这个方法
            List<Map<String, Object>> messages = chatService.getSessionMessages(sessionId, limit);
            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            log.error("Error getting session messages: {}", sessionId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 搜索消息
     */
    @GetMapping("/search")
    public ResponseEntity<List<Map<String, Object>>> searchMessages(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "20") int limit) {
        try {
            List<Map<String, Object>> results = chatService.searchMessages(keyword, limit);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Error searching messages with keyword: {}", keyword, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        try {
            Map<String, Object> stats = sqliteChatMemory.getStatistics();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error getting statistics", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 健康检查端点
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}