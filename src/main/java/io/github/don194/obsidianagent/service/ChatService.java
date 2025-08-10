package io.github.don194.obsidianagent.service;

import io.github.don194.obsidianagent.agent.ObsidianAgent;
import io.github.don194.obsidianagent.entity.ChatMessage;
import io.github.don194.obsidianagent.memory.SqliteChatMemory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 核心聊天服务 - 专注于流式处理
 * 负责动态构建上下文并编排Agent运行
 * 使用SQLite替代Redis，更适合桌面应用
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ObjectProvider<ObsidianAgent> agentProvider;
    private final ChatModel chatModel;
    private final SqliteChatMemory sqliteChatMemory;

    /**
     * 流式聊天 - 主要入口点
     */
    public SseEmitter streamChat(String sessionId, String userMessage) {
        log.info("Starting stream chat for session: {}, message length: {}", sessionId, userMessage.length());

        SseEmitter sseEmitter = new SseEmitter(300000L); // 5分钟超时

        try {
            // 基础验证
            if (userMessage == null || userMessage.trim().isEmpty()) {
                sendErrorAndComplete(sseEmitter, "消息不能为空");
                return sseEmitter;
            }

            // 每次请求都获取一个全新的Agent实例
            ObsidianAgent agent = agentProvider.getObject();

            // 为当前会话创建专门的MessageChatMemoryAdvisor实例
            MessageChatMemoryAdvisor memoryAdvisor = new MessageChatMemoryAdvisor(
                    sqliteChatMemory, sessionId, 50);

            // 动态构建一个包含会话记忆的ChatClient
            ChatClient sessionAwareChatClient = ChatClient.builder(chatModel)
                    .defaultAdvisors(memoryAdvisor)
                    .build();


            // 将这个会话感知的客户端设置给Agent
            agent.setChatClient(sessionAwareChatClient);

            // 异步生成标题（如果需要）
            checkAndGenerateTitle(sessionId, userMessage);

            // 设置SSE错误处理
            sseEmitter.onError(throwable -> {
                log.error("SSE stream error for session: {}", sessionId, throwable);
                handleStreamError(sessionId, throwable);
            });

            sseEmitter.onCompletion(() -> {
                log.info("SSE stream completed for session: {}", sessionId);
                handleStreamCompletion(sessionId);
            });

            sseEmitter.onTimeout(() -> {
                log.warn("SSE stream timeout for session: {}", sessionId);
                handleStreamTimeout(sessionId);
            });

            // 运行Agent并返回SSE流
            return agent.runStream(userMessage);

        } catch (Exception e) {
            log.error("Error starting stream chat for session: {}", sessionId, e);
            sendErrorAndComplete(sseEmitter, "启动聊天流时发生错误: " + e.getMessage());
            return sseEmitter;
        }
    }

    /**
     * 异步生成会话标题
     */
    @Async
    public void checkAndGenerateTitle(String sessionId, String firstUserMessage) {
        // 如果已有标题，跳过
        String existingTitle = sqliteChatMemory.getSessionTitle(sessionId);
        if (existingTitle != null && !existingTitle.trim().isEmpty()) {
            return;
        }

        try {
            log.info("Generating title for session: {}", sessionId);

            // 使用一个完全独立的、不带任何advisor的客户端来生成标题

            ChatClient titleClient = ChatClient.builder(chatModel)
                    .defaultAdvisors()
                    .build();


            String title = titleClient.prompt()
                    .system("""
                        你是一个对话标题生成器。
                        请根据用户的第一条消息，生成一个简洁、准确的中文标题。
                        要求：
                        1. 不超过12个字
                        2. 体现对话的核心主题
                        3. 使用简洁的中文表达
                        4. 不要包含标点符号
                        5. 只返回标题，不要其他内容
                        """)
                    .user("请为这个用户消息生成标题：" + firstUserMessage)
                    .call()
                    .content();

            // 清理并限制标题长度
            title = cleanTitle(title);

            // 使用直接的数据库操作，避免通过ChatMemory
            sqliteChatMemory.setSessionTitle(sessionId, title);

            log.info("Generated title for session {}: {}", sessionId, title);

        } catch (Exception e) {
            log.error("Failed to generate title for session {}: {}", sessionId, e.getMessage());
            // 设置默认标题
            String defaultTitle = generateDefaultTitle();
            try {
                sqliteChatMemory.setSessionTitle(sessionId, defaultTitle);
            } catch (Exception ex) {
                log.error("Failed to set default title: {}", ex.getMessage());
            }
        }
    }

    /**
     * 创建新会话
     */
    public String createNewSession() {
        String sessionId = UUID.randomUUID().toString();
        log.info("Creating new session: {}", sessionId);

        try {
            // 创建空会话，标题稍后生成
            sqliteChatMemory.setSessionTitle(sessionId, "");
            return sessionId;
        } catch (Exception e) {
            log.error("Error creating new session", e);
            throw new RuntimeException("创建新会话失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有会话列表
     */
    public List<Map<String, Object>> getAllSessions() {
        try {
            Map<String, String> sessionsWithTitles = sqliteChatMemory.getAllSessionsWithTitles();

            return sessionsWithTitles.entrySet().stream()
                    .map(entry -> {
                        Map<String, Object> sessionInfo = new HashMap<>();
                        sessionInfo.put("sessionId", entry.getKey());
                        sessionInfo.put("title",
                                entry.getValue() != null && !entry.getValue().isEmpty()
                                        ? entry.getValue()
                                        : "新对话");

                        // 添加更多会话信息
                        try {
                            sessionInfo.put("messageCount", getMessageCount(entry.getKey()));
                            sessionInfo.put("lastActivity", getLastActivity(entry.getKey()));
                        } catch (Exception e) {
                            log.warn("Error getting session details for {}: {}", entry.getKey(), e.getMessage());
                        }

                        return sessionInfo;
                    })
                    .sorted((a, b) -> {
                        // 按最后活动时间排序
                        LocalDateTime timeA = (LocalDateTime) a.getOrDefault("lastActivity", LocalDateTime.MIN);
                        LocalDateTime timeB = (LocalDateTime) b.getOrDefault("lastActivity", LocalDateTime.MIN);
                        return timeB.compareTo(timeA);
                    })
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error getting all sessions", e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取会话详情
     */
    public Map<String, Object> getSessionDetails(String sessionId) {
        try {
            String title = sqliteChatMemory.getSessionTitle(sessionId);
            if (title == null) {
                return null; // 会话不存在
            }

            Map<String, Object> details = new HashMap<>();
            details.put("sessionId", sessionId);
            details.put("title", title.isEmpty() ? "新对话" : title);
            details.put("messageCount", getMessageCount(sessionId));
            details.put("lastActivity", getLastActivity(sessionId));
            details.put("createdAt", getSessionCreatedAt(sessionId));

            return details;
        } catch (Exception e) {
            log.error("Error getting session details for: {}", sessionId, e);
            return null;
        }
    }

    /**
     * 获取会话的聊天历史（用于显示）
     */
    public List<Map<String, Object>> getSessionMessages(String sessionId, int limit) {
        try {
            List<Message> messages = sqliteChatMemory.get(sessionId, limit);

            return messages.stream()
                    .map(this::convertMessageToMap)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error getting session messages for: {}", sessionId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 搜索消息
     */
    public List<Map<String, Object>> searchMessages(String keyword, int limit) {
        try {
            List<ChatMessage> searchResults = sqliteChatMemory.searchMessages(keyword, limit);

            return searchResults.stream()
                    .map(this::convertChatMessageToMap)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error searching messages with keyword: {}", keyword, e);
            return Collections.emptyList();
        }
    }

    /**
     * 搜索指定会话中的消息
     */
    public List<Map<String, Object>> searchSessionMessages(String sessionId, String keyword, int limit) {
        try {
            // 这里需要在SqliteChatMemory中添加相应方法，或者通过repository直接查询
            // 暂时使用全局搜索然后过滤
            return searchMessages(keyword, limit * 2).stream()
                    .filter(msg -> sessionId.equals(msg.get("sessionId")))
                    .limit(limit)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error searching session messages for {}: {}", sessionId, keyword, e);
            return Collections.emptyList();
        }
    }

    /**
     * 更新会话标题
     */
    public void updateSessionTitle(String sessionId, String newTitle) {
        try {
            if (newTitle == null || newTitle.trim().isEmpty()) {
                throw new IllegalArgumentException("标题不能为空");
            }

            String cleanedTitle = cleanTitle(newTitle);
            sqliteChatMemory.setSessionTitle(sessionId, cleanedTitle);
            log.info("Updated title for session {}: {}", sessionId, cleanedTitle);

        } catch (Exception e) {
            log.error("Error updating session title for {}: {}", sessionId, e.getMessage());
            throw new RuntimeException("更新会话标题失败: " + e.getMessage());
        }
    }

    /**
     * 删除会话
     */
    public void deleteSession(String sessionId) {
        try {
            sqliteChatMemory.clear(sessionId);
            log.info("Deleted session: {}", sessionId);
        } catch (Exception e) {
            log.error("Error deleting session: {}", sessionId, e);
            throw new RuntimeException("删除会话失败: " + e.getMessage());
        }
    }

    /**
     * 清除会话历史（保留会话但删除消息）
     */
    public void clearSessionHistory(String sessionId) {
        try {
            // 保存标题
            String title = sqliteChatMemory.getSessionTitle(sessionId);

            // 清除消息
            sqliteChatMemory.clear(sessionId);

            // 恢复标题
            if (title != null) {
                sqliteChatMemory.setSessionTitle(sessionId, title);
            }

            log.info("Cleared history for session: {}", sessionId);
        } catch (Exception e) {
            log.error("Error clearing session history: {}", sessionId, e);
            throw new RuntimeException("清除会话历史失败: " + e.getMessage());
        }
    }

    /**
     * 获取统计信息
     */
    public Map<String, Object> getStatistics() {
        try {
            return sqliteChatMemory.getStatistics();
        } catch (Exception e) {
            log.error("Error getting statistics", e);
            return Map.of("error", "获取统计信息失败");
        }
    }

    // === 私有辅助方法 ===

    /**
     * 发送错误信息并完成SSE流
     */
    private void sendErrorAndComplete(SseEmitter sseEmitter, String errorMessage) {
        try {
            sseEmitter.send("错误: " + errorMessage);
            sseEmitter.complete();
        } catch (IOException e) {
            log.error("Error sending error message", e);
            sseEmitter.completeWithError(e);
        }
    }

    /**
     * 处理流错误
     */
    private void handleStreamError(String sessionId, Throwable error) {
        log.error("Stream error for session {}: {}", sessionId, error.getMessage());
        // 这里可以添加错误恢复逻辑，比如保存错误状态等
    }

    /**
     * 处理流完成
     */
    private void handleStreamCompletion(String sessionId) {
        log.debug("Stream completed for session: {}", sessionId);
        // 这里可以添加完成后的清理逻辑
    }

    /**
     * 处理流超时
     */
    private void handleStreamTimeout(String sessionId) {
        log.warn("Stream timeout for session: {}", sessionId);
        // 这里可以添加超时处理逻辑
    }

    /**
     * 清理标题
     */
    private String cleanTitle(String title) {
        if (title == null) return "新对话";

        title = title.trim()
                .replaceAll("[\"\"''《》【】（）()]", "") // 移除引号和括号
                .replaceAll("\\s+", " ") // 合并多个空格
                .trim();

        return title.length() > 12 ? title.substring(0, 12) : title;
    }

    /**
     * 生成默认标题
     */
    private String generateDefaultTitle() {
        return "对话 " + LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"));
    }

    /**
     * 获取会话消息数量
     */
    private int getMessageCount(String sessionId) {
        try {
            return sqliteChatMemory.get(sessionId, Integer.MAX_VALUE).size();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 获取会话最后活动时间
     */
    private LocalDateTime getLastActivity(String sessionId) {
        try {
            List<Message> messages = sqliteChatMemory.get(sessionId, 1);
            if (!messages.isEmpty()) {
                // 这里需要从消息中获取时间戳，可能需要扩展Message类或从数据库直接查询
                return LocalDateTime.now(); // 临时返回当前时间
            }
            return LocalDateTime.MIN;
        } catch (Exception e) {
            return LocalDateTime.MIN;
        }
    }

    /**
     * 获取会话创建时间
     */
    private LocalDateTime getSessionCreatedAt(String sessionId) {
        try {
            // 需要从数据库查询会话创建时间
            return LocalDateTime.now(); // 临时返回当前时间
        } catch (Exception e) {
            return LocalDateTime.MIN;
        }
    }

    /**
     * 将Spring AI Message转换为Map
     */
    private Map<String, Object> convertMessageToMap(Message message) {
        Map<String, Object> messageMap = new HashMap<>();
        messageMap.put("type", message.getMessageType().name().toLowerCase());
        messageMap.put("content", message.getText());
        messageMap.put("timestamp", System.currentTimeMillis());
        return messageMap;
    }

    /**
     * 将ChatMessage实体转换为Map
     */
    private Map<String, Object> convertChatMessageToMap(ChatMessage chatMessage) {
        Map<String, Object> messageMap = new HashMap<>();
        messageMap.put("sessionId", chatMessage.getSessionId());
        messageMap.put("type", chatMessage.getMessageType().name().toLowerCase());
        messageMap.put("content", chatMessage.getContent());
        messageMap.put("timestamp", chatMessage.getCreatedAt());
        messageMap.put("messageId", chatMessage.getMessageId());
        return messageMap;
    }
}