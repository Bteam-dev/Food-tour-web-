package com.example.FoodTourApp.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "product_variants")
@Data
public class ProductVariant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "variant_name", nullable = false, length = 100)
    private String variantName;

    @Column(name = "variant_value", nullable = false, length = 100)
    private String variantValue;

    @Column(name = "price_adjustment", nullable = false)
    private Double priceAdjustment = 0.0;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
