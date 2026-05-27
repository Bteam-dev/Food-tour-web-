package com.example.FoodTourApp.exception;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Xử lý tập trung tất cả exception từ các controller.
 * Đảm bảo mọi lỗi đều trả về cùng định dạng JSON rõ ràng.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ── Helper tạo error response ─────────────────────────────────────────────
    private Map<String, Object> error(String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", message);
        return body;
    }

    // ── 400 Bad Request ───────────────────────────────────────────────────────

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("IllegalArgumentException: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(error(ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        log.warn("IllegalStateException: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(error(ex.getMessage()));
    }

    /**
     * Xử lý lỗi validation từ @Valid trên DTO (MethodArgumentNotValidException).
     * Trả về danh sách các field lỗi.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("Validation error: {}", message);
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", "Dữ liệu không hợp lệ: " + message);
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
        body.put("errors", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    // ── 403 Forbidden ─────────────────────────────────────────────────────────

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("AccessDeniedException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(error("Bạn không có quyền thực hiện thao tác này"));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, Object>> handleSecurityException(SecurityException ex) {
        log.warn("SecurityException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(error(ex.getMessage()));
    }

    // ── 404 Not Found ─────────────────────────────────────────────────────────

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleEntityNotFound(EntityNotFoundException ex) {
        log.warn("EntityNotFoundException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(error(ex.getMessage()));
    }

    // ── 413 Payload Too Large ─────────────────────────────────────────────────

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("File upload too large: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(error("File tải lên quá lớn. Vui lòng kiểm tra giới hạn kích thước file."));
    }

    // ── 500 Internal Server Error ─────────────────────────────────────────────

    /**
     * RuntimeException: xử lý chung cho các lỗi nghiệp vụ (business logic errors).
     * Các service throw RuntimeException với message rõ ràng bằng tiếng Việt.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException ex) {
        // Phân loại message theo nội dung để trả HTTP status phù hợp
        String msg = ex.getMessage() != null ? ex.getMessage() : "Đã xảy ra lỗi không xác định";

        // Các lỗi do quyền hạn
        if (msg.contains("không có quyền") || msg.contains("quyền thực hiện")
                || msg.contains("permission") || msg.contains("Permission")) {
            log.warn("Permission denied: {}", msg);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error(msg));
        }

        // Các lỗi "không tìm thấy"
        if (msg.contains("không tìm thấy") || msg.contains("not found") || msg.contains("Not found")) {
            log.warn("Not found: {}", msg);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error(msg));
        }

        // Các lỗi nghiệp vụ (đơn hàng, giỏ hàng, shop...)
        log.error("RuntimeException: {}", msg);
        return ResponseEntity.badRequest().body(error(msg));
    }

    /**
     * Xử lý tất cả Exception còn lại (lỗi hệ thống nghiêm trọng).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.internalServerError()
                .body(error("Đã xảy ra lỗi hệ thống. Vui lòng thử lại sau."));
    }
}
