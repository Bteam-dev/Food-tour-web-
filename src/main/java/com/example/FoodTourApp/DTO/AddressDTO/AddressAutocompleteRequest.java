package com.example.FoodTourApp.DTO.AddressDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddressAutocompleteRequest {
    private String query;           // Từ khóa tìm kiếm
    private Integer limit = 5;      // Số kết quả trả về (mặc định 5)
    private String city;            // Lọc theo thành phố (optional)
}

