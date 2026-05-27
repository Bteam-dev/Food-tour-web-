package com.example.FoodTourApp.DTO.ChatbotDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ChatbotMessageResponse {
    private Long id;
    private String senderType;
    private String senderName;
    private String content;
    private LocalDateTime createdAt;
    
    /**
     * Navigation URLs để Android app điều hướng đến trang chi tiết
     * Ví dụ: ["product_detail/8", "product_detail/7", "shop_detail/1"]
     */
    private List<String> navigationUrls;
    
    /**
     * Danh sách sản phẩm được gợi ý (với thông tin chi tiết)
     * Giúp Android app hiển thị list view với thumbnail, giá, rating
     */
    private List<ProductSuggestion> suggestedProducts;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductSuggestion {
        private Integer productId;
        private String productName;
        private String shopName;
        private Integer shopId;
        private BigDecimal price;
        private BigDecimal discountPrice;
        private Double rating;
        private Long totalReviews;
        private String imageUrl;
        private String navigationUrl;
    }
}
