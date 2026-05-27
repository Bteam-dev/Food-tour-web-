package com.example.FoodTourApp.controller.AdminController;

import com.example.FoodTourApp.DTO.PageResponse;
import com.example.FoodTourApp.DTO.ShopDTO.ShopResponseDTO;
import com.example.FoodTourApp.service.ShopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Admin Shop Controller - Quản lý shop và duyệt shop
 */
@RestController
@RequestMapping("/api/admin/shops")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AdminShopController {

    private final ShopService shopService;

    /**
     * Lấy tất cả shops (kể cả chưa verified, đã rejected)
     * GET /api/admin/shops
     */
    @GetMapping
    public ResponseEntity<?> getAllShops(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        try {
            Sort sort = sortDir.equalsIgnoreCase("ASC") ?
                    Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
            Pageable pageable = PageRequest.of(page, size, sort);
            Page<ShopResponseDTO> shops = shopService.getAllShopsForAdmin(pageable);
            PageResponse<ShopResponseDTO> pageResponse = PageResponse.of(shops);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", pageResponse);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error fetching all shops for admin: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Không thể lấy danh sách shop: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Lấy thông tin chi tiết một shop
     * GET /api/admin/shops/{shopId}
     */
    @GetMapping("/{shopId}")
    public ResponseEntity<?> getShopById(@PathVariable Integer shopId) {
        try {
            ShopResponseDTO shop = shopService.getShopById(shopId);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", shop);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error fetching shop {}: {}", shopId, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Admin duyệt shop - shop sẽ được set isVerified=true, isActive=true
     * POST /api/admin/shops/{shopId}/approve
     */
    @PostMapping("/{shopId}/approve")
    public ResponseEntity<?> approveShop(@PathVariable Integer shopId) {
        log.info("Admin approving shop: {}", shopId);
        try {
            ShopResponseDTO shop = shopService.approveShop(shopId);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Shop đã được duyệt thành công");
            result.put("data", shop);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error approving shop {}: {}", shopId, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Admin từ chối shop - shop sẽ được set isVerified=false, isActive=false
     * POST /api/admin/shops/{shopId}/reject
     * Body: { "reason": "Lý do từ chối" }
     */
    @PostMapping("/{shopId}/reject")
    public ResponseEntity<?> rejectShop(
            @PathVariable Integer shopId,
            @RequestBody(required = false) Map<String, String> body) {
        log.info("Admin rejecting shop: {}", shopId);
        try {
            String reason = (body != null) ? body.get("reason") : null;
            if (reason == null || reason.isBlank()) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "Lý do từ chối là bắt buộc khi từ chối shop");
                return ResponseEntity.badRequest().body(error);
            }
            ShopResponseDTO shop = shopService.rejectShop(shopId, reason);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Shop đã bị từ chối");
            result.put("data", shop);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error rejecting shop {}: {}", shopId, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}
