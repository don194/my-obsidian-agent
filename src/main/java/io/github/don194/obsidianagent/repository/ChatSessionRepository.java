package io.github.don194.obsidianagent.repository;

import io.github.don194.obsidianagent.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 聊天会话仓库
 */
@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, String> {

    /**
     * 按更新时间倒序查询所有会话
     */
    List<ChatSession> findAllByOrderByUpdatedAtDesc();

    /**
     * 查询包含指定关键词的会话
     */
    @Query("SELECT s FROM ChatSession s WHERE s.title LIKE %:keyword% ORDER BY s.updatedAt DESC")
    List<ChatSession> findByTitleContaining(String keyword);
}
