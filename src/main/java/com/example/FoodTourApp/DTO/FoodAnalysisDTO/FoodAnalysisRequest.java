package com.example.FoodTourApp.DTO.FoodAnalysisDTO;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class FoodAnalysisRequest {

    @NotBlank(message = "Nội dung câu hỏi không được để trống")
    private String content;

    // sessionId không cần nữa — server tự tìm session theo user + product
}
