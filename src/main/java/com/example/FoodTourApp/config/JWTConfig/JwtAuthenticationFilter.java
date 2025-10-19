package com.example.FoodTourApp.config.JWTConfig;

import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.repository.UserRepository;
import com.example.FoodTourApp.service.TokenBlacklistService;
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
        logger.info("JwtAuthenticationFilter doFilterInternal called for {}", request.getRequestURI());

        String token = null;

        // Ưu tiên lấy token từ Authorization Header (nghiêm ngặt hơn)
        token = jwtUtils.getJwtFromHeader(request);

        // Fallback: Nếu không có trong header, thử lấy từ cookie (để tương thích với code cũ)
        if (token == null) {
            token = jwtUtils.getJwtFromCookies(request);
        }

        if (token != null) {
            // Kiểm tra token có bị blacklist không (đã logout)
            if (tokenBlacklistService.isTokenBlacklisted(token)) {
                logger.warn("Token is blacklisted (user logged out), rejecting request to {}", request.getRequestURI());
                chain.doFilter(request, response);
                return;
            }

            // Kiểm tra token có hợp lệ không
            if (jwtUtils.validateToken(token)) {
                String email = jwtUtils.getUsernameFromToken(token);
                List<String> roles = jwtUtils.getRolesFromToken(token);

                if (email != null && roles != null && !roles.isEmpty()) {
                    // Load the actual User object from database
                    User user = userRepository.findByEmail(email).orElse(null);

                    if (user != null) {
                        // Convert roles to authorities
                        List<SimpleGrantedAuthority> authorities = roles.stream()
                                .map(role -> new SimpleGrantedAuthority(role.startsWith("ROLE_") ? role : "ROLE_" + role.toUpperCase()))
                                .collect(Collectors.toList());

                        logger.info("Setting authentication for email: {}, roles: {}, authorities: {}",
                                   email, roles, authorities);

                        // Set the User object as principal instead of email String
                        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                user, null, authorities);
                        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    } else {
                        logger.warn("User not found for email: {}", email);
                    }
                } else {
                    logger.warn("Email or roles are null/empty in token for request to {}", request.getRequestURI());
                }
            } else {
                logger.info("Invalid JWT token for request to {}", request.getRequestURI());
            }
        } else {
            logger.info("No JWT token found in request to {}", request.getRequestURI());
        }

        chain.doFilter(request, response);
    }
}