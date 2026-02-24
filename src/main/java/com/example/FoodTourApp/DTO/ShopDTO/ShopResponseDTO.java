package com.example.FoodTourApp.DTO.ShopDTO;

import lombok.Data;

import java.time.LocalDateTime;

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

    private String businessLicense;

    private String taxCode;

    private String openingHours;

    private Double rating;

    private Long totalReviews;

    private Boolean isVerified;

    private Boolean isActive;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
