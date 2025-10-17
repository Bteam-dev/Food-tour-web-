package com.example.FoodTourApp.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "reseller_commissions")
@Data
public class ResellerCommission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reseller_id", nullable = false)
    private User reseller;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "commission_rate", nullable = false)
    private Double commissionRate;

    @Column(name = "commission_amount", nullable = false)
    private Double commissionAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CommissionStatus status = CommissionStatus.pending;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum CommissionStatus {
        pending, approved, paid
    }
}
