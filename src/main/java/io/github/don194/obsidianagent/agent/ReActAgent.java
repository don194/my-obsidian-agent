package io.github.don194.obsidianagent.agent;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * ReAct (Reasoning and Acting) 模式的代理抽象类
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public abstract class ReActAgent extends BaseAgent {

    // 当前步骤的用户消息 - 用于在执行步骤中传递给ChatClient
    private String currentUserMessage;

    /**
     * 重写run方法，保存用户消息但不手动管理消息列表
     */
    @Override
    public String run(String userPrompt) {
        // 保存用户消息，供后续步骤使用
        this.currentUserMessage = userPrompt;

        // 调用父类的run方法
        return super.run(userPrompt);
    }
    @Override
    public SseEmitter runStream(String userPrompt) {
        // 保存用户消息，供后续步骤使用
        this.currentUserMessage = userPrompt;

        // 调用父类的runStream方法
        return super.runStream(userPrompt);
    }

    /**
     * 处理当前状态并决定下一步行动
     *
     * @return 是否需要执行行动，true表示需要执行，false表示不需要执行
     */
    public abstract boolean think();

    /**
     * 执行决定的行动
     *
     * @return 行动执行结果
     */
    public abstract String act();

    /**
     * 执行单个步骤：思考和行动
     * 重构版本：不再手动管理消息
     *
     * @return 步骤执行结果
     */
    @Override
    public String step() {
        try {
            // 先思考
            log.info("Agent 开始思考...");
            boolean shouldAct = think();
            if (!shouldAct) {
                return "思考完成 - 无需行动";
            }
            // 再行动
            return act();
        } catch (Exception e) {
            // 记录异常日志
            e.printStackTrace();
            return "步骤执行失败：" + e.getMessage();
        }
    }

    /**
     * 获取当前应该发送给ChatClient的用户消息
     * 第一步使用原始用户消息，后续步骤使用继续提示
     */
    protected String getCurrentStepUserMessage() {
        return (getCurrentStep() == 1) ? currentUserMessage : getNextStepPrompt();
    }

    /**
     * 构建当前步骤的系统提示词
     * 简化版本：始终返回基础系统提示，不再动态修改
     */
    protected String getCurrentStepSystemPrompt() {
        return getSystemPrompt();
    }

    /**
     * 重写cleanup方法
     */
    @Override
    protected void cleanup() {
        super.cleanup();
        this.currentUserMessage = null;
    }
}