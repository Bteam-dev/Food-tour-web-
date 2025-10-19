package com.example.FoodTourApp.service;

import com.example.FoodTourApp.DTO.UserDTO.UserResponse;

public interface UserService {
    UserResponse getUserByUsername(String username);
    UserResponse getUserByEmail(String email);
    boolean existsByUserName(String username);
    boolean existsByEmail(String email);
    boolean is2FAEnabled(String email);
    boolean verify2FA(String email, String code);
}

