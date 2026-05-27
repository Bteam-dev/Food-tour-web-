package com.example.FoodTourApp.DTO.ReviewDTO;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class ReplyRequest {

    @NotBlank(message = "Reply cannot be empty")
    @Size(max = 500, message = "Reply must not exceed 500 characters")
    private String reply;
}

