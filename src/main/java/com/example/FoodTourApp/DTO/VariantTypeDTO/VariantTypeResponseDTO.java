package com.example.FoodTourApp.DTO.VariantTypeDTO;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class VariantTypeResponseDTO {
    private Integer id;
    private String name;
    private String description;
    private String selectionType; // "SINGLE" hoặc "MULTIPLE"
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

