package io.github.don194.obsidianagent.agent;

import cn.hutool.core.util.StrUtil;
import io.github.don194.obsidianagent.model.AgentState;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 处理工具调用的基础代理类，具体实现了 think 和 act 方法
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ToolCallAgent extends ReActAgent {

    // 可用的工具
    private final ToolCallback[] availableTools;

    // 保存工具调用信息的响应结果（要调用那些工具）
    private ChatResponse toolCallChatResponse;

    // 工具调用选项
    private final ToolCallingChatOptions chatOptions;

    public ToolCallAgent(ToolCallback[] availableTools) {
        super();
        this.availableTools = availableTools;
        this.chatOptions = ToolCallingChatOptions.builder()
                .toolCallbacks(availableTools)
                .internalToolExecutionEnabled(false)  // 禁用自动工具执行
                .build();
    }

    /**
     * 处理当前状态并决定下一步行动
     * @return 是否需要执行行动
     */
    @Override
    public boolean think() {
        try {
            // 使用父类提供的辅助方法获取系统提示和用户消息
            String systemPrompt = getSystemPrompt();

            List<Message> conversationHistory = getMemoryManager().getConversationHistory(getSessionId());
            //检查最后一个Message是不是user，如果不是增加
            Message message = conversationHistory.get(conversationHistory.size() - 1);
            if(message.getMessageType() != MessageType.USER) {
                Message nextStepMessage = new UserMessage(getNextStepPrompt());
                conversationHistory.add(nextStepMessage);
            }

            ChatResponse chatResponse = getChatClient()
                    .prompt()
                    .system(systemPrompt)
                    .messages(conversationHistory)
                    .options(chatOptions)
                    .call()
                    .chatResponse();

            // 记录响应，用于后续 act() 方法
            this.toolCallChatResponse = chatResponse;

            // 解析工具调用结果
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            if (getMemoryManager() != null && getSessionId() != null) {
                getMemoryManager().addAssistantMessage(getSessionId(), assistantMessage.getText(), assistantMessage.getToolCalls());
            }
            List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();

            // 输出提示信息
            if (StrUtil.isNotBlank(assistantMessage.getText())) {
                log.info(getName() + "的思考：" + assistantMessage.getText());
            }

            if (!toolCallList.isEmpty()) {
                log.info(getName() + "选择了 " + toolCallList.size() + " 个工具来使用");
                String toolCallInfo = toolCallList.stream()
                        .map(toolCall -> String.format("工具名称：%s，参数：%s", toolCall.name(), toolCall.arguments()))
                        .collect(Collectors.joining("\n"));
                log.info(toolCallInfo);
            }

            // 返回是否需要执行工具
            return !toolCallList.isEmpty();

        } catch (Exception e) {
            log.error(getName() + "的思考过程遇到了问题：" + e.getMessage(), e);
            return false;
        }
    }

    /**
     * 执行工具调用并处理结果
     * @return 执行结果
     */
    @Override
    public String act() {
        try {
            AssistantMessage assistantMessage = toolCallChatResponse.getResult().getOutput();
            List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();

            if (toolCalls.isEmpty()) {
                return "没有工具需要执行";
            }


            // 1. 执行所有工具调用，并收集结果
            List<ToolResponseMessage.ToolResponse> toolResponses = toolCalls.stream().map(toolCall -> {
                log.info("Executing tool: {} with args: {}", toolCall.name(), toolCall.arguments());
                String result;
                try {
                    ToolCallback tool = findTool(toolCall.name());
                    if (tool != null) {
                        result = tool.call(toolCall.arguments());
                        // 检查是否调用了终止工具
                        if ("doTerminate".equals(toolCall.name())) {
                            setState(AgentState.FINISHED);
                        }
                    } else {
                        result = "Error: Tool not found: " + toolCall.name();
                        log.warn(result);
                    }
                } catch (Exception e) {
                    log.error("Error executing tool {}", toolCall.name(), e);
                    result = "Error: " + e.getMessage();
                }
                return new ToolResponseMessage.ToolResponse(toolCall.id(), toolCall.name(), result);
            }).toList();

            getMemoryManager().addToolResponses(getSessionId(), toolResponses);
            String finalResults = toolResponses.stream()
                    .map(tr -> "工具 " + tr.name() + " 返回的结果：" + tr.responseData())
                    .collect(Collectors.joining("\n"));

            log.info("工具执行结果：\n" + finalResults);

            return finalResults;

        } catch (Exception e) {
            log.error("执行工具调用时出错", e);
            return "工具执行失败：" + e.getMessage();
        }
    }

    /**
     * 查找指定名称的工具
     */
    private ToolCallback findTool(String toolName) {
        for (ToolCallback tool : availableTools) {
            if (tool.getToolDefinition().name().equals(toolName)) {
                return tool;
            }
        }
        return null;
    }

    /**
     * 重写cleanup方法
     */
    @Override
    protected void cleanup() {
        super.cleanup();
        this.toolCallChatResponse = null;
    }
}