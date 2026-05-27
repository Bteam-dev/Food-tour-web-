package com.example.FoodTourApp.service;

import com.example.FoodTourApp.DTO.ProductDTO.ProductResponseDTO;
import com.google.api.gax.paging.Page;
import org.springframework.web.multipart.MultipartFile;

import java.awt.print.Pageable;
import java.util.List;

public interface FoodDetectionService {
    List<String> detectFoodNames(MultipartFile imageFile);
}
