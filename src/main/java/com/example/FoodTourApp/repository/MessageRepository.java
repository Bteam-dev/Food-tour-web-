package com.example.FoodTourApp.repository;

import com.example.FoodTourApp.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * Lấy N tin nhắn MỚI NHẤT – dùng khi mở chat lần đầu.
     * ORDER BY id DESC → lấy mới nhất trước, service sẽ reverse lại thành ASC.
     */
    @Query("SELECT m FROM Message m WHERE m.conversation.id = :conversationId ORDER BY m.id DESC")
    List<Message> findLatestMessages(@Param("conversationId") Long conversationId, Pageable pageable);

    /**
     * Cursor-based: lấy N tin nhắn CÓ ID < beforeMessageId (cũ hơn).
     * ORDER BY id DESC → service reverse lại thành ASC.
     */
    @Query("SELECT m FROM Message m WHERE m.conversation.id = :conversationId AND m.id < :beforeMessageId ORDER BY m.id DESC")
    List<Message> findByConversationIdBeforeId(@Param("conversationId") Long conversationId,
                                               @Param("beforeMessageId") Long beforeMessageId,
                                               Pageable pageable);

    @Query("SELECT m FROM Message m WHERE m.conversation.id = :conversationId " +
           "AND m.sender.id != :userId AND m.isRead = false")
    List<Message> findUnreadMessages(@Param("conversationId") Long conversationId,
                                     @Param("userId") Integer userId);

    @Query("SELECT COUNT(m) FROM Message m JOIN m.conversation c JOIN c.participants p " +
           "WHERE p.id = :userId AND m.sender.id != :userId AND m.isRead = false")
    Long countUnreadMessagesByUserId(@Param("userId") Integer userId);
}
