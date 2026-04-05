package com.example.FoodTourApp.controller.PublicController;

import com.example.FoodTourApp.DTO.PageResponse;
import com.example.FoodTourApp.DTO.ProductDTO.ProductResponseDTO;
import com.example.FoodTourApp.service.FoodDetectionService;
import com.example.FoodTourApp.service.ProductSearchService;
import com.example.FoodTourApp.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/public/products")
public class PublicProductController {

    private static final Logger logger = LoggerFactory.getLogger(PublicProductController.class);
    
    private final ProductService productService;
    private final ProductSearchService productSearchService;
    private final FoodDetectionService foodDetectionService;

    // Mapping từ class name của YOLO sang các từ khóa tìm kiếm tiếng Việt
    private static final Map<String, List<String>> FOOD_CLASS_KEYWORDS = new HashMap<>();
    static {
        FOOD_CLASS_KEYWORDS.put("Banh-Mi", Arrays.asList("bánh mì", "banh mi", "bánh mỳ"));
        FOOD_CLASS_KEYWORDS.put("Bot Chien", Arrays.asList("bột chiên", "bot chien"));
        FOOD_CLASS_KEYWORDS.put("Bun", Arrays.asList("bún", "bun"));
        FOOD_CLASS_KEYWORDS.put("Goi-Cuon", Arrays.asList("gỏi cuốn", "goi cuon", "gỏi"));
        FOOD_CLASS_KEYWORDS.put("Pho", Arrays.asList("phở", "pho"));
    }

    public PublicProductController(
            ProductService productService,
            ProductSearchService productSearchService,
            FoodDetectionService foodDetectionService
    ) {
        this.productService = productService;
        this.productSearchService = productSearchService;
        this.foodDetectionService = foodDetectionService;
    }

