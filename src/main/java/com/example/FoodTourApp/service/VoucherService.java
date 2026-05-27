package com.example.FoodTourApp.service;

import com.example.FoodTourApp.DTO.VoucherDTO.CreateVoucherRequest;
import com.example.FoodTourApp.DTO.VoucherDTO.VoucherResponse;
import com.example.FoodTourApp.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface VoucherService {
    /** Admin tạo voucher toàn sàn */
    VoucherResponse createPlatformVoucher(CreateVoucherRequest request, User admin);

    /** Seller tạo voucher cho shop của mình */
    VoucherResponse createShopVoucher(CreateVoucherRequest request, User seller);

    /** Bật/tắt voucher */
    VoucherResponse toggleActive(Integer voucherId, User actor);

    /** Xóa (soft: isActive = false) */
    void deactivate(Integer voucherId, User actor);

    /** Lấy voucher toàn sàn đang hiệu lực (public), có thể lọc theo discountType */
    Page<VoucherResponse> getActivePlatformVouchers(Pageable pageable);

    /** Lấy voucher toàn sàn đang hiệu lực, lọc theo loại giảm giá (PERCENT/FIXED/FREE_SHIP) */
    Page<VoucherResponse> getActivePlatformVouchersByType(String discountType, Pageable pageable);

    /** Lấy voucher của shop đang hiệu lực (public) */
    Page<VoucherResponse> getActiveShopVouchers(Integer shopId, Pageable pageable);

    /** Lấy voucher của shop đang hiệu lực, lọc theo loại giảm giá */
    Page<VoucherResponse> getActiveShopVouchersByType(Integer shopId, String discountType, Pageable pageable);

    /** Seller lấy toàn bộ voucher shop mình */
    Page<VoucherResponse> getMyShopVouchers(User seller, Pageable pageable);

    /** Admin lấy toàn bộ */
    Page<VoucherResponse> getAllVouchers(Pageable pageable);

    /** Kiểm tra mã voucher hợp lệ (trả về thông tin voucher để FE preview) */
    VoucherResponse checkVoucher(String code);
}

