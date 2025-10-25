package com.example.FoodTourApp.controller.SellerController;

import com.example.FoodTourApp.DTO.ShopDTO.CreateShopRequestDTO;
import com.example.FoodTourApp.DTO.ShopDTO.ShopResponseDTO;
import com.example.FoodTourApp.DTO.ShopDTO.UpdateShopRequestDTO;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.service.impl.FileStorageService;
import com.example.FoodTourApp.service.ShopService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Seller Shop Controller - Các endpoint quản lý shop (chỉ Seller)
 * Admin cũng có thể dùng các endpoint này và có quyền động vào tất cả shop
 */
@RestController
@RequestMapping("/api/seller/shops")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
public class SellerShopController {

    private final ShopService shopService;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;

    /**
     * Tạo cửa hàng mới (có thể upload ảnh logo và banner từ máy)
     * POST /api/seller/shops
     *
     * Cách sử dụng giống như đăng bài trên Facebook:
     * - Nếu KHÔNG có ảnh: gửi application/json với data thông thường
     * - Nếu CÓ ảnh: gửi multipart/form-data với data (JSON string) + file logo/banner
     *
     * Form data (khi có ảnh):
     * - data: JSON string (CreateShopRequestDTO)
     * - logo: file (optional, jpg/jpeg/png, max 5MB)
     * - banner: file (optional, jpg/jpeg/png, max 5MB)
     */
    @PostMapping
    public ResponseEntity<ShopResponseDTO> createShop(
            @RequestParam(value = "data", required = false) String dataJson,
            @RequestParam(value = "logo", required = false) MultipartFile logo,
            @RequestParam(value = "banner", required = false) MultipartFile banner,
            @AuthenticationPrincipal User seller) {

        log.info("Creating shop by seller: {}", seller.getId());

        try {
            // Parse request từ JSON string
            if (dataJson == null || dataJson.isEmpty()) {
                throw new RuntimeException("Thiếu dữ liệu shop");
            }

            CreateShopRequestDTO request = objectMapper.readValue(dataJson, CreateShopRequestDTO.class);

            // Upload logo nếu có (giống như chọn ảnh đại diện trên Facebook)
            if (logo != null && !logo.isEmpty()) {
                String logoUrl = fileStorageService.storeFile(logo, "shops/" + seller.getId());
                request.setLogoUrl(logoUrl);
                log.info("Logo uploaded: {}", logoUrl);
            }

            // Upload banner nếu có (giống như chọn ảnh bìa trên Facebook)
            if (banner != null && !banner.isEmpty()) {
                String bannerUrl = fileStorageService.storeFile(banner, "shops/" + seller.getId());
                request.setBannerUrl(bannerUrl);
                log.info("Banner uploaded: {}", bannerUrl);
            }

            // Lưu shop vào database (đã có URL ảnh nếu user đã upload)
            ShopResponseDTO response = shopService.createShop(request, seller);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            log.error("Error creating shop: {}", e.getMessage(), e);
            throw new RuntimeException("Không thể tạo cửa hàng: " + e.getMessage());
        }
    }

    /**
     * Cập nhật thông tin cửa hàng (có thể upload ảnh logo và banner mới từ máy)
     * PUT /api/seller/shops/{shopId}
     *
     * Cách sử dụng giống như đổi ảnh đại diện trên Facebook:
     * - Nếu KHÔNG đổi ảnh: gửi application/json với data thông thường
     * - Nếu CÓ đổi ảnh: gửi multipart/form-data với data + file logo/banner mới
     */
    @PutMapping("/{shopId}")
    public ResponseEntity<ShopResponseDTO> updateShop(
            @PathVariable Integer shopId,
            @RequestParam(value = "data", required = false) String dataJson,
            @RequestParam(value = "logo", required = false) MultipartFile logo,
            @RequestParam(value = "banner", required = false) MultipartFile banner,
            @AuthenticationPrincipal User seller) {

        log.info("Updating shop: {} by seller: {}", shopId, seller.getId());

        try {
            // Parse request từ JSON string
            if (dataJson == null || dataJson.isEmpty()) {
                throw new RuntimeException("Thiếu dữ liệu cập nhật");
            }

            UpdateShopRequestDTO request = objectMapper.readValue(dataJson, UpdateShopRequestDTO.class);

            // Upload logo mới nếu có
            if (logo != null && !logo.isEmpty()) {
                String logoUrl = fileStorageService.storeFile(logo, "shops/" + seller.getId());
                request.setLogoUrl(logoUrl);
                log.info("Logo uploaded: {}", logoUrl);
            }

            // Upload banner mới nếu có
            if (banner != null && !banner.isEmpty()) {
                String bannerUrl = fileStorageService.storeFile(banner, "shops/" + seller.getId());
                request.setBannerUrl(bannerUrl);
                log.info("Banner uploaded: {}", bannerUrl);
            }

            // Lưu cập nhật vào database
            ShopResponseDTO response = shopService.updateShop(shopId, request, seller);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error updating shop: {}", e.getMessage(), e);
            throw new RuntimeException("Không thể cập nhật cửa hàng: " + e.getMessage());
        }
    }

    /**
     * Lấy danh sách cửa hàng của seller đang đăng nhập (chỉ SELLER)
     * GET /api/seller/shops/my-shops
     */
    @GetMapping("/my-shops")
    public ResponseEntity<List<ShopResponseDTO>> getMyShops(@AuthenticationPrincipal User seller) {
        log.info("Getting shops for seller: {}", seller.getId());

        List<ShopResponseDTO> response = shopService.getShopsBySeller(seller);
        return ResponseEntity.ok(response);
    }

    /**
     * Xóa cửa hàng (soft delete) - chỉ SELLER
     * DELETE /api/seller/shops/{shopId}
     */
    @DeleteMapping("/{shopId}")
    public ResponseEntity<Void> deleteShop(
            @PathVariable Integer shopId,
            @AuthenticationPrincipal User seller) {

        log.info("Deleting shop: {} by seller: {}", shopId, seller.getId());

        shopService.deleteShop(shopId, seller);
        return ResponseEntity.noContent().build();
    }
}
