package com.example.FoodTourApp.DTO.ChatDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MessageResponse {
    private Long id;
    private Long conversationId;
    private Integer senderId;
    private String senderName;
    private String senderAvatar;
    private String content;
    private Boolean isRead;
    /** TEXT | IMAGE | VIDEO | AUDIO | FILE */
    private String messageType;
    /** Tên file gốc */
    private String fileName;
    /** MIME type (image/jpeg, video/mp4, audio/mpeg, application/pdf, ...) */
    private String mimeType;
    /** Kích thước file tính bằng bytes */
    private Long fileSize;
    private LocalDateTime createdAt;
}
