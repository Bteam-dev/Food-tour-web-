package com.example.FoodTourApp.repository;

import com.example.FoodTourApp.entity.SearchHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {

    /**
     * Lấy search history của user, mới nhất trước, giới hạn số lượng
     */
    @Query("SELECT sh FROM SearchHistory sh WHERE sh.user.id = :userId ORDER BY sh.searchedAt DESC")
    List<SearchHistory> findByUserIdOrderBySearchedAtDesc(@Param("userId") Integer userId);

    /**
     * Tìm history trùng lặp (cùng user, cùng normalized query) → để update thay vì insert mới
     */
    @Query("SELECT sh FROM SearchHistory sh WHERE sh.user.id = :userId AND sh.queryNormalized = :queryNormalized")
    Optional<SearchHistory> findByUserIdAndQueryNormalized(@Param("userId") Integer userId,
                                                           @Param("queryNormalized") String queryNormalized);

    /**
     * Xóa 1 item history của user
     */
    @Modifying
    @Query("DELETE FROM SearchHistory sh WHERE sh.id = :id AND sh.user.id = :userId")
    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Integer userId);

    /**
     * Xóa toàn bộ history của user
     */
    @Modifying
    @Query("DELETE FROM SearchHistory sh WHERE sh.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Integer userId);

    /**
     * Đếm số history của user
     */
    @Query("SELECT COUNT(sh) FROM SearchHistory sh WHERE sh.user.id = :userId")
    long countByUserId(@Param("userId") Integer userId);
}
