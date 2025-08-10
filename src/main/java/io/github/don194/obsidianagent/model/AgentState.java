package io.github.don194.obsidianagent.model;

/**
 * Agent状态枚举
 *
 * 定义Agent在执行过程中的各种状态
 */
public enum AgentState {

    /**
     * 空闲状态 - Agent准备接收新任务
     */
    IDLE("空闲"),

    /**
     * 运行状态 - Agent正在执行任务
     */
    RUNNING("运行中"),

    /**
     * 完成状态 - Agent已完成当前任务
     */
    FINISHED("已完成"),

    /**
     * 错误状态 - Agent执行过程中出现错误
     */
    ERROR("错误");

    private final String description;

    AgentState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return description;
    }
}