package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.DTO.VoucherDTO.CreateVoucherRequest;
import com.example.FoodTourApp.DTO.VoucherDTO.VoucherResponse;
import com.example.FoodTourApp.entity.Role;
import com.example.FoodTourApp.entity.Shop;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.entity.Voucher;
import com.example.FoodTourApp.repository.ShopRepository;
import com.example.FoodTourApp.repository.UserRepository;
import com.example.FoodTourApp.repository.VoucherRepository;
import com.example.FoodTourApp.service.FCMService;
import com.example.FoodTourApp.service.VoucherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoucherServiceImpl implements VoucherService {

    private final VoucherRepository voucherRepository;
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final FCMService fcmService;

    @Override
    @Transactional
    public VoucherResponse createPlatformVoucher(CreateVoucherRequest request, User admin) {
        validateCode(request.getCode(), null);
        Voucher v = buildVoucher(request);
        v.setScope(Voucher.VoucherScope.PLATFORM);
        v.setShop(null);
        VoucherResponse response = mapToResponse(voucherRepository.save(v));

        // Gửi thông báo FCM cho tất cả người dùng có fcmToken
        try {
            String discountSummary = buildDiscountSummary(v);
            List<User> allUsers = userRepository.findAll();
            for (User user : allUsers) {
                try {
                    fcmService.sendVoucherCreatedNotification(user, v.getTitle(), v.getCode(), discountSummary);
                } catch (Exception e) {
                    log.warn("Không thể gửi FCM cho user {}: {}", user.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Lỗi khi gửi FCM voucher notification: {}", e.getMessage());
        }

        return response;
    }

    @Override
    @Transactional
    public VoucherResponse createShopVoucher(CreateVoucherRequest request, User seller) {
        // Tìm shop của seller
        Integer shopId = request.getShopId();
        if (shopId == null) throw new RuntimeException("Thiếu shopId");
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new RuntimeException("Shop không tồn tại"));

        boolean isAdmin = seller.getRole().getRoleName() == Role.RoleName.ADMIN;
        if (!isAdmin && !shop.getSeller().getId().equals(seller.getId()))
            throw new RuntimeException("Bạn không có quyền tạo voucher cho shop này");

        validateCode(request.getCode(), null);
        Voucher v = buildVoucher(request);
        v.setScope(Voucher.VoucherScope.SHOP);
        v.setShop(shop);
        return mapToResponse(voucherRepository.save(v));
    }

    @Override
    @Transactional
    public VoucherResponse toggleActive(Integer voucherId, User actor) {
        Voucher v = findAndAuthorize(voucherId, actor);
        v.setIsActive(!v.getIsActive());
        v.setUpdatedAt(LocalDateTime.now());
        return mapToResponse(voucherRepository.save(v));
    }

    @Override
    @Transactional
    public void deactivate(Integer voucherId, User actor) {
        Voucher v = findAndAuthorize(voucherId, actor);
        v.setIsActive(false);
        v.setUpdatedAt(LocalDateTime.now());
        voucherRepository.save(v);
    }

    @Override
    public Page<VoucherResponse> getActivePlatformVouchers(Pageable pageable) {
        return voucherRepository.findActivePlatformVouchers(LocalDateTime.now(), pageable)
                .map(this::mapToResponse);
    }

    @Override
    public Page<VoucherResponse> getActivePlatformVouchersByType(String discountType, Pageable pageable) {
        Voucher.DiscountType type = parseDiscountType(discountType);
        return voucherRepository.findActivePlatformVouchersByDiscountType(LocalDateTime.now(), type, pageable)
                .map(this::mapToResponse);
    }

    @Override
    public Page<VoucherResponse> getActiveShopVouchers(Integer shopId, Pageable pageable) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new RuntimeException("Shop không tồn tại"));
        return voucherRepository.findActiveVouchersByShop(shop, LocalDateTime.now(), pageable)
                .map(this::mapToResponse);
    }

    @Override
    public Page<VoucherResponse> getActiveShopVouchersByType(Integer shopId, String discountType, Pageable pageable) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new RuntimeException("Shop không tồn tại"));
        Voucher.DiscountType type = parseDiscountType(discountType);
        return voucherRepository.findActiveVouchersByShopAndDiscountType(shop, LocalDateTime.now(), type, pageable)
                .map(this::mapToResponse);
    }

    private Voucher.DiscountType parseDiscountType(String discountType) {
        try {
            return Voucher.DiscountType.valueOf(discountType.toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("Loại giảm giá không hợp lệ. Phải là: PERCENT, FIXED, hoặc FREE_SHIP");
        }
    }

    @Override
    public Page<VoucherResponse> getMyShopVouchers(User seller, Pageable pageable) {
        List<Shop> shops = shopRepository.findBySeller(seller);
        if (shops.isEmpty()) throw new RuntimeException("Bạn chưa có shop nào");
        return voucherRepository.findByShop(shops.get(0), pageable).map(this::mapToResponse);
    }

    @Override
    public Page<VoucherResponse> getAllVouchers(Pageable pageable) {
        return voucherRepository.findAll(pageable).map(this::mapToResponse);
    }

    @Override
    public VoucherResponse checkVoucher(String code) {
        Voucher v = voucherRepository.findByCodeAndIsActiveTrue(code)
                .orElseThrow(() -> new RuntimeException("Mã voucher không hợp lệ hoặc đã hết hạn"));
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(v.getStartDate()) || now.isAfter(v.getEndDate()))
            throw new RuntimeException("Mã voucher đã hết hạn");
        if (v.getMaxUsage() != null && v.getUsedCount() >= v.getMaxUsage())
            throw new RuntimeException("Mã voucher đã hết lượt sử dụng");
        return mapToResponse(v);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private Voucher findAndAuthorize(Integer voucherId, User actor) {
        Voucher v = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new RuntimeException("Voucher không tồn tại"));
        boolean isAdmin = actor.getRole().getRoleName() == Role.RoleName.ADMIN;
        if (!isAdmin) {
            if (v.getShop() == null || !v.getShop().getSeller().getId().equals(actor.getId()))
                throw new RuntimeException("Bạn không có quyền thao tác voucher này");
        }
        return v;
    }

    private void validateCode(String code, Integer excludeId) {
        voucherRepository.findByCode(code).ifPresent(existing -> {
            if (excludeId == null || !existing.getId().equals(excludeId))
                throw new RuntimeException("Mã voucher '" + code + "' đã tồn tại");
        });
    }

    private Voucher buildVoucher(CreateVoucherRequest req) {
        Voucher v = new Voucher();
        v.setCode(req.getCode().toUpperCase().trim());
        v.setTitle(req.getTitle());
        v.setDescription(req.getDescription());
        v.setDiscountType(Voucher.DiscountType.valueOf(req.getDiscountType().toUpperCase()));
        v.setDiscountValue(req.getDiscountValue());
        v.setMaxDiscountAmount(req.getMaxDiscountAmount());
        v.setMinOrderValue(req.getMinOrderValue());
        v.setMaxUsage(req.getMaxUsage());
        v.setMaxUsagePerUser(req.getMaxUsagePerUser());
        v.setUsedCount(0);
        v.setStartDate(req.getStartDate());
        v.setEndDate(req.getEndDate());
        v.setIsActive(true);
        v.setCreatedAt(LocalDateTime.now());
        v.setUpdatedAt(LocalDateTime.now());
        return v;
    }

    private VoucherResponse mapToResponse(Voucher v) {
        VoucherResponse dto = new VoucherResponse();
        dto.setId(v.getId());
        dto.setCode(v.getCode());
        dto.setTitle(v.getTitle());
        dto.setDescription(v.getDescription());
        dto.setScope(v.getScope().name());
        dto.setDiscountType(v.getDiscountType().name());
        dto.setDiscountValue(v.getDiscountValue());
        dto.setMaxDiscountAmount(v.getMaxDiscountAmount());
        dto.setMinOrderValue(v.getMinOrderValue());
        dto.setMaxUsage(v.getMaxUsage());
        dto.setMaxUsagePerUser(v.getMaxUsagePerUser());
        dto.setUsedCount(v.getUsedCount());
        dto.setStartDate(v.getStartDate());
        dto.setEndDate(v.getEndDate());
        dto.setIsActive(v.getIsActive());
        dto.setCreatedAt(v.getCreatedAt());
        if (v.getShop() != null) {
            dto.setShopId(v.getShop().getId());
            dto.setShopName(v.getShop().getShopName());
        }
        return dto;
    }

    /** Tạo chuỗi mô tả giá trị giảm để gửi FCM */
    private String buildDiscountSummary(Voucher v) {
        if (v.getDiscountType() == null) return "";
        return switch (v.getDiscountType()) {
            case PERCENT   -> "Giảm " + v.getDiscountValue().toPlainString() + "%";
            case FIXED     -> "Giảm " + v.getDiscountValue().toPlainString() + "đ";
            case FREE_SHIP -> "Miễn phí vận chuyển";
        };
    }
}

