package com.example.FoodTourApp.controller.AdminController;

import com.example.FoodTourApp.DTO.CategoryDTO.CreateCategoryRequestDTO;
import com.example.FoodTourApp.DTO.CategoryDTO.CategoryResponseDTO;
import com.example.FoodTourApp.DTO.CategoryDTO.UpdateCategoryRequestDTO;
import com.example.FoodTourApp.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * Admin Category Controller - Các endpoint quản lý category (chỉ Admin)
 */
@RestController
@RequestMapping("/api/admin/categories")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminCategoryController {

    private final CategoryService categoryService;

    /**
     * Tạo danh mục mới (chỉ Admin)
     * POST /api/admin/categories
     */
    @PostMapping
    public ResponseEntity<CategoryResponseDTO> createCategory(
            @Valid @RequestBody CreateCategoryRequestDTO request) {

        log.info("Creating category: {}", request.getName());

        CategoryResponseDTO response = categoryService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Cập nhật danh mục (chỉ Admin)
     * PUT /api/admin/categories/{categoryId}
     */
    @PutMapping("/{categoryId}")
    public ResponseEntity<CategoryResponseDTO> updateCategory(
            @PathVariable Integer categoryId,
            @Valid @RequestBody UpdateCategoryRequestDTO request) {

        log.info("Updating category: {}", categoryId);

        CategoryResponseDTO response = categoryService.updateCategory(categoryId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Xóa danh mục (chỉ Admin) - soft delete
     * DELETE /api/admin/categories/{categoryId}
     */
    @DeleteMapping("/{categoryId}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Integer categoryId) {
        log.info("Deleting category: {}", categoryId);

        categoryService.deleteCategory(categoryId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Lấy tất cả danh mục (kể cả inactive) - chỉ Admin
     * GET /api/admin/categories/all
     */
    @GetMapping("/all")
    public ResponseEntity<List<CategoryResponseDTO>> getAllCategories() {
        log.info("Getting all categories (admin)");

        List<CategoryResponseDTO> response = categoryService.getAllCategories();
        return ResponseEntity.ok(response);
    }
}
