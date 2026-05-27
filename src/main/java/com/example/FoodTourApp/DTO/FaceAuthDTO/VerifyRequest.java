package com.example.FoodTourApp.DTO.FaceAuthDTO;

import lombok.Data;

@Data
public class VerifyRequest {
    private String username;
    // Current frame (base64 JPEG)
    private String frame;
    // Previous frame captured ~200ms before `frame` (base64 JPEG).
    // Required for 6-channel temporal anti-spoof model.
    // If null, falls back to single-frame texture check only.
    private String framePrev;
}
