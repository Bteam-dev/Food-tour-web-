package com.example.FoodTourApp.DTO.UserDTO;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class UserResponse {
    private Integer id;
    private String username;
    private String email;
    private String fullName;
    private String phone;
    private String avatarUrl;
    private LocalDate dateOfBirth;
    private String gender;
    private String roleName;
    private boolean isActive;
    private boolean emailVerified;
    private LocalDateTime lastLogin;

    public void setIsActive(boolean isActive) {
        this.isActive = isActive;
    }

}
