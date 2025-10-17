package com.example.FoodTourApp.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

@Entity
@Table(name = "vouchers")
@Data
public class Voucher {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false)
    private Double discountValue;

    @Column(name = "min_order_amount")
    private Double minOrderAmount = 0.0;

    @Column(name = "max_discount_amount")
    private Double maxDiscountAmount;

    @Column(name = "usage_limit")
    private Integer usageLimit = 1;

    @Column(name = "used_count")
    private Integer usedCount = 0;

    @Column(name = "user_usage_limit")
    private Integer userUsageLimit = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "applicable_to", nullable = false)
    private ApplicableTo applicableTo = ApplicableTo.all;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "applicable_ids")
    private String applicableIds;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum DiscountType {
        percentage, fixed_amount, free_shipping
    }

    public enum ApplicableTo {
        all, specific_shop, specific_category, specific_product
    }
}
