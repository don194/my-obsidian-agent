package io.github.don194.obsidianagent.obsidian;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ObsidianApiClient 的集成测试套件。
 * <p>
 * !!! 警告 !!!
 * 本测试将在你的Obsidian仓库中真实地创建、修改和删除一个名为 "AGENT_TEST_NOTE.md" 的文件。
 * 请确保在运行前备份重要数据，或在一个测试用的仓库中运行此测试。
 * <p>
 * 测试顺序:
 * 1. 测试连接和基本信息获取。
 * 2. 创建一个测试笔记。
 * 3. 读取并验证笔记内容。
 * 4. 向笔记追加内容并验证。
 * 5. 列出所有文件并确认测试文件存在。
 * 6. 搜索测试文件。
 * 7. 删除测试文件。
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("dev") // 确保使用dev配置，避免加载生产环境配置
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ObsidianApiClientTest {

    @Autowired
    private ObsidianApiClient apiClient;

    private static final String TEST_NOTE_PATH = "AGENT_TEST_NOTE.md";
    private static final String INITIAL_CONTENT = "# Agent Test Note\n\nThis is a test note created by ObsidianApiClientTest.";
    private static final String APPEND_CONTENT = "\n\nThis is appended content.";

    /**
     * 在所有测试结束后，执行一次清理操作，确保测试文件被删除。
     */
    @AfterAll
    static void cleanupAfterAll(@Autowired ObsidianApiClient client) {
        try {
            log.info("--- 最终清理 ---");
            client.deleteNote(TEST_NOTE_PATH);
            log.info("成功删除测试笔记: {}", TEST_NOTE_PATH);
        } catch (Exception e) {
            // 如果文件不存在，删除会失败，这是正常的。
            log.warn("最终清理失败或文件已不存在: {}", e.getMessage());
        }
    }

    @Test
    @Order(1)
    @DisplayName("1. 测试连接和获取系统信息")
    void test_getConnectionAndSystemInfo() {
        log.info("--- 开始测试: 1. 连接与系统信息 ---");
        Map<String, Object> systemInfo = apiClient.getSystemInfo();
        assertNotNull(systemInfo, "系统信息不应为null");

        // 增加日志，打印出从API收到的完整响应，方便调试
        log.info("从Obsidian API收到的系统信息: {}", systemInfo);

        // 根据官方API文档更新断言
        //assertTrue((Boolean) systemInfo.getOrDefault("authenticated", true), "API响应应显示为 'authenticated: true'");
        assertTrue(systemInfo.containsKey("versions"), "响应中应包含 'versions' 对象");
        log.info("成功获取系统信息");
        log.info("--- 测试结束: 1. 连接与系统信息 ---");
    }

    @Test
    @Order(2)
    @DisplayName("2. 测试创建笔记")
    void test_createNote() {
        log.info("--- 开始测试: 2. 创建笔记 ---");
        assertDoesNotThrow(() -> {
            apiClient.createOrUpdateNote(TEST_NOTE_PATH, INITIAL_CONTENT);
        }, "创建笔记不应抛出异常");
        log.info("成功发送创建笔记请求: {}", TEST_NOTE_PATH);
        log.info("--- 测试结束: 2. 创建笔记 ---");
    }

    @Test
    @Order(3)
    @DisplayName("3. 测试读取笔记内容")
    void test_readNote() {
        log.info("--- 开始测试: 3. 读取笔记 ---");
        String content = apiClient.readNoteContent(TEST_NOTE_PATH);
        assertNotNull(content, "读取的内容不应为null");
        assertEquals(INITIAL_CONTENT, content.trim(), "读取的内容应与初始内容匹配");
        log.info("成功读取并验证笔记内容");
        log.info("--- 测试结束: 3. 读取笔记 ---");
    }

    @Test
    @Order(4)
    @DisplayName("4. 测试向笔记追加内容")
    void test_appendToNote() {
        log.info("--- 开始测试: 4. 追加内容 ---");
        assertDoesNotThrow(() -> {
            apiClient.appendToNote(TEST_NOTE_PATH, APPEND_CONTENT);
        }, "追加内容不应抛出异常");

        String updatedContent = apiClient.readNoteContent(TEST_NOTE_PATH);
        String expectedContent = INITIAL_CONTENT+ APPEND_CONTENT;
        log.info("更新后的内容: {}", updatedContent);
        assertEquals(expectedContent, updatedContent.trim(), "追加后的内容应正确");
        log.info("成功追加并验证内容");
        log.info("--- 测试结束: 4. 追加内容 ---");
    }

    @Test
    @Order(5)
    @DisplayName("5. 测试列出所有文件")
    void test_listAllFiles() {
        log.info("--- 开始测试: 5. 列出文件 ---");
        List<String> allFiles = apiClient.listAllMarkdownFiles();
        assertNotNull(allFiles, "文件列表不应为null");
        assertTrue(allFiles.contains(TEST_NOTE_PATH), "文件列表中应包含测试笔记");
        log.info("成功在文件列表中找到测试笔记");
        log.info("--- 测试结束: 5. 列出文件 ---");
    }

//    @Test
//    @Order(6)
//    @DisplayName("6. 测试搜索笔记")
//    void test_searchNote() {
//        log.info("--- 开始测试: 6. 搜索笔记 ---");
//        List<ObsidianApiClient.SearchResult> results = apiClient.searchNotes("Agent Test Note");
//        assertNotNull(results, "搜索结果不应为null");
//        assertFalse(results.isEmpty(), "搜索结果不应为空");
//        assertTrue(results.stream().anyMatch(r -> TEST_NOTE_PATH.equals(r.getPath())), "搜索结果中应包含测试笔记");
//        log.info("成功搜索到测试笔记");
//        log.info("--- 测试结束: 6. 搜索笔记 ---");
//    }

    @Test
    @Order(7)
    @DisplayName("7. 测试删除笔记")
    void test_deleteNote() {
        log.info("--- 开始测试: 7. 删除笔记 ---");
        assertDoesNotThrow(() -> {
            apiClient.deleteNote(TEST_NOTE_PATH);
        }, "删除笔记不应抛出异常");
        log.info("成功发送删除笔记请求");

        // 验证文件是否真的被删除
        assertThrows(RuntimeException.class, () -> {
            apiClient.readNoteContent(TEST_NOTE_PATH);
        }, "读取已删除的笔记应抛出异常");
        log.info("确认笔记已被删除");
        log.info("--- 测试结束: 7. 删除笔记 ---");
    }
}

