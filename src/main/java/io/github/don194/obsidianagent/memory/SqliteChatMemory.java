package io.github.don194.obsidianagent.memory;

import io.github.don194.obsidianagent.entity.ChatMessage;
import io.github.don194.obsidianagent.entity.ChatSession;
import io.github.don194.obsidianagent.repository.ChatMessageRepository;
import io.github.don194.obsidianagent.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于SQLite的聊天记忆实现
 * 实现Spring AI的ChatMemory接口，用于替代Redis
 */
@Slf4j
@Component("chatMemory")
@RequiredArgsConstructor
public class SqliteChatMemory implements ChatMemory {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;

    /**
     * 添加消息到指定会话
     */
    @Override
    @Transactional
    public void add(String conversationId, List<Message> messages) {
        if (conversationId == null || conversationId.trim().isEmpty()) {
            log.warn("ConversationId is null or empty, skipping message save");
            return;
        }

        try {
            // 确保会话存在
            ensureSessionExists(conversationId);

            // 保存消息
            for (Message message : messages) {
                ChatMessage chatMessage = convertToChatMessage(conversationId, message);
                messageRepository.save(chatMessage);
            }

            // 更新会话的消息计数和更新时间
            updateSessionMessageCount(conversationId);

            log.debug("Added {} messages to conversation {}", messages.size(), conversationId);
        } catch (Exception e) {
            log.error("Failed to add messages to conversation {}", conversationId, e);
        }
    }

    /**
     * 获取指定会话的最近N条消息
     */
    @Override
    public List<Message> get(String conversationId, int lastN) {
        if (conversationId == null || conversationId.trim().isEmpty()) {
            log.warn("ConversationId is null or empty, returning empty messages");
            return Collections.emptyList();
        }

        try {
            // 查询最近的N条消息（按时间倒序）
            List<ChatMessage> chatMessages = messageRepository.findBySessionIdOrderByCreatedAtDesc(
                    conversationId, PageRequest.of(0, lastN));

            // 转换为Spring AI Message并按时间正序排列
            // 修复：添加null检查，避免NullPointerException
            List<Message> messages = chatMessages.stream()
                    .filter(msg -> msg.getCreatedAt() != null) // 过滤掉创建时间为null的消息
                    .sorted(Comparator.comparing(ChatMessage::getCreatedAt,
                            Comparator.nullsFirst(Comparator.naturalOrder()))) // 安全的排序
                    .map(this::convertToSpringAIMessage)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            log.debug("Retrieved {} messages for conversation {}", messages.size(), conversationId);
            return messages;
        } catch (Exception e) {
            log.error("Failed to retrieve messages for conversation {}", conversationId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 清除指定会话的所有消息
     */
    @Override
    @Transactional
    public void clear(String conversationId) {
        if (conversationId == null || conversationId.trim().isEmpty()) {
            log.warn("ConversationId is null or empty, skipping clear");
            return;
        }

        try {
            messageRepository.deleteBySessionId(conversationId);
            sessionRepository.deleteById(conversationId);
            log.debug("Cleared all messages for conversation {}", conversationId);
        } catch (Exception e) {
            log.error("Failed to clear conversation {}", conversationId, e);
        }
    }

    // --- 会话管理方法 ---

    /**
     * 设置会话标题
     */
    @Transactional
    public void setSessionTitle(String sessionId, String title) {
        try {
            ChatSession session = ensureSessionExists(sessionId);
            session.setTitle(title);
            sessionRepository.save(session);
        } catch (Exception e) {
            log.error("Failed to set title for session {}", sessionId, e);
        }
    }

    /**
     * 获取会话标题
     */
    public String getSessionTitle(String sessionId) {
        try {
            return sessionRepository.findById(sessionId)
                    .map(ChatSession::getTitle)
                    .orElse(null);
        } catch (Exception e) {
            log.error("Failed to get title for session {}", sessionId, e);
            return null;
        }
    }

    /**
     * 获取所有会话及其标题
     */
    public Map<String, String> getAllSessionsWithTitles() {
        try {
            return sessionRepository.findAllByOrderByUpdatedAtDesc().stream()
                    .collect(Collectors.toMap(
                            ChatSession::getSessionId,
                            session -> session.getTitle() != null ? session.getTitle() : "",
                            (existing, replacement) -> existing,
                            LinkedHashMap::new
                    ));
        } catch (Exception e) {
            log.error("Failed to get all sessions", e);
            return new HashMap<>();
        }
    }

    /**
     * 搜索消息内容
     */
    public List<ChatMessage> searchMessages(String keyword, int limit) {
        try {
            return messageRepository.searchByContent(keyword, PageRequest.of(0, limit));
        } catch (Exception e) {
            log.error("Failed to search messages with keyword: {}", keyword, e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取统计信息
     */
    public Map<String, Object> getStatistics() {
        try {
            long sessionCount = sessionRepository.count();
            long messageCount = messageRepository.count();

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalSessions", sessionCount);
            stats.put("totalMessages", messageCount);
            stats.put("averageMessagesPerSession",
                    sessionCount > 0 ? (double) messageCount / sessionCount : 0.0);

            return stats;
        } catch (Exception e) {
            log.error("Failed to get statistics", e);
            return new HashMap<>();
        }
    }

    // --- 私有辅助方法 ---

    /**
     * 确保会话存在，如果不存在则创建
     */
    private ChatSession ensureSessionExists(String sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseGet(() -> {
                    ChatSession newSession = new ChatSession(sessionId);
                    return sessionRepository.save(newSession);
                });
    }

    /**
     * 更新会话的消息计数
     */
    private void updateSessionMessageCount(String sessionId) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            long messageCount = messageRepository.countBySessionId(sessionId);
            session.setMessageCount((int) messageCount);
            sessionRepository.save(session);
        });
    }

    /**
     * 将Spring AI Message转换为ChatMessage实体
     * 修复：确保创建时间不为null
     */
    private ChatMessage convertToChatMessage(String sessionId, Message message) {
        ChatMessage.MessageType messageType;
        switch (message.getMessageType()) {
            case USER:
                messageType = ChatMessage.MessageType.USER;
                break;
            case ASSISTANT:
                messageType = ChatMessage.MessageType.ASSISTANT;
                break;
            case SYSTEM:
                messageType = ChatMessage.MessageType.SYSTEM;
                break;
            default:
                messageType = ChatMessage.MessageType.ASSISTANT;
        }

        ChatMessage chatMessage = new ChatMessage(
                UUID.randomUUID().toString(),
                sessionId,
                messageType,
                message.getText()
        );

        // 修复：手动设置创建时间，确保不为null
        if (chatMessage.getCreatedAt() == null) {
            chatMessage.setCreatedAt(LocalDateTime.now());
        }

        return chatMessage;
    }

    /**
     * 将ChatMessage实体转换为Spring AI Message
     */
    private Message convertToSpringAIMessage(ChatMessage chatMessage) {
        try {
            switch (chatMessage.getMessageType()) {
                case USER:
                    return new UserMessage(chatMessage.getContent());
                case ASSISTANT:
                    return new AssistantMessage(chatMessage.getContent());
                case SYSTEM:
                    return new SystemMessage(chatMessage.getContent());
                default:
                    return new AssistantMessage(chatMessage.getContent());
            }
        } catch (Exception e) {
            log.error("Failed to convert ChatMessage to Spring AI Message", e);
            return null;
        }
    }
}