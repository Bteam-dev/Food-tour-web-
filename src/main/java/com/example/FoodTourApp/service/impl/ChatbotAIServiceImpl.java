package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.entity.ChatbotMessage;
import com.example.FoodTourApp.repository.ChatbotMessageRepository;
import com.example.FoodTourApp.service.ChatbotAI;
import com.example.FoodTourApp.service.ChatbotAIService;
import com.example.FoodTourApp.service.FoodVectorService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class ChatbotAIServiceImpl implements ChatbotAIService {

    private final ChatLanguageModel chatModel;
    private final FoodVectorService vectorService;
    private final ChatbotMessageRepository messageRepository;

    /**
     * Caffeine Cache thay cho ConcurrentHashMap:
     * - expireAfterAccess(30 phút): conversation không dùng 30 phút → tự xóa khỏi RAM
     * - maximumSize(500): tối đa 500 conversations cùng lúc trong RAM
     * → Tự động dọn memory, không bị leak
     */
    private final Cache<Long, ChatbotAI> conversationBots = Caffeine.newBuilder()
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .maximumSize(500)
            .build();

    @Autowired
    public ChatbotAIServiceImpl(ChatLanguageModel chatModel,
                                 FoodVectorService vectorService,
                                 ChatbotMessageRepository messageRepository) {
        this.chatModel = chatModel;
        this.vectorService = vectorService;
        this.messageRepository = messageRepository;
    }

    private ChatbotAI getOrCreateBot(Long conversationId) {
        return conversationBots.get(conversationId, id -> {
            // Load lịch sử từ DB (tối đa 20 tin gần nhất)
            List<ChatbotMessage> history = messageRepository
                    .findByConversation_IdOrderByCreatedAtAsc(id);

            ChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);

            // Nạp lịch sử vào memory để AI nhớ lại context cũ sau restart
            int start = Math.max(0, history.size() - 20);
            for (int i = start; i < history.size(); i++) {
                ChatbotMessage msg = history.get(i);
                if ("USER".equals(msg.getSenderType())) {
                    memory.add(UserMessage.from(msg.getContent()));
                } else {
                    memory.add(AiMessage.from(msg.getContent()));
                }
            }

            return AiServices.builder(ChatbotAI.class)
                    .chatLanguageModel(chatModel)
                    .contentRetriever(vectorService.getContentRetriever())
                    .chatMemory(memory)
                    .build();
        });
    }

    @Override
    public String generateReply(Long conversationId, String userMessage) {
        return getOrCreateBot(conversationId).chat(userMessage);
    }

    @Override
    public void removeConversationMemory(Long conversationId) {
        conversationBots.invalidate(conversationId); // Caffeine dùng invalidate thay vì remove
    }
}
