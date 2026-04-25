package com.example.FoodTourApp.controller.PublicController;

import com.example.FoodTourApp.DTO.PageResponse;
import com.example.FoodTourApp.DTO.ShopDTO.ShopResponseDTO;
import com.example.FoodTourApp.service.ProductSearchService;
import com.example.FoodTourApp.service.ShopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Public Shop Controller - Các endpoint xem shop (không cần authentication)
 */
@RestController
@RequestMapping("/api/public/shops")
@RequiredArgsConstructor
@Slf4j
public class PublicShopController {

    private final ShopService shopService;
    private final ProductSearchService productSearchService;

    /**
     * Lấy thông tin chi tiết cửa hàng (PUBLIC - không cần authentication)
     * GET /api/shops/{shopId}
     */
    @GetMapping("/{shopId}")
    public ResponseEntity<ShopResponseDTO> getShop(@PathVariable Integer shopId) {
        log.info("Getting shop: {}", shopId);

        ShopResponseDTO response = shopService.getShopById(shopId);
        return ResponseEntity.ok(response);
    }

    /**
     * Lấy tất cả cửa hàng đang hoạt động (PUBLIC - không cần authentication) - WITH PAGINATION
     * GET /api/public/shops
     */
    @GetMapping
    public ResponseEntity<?> getAllActiveShops(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        log.info("Getting all active shops with pagination - page: {}, size: {}", page, size);

        try {
            Sort sort = sortDir.equalsIgnoreCase("ASC") ?
                    Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
            Pageable pageable = PageRequest.of(page, size, sort);

            Page<ShopResponseDTO> shops = shopService.getAllActiveShops(pageable);
            PageResponse<ShopResponseDTO> pageResponse = PageResponse.of(shops);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", pageResponse);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error fetching active shops: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to fetch shops");
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Lấy danh sách thành phố có shop đang hoạt động (PUBLIC)
     * GET /api/public/shops/cities
     */
    @GetMapping("/cities")
    public ResponseEntity<?> getAvailableCities() {
        log.info("Getting available cities");
        try {
            List<String> cities = shopService.getAvailableCities();
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", cities);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error fetching cities: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to fetch cities");
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Lấy danh sách quận/huyện trong một thành phố (PUBLIC)
     * GET /api/public/shops/districts?city=Ho+Chi+Minh
     *
     * Dùng cho filter dropdown quận — chỉ hiện sau khi user chọn thành phố.
     * Trả về danh sách quận có sản phẩm available trong thành phố đó.
     */
    @GetMapping("/districts")
    public ResponseEntity<?> getAvailableDistricts(@RequestParam(required = false) String city) {
        log.info("Getting available districts for city={}", city);
        try {
            List<String> districts = productSearchService.getAvailableDistricts(city);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", districts);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error fetching districts for city={}: {}", city, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to fetch districts");
            return ResponseEntity.internalServerError().body(error);
        }
    }
}
