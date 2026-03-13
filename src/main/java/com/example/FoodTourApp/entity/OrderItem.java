package com.example.FoodTourApp.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@Data
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    // nullable = true để đảm bảo khi product bị xóa cứng (hiếm), order item không crash
    // Thực tế chỉ soft-delete, nhưng phòng tránh
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = true)
    private Product product;

    // ── Snapshot tại thời điểm đặt hàng ──────────────────────────────────────
    // Đảm bảo order item luôn hiển thị đúng dù product bị soft-delete hay thay đổi
    @Column(name = "product_name_snapshot", length = 150)
    private String productNameSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "product_image_urls_snapshot", columnDefinition = "TEXT")
    private String productImageUrlsSnapshot; // JSON array: ["url1","url2"]
    // ─────────────────────────────────────────────────────────────────────────

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "total_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalPrice;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "selected_variants")
    private String selectedVariants;

    @Column(name = "special_instructions", columnDefinition = "TEXT")
    private String specialInstructions;
}
