package com.example.FoodTourApp.DTO.ShopDTO;


import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.Email;

/**
 * DTO cho request cập nhật Shop
 */
@Data
public class UpdateShopRequestDTO {

    private String shopName;

    private String description;

    private String logoUrl;

    private String bannerUrl;

    /**
     * Cập nhật địa chỉ (optional)
     */
    @Valid
    private AddressRequestDTO address;

    private String phone;

    @Email(message = "Email không hợp lệ")
    private String email;

    private String taxCode;

    private String openingHours;

    private Boolean isActive;
}

