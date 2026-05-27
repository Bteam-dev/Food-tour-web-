package com.example.FoodTourApp.DTO.ChatbotDTO;

import lombok.Data;
import javax.validation.constraints.NotBlank;

@Data
public class SendChatbotMessageRequest {
    @NotBlank(message = "Message content is required")
    private String content;
}
