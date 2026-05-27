package com.example.FoodTourApp.controller.UserController;

import com.example.FoodTourApp.DTO.FaceAuthDTO.ChallengeRequest;
import com.example.FoodTourApp.DTO.FaceAuthDTO.EnrollRequest;
import com.example.FoodTourApp.DTO.FaceAuthDTO.FaceAuthResponse;
import com.example.FoodTourApp.DTO.FaceAuthDTO.VerifyRequest;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.service.FaceAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Face Authentication REST API
 *
 * POST /api/face/enroll        → Enroll face (requires JWT – user must be logged in)
 * POST /api/face/verify        → Verify face (public – used during login)
 * POST /api/face/challenge     → Solve active liveness challenge (public)
 */
@RestController
@RequestMapping("/api/face")
public class FaceAuthController {

    private static final Logger log = LoggerFactory.getLogger(FaceAuthController.class);

    private final FaceAuthService faceAuthService;

    public FaceAuthController(FaceAuthService faceAuthService) {
        this.faceAuthService = faceAuthService;
    }

    /**
     * Enroll face biometrics.
     * Requires the user to be authenticated (standard username/password login first).
     */
    @PostMapping("/enroll")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FaceAuthResponse> enroll(Authentication auth,
                                                   @RequestBody EnrollRequest request) {
        // Principal is User entity (set by JwtAuthenticationFilter), not a String
        String username = ((User) auth.getPrincipal()).getUsername();
        log.info("[FaceAuth] Enroll request for user={}", username);
        try {
            FaceAuthResponse response = faceAuthService.enroll(username, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[FaceAuth] Enroll error for user={}: {}", username, e.getMessage());
            return ResponseEntity.badRequest().body(
                    FaceAuthResponse.builder()
                            .status(FaceAuthResponse.Status.FAIL)
                            .message("Enrollment failed: " + e.getMessage())
                            .build()
            );
        }
    }

    /**
     * Verify face to authenticate (step in login flow).
     * Public endpoint – no JWT required here; JWT is returned on success.
     */
    @PostMapping("/verify")
    public ResponseEntity<FaceAuthResponse> verify(@RequestBody VerifyRequest request) {
        log.info("[FaceAuth] Verify request for user={}", request.getUsername());
        try {
            FaceAuthResponse response = faceAuthService.verify(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[FaceAuth] Verify error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    FaceAuthResponse.builder()
                            .status(FaceAuthResponse.Status.FAIL)
                            .message("Verification failed: " + e.getMessage())
                            .build()
            );
        }
    }

    /**
     * Delete face enrollment for the authenticated user.
     */
    @DeleteMapping("/enroll")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FaceAuthResponse> deleteEnrollment(Authentication auth) {
        String username = ((User) auth.getPrincipal()).getUsername();
        log.info("[FaceAuth] Delete enrollment request for user={}", username);
        try {
            FaceAuthResponse response = faceAuthService.deleteEnrollment(username);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[FaceAuth] Delete enrollment error for user={}: {}", username, e.getMessage());
            return ResponseEntity.badRequest().body(
                    FaceAuthResponse.builder()
                            .status(FaceAuthResponse.Status.FAIL)
                            .message("Xóa thất bại: " + e.getMessage())
                            .build()
            );
        }
    }

    /**
     * Check if the authenticated user has a face enrolled.
     */
    @GetMapping("/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<java.util.Map<String, Boolean>> getEnrollStatus(Authentication auth) {
        String username = ((User) auth.getPrincipal()).getUsername();
        boolean enrolled = faceAuthService.isEnrolled(username);
        return ResponseEntity.ok(java.util.Map.of("enrolled", enrolled));
    }

    /**
     * Submit challenge frame for active liveness verification.
     * Called after the client receives CHALLENGE_REQUIRED status.
     */
    @PostMapping("/challenge")
    public ResponseEntity<FaceAuthResponse> solveChallenge(@RequestBody ChallengeRequest request) {
        log.info("[FaceAuth] Challenge solve for user={} action={}", request.getUsername(), request.getRequiredAction());
        try {
            FaceAuthResponse response = faceAuthService.solveChallenge(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[FaceAuth] Challenge error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    FaceAuthResponse.builder()
                            .status(FaceAuthResponse.Status.FAIL)
                            .message("Challenge failed: " + e.getMessage())
                            .build()
            );
        }
    }
}
