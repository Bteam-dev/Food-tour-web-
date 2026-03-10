package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.service.ChatbotAI;
import com.example.FoodTourApp.service.ChatbotAIService;
import com.example.FoodTourApp.service.FoodVectorService;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ChatbotAIServiceImpl implements ChatbotAIService {

    private final ChatbotAI chatbotAI;

    @Autowired
    public ChatbotAIServiceImpl(ChatLanguageModel chatModel, FoodVectorService vectorService) {
        this.chatbotAI = AiServices.builder(ChatbotAI.class)
                .chatLanguageModel(chatModel)
                .contentRetriever(vectorService.getContentRetriever())
                .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                .build();
    }

    @Override
    public String generateReply(String userMessage) {
        return chatbotAI.chat(userMessage);
    }
}
