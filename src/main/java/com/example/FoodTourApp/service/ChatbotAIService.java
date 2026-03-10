package com.example.FoodTourApp.service;

public interface ChatbotAIService {
    String generateReply(Long conversationId, String userMessage);
    void removeConversationMemory(Long conversationId);
}