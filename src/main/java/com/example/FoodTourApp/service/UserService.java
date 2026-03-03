package com.example.FoodTourApp.service;

import com.example.FoodTourApp.DTO.UserDTO.CreateUserRequest;
import com.example.FoodTourApp.DTO.UserDTO.UpdateProfileRequest;
import com.example.FoodTourApp.DTO.UserDTO.UpdateUserByAdminRequest;
import com.example.FoodTourApp.DTO.UserDTO.UserResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface UserService {
    UserResponse getUserByUsername(String username);
    UserResponse getUserByEmail(String email);
    boolean existsByUserName(String username);
    boolean existsByEmail(String email);
    boolean is2FAEnabled(String email);
    boolean verify2FA(String email, String code);

    // Admin
    List<UserResponse> getAllUsers();
    UserResponse getUserById(Integer id);
    UserResponse createUserByAdmin(CreateUserRequest request);
    UserResponse updateUserByAdmin(Integer id, UpdateUserByAdminRequest request);
    void deleteUser(Integer id);
    void toggleUserActiveStatus(Integer id);

    // User - Đổi từ email sang userId
    UserResponse updateProfile(Integer userId, UpdateProfileRequest request);
    UserResponse getMyProfile(Integer userId);
    void registerFcmToken(Integer userId, String fcmToken);

    UserResponse updateProfileWithAvatar(Integer userId, UpdateProfileRequest request, MultipartFile avatar);
}
