package io.github.don194.obsidianagent.memory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.*;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话记忆管理器 - 管理内存中的工作记忆和持久化存储的协调
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationMemoryManager {

    private final SqliteChatMemory persistentMemory;

    // 会话ID -> 工作记忆的映射
    private final Map<String, List<Message>> workingMemoryMap = new ConcurrentHashMap<>();

    /**
     * 初始化或获取会话的工作记忆
     */
    public List<Message> getOrCreateWorkingMemory(String sessionId) {
        return workingMemoryMap.computeIfAbsent(sessionId, id -> {
            // 从持久化存储加载历史消息
            List<Message> history = persistentMemory.get(id, 50);
            log.info("Loaded {} messages from persistent memory for session {}",
                    history.size(), id);
            return new ArrayList<>(history);
        });
    }

    /**
     * 添加用户消息
     */
    public void addUserMessage(String sessionId, String content) {
        Message userMsg = new UserMessage(content);

        // 添加到工作记忆
        List<Message> workingMemory = getOrCreateWorkingMemory(sessionId);
        workingMemory.add(userMsg);

        // 持久化
        persistentMemory.add(sessionId, Arrays.asList(userMsg));

        log.debug("Added user message to session {}", sessionId);
    }

    /**
     * 添加助手消息（包含工具调用）
     */
    public void addAssistantMessage(String sessionId, String content,
                                    List<AssistantMessage.ToolCall> toolCalls) {
        AssistantMessage assistantMsg = new AssistantMessage(content,
                Collections.emptyMap(), toolCalls);

        // 添加到工作记忆
        List<Message> workingMemory = getOrCreateWorkingMemory(sessionId);
        workingMemory.add(assistantMsg);

        // 持久化
        persistentMemory.add(sessionId, Arrays.asList(assistantMsg));

        log.debug("Added assistant message with {} tool calls to session {}",
                toolCalls != null ? toolCalls.size() : 0, sessionId);
    }

    /**
     * 添加工具执行结果列表 - 这是关键！
     */
    public void addToolResponses(String sessionId, List<ToolResponseMessage.ToolResponse> toolResponses) {
        if (toolResponses == null || toolResponses.isEmpty()) {
            return;
        }
        // 创建包含所有工具结果的单个消息
        ToolResponseMessage toolMsg = new ToolResponseMessage(toolResponses);

        // 添加到工作记忆
        List<Message> workingMemory = getOrCreateWorkingMemory(sessionId);
        workingMemory.add(toolMsg);

        // 持久化
        persistentMemory.add(sessionId, List.of(toolMsg));

        log.debug("Added {} tool responses to session {}", toolResponses.size(), sessionId);
    }

    /**
     * 获取完整的消息历史用于发送给LLM
     */
    public List<Message> getConversationHistory(String sessionId) {
        List<Message> workingMemory = getOrCreateWorkingMemory(sessionId);

        // 验证消息链完整性
        validateMessageChain(workingMemory);

        // 如果消息太多，进行窗口管理
        if (workingMemory.size() > 100) {
            return truncateMessages(workingMemory);
        }

        return new ArrayList<>(workingMemory);
    }

    /**
     * 验证消息链完整性
     */
    private void validateMessageChain(List<Message> messages) {
        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);

            if (msg instanceof AssistantMessage) {
                AssistantMessage assistantMsg = (AssistantMessage) msg;
                if (assistantMsg.getToolCalls() != null && !assistantMsg.getToolCalls().isEmpty()) {
                    // 检查是否有对应的工具响应
                    boolean hasToolResponse = false;
                    for (int j = i + 1; j < messages.size(); j++) {
                        if (messages.get(j) instanceof ToolResponseMessage) {
                            hasToolResponse = true;
                            break;
                        }
                    }

                    if (!hasToolResponse) {
                        log.warn("Missing tool response after assistant message with tool calls at index {}", i);
                    }
                }
            }
        }
    }

    /**
     * 截断消息保持在合理范围内
     */
    private List<Message> truncateMessages(List<Message> messages) {
        List<Message> truncated = new ArrayList<>();

        // 保留系统消息（如果有）
        if (!messages.isEmpty() && messages.get(0) instanceof SystemMessage) {
            truncated.add(messages.get(0));
        }

        // 保留最近的50条消息
        int startIndex = Math.max(0, messages.size() - 50);
        truncated.addAll(messages.subList(startIndex, messages.size()));

        log.info("Truncated messages from {} to {}", messages.size(), truncated.size());
        return truncated;
    }

    /**
     * 清理会话的工作记忆
     */
    public void clearWorkingMemory(String sessionId) {
        workingMemoryMap.remove(sessionId);
        log.info("Cleared working memory for session {}", sessionId);
    }
}