package com.example.FoodTourApp.controller.PublicController;

import com.example.FoodTourApp.DTO.VoucherDTO.VoucherResponse;
import com.example.FoodTourApp.service.VoucherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Public Voucher Controller
 * - Lấy danh sách voucher toàn sàn còn hiệu lực
 * - Lấy voucher của shop còn hiệu lực
 * - Kiểm tra mã voucher hợp lệ (preview trước khi đặt hàng)
 */
@RestController
@RequestMapping("/api/public/vouchers")
@RequiredArgsConstructor
@Slf4j
public class PublicVoucherController {

    private final VoucherService voucherService;

    /**
     * GET /api/public/vouchers/platform?page=0&size=10&discountType=PERCENT
     * discountType: PERCENT | FIXED | FREE_SHIP (tùy chọn, nếu không truyền trả tất cả)
     */
    @GetMapping("/platform")
    public ResponseEntity<Page<VoucherResponse>> getPlatformVouchers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String discountType) {
        if (discountType != null && !discountType.isBlank()) {
            return ResponseEntity.ok(
                    voucherService.getActivePlatformVouchersByType(discountType, PageRequest.of(page, size)));
        }
        return ResponseEntity.ok(voucherService.getActivePlatformVouchers(PageRequest.of(page, size)));
    }

    /**
     * GET /api/public/vouchers/shop/{shopId}?page=0&size=10&discountType=FIXED
     * discountType: PERCENT | FIXED | FREE_SHIP (tùy chọn)
     */
    @GetMapping("/shop/{shopId}")
    public ResponseEntity<Page<VoucherResponse>> getShopVouchers(
            @PathVariable Integer shopId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String discountType) {
        if (discountType != null && !discountType.isBlank()) {
            return ResponseEntity.ok(
                    voucherService.getActiveShopVouchersByType(shopId, discountType, PageRequest.of(page, size)));
        }
        return ResponseEntity.ok(voucherService.getActiveShopVouchers(shopId, PageRequest.of(page, size)));
    }

    /** GET /api/public/vouchers/check?code=SUMMER20 */
    @GetMapping("/check")
    public ResponseEntity<?> checkVoucher(@RequestParam String code) {
        try {
            VoucherResponse v = voucherService.checkVoucher(code);
            return ResponseEntity.ok(Map.of("success", true, "data", v));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}

