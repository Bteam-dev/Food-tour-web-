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
     * Tạo cửa hàng mới (upload ảnh logo và banner dưới dạng form-data)
     * POST /api/seller/shops
     *
     * Cách sử dụng:
     * - Content-Type: multipart/form-data
     * - data: JSON string (CreateShopRequestDTO) - REQUIRED
     * - logo: file (optional, jpg/jpeg/png, max 5MB)
     * - banner: file (optional, jpg/jpeg/png, max 5MB)
     *
     * NOTE: Không cần gửi logoUrl/bannerUrl trong JSON nữa, chỉ cần upload file
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

            // Tạo subfolder ID dựa trên seller ID (vì chưa có shop ID)
            String subfolderId = "seller_" + seller.getId();

            // Upload logo nếu có - lưu vào thư mục ShopLogo/seller_X/
            if (logo != null && !logo.isEmpty()) {
                String logoUrl = fileStorageService.storeFile(logo, FileStorageService.FileCategory.SHOP_LOGO, subfolderId);
                request.setLogoUrl(logoUrl);
                log.info("Logo uploaded: {}", logoUrl);
            }

            // Upload banner nếu có - lưu vào thư mục ShopBanner/seller_X/
            if (banner != null && !banner.isEmpty()) {
                String bannerUrl = fileStorageService.storeFile(banner, FileStorageService.FileCategory.SHOP_BANNER, subfolderId);
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
     * Cập nhật thông tin cửa hàng (upload ảnh logo và banner mới dưới dạng form-data)
     * PUT /api/seller/shops/{shopId}
     *
     * Cách sử dụng:
     * - Content-Type: multipart/form-data
     * - data: JSON string (UpdateShopRequestDTO) - REQUIRED
     * - logo: file (optional, nếu muốn đổi logo mới)
     * - banner: file (optional, nếu muốn đổi banner mới)
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

            // Tạo subfolder ID dựa trên shop ID - để phân biệt từng shop
            String subfolderId = "shop_" + shopId;

            // Upload logo mới nếu có - lưu vào thư mục ShopLogo/shop_X/
            if (logo != null && !logo.isEmpty()) {
                String logoUrl = fileStorageService.storeFile(logo, FileStorageService.FileCategory.SHOP_LOGO, subfolderId);
                request.setLogoUrl(logoUrl);
                log.info("Logo uploaded: {}", logoUrl);
            }

            // Upload banner mới nếu có - lưu vào thư mục ShopBanner/shop_X/
            if (banner != null && !banner.isEmpty()) {
                String bannerUrl = fileStorageService.storeFile(banner, FileStorageService.FileCategory.SHOP_BANNER, subfolderId);
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
