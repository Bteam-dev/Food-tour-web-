package com.example.FoodTourApp.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "wallet_transactions")
@Data
public class WalletTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private TransactionType transactionType;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "balance_before", nullable = false)
    private BigDecimal balanceBefore;

    @Column(name = "balance_after", nullable = false)
    private BigDecimal balanceAfter;

    @Column(name = "external_order_id")
    private String externalOrderId;

    @Column(name = "status")
    private TransactionStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum TransactionType {
        deposit,           // Nạp tiền vào ví
        withdrawal,        // Rút tiền từ ví
        payment,           // Thanh toán đơn hàng (trừ tiền người mua)
        refund,            // Hoàn tiền
        received_payment,  // Nhận tiền từ đơn hàng (cộng tiền người bán)
        admin_adjustment   // Admin điều chỉnh số dư
    }

    public enum TransactionStatus {
        PENDING, SUCCESS, FAILED
    }
}

