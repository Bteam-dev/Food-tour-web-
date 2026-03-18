package com.example.FoodTourApp.controller.PublicController;

import com.example.FoodTourApp.DTO.PageResponse;
import com.example.FoodTourApp.DTO.ProductDTO.ProductResponseDTO;
import com.example.FoodTourApp.service.FoodDetectionService;
import com.example.FoodTourApp.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/public/products")
public class PublicProductController {

    private static final Logger logger = LoggerFactory.getLogger(PublicProductController.class);
    private final ProductService productService;
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

    public PublicProductController(ProductService productService, FoodDetectionService foodDetectionService) {
        this.productService = productService;
        this.foodDetectionService = foodDetectionService;
    }

    /**
     * GET /api/public/products
     * Lấy danh sách sản phẩm, hỗ trợ lọc tùy chọn:
     *   ?city=HCM                          lọc theo thành phố
     *   ?categoryId=1                      lọc theo danh mục
     *   ?keyword=gà                        tìm kiếm theo tên sản phẩm
     *   ?minPrice=10000&maxPrice=100000     lọc theo khoảng giá
     *   ?sortBy=rating_desc|best_selling|newest|price_asc|price_desc
     *   ?page=0&size=20
     * Nếu không truyền param nào thì trả về tất cả sản phẩm active (sắp xếp theo createdAt DESC).
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
        logger.info("Fetching active products - city={}, categoryId={}, sortBy={}, keyword={}, minPrice={}, maxPrice={}, page={}, size={}",
                city, categoryId, sortBy, keyword, minPrice, maxPrice, page, size);

        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<ProductResponseDTO> products = productService.getFilteredProducts(sortBy, city, categoryId, keyword, minPrice, maxPrice, pageable);
            PageResponse<ProductResponseDTO> pageResponse = PageResponse.of(products);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", pageResponse);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error fetching active products: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        logger.info("Fetching active products for shop: {} with pagination", shopId);

        try {
            Sort sort = sortDir.equalsIgnoreCase("ASC") ?
                    Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
            Pageable pageable = PageRequest.of(page, size, sort);

            Page<ProductResponseDTO> products = productService.getActiveProductsByShop(shopId, pageable);
            PageResponse<ProductResponseDTO> pageResponse = PageResponse.of(products);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", pageResponse);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error fetching products for shop {}: {}", shopId, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to fetch products");
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @PostMapping("/detect-and-search")
    public ResponseEntity<?> detectAndSearch(@RequestParam("image") MultipartFile image) {
        logger.info("Nhận request detect món ăn từ ảnh");

        Map<String, Object> response = new HashMap<>();

        try {
            // Gọi service để detect (toàn bộ logic detect ở đây)
            List<String> detectedFoods = foodDetectionService.detectFoodNames(image);

            if (detectedFoods.isEmpty()) {
                response.put("success", false);
                response.put("message", "Không nhận diện được món ăn từ ảnh");
                return ResponseEntity.ok(response);
            }

            // Chuyển đổi class names sang keywords tìm kiếm
            Set<String> searchKeywords = new LinkedHashSet<>();
            for (String food : detectedFoods) {
                // Lấy keywords từ mapping, nếu không có thì dùng chính class name đã normalize
                List<String> keywords = FOOD_CLASS_KEYWORDS.get(food);
                if (keywords != null) {
                    searchKeywords.addAll(keywords);
                } else {
                    // Normalize class name: bỏ dấu gạch, lowercase
                    searchKeywords.add(food.replace("-", " ").toLowerCase());
                }
            }

            // Gọi service Product để tìm sản phẩm matching với TẤT CẢ keywords
            Map<Integer, ProductResponseDTO> uniqueProducts = new LinkedHashMap<>();
            for (String keyword : searchKeywords) {
                Page<ProductResponseDTO> page = productService.getProductsByNameContaining(keyword, PageRequest.of(0, 50));
                for (ProductResponseDTO product : page.getContent()) {
                    // Dùng Map để tránh trùng lặp sản phẩm
                    uniqueProducts.putIfAbsent(product.getId(), product);
                }
            }

            List<ProductResponseDTO> matchedProducts = new ArrayList<>(uniqueProducts.values());

            response.put("success", true);
            response.put("detected", detectedFoods);
            response.put("searchKeywords", searchKeywords); // Trả về để debug
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

}
