package com.example.FoodTourApp.controller.UserController;

import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user/fcm-token")
@RequiredArgsConstructor
@Slf4j
public class FCMTokenController {

    private final UserService userService;

    /**
     * Đăng ký / cập nhật FCM token cho thiết bị hiện tại.
     * Gọi API này mỗi khi app khởi động hoặc sau khi đăng nhập để cập nhật token mới nhất.
     *
     * Token KHÔNG bị xóa khi logout – tài khoản vẫn nhận thông báo trên thiết bị đó
     * giống hành vi của Facebook / Shopee / Grab.
     *
     * Token chỉ bị thay thế khi:
     *  - Người dùng đăng nhập trên thiết bị khác (token mới ghi đè)
     *  - Firebase tự động làm mới token (app gọi lại API này)
     *
     * POST /api/user/fcm-token
     * Body: { "token": "device_fcm_token_here" }
     */
    @PostMapping
    public ResponseEntity<?> registerToken(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, String> body) {

        String token = body.get("token");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "FCM token không được để trống"
            ));
        }

        userService.registerFcmToken(user.getId(), token);

        log.info("FCM token registered/updated for user {}", user.getId());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "FCM token đã được cập nhật thành công"
        ));
    }
}
