package com.example.FoodTourApp.DTO.ShopDTO;

import lombok.Data;

/**
 * DTO cho địa chỉ trong response
 */
@Data
public class AddressResponseDTO {

    private Integer id;

    private String addressLine;

    private String ward;

    private String district;

    private String city;

    private String country;

    private String postalCode;

    private Double latitude;

    private Double longitude;

    private String fullAddress;
}


