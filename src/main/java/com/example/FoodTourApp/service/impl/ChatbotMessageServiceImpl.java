package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.DTO.ChatbotDTO.ChatbotMessageResponse;
import com.example.FoodTourApp.DTO.ChatbotDTO.SendChatbotMessageRequest;
import com.example.FoodTourApp.entity.ChatbotConversation;
import com.example.FoodTourApp.entity.ChatbotMessage;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.repository.ChatbotConversationRepository;
import com.example.FoodTourApp.repository.ChatbotMessageRepository;
import com.example.FoodTourApp.service.ChatbotMessageService;
import com.example.FoodTourApp.service.ChatbotAIService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatbotMessageServiceImpl implements ChatbotMessageService {

    private final ChatbotConversationRepository conversationRepo;
    private final ChatbotMessageRepository messageRepo;
    private final ChatbotAIService chatbotAIService;

    @Autowired
    public ChatbotMessageServiceImpl(ChatbotConversationRepository conversationRepo,
                                     ChatbotMessageRepository messageRepo,
                                     ChatbotAIService chatbotAIService) {
        this.conversationRepo = conversationRepo;
        this.messageRepo = messageRepo;
        this.chatbotAIService = chatbotAIService;
    }

    @Override
    @Transactional
    public ChatbotMessageResponse sendMessage(User user, Long conversationId, SendChatbotMessageRequest request) {
        ChatbotConversation conv = conversationRepo.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));
        if (!conv.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Not authorized");
        }

        String userName = user.getFullName() != null ? user.getFullName() : user.getUsername();

        // Lưu tin user
        ChatbotMessage userMsg = new ChatbotMessage();
        userMsg.setConversation(conv);
        userMsg.setSenderType("USER");
        userMsg.setSenderName(userName);
        userMsg.setContent(request.getContent());
        messageRepo.save(userMsg);
        conv.addMessage(userMsg);

        // Gọi AI (RAG) — truyền conversationId để memory độc lập per conversation
        String botReply = chatbotAIService.generateReply(conversationId, request.getContent());

        // Lưu tin bot
        ChatbotMessage botMsg = new ChatbotMessage();
        botMsg.setConversation(conv);
        botMsg.setSenderType("BOT");
        botMsg.setSenderName("FoodTour Bot");
        botMsg.setContent(botReply);
        messageRepo.save(botMsg);
        conv.addMessage(botMsg);

        ChatbotMessageResponse response = new ChatbotMessageResponse();
        response.setId(botMsg.getId());
        response.setSenderType("BOT");
        response.setSenderName("FoodTour Bot");
        response.setContent(botReply);
        response.setCreatedAt(botMsg.getCreatedAt());
        return response;
    }

    @Override
    public List<ChatbotMessageResponse> getConversationMessages(Long conversationId, User user) {
        ChatbotConversation conv = conversationRepo.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Not found"));
        if (!conv.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Not authorized");
        }

        return conv.getMessages().stream()
                .map(msg -> {
                    ChatbotMessageResponse response = new ChatbotMessageResponse();
                    response.setId(msg.getId());
                    response.setSenderType(msg.getSenderType());
                    response.setSenderName(msg.getSenderName());
                    response.setContent(msg.getContent());
                    response.setCreatedAt(msg.getCreatedAt());
                    return response;
                }).collect(Collectors.toList());
    }
}
