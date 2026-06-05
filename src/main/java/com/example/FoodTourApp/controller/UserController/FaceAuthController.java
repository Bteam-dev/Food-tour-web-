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
 * POST /api/face/enroll              → Enroll face (requires JWT – user must be logged in)
 * DELETE /api/face/enroll            → Delete face enrollment
 * GET  /api/face/status             → Check enrollment status
 * POST /api/face/verify-security    → Verify face as security method (uses verifyToken)
 * POST /api/face/challenge-security → Solve challenge as security method
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
     * Face verification as a security method during login.
     * Uses verifyToken from the login step.
     */
    @PostMapping("/verify-security")
    public ResponseEntity<FaceAuthResponse> verifyForSecurity(
            @RequestBody VerifyRequest request,
            @RequestParam("verifyToken") String verifyToken) {
        log.info("[FaceAuth] Security verify with verifyToken");
        try {
            FaceAuthResponse response = faceAuthService.verifyForSecurity(request, verifyToken);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[FaceAuth] Security verify error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    FaceAuthResponse.builder()
                            .status(FaceAuthResponse.Status.FAIL)
                            .message("Verification failed: " + e.getMessage())
                            .build()
            );
        }
    }

    /**
     * Face challenge as a security method during login.
     */
    @PostMapping("/challenge-security")
    public ResponseEntity<FaceAuthResponse> solveChallengeForSecurity(
            @RequestBody ChallengeRequest request,
            @RequestParam("verifyToken") String verifyToken) {
        log.info("[FaceAuth] Security challenge solve");
        try {
            FaceAuthResponse response = faceAuthService.solveChallengeForSecurity(request, verifyToken);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[FaceAuth] Security challenge error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    FaceAuthResponse.builder()
                            .status(FaceAuthResponse.Status.FAIL)
                            .message("Challenge failed: " + e.getMessage())
                            .build()
            );
        }
    }
}
