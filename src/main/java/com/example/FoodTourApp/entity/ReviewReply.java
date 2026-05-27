package com.example.FoodTourApp.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "review_replies")
@Data
public class ReviewReply {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "reply_text", columnDefinition = "TEXT", nullable = false)
    private String replyText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "images")
    private String images; // JSON array chứa URLs của ảnh

    @Enumerated(EnumType.STRING)
    @Column(name = "reply_type", nullable = false)
    private ReplyType replyType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public enum ReplyType {
        SHOP_OWNER,  // Reply từ shop owner
        USER         // Reply từ user (người review)
    }
}