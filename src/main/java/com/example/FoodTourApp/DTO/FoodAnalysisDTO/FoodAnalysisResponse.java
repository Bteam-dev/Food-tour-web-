package com.example.FoodTourApp.DTO.FoodAnalysisDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoodAnalysisResponse {

    /** Câu trả lời từ AI */
    private String reply;

    /**
     * SessionKey để client gửi kèm trong các câu hỏi tiếp theo.
     * Client lưu lại giá trị này ngay sau lần chat đầu tiên.
     */
    private String sessionId;

    /** Thời điểm phản hồi */
    private LocalDateTime createdAt;
}
