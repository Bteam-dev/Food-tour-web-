package com.example.FoodTourApp.DTO.ChatDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.NotNull;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SendMessageRequest {
    @NotNull(message = "Conversation ID không được để trống")
    private Long conversationId;

    /** Nội dung text – bắt buộc khi messageType = TEXT, optional khi gửi file */
    private String content;

    /** TEXT | IMAGE | VIDEO | AUDIO | FILE – mặc định TEXT */
    private String messageType = "TEXT";

    /** Tên file gốc – có thể null khi là tin nhắn TEXT */
    private String fileName;

    /** MIME type – có thể null khi là tin nhắn TEXT */
    private String mimeType;

    /** Kích thước file (bytes) – có thể null khi là tin nhắn TEXT */
    private Long fileSize;

    /** Validation: tin nhắn TEXT tuyệt đối phải có nội dung */
    @AssertTrue(message = "Nội dung tin nhắn không được để trống")
    private boolean isContentValid() {
        if (messageType == null || "TEXT".equalsIgnoreCase(messageType)) {
            return content != null && !content.isBlank();
        }
        // Tin nhắn file: content là URL file, cũng không được null
        return content != null && !content.isBlank();
    }
}
