package com.example.FoodTourApp.DTO.SellerApprovalDTO;

import lombok.Data;

/**
 * DTO cho request đăng ký seller.
 * Ảnh căn cước công dân (idCardImages) được gửi riêng dưới dạng MultipartFile[]
 * trong controller (multipart/form-data), không nằm trong JSON body này.
 */
@Data
public class SellerApprovalRequest {
    private String facebookUrl;
    private String zaloUrl;
}
