package com.example.FoodTourApp.DTO.AddressDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddressDetailDTO {
    private String addressLine;     // Địa chỉ chi tiết (số nhà, tên đường)
    private String ward;            // Phường/xã
    private String district;        // Quận/huyện
    private String city;            // Tỉnh/thành phố
    private String country;         // Quốc gia
    private String postalCode;      // Mã bưu điện
    private Double latitude;        // Vĩ độ
    private Double longitude;       // Kinh độ
    private String fullAddress;     // Địa chỉ đầy đủ
}


