package io.github.don194.obsidianagent.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    /**
     * 添加消息到指定会话
     *
     * @param conversationId 会话ID
     * @param messages       消息列表
     */
    @Override
    @Transactional
    public void add(String conversationId, List<Message> messages) {
        if (conversationId == null || conversationId.trim().isEmpty()) {
            log.warn("会话ID为空，跳过消息保存");
            return;
        }
        try {
            // 确保会话存在
            ensureSessionExists(conversationId);
            // 遍历并保存每一条消息
            for (Message message : messages) {
                ChatMessage chatMessage = convertToChatMessage(conversationId, message);
                messageRepository.save(chatMessage);
            }
            // 更新会话的统计信息
            updateSessionMessageCount(conversationId);
            log.debug("向会话 {} 添加了 {} 条消息", messages.size(), conversationId);
        } catch (Exception e) {
            log.error("向会话 {} 添加消息失败", conversationId, e);
        }
    }

    /**
     * 获取指定会话的最近N条消息
     *
     * @param conversationId 会话ID
     * @param lastN          要获取的消息数量
     * @return 消息列表
     */
    @Override
    public List<Message> get(String conversationId, int lastN) {
        if (conversationId == null || conversationId.trim().isEmpty()) {
            log.warn("会话ID为空，返回空消息列表");
            return Collections.emptyList();
        }
        try {
            // 从数据库按时间倒序查询最新的N条消息
            List<ChatMessage> chatMessages = messageRepository.findBySessionIdOrderByCreatedAtDesc(
                    conversationId, PageRequest.of(0, lastN));
            // 将数据库实体转换为Spring AI的Message对象，并按时间正序排列
            List<Message> messages = chatMessages.stream()
                    .filter(msg -> msg.getCreatedAt() != null)
                    .sorted(Comparator.comparing(ChatMessage::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                    .map(this::convertToSpringAIMessage)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            log.debug("从会话 {} 中获取了 {} 条消息", conversationId, messages.size());
            return messages;
        } catch (Exception e) {
            log.error("从会话 {} 获取消息失败", conversationId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 清除指定会话的所有消息和会话本身
     *
     * @param conversationId 会话ID
     */
    @Override
    @Transactional
    public void clear(String conversationId) {
        if (conversationId == null || conversationId.trim().isEmpty()) {
            log.warn("会话ID为空，跳过清除操作");
            return;
        }
        try {
            messageRepository.deleteBySessionId(conversationId);
            sessionRepository.deleteById(conversationId);
            log.debug("已清除会话 {}", conversationId);
        } catch (Exception e) {
            log.error("清除会话 {} 失败", conversationId, e);
        }
    }

    /**
     * 设置会话标题
     *
     * @param sessionId 会话ID
     * @param title     标题
     */
    @Transactional
    public void setSessionTitle(String sessionId, String title) {
        try {
            ChatSession session = ensureSessionExists(sessionId);
            session.setTitle(title);
            sessionRepository.save(session);
        } catch (Exception e) {
            log.error("为会话 {} 设置标题失败", sessionId, e);
        }
    }

    /**
     * 获取会话标题
     *
     * @param sessionId 会话ID
     * @return 标题
     */
    public String getSessionTitle(String sessionId) {
        try {
            return sessionRepository.findById(sessionId)
                    .map(ChatSession::getTitle)
                    .orElse(null);
        } catch (Exception e) {
            log.error("获取会话 {} 的标题失败", sessionId, e);
            return null;
        }
    }

    /**
     * 获取所有会话及其标题
     *
     * @return 包含所有会话ID和标题的Map
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
            log.error("获取所有会话失败", e);
            return new HashMap<>();
        }
    }

    /**
     * 根据关键词搜索消息内容
     *
     * @param keyword 关键词
     * @param limit   返回结果数量
     * @return ChatMessage实体列表
     */
    public List<ChatMessage> searchMessages(String keyword, int limit) {
        try {
            return messageRepository.searchByContent(keyword, PageRequest.of(0, limit));
        } catch (Exception e) {
            log.error("使用关键词 '{}' 搜索消息失败", keyword, e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取统计信息
     *
     * @return 包含统计数据的Map
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
            log.error("获取统计信息失败", e);
            return new HashMap<>();
        }
    }

    /**
     * 确保会话存在，如果不存在则创建
     *
     * @param sessionId 会话ID
     * @return ChatSession实体
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
     *
     * @param sessionId 会话ID
     */
    private void updateSessionMessageCount(String sessionId) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            long messageCount = messageRepository.countBySessionId(sessionId);
            session.setMessageCount((int) messageCount);
            sessionRepository.save(session);
        });
    }

    /**
     * 将Spring AI的Message对象转换为用于数据库存储的ChatMessage实体。
     * @param sessionId 会话ID
     * @param message   Spring AI的Message对象
     * @return ChatMessage实体
     */
    private ChatMessage convertToChatMessage(String sessionId, Message message) {
        ChatMessage.MessageType messageType;
        String content;

        try {
            // 使用 instanceof 进行类型判断，兼容 Java 17
            if (message instanceof UserMessage) {
                messageType = ChatMessage.MessageType.USER;
                content = message.getText();
            } else if (message instanceof AssistantMessage assistantMessage) {
                messageType = ChatMessage.MessageType.ASSISTANT;
                // 对于助手消息，将文本和工具调用信息打包成一个Map再序列化为JSON
                Map<String, Object> assistantData = new HashMap<>();
                assistantData.put("text", assistantMessage.getText());
                assistantData.put("toolCalls", assistantMessage.getToolCalls());
                content = objectMapper.writeValueAsString(assistantData);
            } else if (message instanceof SystemMessage) {
                messageType = ChatMessage.MessageType.SYSTEM;
                content = message.getText();
            } else if (message instanceof ToolResponseMessage toolResponseMessage) {
                messageType = ChatMessage.MessageType.TOOL;
                // 对于工具响应消息，将响应列表打包成一个Map再序列化为JSON
                Map<String, Object> toolData = new HashMap<>();
                toolData.put("responses", toolResponseMessage.getResponses());
                content = objectMapper.writeValueAsString(toolData);
            } else {
                // 对于未知的消息类型，提供一个默认处理方式
                messageType = ChatMessage.MessageType.ASSISTANT;
                content = message.getText();
            }
        } catch (Exception e) {
            // 如果序列化失败，记录错误并回退到只保存纯文本内容
            log.error("序列化消息为JSON时出错", e);
            messageType = ChatMessage.MessageType.valueOf(message.getMessageType().name());
            content = message.getText();
        }

        ChatMessage chatMessage = new ChatMessage(
                UUID.randomUUID().toString(),
                sessionId,
                messageType,
                content
        );

        // 确保创建时间不为空
        if (chatMessage.getCreatedAt() == null) {
            chatMessage.setCreatedAt(LocalDateTime.now());
        }
        return chatMessage;
    }

    /**
     * 将数据库中的ChatMessage实体转换回Spring AI的Message对象。
     *
     * @param chatMessage 数据库实体
     * @return Spring AI的Message对象
     */
    private Message convertToSpringAIMessage(ChatMessage chatMessage) {
        try {
            switch (chatMessage.getMessageType()) {
                case USER:
                    return new UserMessage(chatMessage.getContent());
                case SYSTEM:
                    return new SystemMessage(chatMessage.getContent());
                case ASSISTANT:
                    // 从JSON反序列化回Map，然后手动重建AssistantMessage对象
                    Map<String, Object> assistantData = objectMapper.readValue(chatMessage.getContent(), new TypeReference<>() {});
                    String text = (String) assistantData.get("text");
                    List<AssistantMessage.ToolCall> toolCalls = objectMapper.convertValue(assistantData.get("toolCalls"), new TypeReference<>() {});
                    return new AssistantMessage(text, Collections.emptyMap(), toolCalls);
                case TOOL:
                    // 从JSON反序列化回Map，然后手动重建ToolResponseMessage对象
                    Map<String, Object> toolData = objectMapper.readValue(chatMessage.getContent(), new TypeReference<>() {});
                    List<ToolResponseMessage.ToolResponse> responses = objectMapper.convertValue(toolData.get("responses"), new TypeReference<>() {});
                    return new ToolResponseMessage(responses);
                default:
                    log.warn("未知的消息类型: {}，将作为助手消息处理。", chatMessage.getMessageType());
                    return new AssistantMessage(chatMessage.getContent());
            }
        } catch (Exception e) {
            // 如果JSON解析失败（例如处理旧的纯文本数据），则回退到简单文本模式
            log.error("将ChatMessage转换为Spring AI Message失败，回退到纯文本模式。内容: {}", chatMessage.getContent(), e);
            return new AssistantMessage(chatMessage.getContent());
        }
    }
}