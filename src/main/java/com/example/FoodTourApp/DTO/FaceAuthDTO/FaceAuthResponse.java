package com.example.FoodTourApp.DTO.FaceAuthDTO;

import com.example.FoodTourApp.DTO.UserDTO.UserResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FaceAuthResponse {

    public enum Status { PASS, FAIL, CHALLENGE_REQUIRED, ENROLLED }

    private Status status;
    private String message;
    private Double similarity;
    private Double livenessScore;

    // Only present when status = CHALLENGE_REQUIRED
    private String challengeAction;   // TURN_LEFT | TURN_RIGHT | LOOK_UP | LOOK_DOWN
    private String challengeToken;    // Short-lived token to resume after challenge

    // Only present when status = PASS (login flow) – matches AuthResponse format
    private String accessToken;
    private String refreshToken;
    private UserResponse user;
}
