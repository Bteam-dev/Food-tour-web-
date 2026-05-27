package com.example.FoodTourApp.service;

import com.example.FoodTourApp.DTO.ChatbotDTO.ChatbotConversationResponse;
import com.example.FoodTourApp.DTO.ChatbotDTO.CreateChatbotConversationRequest;
import com.example.FoodTourApp.DTO.ChatbotDTO.RenameChatbotConversationRequest;
import com.example.FoodTourApp.entity.User;

import java.util.List;

public interface ChatbotConversationService {
    ChatbotConversationResponse createConversation(User user, CreateChatbotConversationRequest request);
    List<ChatbotConversationResponse> getUserConversations(User user);
    ChatbotConversationResponse renameConversation(Long conversationId, RenameChatbotConversationRequest request, User user);    void deleteConversation(Long conversationId, User user);
}
