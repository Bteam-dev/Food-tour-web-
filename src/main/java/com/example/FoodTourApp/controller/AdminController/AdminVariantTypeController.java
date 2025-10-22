package com.example.FoodTourApp.controller.AdminController;

import com.example.FoodTourApp.DTO.VariantTypeDTO.VariantTypeRequestDTO;
import com.example.FoodTourApp.DTO.VariantTypeDTO.VariantTypeResponseDTO;
import com.example.FoodTourApp.service.VariantTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/admin/variant-types")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminVariantTypeController {
    private final VariantTypeService variantTypeService;

    // Lấy tất cả variant types
    @GetMapping
    public ResponseEntity<List<VariantTypeResponseDTO>> getAllVariantTypes() {
        return ResponseEntity.ok(variantTypeService.getAllVariantTypes());
    }

    // Lấy variant type theo ID
    @GetMapping("/{id}")
    public ResponseEntity<VariantTypeResponseDTO> getVariantTypeById(@PathVariable Integer id) {
        return ResponseEntity.ok(variantTypeService.getVariantTypeById(id));
    }

    // Tạo variant type mới
    @PostMapping
    public ResponseEntity<VariantTypeResponseDTO> createVariantType(
            @Valid @RequestBody VariantTypeRequestDTO requestDTO) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(variantTypeService.createVariantType(requestDTO));
    }

    // Cập nhật variant type
    @PutMapping("/{id}")
    public ResponseEntity<VariantTypeResponseDTO> updateVariantType(
            @PathVariable Integer id,
            @Valid @RequestBody VariantTypeRequestDTO requestDTO) {
        return ResponseEntity.ok(variantTypeService.updateVariantType(id, requestDTO));
    }

    // Xóa mềm variant type
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVariantType(@PathVariable Integer id) {
        variantTypeService.deleteVariantType(id);
        return ResponseEntity.noContent().build();
    }

    // Kích hoạt lại variant type
    @PatchMapping("/{id}/activate")
    public ResponseEntity<VariantTypeResponseDTO> activateVariantType(@PathVariable Integer id) {
        return ResponseEntity.ok(variantTypeService.activateVariantType(id));
    }
}
