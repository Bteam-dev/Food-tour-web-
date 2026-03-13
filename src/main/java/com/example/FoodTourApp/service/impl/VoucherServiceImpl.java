package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.DTO.VoucherDTO.CreateVoucherRequest;
import com.example.FoodTourApp.DTO.VoucherDTO.VoucherResponse;
import com.example.FoodTourApp.entity.Role;
import com.example.FoodTourApp.entity.Shop;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.entity.Voucher;
import com.example.FoodTourApp.repository.ShopRepository;
import com.example.FoodTourApp.repository.VoucherRepository;
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

    @Override
    @Transactional
    public VoucherResponse createPlatformVoucher(CreateVoucherRequest request, User admin) {
        validateCode(request.getCode(), null);
        Voucher v = buildVoucher(request);
        v.setScope(Voucher.VoucherScope.PLATFORM);
        v.setShop(null);
        return mapToResponse(voucherRepository.save(v));
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
    public Page<VoucherResponse> getActiveShopVouchers(Integer shopId, Pageable pageable) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new RuntimeException("Shop không tồn tại"));
        return voucherRepository.findActiveVouchersByShop(shop, LocalDateTime.now(), pageable)
                .map(this::mapToResponse);
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
}

