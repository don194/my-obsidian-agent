package io.github.don194.obsidianagent.repository;

import io.github.don194.obsidianagent.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 聊天消息仓库
 */
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * 查询指定会话的最新N条消息
     */
    List<ChatMessage> findBySessionIdOrderByCreatedAtDesc(String sessionId, Pageable pageable);

    /**
     * 查询指定会话的所有消息（按时间正序）
     */
    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    /**
     * 删除指定会话的所有消息
     */
    void deleteBySessionId(String sessionId);

    /**
     * 统计指定会话的消息数量
     */
    long countBySessionId(String sessionId);

    /**
     * 全文搜索消息内容
     */
    @Query("SELECT m FROM ChatMessage m WHERE m.content LIKE %:keyword% ORDER BY m.createdAt DESC")
    List<ChatMessage> searchByContent(String keyword, Pageable pageable);

    /**
     * 搜索指定会话中的消息
     */
    @Query("SELECT m FROM ChatMessage m WHERE m.sessionId = :sessionId AND m.content LIKE %:keyword% ORDER BY m.createdAt DESC")
    List<ChatMessage> searchBySessionIdAndContent(String sessionId, String keyword, Pageable pageable);
}