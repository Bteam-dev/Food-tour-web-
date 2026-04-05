package com.example.FoodTourApp.service;

import com.example.FoodTourApp.DTO.ProductDTO.ProductResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Service interface for Elasticsearch-based product search.
 * 
 * Architecture:
 * - SYNC: Uses Python script (scripts/search/sync_es_search.py) - giống pattern chatbot
 * - SEARCH: Uses ES Java client directly - để tận dụng Spring pagination
 * 
 * Index: foodtour_products_search
 */
public interface ProductSearchService {
    
    // ═══════════════════════════════════════════════════════════════════════════
    // SYNC OPERATIONS (gọi Python script)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Sync a single product to ES (called when product is created/updated).
     * Internally calls: python sync_es_search.py --product-id {id}
     */
    void syncProductToEs(Integer productId);
    
    /**
     * Delete a product from ES (called when product is deleted/unavailable).
     * Internally calls: python sync_es_search.py --delete-product-id {id}
     */
    void deleteProductFromEs(Integer productId);
    
    /**
     * Full sync all products (for initial setup or manual re-sync).
     * Internally calls: python sync_es_search.py --full
     */
    void fullSyncToEs();
    
    /**
     * Recreate index and sync all (for schema changes).
     * Internally calls: python sync_es_search.py --recreate-index
     */
    void recreateIndexAndSync();
    
    /**
     * Update sales statistics for all products.
     * Internally calls: python sync_es_search.py --update-sales
     */
    void updateSalesStatistics();
    
    // ═══════════════════════════════════════════════════════════════════════════
    // SEARCH OPERATIONS (ES Java client)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Main search method - tìm kiếm sản phẩm với full-text search, filter, sort.
     * Dùng cho API: GET /api/public/products
     *
     * @param keyword    Từ khóa tìm kiếm (tìm trong name, description, ingredients, tags)
     * @param city       Filter theo thành phố của shop
     * @param categoryId Filter theo category
     * @param minPrice   Giá tối thiểu (effective_price)
     * @param maxPrice   Giá tối đa (effective_price)
     * @param sortBy     Sắp xếp: newest, rating_desc, rating_asc, price_asc, price_desc, best_selling
     * @param pageable   Phân trang
     * @return Page<ProductResponseDTO> - Đã convert sang DTO để controller dùng trực tiếp
     */
    Page<ProductResponseDTO> searchProducts(
            String keyword,
            String city,
            Integer categoryId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String sortBy,
            Pageable pageable
    );
    
    /**
     * Search products by shop ID.
     * Dùng cho API: GET /api/public/products/shop/{shopId}
     */
    Page<ProductResponseDTO> searchProductsByShop(
            Integer shopId,
            String keyword,
            String sortBy,
            Pageable pageable
    );
    
    /**
     * Autocomplete/suggest - gợi ý khi người dùng gõ.
     * Return chỉ id, name, imageUrl, price để response nhanh.
     */
    List<Map<String, Object>> suggestProducts(String prefix, int limit);
    
    /**
     * Tìm sản phẩm tương tự (cùng category, tags gần giống).
     */
    List<ProductResponseDTO> findSimilarProducts(Integer productId, int limit);
    
    /**
     * Lấy danh sách thành phố có sản phẩm (cho filter dropdown).
     */
    List<String> getAvailableCities();
    
    /**
     * Lấy thống kê giá (min, max, avg) - cho price range slider.
     */
    PriceRangeStats getPriceRangeStats(String city, Integer categoryId);
    
    /**
     * Check xem ES có available không.
     */
    boolean isElasticsearchAvailable();
    
    // ═══════════════════════════════════════════════════════════════════════════
    // DTOs
    // ═══════════════════════════════════════════════════════════════════════════
    
    record PriceRangeStats(BigDecimal minPrice, BigDecimal maxPrice, BigDecimal avgPrice) {}
}
