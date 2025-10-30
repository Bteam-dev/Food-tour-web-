package com.example.FoodTourApp.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "momo_transactions")
@Data
public class MomoTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "order_id", nullable = false, unique = true)
    private String orderId;  // Mã đơn hàng của mình

    @Column(name = "request_id", nullable = false)
    private String requestId;  // Request ID gửi cho MOMO

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "trans_id")
    private Long transId;  // Transaction ID từ MOMO (có sau khi thanh toán)

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MomoTransactionStatus status;

    @Column(name = "result_code")
    private Integer resultCode;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "pay_url", columnDefinition = "TEXT")
    private String payUrl;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public enum MomoTransactionStatus {
        pending,    // Đang chờ thanh toán
        success,    // Thanh toán thành công
        failed,     // Thanh toán thất bại
        cancelled   // Đã hủy
    }
}

