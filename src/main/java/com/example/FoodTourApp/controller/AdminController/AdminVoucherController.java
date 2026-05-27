package com.example.FoodTourApp.controller.AdminController;

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
 * Admin Voucher Controller
 * Admin tạo / quản lý voucher toàn sàn (PLATFORM scope)
 */
@RestController
@RequestMapping("/api/admin/vouchers")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AdminVoucherController {

    private final VoucherService voucherService;

    /** POST /api/admin/vouchers/platform – tạo voucher toàn sàn */
    @PostMapping("/platform")
    public ResponseEntity<?> createPlatformVoucher(
            @Valid @RequestBody CreateVoucherRequest request,
            @AuthenticationPrincipal User admin) {
        try {
            VoucherResponse v = voucherService.createPlatformVoucher(request, admin);
            return ResponseEntity.ok(Map.of("success", true, "data", v));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /** GET /api/admin/vouchers?page=0&size=20 – lấy tất cả voucher */
    @GetMapping
    public ResponseEntity<Page<VoucherResponse>> getAllVouchers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(voucherService.getAllVouchers(PageRequest.of(page, size)));
    }

    /** PATCH /api/admin/vouchers/{id}/toggle – bật/tắt bất kỳ voucher */
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<?> toggleVoucher(
            @PathVariable Integer id,
            @AuthenticationPrincipal User admin) {
        try {
            VoucherResponse v = voucherService.toggleActive(id, admin);
            return ResponseEntity.ok(Map.of("success", true, "data", v));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /** DELETE /api/admin/vouchers/{id} – vô hiệu hóa */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deactivateVoucher(
            @PathVariable Integer id,
            @AuthenticationPrincipal User admin) {
        try {
            voucherService.deactivate(id, admin);
            return ResponseEntity.ok(Map.of("success", true, "message", "Voucher đã được vô hiệu hóa"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}

