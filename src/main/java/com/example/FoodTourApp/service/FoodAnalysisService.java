package com.example.FoodTourApp.service;

import com.example.FoodTourApp.DTO.FoodAnalysisDTO.FoodAnalysisMessageItem;
import com.example.FoodTourApp.DTO.FoodAnalysisDTO.FoodAnalysisRequest;
import com.example.FoodTourApp.DTO.FoodAnalysisDTO.FoodAnalysisResponse;
import com.example.FoodTourApp.entity.User;

import java.util.List;

public interface FoodAnalysisService {

    /** Gửi câu hỏi phân tích món ăn, nhận câu trả lời từ AI */
    FoodAnalysisResponse chat(User user, Integer productId, FoodAnalysisRequest request);

    /** Load lại lịch sử hội thoại khi user quay lại màn hình */
    List<FoodAnalysisMessageItem> getHistory(User user, Integer productId);

    /** Xóa toàn bộ phiên — user muốn bắt đầu lại từ đầu */
    void deleteSession(User user, Integer productId);
}
