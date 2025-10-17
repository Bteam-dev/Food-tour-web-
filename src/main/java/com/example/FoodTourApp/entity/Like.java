package com.example.FoodTourApp.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "likes", uniqueConstraints = @UniqueConstraint(name = "unique_like", columnNames = {"user_id", "likeable_type", "likeable_id"}))
@Data
public class Like {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "likeable_type", nullable = false)
    private LikeableType likeableType;

    @Column(name = "likeable_id", nullable = false)
    private Integer likeableId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum LikeableType {
        post, comment
    }
}
