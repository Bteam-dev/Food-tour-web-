package com.example.FoodTourApp.DTO.UserDTO;

import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateUserByAdminRequest {
    private String username;
    private String email;
    private String password;
    private String fullName;
    private String phone;
    private LocalDate dateOfBirth;
    private String gender;
    private String roleName;
    private Boolean isActive;
}
