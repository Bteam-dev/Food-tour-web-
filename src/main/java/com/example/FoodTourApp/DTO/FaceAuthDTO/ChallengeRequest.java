package com.example.FoodTourApp.DTO.FaceAuthDTO;

import lombok.Data;

@Data
public class ChallengeRequest {
    private String frame;           // base64 JPEG
    private String username;
    private String requiredAction;  // TURN_LEFT | TURN_RIGHT | LOOK_UP | LOOK_DOWN
    private String challengeToken;  // one-time token issued by /verify
}
