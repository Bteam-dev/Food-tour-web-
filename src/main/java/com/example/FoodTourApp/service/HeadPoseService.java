package com.example.FoodTourApp.service;

/**
 * Head pose estimation using WHENet.
 * Estimates yaw/pitch/roll from a face image for liveness challenge verification.
 */
public interface HeadPoseService {

    enum HeadAction { TURN_LEFT, TURN_RIGHT, LOOK_UP, LOOK_DOWN }

    record PoseResult(float yaw, float pitch, float roll) {}

    HeadAction randomChallenge();

    PoseResult estimatePose(String base64Image) throws Exception;

    PoseResult estimatePoseFromBytes(byte[] imageBytes) throws Exception;

    boolean verifyChallenge(PoseResult pose, HeadAction required);
}
