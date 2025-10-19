package com.example.FoodTourApp.DTO.ShopDTO;

import com.example.FoodTourApp.entity.Address;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * DTO cho địa chỉ khi tạo/cập nhật Shop
 */
@Data
public class AddressRequestDTO {

    // Bỏ @NotBlank vì khi chọn locality từ HERE API (ví dụ: "Quận Cầu Giấy")
    // thì không có addressLine (số nhà/đường), chỉ có district + city
    private String addressLine;

    private String ward;

    private String district;

    @NotBlank(message = "Thành phố không được để trống")
    private String city;

    private String country = "Vietnam";

    private String postalCode;

    private Double latitude;

    private Double longitude;

    private Address.AddressType addressType = Address.AddressType.shop;
}