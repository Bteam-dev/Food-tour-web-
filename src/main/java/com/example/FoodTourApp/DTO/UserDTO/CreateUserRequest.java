package com.example.FoodTourApp.DTO.UserDTO;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.time.LocalDate;

@Data
public class CreateUserRequest {
    @NotBlank
    @Size(min = 3, max = 50)
    private String username;

    @NotBlank @Email
    private String email;

    @NotBlank @Size(min = 6)
    private String password;

    @NotBlank @Size(max = 100)
    private String fullName;

    @Size(max = 20)
    private String phone;

    private LocalDate dateOfBirth;

    private String gender; // "male", "female", "other"

    @NotBlank
    private String roleName; // "USER", "SELLER", "RESELLER", "ADMIN"

    private Boolean isActive;
    private Boolean emailVerified;
}
