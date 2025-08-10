package io.github.don194.obsidianagent.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 聊天会话实体
 */
@Entity
@Table(name = "chat_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ChatSession {

    @Id
    @Column(name = "session_id", length = 36)
    private String sessionId;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "created_at")
    @CreatedDate
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Column(name = "message_count")
    private Integer messageCount = 0;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ChatMessage> messages = new ArrayList<>();

    public ChatSession(String sessionId) {
        this.sessionId = sessionId;
        this.title = "";
        this.messageCount = 0;
    }

    public ChatSession(String sessionId, String title) {
        this.sessionId = sessionId;
        this.title = title;
        this.messageCount = 0;
    }
}