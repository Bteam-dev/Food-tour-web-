package com.example.FoodTourApp.service;

import com.example.FoodTourApp.DTO.CategoryDTO.CreateCategoryRequestDTO;
import com.example.FoodTourApp.DTO.CategoryDTO.CategoryResponseDTO;
import com.example.FoodTourApp.DTO.CategoryDTO.UpdateCategoryRequestDTO;

import java.util.List;

/**
 * Interface cho Category Service
 */
public interface CategoryService {

    /**
     * Tạo danh mục mới
     * @param request Thông tin danh mục
     * @return Danh mục đã tạo
     */
    CategoryResponseDTO createCategory(CreateCategoryRequestDTO request);

    /**
     * Cập nhật danh mục
     * @param categoryId ID của danh mục
     * @param request Thông tin cập nhật
     * @return Danh mục đã cập nhật
     */
    CategoryResponseDTO updateCategory(Integer categoryId, UpdateCategoryRequestDTO request);

    /**
     * Xóa danh mục (soft delete - set isActive = false)
     * @param categoryId ID của danh mục
     */
    void deleteCategory(Integer categoryId);

    /**
     * Lấy chi tiết danh mục
     * @param categoryId ID của danh mục
     * @return Chi tiết danh mục
     */
    CategoryResponseDTO getCategoryById(Integer categoryId);

    /**
     * Lấy tất cả danh mục (kể cả inactive) - cho admin
     * @return Danh sách tất cả danh mục
     */
    List<CategoryResponseDTO> getAllCategories();

    /**
     * Lấy danh mục đang active (cho user)
     * @return Danh sách danh mục active
     */
    List<CategoryResponseDTO> getActiveCategories();

    /**
     * Lấy danh mục cha (parent_id = null)
     * @return Danh sách danh mục cha
     */
    List<CategoryResponseDTO> getParentCategories();

    /**
     * Lấy danh mục con theo parent
     * @param parentId ID của danh mục cha
     * @return Danh sách danh mục con
     */
    List<CategoryResponseDTO> getCategoriesByParent(Integer parentId);

    /**
     * Lấy cây danh mục (hierarchical structure)
     * @return Danh sách danh mục có cấu trúc phân cấp
     */
    List<CategoryResponseDTO> getCategoryTree();
}

