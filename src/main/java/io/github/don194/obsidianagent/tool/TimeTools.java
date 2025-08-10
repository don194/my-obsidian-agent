package io.github.don194.obsidianagent.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 时间查询工具类 - 使用@Tool注解
 */
@Slf4j
@Component
public class TimeTools {

    /**
     * 获取当前时间信息
     */
    @Tool(description = "获取当前的日期和时间信息，包括年月日、时分秒和星期几")
    public String getCurrentDateTime() {
        log.info("时间工具被调用");

        try {
            LocalDateTime now = LocalDateTime.now();

            String currentTime = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            String date = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String dayOfWeek = now.format(DateTimeFormatter.ofPattern("E"));

            String result = String.format("现在是 %s %s %s", date, currentTime, dayOfWeek);

            log.info("时间查询完成: {}", result);
            return result;

        } catch (Exception e) {
            log.error("获取时间信息时出错", e);
            return "获取时间失败：" + e.getMessage();
        }
    }

    /**
     * 获取格式化的当前时间
     */
    @Tool(description = "获取指定格式的当前时间，支持中文格式、ISO格式等")
    public String getFormattedDateTime(
            @ToolParam(description = "时间格式：chinese(中文), iso(ISO格式), simple(简单格式)")
            String format) {

        log.info("格式化时间工具被调用，格式: {}", format);

        try {
            LocalDateTime now = LocalDateTime.now();

            String result = switch (format.toLowerCase().trim()) {
                case "chinese" -> now.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH点mm分ss秒 E"));
                case "iso" -> now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                case "simple" -> now.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
                default -> now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss E"));
            };

            log.info("格式化时间完成: {}", result);
            return result;

        } catch (Exception e) {
            log.error("格式化时间时出错", e);
            return "获取格式化时间失败：" + e.getMessage();
        }
    }
}