package com.example.FoodTourApp.service;

import com.example.FoodTourApp.DTO.FaceAuthDTO.ChallengeRequest;
import com.example.FoodTourApp.DTO.FaceAuthDTO.EnrollRequest;
import com.example.FoodTourApp.DTO.FaceAuthDTO.FaceAuthResponse;
import com.example.FoodTourApp.DTO.FaceAuthDTO.VerifyRequest;

public interface FaceAuthService {
    FaceAuthResponse enroll(String username, EnrollRequest request) throws Exception;
    FaceAuthResponse verify(VerifyRequest request) throws Exception;
    FaceAuthResponse solveChallenge(ChallengeRequest request) throws Exception;
    FaceAuthResponse deleteEnrollment(String username) throws Exception;
    boolean isEnrolled(String username);

    /** Verify face as a security method during login (uses verifyToken, issues JWT on success) */
    FaceAuthResponse verifyForSecurity(VerifyRequest request, String verifyToken) throws Exception;
    FaceAuthResponse solveChallengeForSecurity(ChallengeRequest request, String verifyToken) throws Exception;
}
