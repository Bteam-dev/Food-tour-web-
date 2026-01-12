package com.example.FoodTourApp.controller.UserController;

import com.example.FoodTourApp.DTO.UserDTO.UpdateProfileRequest;
import com.example.FoodTourApp.DTO.UserDTO.UserResponse;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user/profile")
@PreAuthorize("hasAnyRole('USER', 'SELLER', 'RESELLER', 'ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class UserProfileController {

    private final UserService userService;

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
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Cập nhật profile cá nhân
     * PUT /api/user/profile
     */
    @PutMapping
    public ResponseEntity<?> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal User user) {
        log.info("User ID {} is updating their profile", user.getId());

        try {
            UserResponse response = userService.updateProfile(user.getId(), request);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Cập nhật hồ sơ thành công");
            result.put("data", response);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error updating profile for user ID {}: {}", user.getId(), e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}