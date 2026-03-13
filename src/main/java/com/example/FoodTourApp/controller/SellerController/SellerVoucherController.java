package com.example.FoodTourApp.controller.SellerController;

import com.example.FoodTourApp.DTO.VoucherDTO.CreateVoucherRequest;
import com.example.FoodTourApp.DTO.VoucherDTO.VoucherResponse;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.service.VoucherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Map;

/**
 * Seller Voucher Controller
 * Seller tạo / quản lý voucher cho shop của mình
 */
@RestController
@RequestMapping("/api/seller/vouchers")
@PreAuthorize("hasAnyRole('SELLER','ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class SellerVoucherController {

    private final VoucherService voucherService;

    /** POST /api/seller/vouchers  – tạo voucher shop */
    @PostMapping
    public ResponseEntity<?> createShopVoucher(
            @Valid @RequestBody CreateVoucherRequest request,
            @AuthenticationPrincipal User seller) {
        try {
            VoucherResponse v = voucherService.createShopVoucher(request, seller);
            return ResponseEntity.ok(Map.of("success", true, "data", v));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /** GET /api/seller/vouchers?page=0&size=10 */
    @GetMapping
    public ResponseEntity<Page<VoucherResponse>> getMyVouchers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User seller) {
        return ResponseEntity.ok(voucherService.getMyShopVouchers(seller, PageRequest.of(page, size)));
    }

    /** PATCH /api/seller/vouchers/{id}/toggle  – bật/tắt voucher */
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<?> toggleVoucher(
            @PathVariable Integer id,
            @AuthenticationPrincipal User seller) {
        try {
            VoucherResponse v = voucherService.toggleActive(id, seller);
            return ResponseEntity.ok(Map.of("success", true, "data", v));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /** DELETE /api/seller/vouchers/{id}  – vô hiệu hóa voucher */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deactivateVoucher(
            @PathVariable Integer id,
            @AuthenticationPrincipal User seller) {
        try {
            voucherService.deactivate(id, seller);
            return ResponseEntity.ok(Map.of("success", true, "message", "Voucher đã được vô hiệu hóa"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}

