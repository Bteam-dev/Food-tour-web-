package com.example.FoodTourApp.controller.PublicController;

import com.example.FoodTourApp.DTO.VariantTypeDTO.VariantTypeResponseDTO;
import com.example.FoodTourApp.service.impl.VariantTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/public/variant-types")
@RequiredArgsConstructor
public class PublicVariantTypeController {
    private final VariantTypeService variantTypeService;

    // Lấy tất cả variant types đang active (cho Seller và Public)
    @GetMapping
    public ResponseEntity<List<VariantTypeResponseDTO>> getAllActiveVariantTypes() {
        return ResponseEntity.ok(variantTypeService.getAllActiveVariantTypes());
    }

    // Lấy variant type theo ID
    @GetMapping("/{id}")
    public ResponseEntity<VariantTypeResponseDTO> getVariantTypeById(@PathVariable Integer id) {
        return ResponseEntity.ok(variantTypeService.getVariantTypeById(id));
    }
}

