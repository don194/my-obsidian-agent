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

    private String lastActionResult;
    /**
     * 重写run方法，保存用户消息但不手动管理消息列表
     */
    @Override
    public String run(String userPrompt) {
        // 调用父类的run方法
        return super.run(userPrompt);
    }
    @Override
    public SseEmitter runStream(String userPrompt) {

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
            String actionResult = act();

            // 关键：将行动结果保存，用作下一步的系统消息
            this.lastActionResult = actionResult;
            return actionResult;
        } catch (Exception e) {
            // 记录异常日志
            e.printStackTrace();
            return "步骤执行失败：" + e.getMessage();
        }
    }

    /**
     * 重写cleanup方法
     */
    @Override
    protected void cleanup() {
        super.cleanup();
    }
}