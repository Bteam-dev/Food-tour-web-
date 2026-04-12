package com.example.FoodTourApp.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Value("${gemini.api-key}")
    private String geminiApiKey;

    @Value("${gemini.model:gemini-2.0-flash}")
    private String geminiModel;

    @Bean
    public ChatLanguageModel chatModel() {
        return GoogleAiGeminiChatModel.builder()
                .apiKey(geminiApiKey)
                .modelName(geminiModel)
                .temperature(0.7)
                .maxOutputTokens(1024)
                .build();
    }
}
