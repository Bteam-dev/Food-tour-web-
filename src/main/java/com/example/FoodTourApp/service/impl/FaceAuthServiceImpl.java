package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.DTO.FaceAuthDTO.ChallengeRequest;
import com.example.FoodTourApp.DTO.FaceAuthDTO.EnrollRequest;
import com.example.FoodTourApp.DTO.FaceAuthDTO.FaceAuthResponse;
import com.example.FoodTourApp.DTO.FaceAuthDTO.VerifyRequest;
import com.example.FoodTourApp.DTO.UserDTO.UserResponse;
import com.example.FoodTourApp.config.JWTConfig.JwtUtils;
import com.example.FoodTourApp.entity.FaceEmbedding;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.repository.FaceEmbeddingRepository;
import com.example.FoodTourApp.repository.UserRepository;
import com.example.FoodTourApp.service.AntiSpoofService;
import com.example.FoodTourApp.service.FaceAuthService;
import com.example.FoodTourApp.service.FaceEmbeddingExtractorService;
import com.example.FoodTourApp.service.HeadPoseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class FaceAuthServiceImpl implements FaceAuthService {

    private static final Logger log = LoggerFactory.getLogger(FaceAuthServiceImpl.class);

    // Thresholds — calibrated for ArcFace WITH 5-point alignment:
    //   Same person:  0.97–0.99
    //   Diff person:  0.15–0.40  (was 0.90–0.96 without alignment)
    private static final double PASS_THRESHOLD        = 0.82;
    private static final double CHALLENGE_LOW         = 0.65;
    private static final int    MIN_VALID_FRAMES      = 3;
    private static final Duration CHALLENGE_TTL       = Duration.ofSeconds(90);
    private static final int    MAX_VERIFY_ATTEMPTS   = 5;
    private static final Duration RATE_LIMIT_WINDOW   = Duration.ofMinutes(1);

    private final FaceEmbeddingExtractorService embeddingService;
    private final AntiSpoofService antiSpoofService;
    private final HeadPoseService headPoseService;
    private final FaceEmbeddingRepository embeddingRepo;
    private final UserRepository userRepo;
    private final JwtUtils jwtUtils;
    private final StringRedisTemplate redis;

    public FaceAuthServiceImpl(FaceEmbeddingExtractorService embeddingService,
                               AntiSpoofService antiSpoofService,
                               HeadPoseService headPoseService,
                               FaceEmbeddingRepository embeddingRepo,
                               UserRepository userRepo,
                               JwtUtils jwtUtils,
                               StringRedisTemplate redis) {
        this.embeddingService = embeddingService;
        this.antiSpoofService = antiSpoofService;
        this.headPoseService  = headPoseService;
        this.embeddingRepo    = embeddingRepo;
        this.userRepo         = userRepo;
        this.jwtUtils         = jwtUtils;
        this.redis            = redis;
    }

    // ── Enrollment ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public FaceAuthResponse enroll(String username, EnrollRequest request) throws Exception {
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        List<String> frames = request.getFrames();
        if (frames == null || frames.size() < 5) {
            return FaceAuthResponse.builder()
                    .status(FaceAuthResponse.Status.FAIL)
                    .message("Need at least 5 frames for enrollment")
                    .build();
        }

        // Extract embeddings from each frame (skip if no face detected)
        List<float[]> validEmbeddings = new ArrayList<>();
        for (String frame : frames) {
            try {
                float[] emb = embeddingService.extractEmbedding(frame);
                if (emb != null) validEmbeddings.add(emb);
            } catch (Exception e) {
                log.warn("[Enroll] Frame skipped: {}", e.getMessage());
            }
        }

        if (validEmbeddings.size() < MIN_VALID_FRAMES) {
            return FaceAuthResponse.builder()
                    .status(FaceAuthResponse.Status.FAIL)
                    .message("Not enough valid face frames. Please ensure good lighting and face visibility.")
                    .build();
        }

        float[] avgEmbedding = embeddingService.averageEmbeddings(validEmbeddings);

        // Persist or update face embedding
        FaceEmbedding faceEmbedding = embeddingRepo.findByUserId(user.getId())
                .orElse(new FaceEmbedding());
        faceEmbedding.setUser(user);
        faceEmbedding.setEmbeddingVector(floatsToString(avgEmbedding));
        faceEmbedding.setIsEnrolled(true);
        faceEmbedding.setUpdatedAt(LocalDateTime.now());
        if (faceEmbedding.getCreatedAt() == null) faceEmbedding.setCreatedAt(LocalDateTime.now());

        embeddingRepo.save(faceEmbedding);

        log.info("[Enroll] User {} enrolled with {} valid frames", username, validEmbeddings.size());
        return FaceAuthResponse.builder()
                .status(FaceAuthResponse.Status.ENROLLED)
                .message("Face enrolled successfully using " + validEmbeddings.size() + " frames")
                .build();
    }

    // ── Verification ───────────────────────────────────────────────────────────

    @Override
    public FaceAuthResponse verify(VerifyRequest request) throws Exception {
        String username = request.getUsername();
        String frame    = request.getFrame();

        // TODO: re-enable rate limiting after face auth is stable
//        String rateLimitKey = "face_verify_ratelimit:" + username;
//        Long attempts = redis.opsForValue().increment(rateLimitKey);
//        if (attempts != null && attempts == 1L) {
//            redis.expire(rateLimitKey, RATE_LIMIT_WINDOW);
//        }
//        if (attempts != null && attempts > MAX_VERIFY_ATTEMPTS) {
//            log.warn("[Verify] Rate limit exceeded for user={}", username);
//            return FaceAuthResponse.builder()
//                    .status(FaceAuthResponse.Status.FAIL)
//                    .message("Too many attempts. Please try again in 1 minute.")
//                    .build();
//        }

        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        FaceEmbedding stored = embeddingRepo.findByUserId(user.getId())
                .orElse(null);
        if (stored == null || !stored.getIsEnrolled()) {
            return FaceAuthResponse.builder()
                    .status(FaceAuthResponse.Status.FAIL)
                    .message("No face enrolled for this account")
                    .build();
        }

        // 1. Extract embedding from submitted frame
        float[] queryEmb = embeddingService.extractEmbedding(frame);
        if (queryEmb == null) {
            return FaceAuthResponse.builder()
                    .status(FaceAuthResponse.Status.FAIL)
                    .message("No face detected in the image")
                    .build();
        }

        // 2. Compute cosine similarity
        float[] storedEmb = stringToFloats(stored.getEmbeddingVector());
        double similarity = embeddingService.cosineSimilarity(queryEmb, storedEmb);
        log.info("[Verify] user={} similarity={}", username, String.format("%.4f", similarity));

        // 3. Passive liveness — 6-channel temporal if framePrev available
        String framePrev = request.getFramePrev();
        AntiSpoofService.LivenessResult liveness = antiSpoofService.predict(frame, framePrev);
        log.info("[Verify] user={} liveness={} score={} temporal={}",
                 username, liveness.isLive(),
                 String.format("%.4f", liveness.score()), liveness.temporalAvailable());

        // 4. Decision logic

        // Liveness clearly failed AND temporal signal was available → reject immediately.
        // Do NOT challenge: a fake face stays fake regardless of head pose.
        if (!liveness.isLive() && liveness.temporalAvailable()) {
            log.info("[Verify] user={} FAIL — liveness rejected (score={})",
                     username, String.format("%.4f", liveness.score()));
            return FaceAuthResponse.builder()
                    .status(FaceAuthResponse.Status.FAIL)
                    .message("Face verification failed")
                    .similarity(similarity)
                    .livenessScore(liveness.score())
                    .build();
        }

        // Liveness passes and similarity high enough → grant access
        if (similarity >= PASS_THRESHOLD && liveness.isLive()) {
            AuthTokens tokens = issueTokens(user);
            return FaceAuthResponse.builder()
                    .status(FaceAuthResponse.Status.PASS)
                    .message("Face verified successfully")
                    .similarity(similarity)
                    .livenessScore(liveness.score())
                    .accessToken(tokens.accessToken)
                    .refreshToken(tokens.refreshToken)
                    .user(buildUserResponse(user))
                    .build();
        }

        // Similarity in range but no temporal data (framePrev missing) → active challenge
        // Only reach here when we genuinely cannot decide (no temporal signal, uncertain model).
        if (similarity >= CHALLENGE_LOW) {
            HeadPoseService.HeadAction action = headPoseService.randomChallenge();
            String token = storeChallengeToken(username, action.name(), similarity);
            return FaceAuthResponse.builder()
                    .status(FaceAuthResponse.Status.CHALLENGE_REQUIRED)
                    .message("Additional verification required")
                    .similarity(similarity)
                    .livenessScore(liveness.score())
                    .challengeAction(action.name())
                    .challengeToken(token)
                    .build();
        }

        return FaceAuthResponse.builder()
                .status(FaceAuthResponse.Status.FAIL)
                .message("Face verification failed")
                .similarity(similarity)
                .livenessScore(liveness.score())
                .build();
    }

    // ── Challenge (active liveness) ────────────────────────────────────────────

    @Override
    public FaceAuthResponse solveChallenge(ChallengeRequest request) throws Exception {
        String token    = request.getChallengeToken();
        String username = request.getUsername();
        String frame    = request.getFrame();

        // 1. Validate challenge token from Redis
        String redisKey   = "face_challenge:" + token;
        String storedData = redis.opsForValue().get(redisKey);
        if (storedData == null) {
            return FaceAuthResponse.builder()
                    .status(FaceAuthResponse.Status.FAIL)
                    .message("Challenge expired or invalid")
                    .build();
        }

        String[] parts        = storedData.split(":");
        String storedUsername = parts[0];
        String storedAction   = parts[1];

        if (!storedUsername.equals(username)) {
            redis.delete(redisKey);
            return FaceAuthResponse.builder()
                    .status(FaceAuthResponse.Status.FAIL)
                    .message("Challenge token mismatch")
                    .build();
        }

        // 2. Delete token immediately — one-time use regardless of outcome
        redis.delete(redisKey);

        // 3. Head pose check
        HeadPoseService.HeadAction required = HeadPoseService.HeadAction.valueOf(storedAction);
        HeadPoseService.PoseResult pose = headPoseService.estimatePose(frame);
        boolean poseOk = headPoseService.verifyChallenge(pose, required);
        if (!poseOk) {
            return FaceAuthResponse.builder()
                    .status(FaceAuthResponse.Status.FAIL)
                    .message("Challenge not passed. Please try again.")
                    .build();
        }

        // 4. Re-verify face similarity from challenge frame (prevent attacker using own face)
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        FaceEmbedding storedFace = embeddingRepo.findByUserId(user.getId()).orElse(null);
        if (storedFace == null || !storedFace.getIsEnrolled()) {
            return FaceAuthResponse.builder()
                    .status(FaceAuthResponse.Status.FAIL)
                    .message("No face enrolled for this account")
                    .build();
        }

        float[] challengeEmb = embeddingService.extractEmbedding(frame);
        if (challengeEmb == null) {
            return FaceAuthResponse.builder()
                    .status(FaceAuthResponse.Status.FAIL)
                    .message("No face detected in challenge frame")
                    .build();
        }

        double challengeSimilarity = embeddingService.cosineSimilarity(
                challengeEmb, stringToFloats(storedFace.getEmbeddingVector()));
        log.info("[Challenge] user={} similarity={}", username, String.format("%.4f", challengeSimilarity));

        if (challengeSimilarity < PASS_THRESHOLD) {
            return FaceAuthResponse.builder()
                    .status(FaceAuthResponse.Status.FAIL)
                    .message("Face not recognized in challenge frame. Please try again.")
                    .similarity(challengeSimilarity)
                    .build();
        }

        // 5. All checks passed → issue tokens
        AuthTokens tokens = issueTokens(user);
        return FaceAuthResponse.builder()
                .status(FaceAuthResponse.Status.PASS)
                .message("Face verified with active liveness")
                .similarity(challengeSimilarity)
                .accessToken(tokens.accessToken)
                .refreshToken(tokens.refreshToken)
                .user(buildUserResponse(user))
                .build();
    }

    // ── Check Enrollment ──────────────────────────────────────────────────────

    @Override
    public boolean isEnrolled(String username) {
        return userRepo.findByUsername(username)
                .map(user -> embeddingRepo.findByUserId(user.getId())
                        .map(FaceEmbedding::getIsEnrolled)
                        .orElse(false))
                .orElse(false);
    }

    // ── Delete Enrollment ──────────────────────────────────────────────────────

    @Override
    @Transactional
    public FaceAuthResponse deleteEnrollment(String username) throws Exception {
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        FaceEmbedding embedding = embeddingRepo.findByUserId(user.getId()).orElse(null);
        if (embedding == null || !embedding.getIsEnrolled()) {
            return FaceAuthResponse.builder()
                    .status(FaceAuthResponse.Status.FAIL)
                    .message("Không có khuôn mặt nào được đăng ký cho tài khoản này")
                    .build();
        }

        embeddingRepo.delete(embedding);
        log.info("[FaceAuth] Enrollment deleted for user={}", username);
        return FaceAuthResponse.builder()
                .status(FaceAuthResponse.Status.PASS)
                .message("Đã xóa bảo mật khuôn mặt thành công")
                .build();
    }

    // ── Security Method Face Verification ─────────────────────────────────────

    @Override
    public FaceAuthResponse verifyForSecurity(VerifyRequest request, String verifyToken) throws Exception {
        // Validate verifyToken exists in Redis
        String redisKey = "security_verify:" + verifyToken;
        String userIdStr = redis.opsForValue().get(redisKey);
        if (userIdStr == null) {
            return FaceAuthResponse.builder()
                    .status(FaceAuthResponse.Status.FAIL)
                    .message("Token xác thực đã hết hạn. Vui lòng đăng nhập lại.")
                    .build();
        }

        Integer userId = Integer.parseInt(userIdStr);
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Override username from verifyToken
        request.setUsername(user.getUsername());

        FaceAuthResponse response = verify(request);

        // If PASS, delete verifyToken and issue tokens (the verify method already issues tokens)
        if (response.getStatus() == FaceAuthResponse.Status.PASS) {
            redis.delete(redisKey);
        }
        // If CHALLENGE_REQUIRED, store the verifyToken in the challenge data
        if (response.getStatus() == FaceAuthResponse.Status.CHALLENGE_REQUIRED && response.getChallengeToken() != null) {
            // Store verifyToken alongside the challenge
            String challengeKey = "face_challenge_verify:" + response.getChallengeToken();
            redis.opsForValue().set(challengeKey, verifyToken, Duration.ofSeconds(90));
        }

        return response;
    }

    @Override
    public FaceAuthResponse solveChallengeForSecurity(ChallengeRequest request, String verifyToken) throws Exception {
        // In the security flow the client doesn't know the username; extract it from the stored challenge token
        if (request.getUsername() == null || request.getUsername().isEmpty()) {
            String challengeKey = "face_challenge:" + request.getChallengeToken();
            String stored = redis.opsForValue().get(challengeKey);
            if (stored != null) {
                request.setUsername(stored.split(":")[0]);
            }
        }

        FaceAuthResponse response = solveChallenge(request);

        // If PASS, delete verifyToken
        if (response.getStatus() == FaceAuthResponse.Status.PASS) {
            String redisKey = "security_verify:" + verifyToken;
            redis.delete(redisKey);
        }

        return response;
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private record AuthTokens(String accessToken, String refreshToken) {}

    private AuthTokens issueTokens(User user) {
        List<String> roles = List.of(user.getRole().getRoleName().name());
        String accessToken  = jwtUtils.generateAccessToken(user.getId(), user.getUsername(), roles, "face-auth");
        String refreshToken = jwtUtils.generateRefreshToken(user.getId(), roles);
        return new AuthTokens(accessToken, refreshToken);
    }

    private UserResponse buildUserResponse(User user) {
        UserResponse dto = new UserResponse();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setFullName(user.getFullName());
        dto.setPhone(user.getPhone());
        dto.setAvatarUrl(user.getAvatarUrl());
        dto.setDateOfBirth(user.getDateOfBirth());
        dto.setGender(user.getGender() != null ? user.getGender().name() : null);
        dto.setRoleName(user.getRole().getRoleName().name());
        dto.setIsActive(user.getIsActive());
        dto.setEmailVerified(user.getEmailVerified());
        dto.setLastLogin(user.getLastLogin());
        return dto;
    }

    private String storeChallengeToken(String username, String action, double similarity) {
        String token    = UUID.randomUUID().toString().replace("-", "");
        String redisKey = "face_challenge:" + token;
        redis.opsForValue().set(redisKey, username + ":" + action + ":" + similarity, CHALLENGE_TTL);
        return token;
    }

    private String floatsToString(float[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    private float[] stringToFloats(String s) {
        String[] parts = s.split(",");
        float[] arr = new float[parts.length];
        for (int i = 0; i < parts.length; i++) arr[i] = Float.parseFloat(parts[i]);
        return arr;
    }
}
