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

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_before", nullable = false, precision = 15, scale = 2)
    private BigDecimal balanceBefore;

    @Column(name = "balance_after", nullable = false, precision = 15, scale = 2)
    private BigDecimal balanceAfter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "momo_transaction_id")
    private MomoTransaction momoTransaction;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum TransactionType {
        deposit,            // Nạp tiền vào ví
        withdrawal,         // Rút tiền từ ví
        payment,            // Thanh toán đơn hàng (trừ tiền người mua)
        refund,             // Hoàn tiền cho người mua
        received_payment,   // Nhận tiền từ đơn hàng sau khi giao thành công (seller)
        admin_adjustment,   // Admin điều chỉnh số dư thủ công
        platform_commission, // Hoa hồng nền tảng (admin giữ sau khi đơn giao xong)
        escrow_hold,        // Giữ tiền escrow khi buyer thanh toán (cộng vào admin, chờ giao)
        escrow_release,     // Giải phóng escrow cho seller sau khi giao thành công
        commission_refund   // Hoàn hoa hồng về buyer khi refund sau delivered
    }
}
