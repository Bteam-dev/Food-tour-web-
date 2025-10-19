package com.example.FoodTourApp.controller;

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
 * Controller cho quản lý Category
 * Chỉ Admin mới có quyền tạo/sửa/xóa
 */
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Slf4j
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * Tạo danh mục mới (chỉ Admin)
     * POST /api/categories
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategoryResponseDTO> createCategory(
            @Valid @RequestBody CreateCategoryRequestDTO request) {

        log.info("Creating category: {}", request.getName());

        CategoryResponseDTO response = categoryService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Cập nhật danh mục (chỉ Admin)
     * PUT /api/categories/{categoryId}
     */
    @PutMapping("/{categoryId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategoryResponseDTO> updateCategory(
            @PathVariable Integer categoryId,
            @Valid @RequestBody UpdateCategoryRequestDTO request) {

        log.info("Updating category: {}", categoryId);

        CategoryResponseDTO response = categoryService.updateCategory(categoryId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Xóa danh mục (chỉ Admin) - soft delete
     * DELETE /api/categories/{categoryId}
     */
    @DeleteMapping("/{categoryId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteCategory(@PathVariable Integer categoryId) {
        log.info("Deleting category: {}", categoryId);

        categoryService.deleteCategory(categoryId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Lấy chi tiết danh mục (public)
     * GET /api/categories/{categoryId}
     */
    @GetMapping("/{categoryId}")
    public ResponseEntity<CategoryResponseDTO> getCategory(@PathVariable Integer categoryId) {
        log.info("Getting category: {}", categoryId);

        CategoryResponseDTO response = categoryService.getCategoryById(categoryId);
        return ResponseEntity.ok(response);
    }

    /**
     * Lấy tất cả danh mục (kể cả inactive) - chỉ Admin
     * GET /api/categories/all
     */
    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CategoryResponseDTO>> getAllCategories() {
        log.info("Getting all categories (admin)");

        List<CategoryResponseDTO> response = categoryService.getAllCategories();
        return ResponseEntity.ok(response);
    }

    /**
     * Lấy danh mục đang active (public)
     * GET /api/categories
     */
    @GetMapping
    public ResponseEntity<List<CategoryResponseDTO>> getActiveCategories() {
        log.info("Getting active categories");

        List<CategoryResponseDTO> response = categoryService.getActiveCategories();
        return ResponseEntity.ok(response);
    }

    /**
     * Lấy danh mục cha (parent_id = null) - public
     * GET /api/categories/parents
     */
    @GetMapping("/parents")
    public ResponseEntity<List<CategoryResponseDTO>> getParentCategories() {
        log.info("Getting parent categories");

        List<CategoryResponseDTO> response = categoryService.getParentCategories();
        return ResponseEntity.ok(response);
    }

    /**
     * Lấy danh mục con theo parent - public
     * GET /api/categories/parent/{parentId}
     */
    @GetMapping("/parent/{parentId}")
    public ResponseEntity<List<CategoryResponseDTO>> getCategoriesByParent(
            @PathVariable Integer parentId) {

        log.info("Getting categories by parent: {}", parentId);

        List<CategoryResponseDTO> response = categoryService.getCategoriesByParent(parentId);
        return ResponseEntity.ok(response);
    }

    /**
     * Lấy cây danh mục (hierarchical structure) - public
     * GET /api/categories/tree
     */
    @GetMapping("/tree")
    public ResponseEntity<List<CategoryResponseDTO>> getCategoryTree() {
        log.info("Getting category tree");

        List<CategoryResponseDTO> response = categoryService.getCategoryTree();
        return ResponseEntity.ok(response);
    }
}

