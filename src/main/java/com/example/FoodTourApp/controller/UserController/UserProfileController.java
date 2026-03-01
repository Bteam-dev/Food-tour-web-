package com.example.FoodTourApp.controller.UserController;

import com.example.FoodTourApp.DTO.UserDTO.UpdateProfileRequest;
import com.example.FoodTourApp.DTO.UserDTO.UserResponse;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user/profile")
@PreAuthorize("hasAnyRole('USER', 'SELLER', 'RESELLER', 'ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class UserProfileController {

    private final UserService userService;
    private final ObjectMapper objectMapper;

    /**
     * Xem thông tin profile cá nhân
     * GET /api/user/profile
     */
    @GetMapping
    public ResponseEntity<?> getMyProfile(@AuthenticationPrincipal User user) {
        log.info("User ID {} is retrieving their profile", user.getId());

        try {
            UserResponse response = userService.getMyProfile(user.getId());

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", response);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error retrieving profile for user ID {}: {}", user.getId(), e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Cập nhật profile cá nhân (có thể upload avatar)
     * PUT /api/user/profile
     *
     * Cách sử dụng:
     * - Không có avatar: gửi application/json với UpdateProfileRequest
     * - Có avatar: gửi multipart/form-data với data (JSON string) + file avatar
     */
    @PutMapping
    public ResponseEntity<?> updateProfile(
            @RequestParam(value = "data", required = false) String dataJson,
            @RequestParam(value = "avatar", required = false) MultipartFile avatar,
            @AuthenticationPrincipal User user) {
        log.info("User ID {} is updating their profile", user.getId());

        try {
            UpdateProfileRequest request;

            // Nếu có dataJson (form-data) thì parse, nếu không thì tạo request rỗng
            if (dataJson != null && !dataJson.isEmpty()) {
                request = objectMapper.readValue(dataJson, UpdateProfileRequest.class);
            } else {
                request = new UpdateProfileRequest();
            }

            UserResponse response = userService.updateProfileWithAvatar(user.getId(), request, avatar);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Cập nhật hồ sơ thành công");
            result.put("data", response);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error updating profile for user ID {}: {}", user.getId(), e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}