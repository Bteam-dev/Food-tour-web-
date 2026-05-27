package com.example.FoodTourApp.repository;

import com.example.FoodTourApp.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Integer> {

    // Tìm category theo tên
    Optional<Category> findByName(String name);

    // Tìm tất cả category đang active
    List<Category> findByIsActiveTrueOrderBySortOrderAsc();

    // Tìm tất cả category (kể cả inactive) - sắp xếp theo sort_order
    List<Category> findAllByOrderBySortOrderAsc();

    // Tìm category cha (parent_id = null)
    List<Category> findByParentIsNullOrderBySortOrderAsc();

    // Tìm category cha đang active
    List<Category> findByParentIsNullAndIsActiveTrueOrderBySortOrderAsc();

    // Tìm category con theo parent
    List<Category> findByParentOrderBySortOrderAsc(Category parent);

    // Tìm category con đang active theo parent
    List<Category> findByParentAndIsActiveTrueOrderBySortOrderAsc(Category parent);

    // Đếm số category con
    Long countByParent(Category parent);

    // Kiểm tra tên category đã tồn tại chưa (để tránh trùng)
    boolean existsByName(String name);

    // Kiểm tra tên category đã tồn tại chưa (trừ category hiện tại - dùng khi update)
    boolean existsByNameAndIdNot(String name, Integer id);
}

