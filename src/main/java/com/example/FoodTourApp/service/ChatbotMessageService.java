package com.example.FoodTourApp.service;

import com.example.FoodTourApp.DTO.ChatbotDTO.ChatbotMessageResponse;
import com.example.FoodTourApp.DTO.ChatbotDTO.SendChatbotMessageRequest;
import com.example.FoodTourApp.entity.User;

import java.util.List;


public interface ChatbotMessageService {
    ChatbotMessageResponse sendMessage(User user, Long conversationId, SendChatbotMessageRequest request);
    List<ChatbotMessageResponse> getConversationMessages(Long conversationId, User user);
}
