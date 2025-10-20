package com.example.FoodTourApp.controller.PublicController;

import com.example.FoodTourApp.DTO.CategoryDTO.CategoryResponseDTO;
import com.example.FoodTourApp.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Public Category Controller - Các endpoint xem category (không cần authentication)
 */
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Slf4j
public class PublicCategoryController {

    private final CategoryService categoryService;

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

