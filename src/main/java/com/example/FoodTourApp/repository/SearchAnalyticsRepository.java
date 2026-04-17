package com.example.FoodTourApp.repository;

import com.example.FoodTourApp.entity.SearchAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SearchAnalyticsRepository extends JpaRepository<SearchAnalytics, Long> {

    /**
     * Top trending queries trong khoảng thời gian
     * Trả về [query_text, count] đã group + sort
     */
    @Query("SELECT sa.queryText, COUNT(sa) as cnt " +
           "FROM SearchAnalytics sa " +
           "WHERE sa.searchedAt >= :since " +
           "GROUP BY sa.queryNormalized, sa.queryText " +
           "ORDER BY cnt DESC")
    List<Object[]> findTrendingQueries(@Param("since") LocalDateTime since);

    /**
     * Đếm tổng search cho 1 query (normalized) trong khoảng thời gian
     */
    @Query("SELECT COUNT(sa) FROM SearchAnalytics sa " +
           "WHERE sa.queryNormalized = :queryNormalized AND sa.searchedAt >= :since")
    long countByQueryNormalizedSince(@Param("queryNormalized") String queryNormalized,
                                     @Param("since") LocalDateTime since);

    /**
     * Lấy analytics có click (dùng cho export training data)
     */
    @Query("SELECT sa FROM SearchAnalytics sa WHERE sa.clickedProductId IS NOT NULL ORDER BY sa.searchedAt DESC")
    List<SearchAnalytics> findAllWithClicks();
}
