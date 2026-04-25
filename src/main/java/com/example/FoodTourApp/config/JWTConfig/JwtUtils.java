package com.example.FoodTourApp.config.JWTConfig;

import com.example.FoodTourApp.service.impl.TokenStorageService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;

@Component
public class JwtUtils {
    private static final Logger log = LoggerFactory.getLogger(JwtUtils.class);
    
    @Value("${JWT_SECRET}")
    private String jwtSecret;
    
    private SecretKey key;
    
    @Value("${JWT_EXPIRATION}")
    private long JWT_EXPIRATION;
    
    @Value("${JWT_REFRESH_EXPIRATION}")
    private long REFRESH_TOKEN_EXPIRATION;
    
    private final String JWT_COOKIE_NAME = "jwt";
    
    @Autowired
    private TokenStorageService tokenStorageService;
    
    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
        log.info("JwtUtils initialized with JWT_EXPIRATION: {}ms, REFRESH_EXPIRATION: {}ms", 
                 JWT_EXPIRATION, REFRESH_TOKEN_EXPIRATION);
    }

    /**
     * Tạo Access Token với userId, username và roles (truyền qua Header)
     * Subject của token là userId thay vì username
     * 🔥 NEW: Lưu token vào Redis để tăng cường bảo mật
     */
    public String generateAccessToken(Integer userId, String username, List<String> roles, String deviceInfo) {
        long expiryTime = System.currentTimeMillis() + JWT_EXPIRATION;
        
        String token = Jwts.builder()
                .subject(userId.toString())  // Dùng userId làm subject
                .claim("username", username)  // Lưu username vào claim
                .claim("roles", roles)
                .issuedAt(new Date())
                .expiration(new Date(expiryTime))
                .signWith(key)
                .compact();

        // 🔥 NEW: Lưu token vào Redis
        String tokenId = tokenStorageService.storeAccessToken(userId, token, roles, expiryTime, deviceInfo);
        if (tokenId == null) {
            log.error("❌ Failed to store access token in Redis for userId: {}", userId);
        }

        return token;
    }

    /**
     * Compatibility method for backward compatibility
     */
    public String generateAccessToken(Integer userId, String username, List<String> roles) {
        return generateAccessToken(userId, username, roles, "unknown");
    }

    /**
     * Tạo Refresh Token với userId và roles
     * ✅ Thêm roles để bảo mật hơn - tránh refresh token bị lạm dụng
     * 🔥 NEW: Lưu refresh token vào Redis để kiểm soát session
     */
    public String generateRefreshToken(Integer userId, List<String> roles) {
        long expiryTime = System.currentTimeMillis() + REFRESH_TOKEN_EXPIRATION;
        
        String token = Jwts.builder()
                .subject(userId.toString())  // Dùng userId làm subject
                .claim("roles", roles)        // ✅ Thêm roles để bảo mật hơn
                .claim("type", "refresh")     // ✅ Đánh dấu đây là refresh token
                .issuedAt(new Date())
                .expiration(new Date(expiryTime))
                .signWith(key)
                .compact();

        // 🔥 NEW: Lưu refresh token vào Redis
        String tokenId = tokenStorageService.storeRefreshToken(userId, token, roles, expiryTime);
        if (tokenId == null) {
            log.error("❌ Failed to store refresh token in Redis for userId: {}", userId);
        }

        return token;
    }

    /**
     * Lấy JWT từ Authorization Header
     */
    public String getJwtFromHeader(jakarta.servlet.http.HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    /**
     * Deprecated: Dùng generateAccessToken(userId, username, roles) thay thế
     */
    @Deprecated
    public ResponseCookie generateTokenCookie(Integer userId, String username, List<String> roles) {
        String token = generateAccessToken(userId, username, roles);
        return ResponseCookie.from(JWT_COOKIE_NAME, token)
                .path("/")
                .maxAge(24 * 60 * 60) // 24 hours
                .httpOnly(true)
                .secure(false) // Set to true in production with HTTPS
                .sameSite("Lax")
                .build();
    }

    @Deprecated
    public ResponseCookie getCleanJwtCookie() {
        return ResponseCookie.from(JWT_COOKIE_NAME, "")
                .path("/")
                .maxAge(0)
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .build();
    }

    /**
     * Lấy userId từ token (từ subject)
     */
    public Integer getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        String userIdStr = claims.getSubject();
        return userIdStr != null ? Integer.parseInt(userIdStr) : null;
    }

    /**
     * Lấy username từ token (từ claim, không phải subject)
     */
    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.get("username", String.class);
    }

    @SuppressWarnings("unchecked")
    public List<String> getRolesFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.get("roles", List.class);
    }

    /**
     * Lấy thời gian hết hạn của token
     */
    public Date getExpirationDateFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getExpiration();
    }

    /**
     * Kiểm tra xem token có phải là refresh token không
     */
    public boolean isRefreshToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String type = claims.get("type", String.class);
            return "refresh".equals(type);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Validate JWT token signature và expiry (basic validation)
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            log.error("JWT token is expired: {}", e.getMessage());
            return false;
        } catch (io.jsonwebtoken.MalformedJwtException e) {
            log.error("JWT token is malformed: {}", e.getMessage());
            return false;
        } catch (io.jsonwebtoken.security.SignatureException e) {
            log.error("JWT signature does not match: {}", e.getMessage());
            return false;
        } catch (io.jsonwebtoken.UnsupportedJwtException e) {
            log.error("JWT token is unsupported: {}", e.getMessage());
            return false;
        } catch (IllegalArgumentException e) {
            log.error("JWT claims string is empty: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("JWT token validation error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 🔥 NEW: Validate token thông qua Redis (bảo mật cao hơn)
     * Kiểm tra token có tồn tại trong Redis không
     */
    public TokenStorageService.TokenValidationResult validateTokenWithRedis(String token) {
        try {
            // Validate JWT signature và expiry trước
            if (!validateToken(token)) {
                return TokenStorageService.TokenValidationResult.invalid();
            }

            // Kiểm tra token có tồn tại trong Redis không
            return tokenStorageService.validateAccessToken(token);
        } catch (Exception e) {
            log.error("❌ Error validating token with Redis", e);
            return TokenStorageService.TokenValidationResult.invalid();
        }
    }

    /**
     * 🔥 NEW: Validate refresh token thông qua Redis
     */
    public TokenStorageService.RefreshTokenValidationResult validateRefreshTokenWithRedis(String token) {
        try {
            // Validate JWT signature và expiry trước
            if (!validateToken(token) || !isRefreshToken(token)) {
                return TokenStorageService.RefreshTokenValidationResult.invalid();
            }

            // Kiểm tra refresh token có tồn tại trong Redis không
            return tokenStorageService.validateRefreshToken(token);
        } catch (Exception e) {
            log.error("❌ Error validating refresh token with Redis", e);
            return TokenStorageService.RefreshTokenValidationResult.invalid();
        }
    }

    /**
     * 🔥 NEW: Logout token (revoke from Redis)
     */
    public boolean logoutToken(String token) {
        try {
            return tokenStorageService.revokeToken(token);
        } catch (Exception e) {
            log.error("❌ Error logging out token", e);
            return false;
        }
    }

    /**
     * 🔥 NEW: Logout all tokens của user (logout all devices)
     */
    public boolean logoutAllUserTokens(Integer userId) {
        try {
            return tokenStorageService.revokeAllUserTokens(userId);
        } catch (Exception e) {
            log.error("❌ Error logging out all user tokens for userId: {}", userId, e);
            return false;
        }
    }

    /**
     * Revoke token theo tokenId (dùng cho admin - không cần JWT string)
     * Admin lấy tokenId từ getUserActiveSessions() rồi revoke trực tiếp
     */
    public boolean revokeTokenById(String tokenId) {
        try {
            return tokenStorageService.revokeTokenById(tokenId);
        } catch (Exception e) {
            log.error("❌ Error revoking token by ID: {}", tokenId, e);
            return false;
        }
    }

    /**
     * 🔥 NEW: Get user active sessions
     */
    public List<TokenStorageService.SessionInfo> getUserActiveSessions(Integer userId) {
        try {
            return tokenStorageService.getUserActiveSessions(userId);
        } catch (Exception e) {
            log.error("❌ Error getting user active sessions for userId: {}", userId, e);
            return List.of();
        }
    }

    @Deprecated
    public String getJwtFromCookies(jakarta.servlet.http.HttpServletRequest request) {
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (jakarta.servlet.http.Cookie cookie : cookies) {
                if (JWT_COOKIE_NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    // =================================================================
    // PHƯƠNG THỨC CHO CÁC TOKEN ĐẶC BIỆT (Email Verification, Password Reset)
    // Những token này vẫn dùng email làm subject để tương thích với link gửi email
    // =================================================================

    /**
     * Tạo token đặc biệt với userId và type (VERIFY_EMAIL, RESET_PASSWORD)
     */
    public String generateSpecialToken(Integer userId, String email, String type) {
        return Jwts.builder()
                .subject(userId.toString())  // Dùng userId làm subject
                .claim("email", email)       // Lưu email vào claim
                .claim("type", type)         // VERIFY_EMAIL hoặc RESET_PASSWORD
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + JWT_EXPIRATION))
                .signWith(key)
                .compact();
    }

    /**
     * Lấy email từ token đặc biệt
     */
    public String getEmailFromSpecialToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.get("email", String.class);
    }

    /**
     * Lấy type từ token đặc biệt
     */
    public String getTypeFromSpecialToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.get("type", String.class);
    }

    /**
     * @deprecated Dùng getEmailFromSpecialToken thay thế
     */
    @Deprecated
    public String getEmailFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        // Thử lấy từ email claim trước (token mới)
        String email = claims.get("email", String.class);
        if (email != null) return email;

        // Thử lấy từ username claim (access token)
        String username = claims.get("username", String.class);
        if (username != null) return username;

        // Fallback: lấy từ subject (token cũ)
        return claims.getSubject();
    }

    /**
     * @deprecated Dùng getTypeFromSpecialToken thay thế
     */
    @Deprecated
    public String getRoleFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        // Thử lấy type trước (token mới)
        String type = claims.get("type", String.class);
        if (type != null) return type;

        // Fallback: lấy role (token cũ)
        return claims.get("role", String.class);
    }
}