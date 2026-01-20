package com.example.FoodTourApp.DTO.ShopDTO;


import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * DTO cho request tạo Shop (bao gồm cả Address)
 */
@Data
public class CreateShopRequestDTO {

    @NotBlank(message = "Tên cửa hàng không được để trống")
    private String shopName;

    private String description;

    private String logoUrl;

    private String bannerUrl;

    /**
     * Địa chỉ cửa hàng - có thể lấy từ HERE API autocomplete/lookup
     */
    @NotNull(message = "Địa chỉ cửa hàng không được để trống")
    @Valid
    private AddressRequestDTO address;

    /**
     * Số điện thoại của shop - người dùng tự điền
     */
    @NotBlank(message = "Số điện thoại không được để trống")
    private String phone;

    /**
     * Email của shop - người dùng tự điền, nếu không điền sẽ lấy email của user đăng ký
     */
    @Email(message = "Email không hợp lệ")
    private String email;

    @NotBlank(message = "Giấy phép kinh doanh không được để trống")
    private String businessLicense;

    @NotBlank(message = "Mã số thuế không được để trống")
    private String taxCode;

    /**
     * Giờ mở cửa - format JSON
     * Ví dụ: {"monday": {"open": "08:00", "close": "22:00"}, ...}
     */
    @NotBlank(message = "Giờ mở cửa không được để trống")
    private String openingHours;
}
