package com.example.FoodTourApp.DTO.AddressDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeocodeRequest {
    private String address;         // Địa chỉ đầy đủ cần geocode
}

