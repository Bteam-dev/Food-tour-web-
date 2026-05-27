package com.example.FoodTourApp.service;

/**
 * Passive liveness detection — 6-channel temporal anti-spoofing model.
 */
public interface AntiSpoofService {

    record LivenessResult(boolean isLive, double score, boolean temporalAvailable) {}

    LivenessResult predict(String base64Frame, String base64FramePrev) throws Exception;

    LivenessResult predict(String base64Frame) throws Exception;
}
