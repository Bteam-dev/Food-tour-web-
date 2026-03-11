package com.example.FoodTourApp.DTO.ChatbotDTO;

import lombok.Data;
import javax.validation.constraints.NotBlank;

@Data
public class RenameChatbotConversationRequest {
    @NotBlank(message = "Conversation title is required")
    private String title;
}
