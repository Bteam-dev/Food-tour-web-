package com.example.FoodTourApp.config.JWTConfig;

import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.repository.UserRepository;
import com.example.FoodTourApp.service.impl.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final TokenBlacklistService tokenBlacklistService;
    private final UserRepository userRepository;

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    public JwtAuthenticationFilter(JwtUtils jwtUtils, TokenBlacklistService tokenBlacklistService, UserRepository userRepository) {
        this.jwtUtils = jwtUtils;
        this.tokenBlacklistService = tokenBlacklistService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String requestUri = request.getRequestURI();
        logger.info("=== JwtAuthenticationFilter START for {} ===", requestUri);

        String token = null;

        // Ưu tiên lấy token từ Authorization Header (nghiêm ngặt hơn)
        token = jwtUtils.getJwtFromHeader(request);
        logger.info("Token from header: {}", token != null ? "Found" : "Not found");

        // Fallback: Nếu không có trong header, thử lấy từ cookie (để tương thích với code cũ)
        if (token == null) {
            token = jwtUtils.getJwtFromCookies(request);
            logger.info("Token from cookie: {}", token != null ? "Found" : "Not found");
        }

        // For WebSocket connections, try to get token from query parameter
        if (token == null && requestUri.startsWith("/ws")) {
            String tokenParam = request.getParameter("token");
            if (tokenParam != null && !tokenParam.isEmpty()) {
                token = tokenParam;
                logger.info("Token from query parameter: Found");
            } else {
                logger.info("Token from query parameter: Not found");
            }
        }

        if (token != null) {
            // Kiểm tra token có bị blacklist không (đã logout)
            if (tokenBlacklistService.isTokenBlacklisted(token)) {
                logger.warn("Token is blacklisted (user logged out), rejecting request to {}", requestUri);
                chain.doFilter(request, response);
                return;
            }

            // Kiểm tra token có hợp lệ không
            logger.info("Validating token...");
            if (jwtUtils.validateToken(token)) {
                String email = jwtUtils.getUsernameFromToken(token);
                List<String> roles = jwtUtils.getRolesFromToken(token);

                logger.info("Token valid! Email: {}, Roles from token: {}", email, roles);

                if (email != null && roles != null && !roles.isEmpty()) {
                    // Load the actual User object from database
                    logger.info("Loading user from database for email: {}", email);

                    User user = null;
                    try {
                        user = userRepository.findByEmail(email).orElse(null);
                    } catch (Exception e) {
                        logger.error("ERROR loading user from database: {}", e.getMessage(), e);
                    }

                    if (user != null) {
                        logger.info("User found in database: ID={}, Email={}, Role={}",
                                   user.getId(), user.getEmail(), user.getRole().getRoleName());

                        // Convert roles to authorities
                        List<SimpleGrantedAuthority> authorities = roles.stream()
                                .map(role -> {
                                    String authority = "ROLE_" + role.toUpperCase();
                                    logger.info("Mapping role '{}' to authority '{}'", role, authority);
                                    return new SimpleGrantedAuthority(authority);
                                })
                                .collect(Collectors.toList());

                        logger.info("Setting authentication for email: {}, roles: {}, authorities: {}",
                                   email, roles, authorities);

                        // Set the User object as principal instead of email String
                        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                user, null, authorities);
                        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(auth);

                        logger.info("✅ Authentication SET successfully for {}", email);
                    } else {
                        logger.error("❌ USER NOT FOUND in database for email: {} - THIS WILL CAUSE 403!", email);
                        logger.error("Token is valid but user doesn't exist in DB. Possible causes:");
                        logger.error("1. Database connection issue");
                        logger.error("2. User was deleted");
                        logger.error("3. Hibernate/JPA cache issue");
                    }
                } else {
                    logger.warn("Email or roles are null/empty in token for request to {}", requestUri);
                    logger.warn("Email: {}, Roles: {}", email, roles);
                }
            } else {
                logger.warn("Invalid JWT token for request to {}", requestUri);
            }
        } else {
            logger.info("No JWT token found in request to {}", requestUri);
        }

        logger.info("=== JwtAuthenticationFilter END for {} ===", requestUri);
        chain.doFilter(request, response);
    }
}