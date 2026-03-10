package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.DTO.ChatbotDTO.ChatbotConversationResponse;
import com.example.FoodTourApp.DTO.ChatbotDTO.CreateChatbotConversationRequest;
import com.example.FoodTourApp.DTO.ChatbotDTO.RenameChatbotConversationRequest;
import com.example.FoodTourApp.entity.ChatbotConversation;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.repository.ChatbotConversationRepository;
import com.example.FoodTourApp.service.ChatbotConversationService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatbotConversationServiceImpl implements ChatbotConversationService {

    private final ChatbotConversationRepository conversationRepo;

    public ChatbotConversationServiceImpl(ChatbotConversationRepository conversationRepo) {
        this.conversationRepo = conversationRepo;
    }

    @Override
    @Transactional
    public ChatbotConversationResponse createConversation(User user, CreateChatbotConversationRequest request) {
        ChatbotConversation conv = new ChatbotConversation();
        conv.setUser(user);
        conv.setTitle(request.getTitle() != null ? request.getTitle() : "New Chat");
        conv = conversationRepo.save(conv);
        return mapToResponse(conv);
    }

    @Override
    public List<ChatbotConversationResponse> getUserConversations(User user) {
        return conversationRepo.findByUserOrderByUpdatedAtDesc(user).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ChatbotConversationResponse renameConversation(Long conversationId, RenameChatbotConversationRequest request, User user) {
        ChatbotConversation conv = conversationRepo.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));
        if (!conv.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Not authorized");
        }
        conv.setTitle(request.getTitle());
        conv = conversationRepo.save(conv);
        return mapToResponse(conv);
    }

    @Override
    @Transactional
    public void deleteConversation(Long conversationId, User user) {
        ChatbotConversation conv = conversationRepo.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Not found"));
        if (!conv.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Not authorized");
        }
        conversationRepo.delete(conv);
    }

    private ChatbotConversationResponse mapToResponse(ChatbotConversation conv) {
        ChatbotConversationResponse response = new ChatbotConversationResponse();
        response.setId(conv.getId());
        response.setTitle(conv.getTitle());
        response.setCreatedAt(conv.getCreatedAt());
        response.setUpdatedAt(conv.getUpdatedAt());
        return response;
    }
}