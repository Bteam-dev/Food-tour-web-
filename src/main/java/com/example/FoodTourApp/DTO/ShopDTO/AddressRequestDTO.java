package com.example.FoodTourApp.DTO.ShopDTO;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * DTO cho địa chỉ khi tạo/cập nhật Shop hoặc Order
 */
@Data
public class AddressRequestDTO {

    private String addressLine;

    private String ward;

    private String district;

    @NotBlank(message = "Thành phố không được để trống")
    private String city;

    private String country = "Vietnam";

    private String postalCode;

    private Double latitude;

    private Double longitude;
}