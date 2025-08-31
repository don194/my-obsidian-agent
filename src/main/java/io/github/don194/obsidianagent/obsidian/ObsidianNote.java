package io.github.don194.obsidianagent.obsidian;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代表一个Obsidian笔记的数据传输对象 (DTO)。
 * 包含笔记的路径和内容。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ObsidianNote {

    /**
     * 笔记在仓库中的相对路径。
     * 例如："Notes/My Note.md"
     */
    private String path;

    /**
     * 笔记的Markdown原始内容。
     */
    private String content;
}

