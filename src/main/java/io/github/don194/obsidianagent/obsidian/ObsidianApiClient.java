package io.github.don194.obsidianagent.obsidian;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.don194.obsidianagent.config.ObsidianApiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Obsidian API 客户端
 * <p>
 * 负责与Obsidian Local REST API进行通信，提供笔记的增删改查、搜索等功能。
 */
@Slf4j
@Service
@EnableConfigurationProperties(ObsidianApiProperties.class)
public class ObsidianApiClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final HttpHeaders headers;
    private final HttpEntity<String> httpEntity; // 创建一个可重用的HttpEntity

    public ObsidianApiClient(ObsidianApiProperties properties) {
        this.restTemplate = new RestTemplate();
        this.baseUrl = Optional.ofNullable(properties.getBaseUrl())
                .filter(url -> !url.isBlank())
                .orElseThrow(() -> new IllegalArgumentException("Obsidian API base URL must be configured in application.yml"));

        this.headers = new HttpHeaders();
        headers.setBearerAuth(properties.getToken());
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN));

        // 创建一个包含headers的HttpEntity，用于无body的GET和DELETE请求
        this.httpEntity = new HttpEntity<>(headers);
    }

    // ===================================================================================
    // Public API Methods (公共API方法)
    // ===================================================================================

    /**
     * 获取Obsidian服务器的基本信息和认证状态。
     *
     * @return 包含服务器信息的Map
     */
    @SuppressWarnings("rawtypes")
    public Map<String, Object> getSystemInfo() {
        log.debug("获取Obsidian系统信息: {}", baseUrl);
        try {
            // 修正：使用exchange方法以确保发送认证头
            // 尽管 / 端点可能不需要认证，但为保持一致性，统一发送
            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl + "/",
                    HttpMethod.GET,
                    this.httpEntity,
                    Map.class
            );
            return response.getBody();
        } catch (Exception e) {
            log.error("获取Obsidian系统信息失败", e);
            throw new RuntimeException("无法连接到Obsidian API，请检查配置和服务器状态。", e);
        }
    }

    /**
     * 递归地列出仓库中所有的Markdown文件。
     *
     * @return Markdown文件的相对路径列表
     */
    public List<String> listAllMarkdownFiles() {
        log.info("从Obsidian仓库中递归获取所有Markdown文件...");
        List<String> markdownFiles = new ArrayList<>();
        recursivelyScanDirectory("", markdownFiles);
        log.info("成功获取到 {} 个Markdown文件。", markdownFiles.size());
        return markdownFiles;
    }


    /**
     * 读取指定路径笔记的完整内容。
     *
     * @param path 笔记的相对路径 (e.g., "Notes/My Note.md")
     * @return 笔记的文本内容
     */
    public String readNoteContent(String path) {
        log.debug("读取笔记内容: {}", path);
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + "/vault/{path}",
                    HttpMethod.GET,
                    this.httpEntity,
                    String.class,
                    path
            );
            return response.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("笔记未找到: {}", path);
            throw new RuntimeException("笔记 '" + path + "' 未找到。", e);
        } catch (Exception e) {
            log.error("读取笔记 '{}' 失败", path, e);
            throw new RuntimeException("读取笔记 '" + path + "' 失败。", e);
        }
    }

    /**
     * 创建一个新笔记或用新内容完全覆盖一个现有笔记。
     *
     * @param path    笔记的相对路径
     * @param content 要写入的完整内容
     */
    public void createOrUpdateNote(String path, String content) {
        log.info("创建或更新笔记: {}", path);
        try {
            // 对于写操作，需要一个包含body的HttpEntity
            HttpHeaders writeHeaders = new HttpHeaders(this.headers);
            writeHeaders.setContentType(MediaType.TEXT_PLAIN);
            HttpEntity<String> request = new HttpEntity<>(content, writeHeaders);

            restTemplate.exchange(
                    baseUrl + "/vault/{path}",
                    HttpMethod.PUT,
                    request,
                    Void.class,
                    path
            );
        } catch (Exception e) {
            log.error("创建或更新笔记 '{}' 失败", path, e);
            throw new RuntimeException("创建或更新笔记 '" + path + "' 失败。", e);
        }
    }

    /**
     * 向现有笔记追加内容。
     * <p>
     * 采用更稳健的“读取-修改-写入”策略，避免依赖可能存在歧义的追加API。
     *
     * @param path    笔记的相对路径
     * @param content 要追加的内容
     */
    public void appendToNote(String path, String content) {
        log.info("向笔记追加内容: {}", path);
        try {
            String existingContent = "";
            try {
                existingContent = readNoteContent(path);
            } catch (RuntimeException e) {
                // 如果笔记不存在，readNoteContent会抛出异常，这是正常情况
                log.info("笔记 '{}' 不存在，将创建新文件。", path);
            }

            String newContent = (existingContent == null ? "" : existingContent) + content;
            createOrUpdateNote(path, newContent);
        } catch (Exception e) {
            log.error("向笔记 '{}' 追加内容失败", path, e);
            throw new RuntimeException("向笔记 '" + path + "' 追加内容失败。", e);
        }
    }

    /**
     * 删除一个笔记。
     *
     * @param path 笔记的相对路径
     */
    public void deleteNote(String path) {
        log.info("删除笔记: {}", path);
        try {
            restTemplate.exchange(
                    baseUrl + "/vault/{path}",
                    HttpMethod.DELETE,
                    this.httpEntity,
                    Void.class,
                    path
            );
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("尝试删除的笔记 '{}' 已不存在", path);
        } catch (Exception e) {
            log.error("删除笔记 '{}' 失败", path, e);
            throw new RuntimeException("删除笔记 '" + path + "' 失败。", e);
        }
    }


    /**
     * 在仓库中搜索包含指定文本的笔记。
     *
     * @param query 搜索查询字符串
     * @return 匹配的笔记列表，包含路径和文件名
     */
    public List<SearchResult> searchNotes(String query) {
        log.info("在Obsidian仓库中搜索: '{}'", query);
        try {
            HttpHeaders searchHeaders = new HttpHeaders(this.headers);
            searchHeaders.setContentType(MediaType.APPLICATION_JSON);
            Map<String, String> requestBody = Map.of("query", query);
            HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, searchHeaders);

            ResponseEntity<SearchResult[]> response = restTemplate.exchange(
                    baseUrl + "/search/simple/",
                    HttpMethod.POST,
                    request,
                    SearchResult[].class
            );
            return response.getBody() != null ? Arrays.asList(response.getBody()) : Collections.emptyList();
        } catch (Exception e) {
            log.error("搜索Obsidian仓库失败", e);
            throw new RuntimeException("搜索Obsidian仓库失败。", e);
        }
    }

    /**
     * 请求在Obsidian UI中打开一个笔记。
     *
     * @param path 笔记的相对路径
     */
    public void openNoteInObsidian(String path) {
        log.info("请求在Obsidian中打开笔记: {}", path);
        try {
            // POST请求，body为空
            HttpEntity<String> request = new HttpEntity<>("", this.headers);
            restTemplate.exchange(
                    baseUrl + "/open/{path}",
                    HttpMethod.POST,
                    request,
                    Void.class,
                    path
            );
        } catch (Exception e) {
            log.error("请求打开笔记 '{}' 失败", path, e);
            log.warn("无法在Obsidian UI中打开笔记，但这不影响其他操作。");
        }
    }


    // ===================================================================================
    // Private Helper Methods (私有辅助方法)
    // ===================================================================================

    /**
     * 递归扫描指定目录，收集所有Markdown文件。
     *
     * @param directoryPath 要扫描的目录路径
     * @param allFiles      用于收集文件路径的列表
     */
    private void recursivelyScanDirectory(String directoryPath, List<String> allFiles) {
        try {
            String url = baseUrl + "/vault/" + (directoryPath.isEmpty() ? "" : directoryPath + "/");

            // 修正：使用exchange方法并传入认证头
            ResponseEntity<VaultListResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    this.httpEntity,
                    VaultListResponse.class
            );

            VaultListResponse responseBody = response.getBody();
            if (responseBody != null && responseBody.getFiles() != null) {
                for (String itemPath : responseBody.getFiles()) {
                    String fullPath = directoryPath.isEmpty() ? itemPath : directoryPath + "/" + itemPath;

                    if (itemPath.endsWith("/")) {
                        String subDirectory = fullPath.substring(0, fullPath.length() - 1);
                        recursivelyScanDirectory(subDirectory, allFiles);
                    } else if (itemPath.toLowerCase().endsWith(".md")) {
                        allFiles.add(fullPath);
                    }
                }
            }
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("目录 '{}' 未找到或为空，跳过扫描。", directoryPath);
        } catch (Exception e) {
            log.error("扫描目录 '{}' 失败", directoryPath, e);
        }
    }

    // ===================================================================================
    // Inner DTO Classes (内部DTO类)
    // ===================================================================================

    /**
     * 用于反序列化 `/vault/{directory}` API响应的内部类。
     */
    private static class VaultListResponse {
        private List<String> files;

        public List<String> getFiles() {
            return files;
        }

        public void setFiles(List<String> files) {
            this.files = files;
        }
    }


    /**
     * 用于反序列化搜索结果的DTO。
     */
    public static class SearchResult {
        @JsonProperty("path")
        private String path;
        @JsonProperty("filename")
        private String filename;

        // Getters and Setters
        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getFilename() {
            return filename;
        }

        public void setFilename(String filename) {
            this.filename = filename;
        }

        @Override
        public String toString() {
            return "SearchResult{" +
                    "path='" + path + '\'' +
                    ", filename='" + filename + '\'' +
                    '}';
        }
    }
}

