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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class ChatbotAIServiceImpl implements ChatbotAIService {

    private static final Logger log = LoggerFactory.getLogger(ChatbotAIServiceImpl.class);

    // Số tin nhắn tối đa giữ trong context window của LLM
    // 30 messages = 15 lượt hỏi-đáp — đủ để bot nhớ toàn bộ context trong 1 cuộc hội thoại dài
    private static final int MEMORY_WINDOW_SIZE = 30;

    private final ChatLanguageModel chatModel;
    private final FoodVectorService vectorService;
    private final ChatbotMessageRepository messageRepository;

    /**
     * Caffeine Cache per-conversation:
     * - expireAfterAccess(60 phút): inactive 60 phút → tự xóa khỏi RAM
     * - maximumSize(500): tối đa 500 conversations đồng thời trong RAM
     *
     * Khi cache evict (hết TTL hoặc đầy), getOrCreateBot() tự reload lịch sử từ DB
     * → User quay lại sau nhiều giờ vẫn có context đầy đủ
     */
    private final Cache<Long, ChatbotAI> conversationBots = Caffeine.newBuilder()
            .expireAfterAccess(60, TimeUnit.MINUTES)
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

    /**
     * Lấy bot instance từ cache, hoặc tạo mới từ DB nếu cache miss.
     * Cache miss xảy ra khi: (1) lần đầu chat, (2) sau khi inactive > 60 phút.
     * Trong cả 2 trường hợp, lịch sử DB đều được nạp lại đầy đủ.
     */
    private ChatbotAI getOrCreateBot(Long conversationId) {
        return conversationBots.get(conversationId, id -> {
            List<ChatbotMessage> history = messageRepository
                    .findByConversation_IdOrderByCreatedAtAsc(id);

            ChatMemory memory = MessageWindowChatMemory.withMaxMessages(MEMORY_WINDOW_SIZE);

            // Nạp N tin gần nhất vào memory (tính từ cuối — giữ context mới nhất)
            int start = Math.max(0, history.size() - MEMORY_WINDOW_SIZE);
            for (int i = start; i < history.size(); i++) {
                ChatbotMessage msg = history.get(i);
                if ("USER".equals(msg.getSenderType())) {
                    memory.add(UserMessage.from(msg.getContent()));
                } else {
                    memory.add(AiMessage.from(msg.getContent()));
                }
            }

            if (!history.isEmpty()) {
                log.info("[Chatbot] Restored {} messages from DB for conversation {}",
                        history.size() - start, id);
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
        try {
            return getOrCreateBot(conversationId).chat(userMessage);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("503") || msg.contains("UNAVAILABLE") || msg.contains("high demand")) {
                log.warn("[Chatbot] Gemini 503 for conversation {}: {}", conversationId, msg);
                return "Xin lỗi, hệ thống AI đang bận. Vui lòng thử lại sau vài giây nhé! 🙏";
            }
            if (msg.contains("429") || msg.contains("RESOURCE_EXHAUSTED") || msg.contains("quota")) {
                log.warn("[Chatbot] Gemini rate limit for conversation {}: {}", conversationId, msg);
                return "Bạn đang chat quá nhanh! Vui lòng chờ một chút rồi thử lại nhé 😊";
            }
            log.error("[Chatbot] AI error for conversation {}: {}", conversationId, msg, e);
            return "Xin lỗi, mình gặp sự cố kết nối. Vui lòng thử lại sau!";
        }
    }

    @Override
    public void removeConversationMemory(Long conversationId) {
        conversationBots.invalidate(conversationId); // Caffeine dùng invalidate thay vì remove
    }
}
