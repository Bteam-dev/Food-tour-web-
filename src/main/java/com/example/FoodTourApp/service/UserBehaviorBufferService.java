package com.example.FoodTourApp.service;

import com.example.FoodTourApp.entity.UserBehavior;

/**
 * Buffer hành vi người dùng vào Redis trước khi flush batch vào MySQL.
 *
 * Tại sao cần buffer?
 * - UserBehavior được ghi rất nhiều (view, click, cart... mỗi thao tác)
 * - Ghi thẳng MySQL từng request → write pressure cao, latency tăng
 * - Buffer Redis → batch INSERT mỗi 30s → MySQL chỉ nhận 1 INSERT/30s thay vì N INSERT/30s
 * - Giảm 90%+ write pressure mà không mất data (Redis persistent)
 *
 * Training data: vẫn lấy từ MySQL (buffer flush đảm bảo data vào MySQL trong vòng 30s).
 * Real-time embedding: updateUserEmbedding() vẫn gọi ngay sau buffer → 30s lag tối đa, chấp nhận được.
 */
public interface UserBehaviorBufferService {

    /**
     * Buffer 1 hành vi vào Redis list để flush batch sau.
     * Non-blocking: ghi Redis xong return ngay, không chờ MySQL.
     * Fallback: nếu Redis lỗi → ghi thẳng MySQL để không mất data.
     */
    void buffer(Integer userId, Integer productId,
                UserBehavior.ActionType actionType,
                double weight,
                String sessionId,
                UserBehavior.BehaviorSource source,
                Integer viewDurationSeconds,
                Integer quantity,
                Integer rating,
                Integer categoryId,
                Integer shopId);

    /**
     * Flush toàn bộ buffer vào MySQL (batch INSERT).
     * Gọi bởi @Scheduled mỗi 30s.
     *
     * @return số behaviors đã flush
     */
    int flush();
}
