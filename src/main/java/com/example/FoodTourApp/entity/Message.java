package com.example.FoodTourApp.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
@Data
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false)
    private MessageType messageType = MessageType.TEXT;

    /** Tên file gốc (chỉ dùng khi messageType != TEXT) */
    @Column(name = "file_name")
    private String fileName;

    /** MIME type của file (vd: image/jpeg, video/mp4, audio/mpeg) */
    @Column(name = "mime_type")
    private String mimeType;

    /** Kích thước file tính bằng bytes */
    @Column(name = "file_size")
    private Long fileSize;

    public enum MessageType {
        TEXT,
        IMAGE,   // ảnh – hiển thị inline
        VIDEO,   // video – hiển thị player inline
        AUDIO,   // âm thanh – hiển thị player inline
        FILE     // file khác (PDF, docx, zip…) – hiển thị download
    }
}
