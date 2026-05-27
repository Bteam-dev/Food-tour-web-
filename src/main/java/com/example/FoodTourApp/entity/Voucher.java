package com.example.FoodTourApp.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Bảng Voucher - Mã giảm giá
 *
 * Scope:
 *  - PLATFORM: do Admin tạo, áp dụng cho toàn sàn
 *  - SHOP: do Seller tạo, chỉ áp dụng cho shop đó
 *
 * DiscountType:
 *  - PERCENT: giảm theo % (discountValue = %, maxDiscountAmount = trần giảm)
 *  - FIXED:   giảm cố định (discountValue = số tiền VND)
 *  - FREE_SHIP: miễn phí ship (discountValue không dùng)
 */
@Entity
@Table(name = "vouchers")
@Data
public class Voucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** Shop áp dụng – NULL nếu là voucher nền tảng (PLATFORM) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id")
    private Shop shop;

    /** Mã voucher – duy nhất, user nhập khi đặt hàng */
    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 20)
    private VoucherScope scope = VoucherScope.PLATFORM;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    /** Giá trị giảm: % (0-100) nếu PERCENT, VND nếu FIXED */
    @Column(name = "discount_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal discountValue;

    /** Trần giảm tối đa (chỉ dùng cho PERCENT, NULL = không giới hạn) */
    @Column(name = "max_discount_amount", precision = 15, scale = 2)
    private BigDecimal maxDiscountAmount;

    /** Giá trị đơn hàng tối thiểu để áp dụng (NULL = không yêu cầu) */
    @Column(name = "min_order_value", precision = 15, scale = 2)
    private BigDecimal minOrderValue;

    /** Tổng lượt dùng tối đa (NULL = không giới hạn) */
    @Column(name = "max_usage")
    private Integer maxUsage;

    /** Mỗi user tối đa dùng bao nhiêu lần (NULL = không giới hạn) */
    @Column(name = "max_usage_per_user")
    private Integer maxUsagePerUser;

    /** Đã dùng bao nhiêu lần */
    @Column(name = "used_count", nullable = false)
    private Integer usedCount = 0;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public enum VoucherScope {
        PLATFORM,   // Toàn sàn – Admin quản lý
        SHOP        // Theo shop – Seller quản lý
    }

    public enum DiscountType {
        PERCENT,    // Giảm %
        FIXED,      // Giảm số tiền cố định
        FREE_SHIP   // Miễn phí ship
    }
}

