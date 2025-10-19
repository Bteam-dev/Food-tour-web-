package com.example.FoodTourApp.service;

import com.example.FoodTourApp.DTO.AddressDTO.AddressDetailDTO;
import com.example.FoodTourApp.DTO.AddressDTO.AddressSuggestionDTO;

import java.util.List;

/**
 * Interface cho HERE Platform API Service
 */
public interface HereApiService {

    /**
     * Autocomplete địa chỉ khi người dùng nhập
     * @param query Từ khóa tìm kiếm
     * @param limit Số lượng kết quả (mặc định 5)
     * @param city Lọc theo thành phố (optional)
     * @return Danh sách gợi ý địa chỉ
     */
    List<AddressSuggestionDTO> autocompleteAddress(String query, Integer limit, String city);

    /**
     * Lấy chi tiết địa chỉ từ ID (sau khi user chọn từ autocomplete)
     * @param addressId ID của địa chỉ từ HERE API
     * @return Chi tiết địa chỉ đầy đủ
     */
    AddressDetailDTO lookupAddress(String addressId);

    /**
     * Geocode địa chỉ (chuyển text thành tọa độ + thông tin chi tiết)
     * @param address Địa chỉ dạng text
     * @return Chi tiết địa chỉ với tọa độ
     */
    AddressDetailDTO geocodeAddress(String address);
}

