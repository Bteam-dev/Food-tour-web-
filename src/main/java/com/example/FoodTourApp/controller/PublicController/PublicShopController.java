package com.example.FoodTourApp.controller.PublicController;

import com.example.FoodTourApp.DTO.ShopDTO.ShopResponseDTO;
import com.example.FoodTourApp.service.ShopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Public Shop Controller - Các endpoint xem shop (không cần authentication)
 */
@RestController
@RequestMapping("/api/public/shops")
@RequiredArgsConstructor
@Slf4j
public class PublicShopController {

    private final ShopService shopService;

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
     * Lấy tất cả cửa hàng đang hoạt động (PUBLIC - không cần authentication)
     * GET /api/shops
     */
    @GetMapping
    public ResponseEntity<List<ShopResponseDTO>> getAllActiveShops() {
        log.info("Getting all active shops");

        List<ShopResponseDTO> response = shopService.getAllActiveShops();
        return ResponseEntity.ok(response);
    }
}

