package com.example.FoodTourApp.repository;

import com.example.FoodTourApp.entity.ChatbotMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatbotMessageRepository extends JpaRepository<ChatbotMessage, Long> {
    List<ChatbotMessage> findByConversation_IdOrderByCreatedAtAsc(Long conversationId);
    void deleteByConversation_Id(Long conversationId);
}
