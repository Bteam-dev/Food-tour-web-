package com.example.FoodTourApp.DTO.ChatDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserListResponse {
    private Integer id;
    private String username;
    private String fullName;
    private String avatarUrl;
    private String email;
    private Boolean isActive;
}

