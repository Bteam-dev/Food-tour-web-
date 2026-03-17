package com.example.FoodTourApp.DTO.ShopDTO;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO cho response Shop (trả về cho client)
 */
@Data
public class ShopResponseDTO {

    private Integer id;

    private Integer sellerId;

    private String sellerName;

    private String shopName;

    private String description;

    private String logoUrl;

    private String bannerUrl;

    /**
     * Thông tin địa chỉ của shop
     */
    private AddressResponseDTO address;

    private String phone;

    private String email;

    private List<String> businessLicenseImageUrls;

    private String taxCode;

    private String openingHours;

    private Double rating;

    private Long totalReviews;

    private Boolean isVerified;

    private Boolean isActive;

    /** Lý do từ chối (nếu shop bị từ chối) */
    private String rejectionReason;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
