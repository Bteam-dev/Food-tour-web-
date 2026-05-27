package com.example.FoodTourApp.DTO.FaceAuthDTO;

import lombok.Data;
import java.util.List;

@Data
public class EnrollRequest {
    // List of base64-encoded JPEG frames (10–20 frames)
    private List<String> frames;
}
