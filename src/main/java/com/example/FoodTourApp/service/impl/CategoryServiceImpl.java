package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.DTO.CategoryDTO.CreateCategoryRequestDTO;
import com.example.FoodTourApp.DTO.CategoryDTO.CategoryResponseDTO;
import com.example.FoodTourApp.DTO.CategoryDTO.UpdateCategoryRequestDTO;
import com.example.FoodTourApp.entity.Category;
import com.example.FoodTourApp.repository.CategoryRepository;
import com.example.FoodTourApp.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    @Override
    @Transactional
    public CategoryResponseDTO createCategory(CreateCategoryRequestDTO request) {
        log.info("Creating category: {}", request.getName());

        // Kiểm tra tên category đã tồn tại chưa
        if (categoryRepository.existsByName(request.getName())) {
            throw new RuntimeException("Tên danh mục đã tồn tại");
        }

        Category category = new Category();
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        category.setImageUrl(request.getImageUrl());
        category.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        category.setIsActive(true);
        category.setCreatedAt(LocalDateTime.now());

        // Set parent nếu có
        if (request.getParentId() != null) {
            Category parent = categoryRepository.findById(request.getParentId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục cha"));
            category.setParent(parent);
        }

        category = categoryRepository.save(category);

        log.info("Category created successfully with id: {}", category.getId());
        return mapToCategoryResponseDTO(category);
    }

    @Override
    @Transactional
    public CategoryResponseDTO updateCategory(Integer categoryId, UpdateCategoryRequestDTO request) {
        log.info("Updating category: {}", categoryId);

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục"));

        // Kiểm tra tên category mới đã tồn tại chưa (trừ category hiện tại)
        if (request.getName() != null && categoryRepository.existsByNameAndIdNot(request.getName(), categoryId)) {
            throw new RuntimeException("Tên danh mục đã tồn tại");
        }

        // Cập nhật thông tin
        if (request.getName() != null) {
            category.setName(request.getName());
        }
        if (request.getDescription() != null) {
            category.setDescription(request.getDescription());
        }
        if (request.getImageUrl() != null) {
            category.setImageUrl(request.getImageUrl());
        }
        if (request.getSortOrder() != null) {
            category.setSortOrder(request.getSortOrder());
        }
        if (request.getIsActive() != null) {
            category.setIsActive(request.getIsActive());
        }

        // Cập nhật parent
        if (request.getParentId() != null) {
            // Kiểm tra không được set parent là chính nó hoặc con của nó
            if (request.getParentId().equals(categoryId)) {
                throw new RuntimeException("Danh mục không thể là cha của chính nó");
            }
            Category parent = categoryRepository.findById(request.getParentId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục cha"));
            category.setParent(parent);
        }

        category = categoryRepository.save(category);

        log.info("Category updated successfully: {}", categoryId);
        return mapToCategoryResponseDTO(category);
    }

    @Override
    @Transactional
    public void deleteCategory(Integer categoryId) {
        log.info("Deleting category: {}", categoryId);

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục"));

        // Kiểm tra có category con không
        Long childCount = categoryRepository.countByParent(category);
        if (childCount > 0) {
            throw new RuntimeException("Không thể xóa danh mục có danh mục con. Vui lòng xóa danh mục con trước.");
        }

        // Soft delete
        category.setIsActive(false);
        categoryRepository.save(category);

        log.info("Category deleted (soft delete) successfully: {}", categoryId);
    }

    @Override
    public CategoryResponseDTO getCategoryById(Integer categoryId) {
        log.info("Getting category by id: {}", categoryId);

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục"));

        return mapToCategoryResponseDTO(category);
    }

    @Override
    public List<CategoryResponseDTO> getAllCategories() {
        log.info("Getting all categories");

        List<Category> categories = categoryRepository.findAllByOrderBySortOrderAsc();
        return categories.stream()
                .map(this::mapToCategoryResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<CategoryResponseDTO> getActiveCategories() {
        log.info("Getting active categories");

        List<Category> categories = categoryRepository.findByIsActiveTrueOrderBySortOrderAsc();
        return categories.stream()
                .map(this::mapToCategoryResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<CategoryResponseDTO> getParentCategories() {
        log.info("Getting parent categories");

        List<Category> categories = categoryRepository.findByParentIsNullAndIsActiveTrueOrderBySortOrderAsc();
        return categories.stream()
                .map(this::mapToCategoryResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<CategoryResponseDTO> getCategoriesByParent(Integer parentId) {
        log.info("Getting categories by parent: {}", parentId);

        Category parent = categoryRepository.findById(parentId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục cha"));

        List<Category> categories = categoryRepository.findByParentAndIsActiveTrueOrderBySortOrderAsc(parent);
        return categories.stream()
                .map(this::mapToCategoryResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<CategoryResponseDTO> getCategoryTree() {
        log.info("Getting category tree");

        // Lấy tất cả category cha
        List<Category> parentCategories = categoryRepository.findByParentIsNullAndIsActiveTrueOrderBySortOrderAsc();

        return parentCategories.stream()
                .map(this::mapToCategoryTreeDTO)
                .collect(Collectors.toList());
    }

    /**
     * Map Category entity sang CategoryResponseDTO (không bao gồm children)
     */
    private CategoryResponseDTO mapToCategoryResponseDTO(Category category) {
        CategoryResponseDTO dto = new CategoryResponseDTO();
        dto.setId(category.getId());
        dto.setName(category.getName());
        dto.setDescription(category.getDescription());
        dto.setImageUrl(category.getImageUrl());
        dto.setIsActive(category.getIsActive());
        dto.setSortOrder(category.getSortOrder());
        dto.setCreatedAt(category.getCreatedAt());

        if (category.getParent() != null) {
            dto.setParentId(category.getParent().getId());
            dto.setParentName(category.getParent().getName());
        }

        return dto;
    }

    /**
     * Map Category entity sang CategoryResponseDTO (bao gồm cả children - dùng cho tree)
     */
    private CategoryResponseDTO mapToCategoryTreeDTO(Category category) {
        CategoryResponseDTO dto = mapToCategoryResponseDTO(category);

        // Lấy danh sách category con
        List<Category> children = categoryRepository.findByParentAndIsActiveTrueOrderBySortOrderAsc(category);
        if (!children.isEmpty()) {
            dto.setChildren(children.stream()
                    .map(this::mapToCategoryTreeDTO)
                    .collect(Collectors.toList()));
        }

        return dto;
    }
}

