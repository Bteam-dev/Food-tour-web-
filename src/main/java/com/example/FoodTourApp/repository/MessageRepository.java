package com.example.FoodTourApp.repository;

import com.example.FoodTourApp.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    Page<Message> findByConversationIdOrderByCreatedAtDesc(Long conversationId, Pageable pageable);

    @Query("SELECT m FROM Message m WHERE m.conversation.id = :conversationId " +
           "AND m.sender.id != :userId AND m.isRead = false")
    List<Message> findUnreadMessages(@Param("conversationId") Long conversationId,
                                     @Param("userId") Integer userId);

    @Query("SELECT COUNT(m) FROM Message m JOIN m.conversation c JOIN c.participants p " +
           "WHERE p.id = :userId AND m.sender.id != :userId AND m.isRead = false")
    Long countUnreadMessagesByUserId(@Param("userId") Integer userId);
}

