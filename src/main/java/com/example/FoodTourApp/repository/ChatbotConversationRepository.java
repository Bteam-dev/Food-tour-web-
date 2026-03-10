package com.example.FoodTourApp.repository;

import com.example.FoodTourApp.entity.ChatbotConversation;
import com.example.FoodTourApp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatbotConversationRepository extends JpaRepository<ChatbotConversation, Long> {
    List<ChatbotConversation> findByUserOrderByUpdatedAtDesc(User user);
}
