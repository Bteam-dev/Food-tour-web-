package com.example.FoodTourApp.repository;

import com.example.FoodTourApp.entity.UserBehavior;
import com.example.FoodTourApp.entity.UserBehavior.ActionType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserBehaviorRepository extends JpaRepository<UserBehavior, Long> {

    // ══════════════════════════════════════════════════════════════════════════
    // REAL-TIME QUERIES - Dùng cho recommendation
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Lấy N hành vi gần nhất của user (cho real-time recommendation)
     */
    @Query("""
        SELECT ub FROM UserBehavior ub
        WHERE ub.user.id = :userId
        ORDER BY ub.createdAt DESC
        """)
    List<UserBehavior> findRecentByUserId(@Param("userId") Integer userId, Pageable pageable);

    /**
     * Lấy các product ID mà user đã tương tác gần đây với weight > 0
     */
    @Query("""
        SELECT ub.product.id FROM UserBehavior ub
        WHERE ub.user.id = :userId
        AND ub.weight > 0
        AND ub.createdAt > :since
        GROUP BY ub.product.id
        ORDER BY MAX(ub.createdAt) DESC
        """)
    List<Integer> findRecentPositiveProductIds(
            @Param("userId") Integer userId,
            @Param("since") LocalDateTime since);

    /**
     * Lấy các product ID mà user KHÔNG thích (weight < 0)
     */
    @Query("""
        SELECT DISTINCT ub.product.id FROM UserBehavior ub
        WHERE ub.user.id = :userId
        AND ub.weight < 0
        """)
    List<Integer> findNegativeProductIds(@Param("userId") Integer userId);

    /**
     * Tính tổng weighted score của user cho mỗi product trong N ngày gần nhất.
     * Trả thêm MAX(createdAt) để caller có thể apply time decay.
     */
    @Query("""
        SELECT ub.product.id, SUM(ub.weight) as totalWeight, MAX(ub.createdAt) as lastInteraction
        FROM UserBehavior ub
        WHERE ub.user.id = :userId
        AND ub.weight > 0
        AND ub.createdAt > :since
        GROUP BY ub.product.id
        ORDER BY SUM(ub.weight) DESC
        """)
    List<Object[]> findUserProductWeights(@Param("userId") Integer userId,
                                          @Param("since") LocalDateTime since);

    /**
     * Lấy các category mà user hay tương tác (cho content-based)
     */
    @Query("""
        SELECT ub.categoryId, SUM(ub.weight) as totalWeight
        FROM UserBehavior ub
        WHERE ub.user.id = :userId
        AND ub.categoryId IS NOT NULL
        AND ub.weight > 0
        GROUP BY ub.categoryId
        ORDER BY SUM(ub.weight) DESC
        """)
    List<Object[]> findUserCategoryPreferences(@Param("userId") Integer userId, Pageable pageable);

    /**
     * Lấy các shop mà user hay mua (cho collaborative)
     */
    @Query("""
        SELECT ub.shopId, SUM(ub.weight) as totalWeight
        FROM UserBehavior ub
        WHERE ub.user.id = :userId
        AND ub.shopId IS NOT NULL
        AND ub.actionType IN ('PURCHASE', 'REVIEW_POSITIVE')
        GROUP BY ub.shopId
        ORDER BY SUM(ub.weight) DESC
        """)
    List<Object[]> findUserShopPreferences(@Param("userId") Integer userId, Pageable pageable);

    // ══════════════════════════════════════════════════════════════════════════
    // SESSION-BASED QUERIES - Gợi ý theo session hiện tại
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Lấy các product trong session hiện tại
     */
    @Query("""
        SELECT ub FROM UserBehavior ub
        WHERE ub.sessionId = :sessionId
        ORDER BY ub.createdAt DESC
        """)
    List<UserBehavior> findBySessionId(@Param("sessionId") String sessionId);

    /**
     * Lấy product IDs trong session hiện tại
     */
    @Query("""
        SELECT DISTINCT ub.product.id FROM UserBehavior ub
        WHERE ub.sessionId = :sessionId
        AND ub.weight > 0
        """)
    List<Integer> findProductIdsBySessionId(@Param("sessionId") String sessionId);

    // ══════════════════════════════════════════════════════════════════════════
    // TRAINING DATA EXPORT - Dùng cho Colab
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Export all behaviors for training (với timestamp filter)
     */
    @Query("""
        SELECT ub FROM UserBehavior ub
        WHERE ub.createdAt > :since
        ORDER BY ub.createdAt ASC
        """)
    List<UserBehavior> findAllForTraining(@Param("since") LocalDateTime since);

    /**
     * Export aggregated user-product interactions
     */
    @Query("""
        SELECT ub.user.id, ub.product.id, SUM(ub.weight), COUNT(ub.id), MAX(ub.createdAt)
        FROM UserBehavior ub
        WHERE ub.createdAt > :since
        GROUP BY ub.user.id, ub.product.id
        """)
    List<Object[]> findAggregatedInteractions(@Param("since") LocalDateTime since);

    // ══════════════════════════════════════════════════════════════════════════
    // COLLABORATIVE FILTERING - Tìm users có hành vi tương tự
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Tìm users khác đã mua cùng sản phẩm
     */
    @Query("""
        SELECT DISTINCT ub.user.id FROM UserBehavior ub
        WHERE ub.product.id IN :productIds
        AND ub.user.id != :userId
        AND ub.actionType = 'PURCHASE'
        """)
    List<Integer> findSimilarUsers(
            @Param("userId") Integer userId,
            @Param("productIds") List<Integer> productIds);

    /**
     * Lấy products mà các similar users đã mua (nhưng current user chưa)
     */
    @Query("""
        SELECT ub.product.id, SUM(ub.weight) as score
        FROM UserBehavior ub
        WHERE ub.user.id IN :similarUserIds
        AND ub.product.id NOT IN :excludeProductIds
        AND ub.weight > 0
        GROUP BY ub.product.id
        ORDER BY SUM(ub.weight) DESC
        """)
    List<Object[]> findProductsFromSimilarUsers(
            @Param("similarUserIds") List<Integer> similarUserIds,
            @Param("excludeProductIds") List<Integer> excludeProductIds,
            Pageable pageable);

    // ══════════════════════════════════════════════════════════════════════════
    // COLD-START — Trending & Popular products cho user mới chưa có hành vi
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Trending: các product được mua nhiều nhất trong khoảng thời gian gần đây.
     * Dùng cho cold-start thay vì chỉ top-rated tĩnh.
     */
    @Query("""
        SELECT ub.product.id, COUNT(ub.id) as purchase_count
        FROM UserBehavior ub
        WHERE ub.actionType = 'PURCHASE'
        AND ub.createdAt > :since
        GROUP BY ub.product.id
        ORDER BY COUNT(ub.id) DESC
        """)
    List<Object[]> findTrendingProductIds(
            @Param("since") LocalDateTime since,
            Pageable pageable);

    /**
     * Kiểm tra user có bất kỳ hành vi nào chưa (để detect cold-start).
     */
    @Query("SELECT COUNT(ub) > 0 FROM UserBehavior ub WHERE ub.user.id = :userId")
    boolean existsByUserId(@Param("userId") Integer userId);

    /**
     * Item-based CF: Tìm sản phẩm hay được mua cùng với productId
     * Dùng UserBehavior PURCHASE records: users nào mua productId cũng mua gì khác
     */
    @Query("""
        SELECT ub2.product.id, COUNT(ub2.id) as co_count
        FROM UserBehavior ub1
        JOIN UserBehavior ub2 ON ub1.user.id = ub2.user.id
        WHERE ub1.product.id = :productId
        AND ub1.actionType = 'PURCHASE'
        AND ub2.actionType = 'PURCHASE'
        AND ub2.product.id != :productId
        GROUP BY ub2.product.id
        ORDER BY COUNT(ub2.id) DESC
        """)
    List<Object[]> findCoPurchasedProducts(
            @Param("productId") Integer productId,
            Pageable pageable);

    /**
     * Weak CF signals: users nào VIEW/ADD_CART productId cũng tương tác với gì khác
     * Dùng khi PURCHASE data còn thưa
     */
    @Query("""
        SELECT ub2.product.id, SUM(ub2.weight) as total_score
        FROM UserBehavior ub1
        JOIN UserBehavior ub2 ON ub1.user.id = ub2.user.id
        WHERE ub1.product.id = :productId
        AND ub1.actionType IN ('PURCHASE', 'ADD_CART', 'WISHLIST_ADD')
        AND ub2.actionType IN ('PURCHASE', 'ADD_CART', 'WISHLIST_ADD')
        AND ub2.product.id != :productId
        AND ub2.weight > 0
        GROUP BY ub2.product.id
        ORDER BY SUM(ub2.weight) DESC
        """)
    List<Object[]> findCoInteractedProducts(
            @Param("productId") Integer productId,
            Pageable pageable);

    // ══════════════════════════════════════════════════════════════════════════
    // STATISTICS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Đếm số hành vi theo loại
     */
    @Query("""
        SELECT ub.actionType, COUNT(ub)
        FROM UserBehavior ub
        WHERE ub.createdAt > :since
        GROUP BY ub.actionType
        """)
    List<Object[]> countByActionType(@Param("since") LocalDateTime since);

    /**
     * Kiểm tra user đã có hành vi với product chưa (để tránh duplicate)
     */
    boolean existsByUserIdAndProductIdAndActionType(
            Integer userId, Integer productId, ActionType actionType);

    /**
     * Đếm số unique users có hành vi
     */
    @Query("SELECT COUNT(DISTINCT ub.user.id) FROM UserBehavior ub")
    Long countDistinctUsers();

    /**
     * Đếm số unique products có hành vi
     */
    @Query("SELECT COUNT(DISTINCT ub.product.id) FROM UserBehavior ub")
    Long countDistinctProducts();

    /**
     * Tìm VIEW behavior record gần đây nhất để update duration
     * Đơn giản: match cả userId + sessionId + productId + actionType
     */
    @Query("""
        SELECT ub FROM UserBehavior ub
        WHERE ub.product.id = :productId
        AND ub.actionType = :actionType
        AND ub.user.id = :userId
        AND ub.sessionId = :sessionId
        AND ub.viewDurationSeconds IS NULL
        ORDER BY ub.createdAt DESC
        LIMIT 1
        """)
    UserBehavior findLatestViewBehavior(
        @Param("userId") Integer userId,
        @Param("productId") Integer productId, 
        @Param("sessionId") String sessionId,
        @Param("actionType") ActionType actionType
    );
}