    /**
     * GET /api/public/products
     * Tìm kiếm sản phẩm với Elasticsearch (ONLY - no fallback).
     * 
     * Params:
     *   ?keyword=phở                        full-text search (tên, mô tả, nguyên liệu, tags)
     *   ?city=HCM                           lọc theo thành phố
     *   ?categoryId=1                       lọc theo danh mục
     *   ?minPrice=10000&maxPrice=100000     lọc theo khoảng giá
     *   ?sortBy=rating_desc|best_selling|newest|price_asc|price_desc
     *   ?page=0&size=20
     */
    @GetMapping
    public ResponseEntity<?> getAllActiveProducts(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        logger.info("ES Search - keyword={}, city={}, categoryId={}, sortBy={}, minPrice={}, maxPrice={}, page={}, size={}",
                keyword, city, categoryId, sortBy, minPrice, maxPrice, page, size);

        try {
            Pageable pageable = PageRequest.of(page, size);
            
            Page<ProductResponseDTO> products = productSearchService.searchProducts(
                    keyword, city, categoryId, minPrice, maxPrice, sortBy, pageable
            );

            PageResponse<ProductResponseDTO> pageResponse = PageResponse.of(products);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", pageResponse);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("ES Search error: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Elasticsearch error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * GET /api/public/products/suggest?q=ph&limit=5
     * Autocomplete/gợi ý sản phẩm khi người dùng gõ.
     */
    @GetMapping("/suggest")
    public ResponseEntity<?> suggestProducts(
            @RequestParam("q") String query,
            @RequestParam(defaultValue = "5") int limit) {
        
        logger.debug("Suggest products for query: {}", query);
        
        try {
            List<Map<String, Object>> suggestions = productSearchService.suggestProducts(query, limit);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", suggestions);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("Error suggesting products: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", true, "data", List.of()));
        }
    }

    /**
     * GET /api/public/products/cities
     * Lấy danh sách thành phố có sản phẩm (cho filter dropdown).
     */
    @GetMapping("/cities")
    public ResponseEntity<?> getAvailableCities() {
        try {
            List<String> cities = productSearchService.getAvailableCities();
            return ResponseEntity.ok(Map.of("success", true, "data", cities));
        } catch (Exception e) {
            logger.error("Error getting cities: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", true, "data", List.of()));
        }
    }

    /**
     * GET /api/public/products/price-range?city=HCM&categoryId=1
     * Lấy thống kê giá (min, max, avg) cho price slider.
     */
    @GetMapping("/price-range")
    public ResponseEntity<?> getPriceRange(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Integer categoryId) {
        
        try {
            ProductSearchService.PriceRangeStats stats = productSearchService.getPriceRangeStats(city, categoryId);
            
            Map<String, Object> data = new HashMap<>();
            data.put("minPrice", stats.minPrice());
            data.put("maxPrice", stats.maxPrice());
            data.put("avgPrice", stats.avgPrice());
            
            return ResponseEntity.ok(Map.of("success", true, "data", data));
        } catch (Exception e) {
            logger.error("Error getting price range: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", true, "data", Map.of()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getProductById(@PathVariable Integer id) {
        logger.info("Fetching product with id: {}", id);

        try {
            ProductResponseDTO product = productService.getProductById(id);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", product);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error fetching product {}: {}", id, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/shop/{shopId}")
    public ResponseEntity<?> getActiveProductsByShop(
            @PathVariable Integer shopId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "newest") String sortBy) {
        
        logger.info("ES Search for shop: {} with keyword={}", shopId, keyword);

        try {
            Pageable pageable = PageRequest.of(page, size);
            
            Page<ProductResponseDTO> products = productSearchService.searchProductsByShop(
                    shopId, keyword, sortBy, pageable
            );

            PageResponse<ProductResponseDTO> pageResponse = PageResponse.of(products);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", pageResponse);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("ES Search for shop {} error: {}", shopId, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Elasticsearch error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @PostMapping("/detect-and-search")
    public ResponseEntity<?> detectAndSearch(@RequestParam("image") MultipartFile image) {
        logger.info("Nhận request detect món ăn từ ảnh");

        Map<String, Object> response = new HashMap<>();

        try {
            // Gọi service để detect
            List<String> detectedFoods = foodDetectionService.detectFoodNames(image);

            if (detectedFoods.isEmpty()) {
                response.put("success", false);
                response.put("message", "Không nhận diện được món ăn từ ảnh");
                return ResponseEntity.ok(response);
            }

            // Chuyển đổi class names sang keywords tìm kiếm
            Set<String> searchKeywords = new LinkedHashSet<>();
            for (String food : detectedFoods) {
                List<String> keywords = FOOD_CLASS_KEYWORDS.get(food);
                if (keywords != null) {
                    searchKeywords.addAll(keywords);
                } else {
                    searchKeywords.add(food.replace("-", " ").toLowerCase());
                }
            }

            // Tìm kiếm bằng ES only
            Map<Integer, ProductResponseDTO> uniqueProducts = new LinkedHashMap<>();
            for (String keyword : searchKeywords) {
                Page<ProductResponseDTO> page = productSearchService.searchProducts(
                        keyword, null, null, null, null, null, PageRequest.of(0, 50)
                );
                for (ProductResponseDTO product : page.getContent()) {
                    uniqueProducts.putIfAbsent(product.getId(), product);
                }
            }

            List<ProductResponseDTO> matchedProducts = new ArrayList<>(uniqueProducts.values());

            response.put("success", true);
            response.put("detected", detectedFoods);
            response.put("searchKeywords", searchKeywords);
            response.put("products", matchedProducts);
            logger.info("Detect thành công: {} món ăn, {} keywords, {} sản phẩm matching", 
                    detectedFoods.size(), searchKeywords.size(), matchedProducts.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Lỗi khi xử lý detect-and-search: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Lỗi server: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * GET /api/public/products/similar/{productId}?limit=6
     * Gợi ý món tương tự - dùng ES only
     */
    @GetMapping("/similar/{productId}")
    public ResponseEntity<?> getSimilar(
            @PathVariable Integer productId,
            @RequestParam(defaultValue = "6") int limit
    ) {
        logger.info("Getting similar products for product {} (limit={})", productId, limit);
        try {
            List<ProductResponseDTO> similar = productSearchService.findSimilarProducts(productId, limit);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", similar);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error getting similar products {}: {}", productId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", e.getMessage())
            );
        }
    }
}
