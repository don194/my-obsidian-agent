package io.github.don194.obsidianagent.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import io.github.don194.obsidianagent.agent.ToolCallAgent;

/**
 * ## Obsidian 代理
 *
 * 一个专门用于与 Obsidian 仓库进行交互的智能代理。
 *
 * ### 核心能力:
 * - **笔记管理:** 创建、读取、更新和搜索笔记。
 * - **任务自动化:** 在笔记中管理待办事项列表和跟踪任务。
 * - **知识组织:** 根据用户请求智能地链接笔记和组织信息。
 * - **ReAct 模式:** 利用思考-行动（Think-Act）循环来推理用户请求，并选择合适的 Obsidian 工具来完成任务。
 *
 * 该代理继承自 `ToolCallAgent`，使其能够动态使用一组提供的工具（例如 `createNoteTool`, `findNoteTool`）来在仓库中执行操作。
 */
@Component
public class ObsidianAgent extends ToolCallAgent {

    /**
     * 构造 ObsidianAgent。
     *
     * @param allTools 一组 `ToolCallback` 函数
     * @param chatModel 为代理的推理能力提供支持的底层 AI 聊天模型。
     */
    public ObsidianAgent(ToolCallback[] allTools, ChatModel chatModel) {
        // 调用 ToolCallAgent 的父类构造函数
        super(allTools);

        // 设置代理的名称，用于日志记录和识别
        this.setName("ObsidianAgent");

        // 定义代理的角色和核心目标。
        final String SYSTEM_PROMPT = """
                你是一个名为 "ObsidianAgent" 的专业 AI 助手，与 Obsidian 笔记应用集成。
                你的主要目的是帮助用户在他们的仓库中高效地管理知识库、笔记和任务。
                你是在创建、查找和组织信息方面的专家。

                工作流程：
                - 根据用户的请求，分析用户的意图和需求，一步一步解决问题
                - 如果需要操作 Obsidian，从你的可用函数中确定要使用的最佳工具
                - 在每次工具执行后，分析结果并决定下一步行动
                
                如果完成了用户请求，请调用务必调用doTerminate 工具结束对话。
                """;
        this.setSystemPrompt(SYSTEM_PROMPT);

        // 指导代理在每个步骤后如何继续。
        final String NEXT_STEP_PROMPT = """
                请基于之前的对话历史和结果继续执行下一步。
                """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);

        // 设置一个限制以防止无限循环
        this.setMaxSteps(5);

        // 使用指定的模型和自定义记录器构建 ChatClient
        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
        this.setChatClient(chatClient);
    }
}