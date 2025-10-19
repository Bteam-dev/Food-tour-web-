package com.example.FoodTourApp.controller;

import com.example.FoodTourApp.DTO.ShopDTO.CreateShopRequestDTO;
import com.example.FoodTourApp.DTO.ShopDTO.ShopResponseDTO;
import com.example.FoodTourApp.DTO.ShopDTO.UpdateShopRequestDTO;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.service.ShopService;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller cho quản lý Shop
 * Hỗ trợ tạo shop kèm địa chỉ cùng lúc
 */
@RestController
@RequestMapping("/api/shops")
@RequiredArgsConstructor
@Slf4j
public class ShopController {

    private final ShopService shopService;

    /**
     * Tạo cửa hàng mới (bao gồm địa chỉ)
     * POST /api/shops
     *
     * Flow:
     * 1. Frontend gọi HERE API autocomplete để user chọn địa chỉ
     * 2. Frontend gọi HERE API lookup để lấy đầy đủ thông tin địa chỉ
     * 3. Frontend gửi request tạo shop kèm thông tin địa chỉ đã có
     *
     * Body mẫu:
     * {
     *   "shopName": "Quán Ăn Ngon",
     *   "description": "Món ăn địa phương",
     *   "phone": "0905123456",
     *   "email": "quan@example.com",
     *   "address": {
     *     "addressLine": "123 Nguyễn Văn Linh",
     *     "ward": "Hòa Xuân",
     *     "district": "Cẩm Lệ",
     *     "city": "Đà Nẵng",
     *     "country": "Vietnam",
     *     "postalCode": "550000",
     *     "latitude": 16.0544,
     *     "longitude": 108.2022
     *   }
     * }
     */
    @PostMapping
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ShopResponseDTO> createShop(
            @Valid @RequestBody CreateShopRequestDTO request,
            @AuthenticationPrincipal User seller) {

        log.info("Creating shop: {} by seller: {}", request.getShopName(), seller.getId());

        ShopResponseDTO response = shopService.createShop(request, seller);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Cập nhật thông tin cửa hàng
     * PUT /api/shops/{shopId}
     */
    @PutMapping("/{shopId}")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ShopResponseDTO> updateShop(
            @PathVariable Integer shopId,
            @Valid @RequestBody UpdateShopRequestDTO request,
            @AuthenticationPrincipal User seller) {

        log.info("Updating shop: {} by seller: {}", shopId, seller.getId());

        ShopResponseDTO response = shopService.updateShop(shopId, request, seller);
        return ResponseEntity.ok(response);
    }

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
     * Lấy danh sách cửa hàng của seller đang đăng nhập (chỉ SELLER)
     * GET /api/shops/my-shops
     */
    @GetMapping("/my-shops")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<List<ShopResponseDTO>> getMyShops(@AuthenticationPrincipal User seller) {
        log.info("Getting shops for seller: {}", seller.getId());

        List<ShopResponseDTO> response = shopService.getShopsBySeller(seller);
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

    /**
     * Xóa cửa hàng (soft delete) - chỉ SELLER
     * DELETE /api/shops/{shopId}
     */
    @DeleteMapping("/{shopId}")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<Void> deleteShop(
            @PathVariable Integer shopId,
            @AuthenticationPrincipal User seller) {

        log.info("Deleting shop: {} by seller: {}", shopId, seller.getId());

        shopService.deleteShop(shopId, seller);
        return ResponseEntity.noContent().build();
    }
}
