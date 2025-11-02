package com.example.FoodTourApp.config;

import com.example.FoodTourApp.config.JWTConfig.JwtUtils;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null) {
            log.info("WebSocket message - Command: {}, Destination: {}",
                accessor.getCommand(), accessor.getDestination());

            // Authenticate cho CONNECT và mọi message khác
            if (StompCommand.CONNECT.equals(accessor.getCommand()) || accessor.getUser() == null) {
                String token = extractToken(accessor);

                if (token != null && jwtUtils.validateToken(token)) {
                    authenticateUser(token, accessor);
                } else {
                    log.warn("WebSocket message - Invalid or missing token");
                }
            }
        }

        return message;
    }

    private String extractToken(StompHeaderAccessor accessor) {
        // 1. Thử lấy từ Authorization header
        List<String> authorization = accessor.getNativeHeader("Authorization");
        if (authorization != null && !authorization.isEmpty()) {
            String bearerToken = authorization.get(0);
            if (bearerToken.startsWith("Bearer ")) {
                log.info("Token found in Authorization header");
                return bearerToken.substring(7);
            }
        }

        // 2. Thử lấy từ token header (khi client gửi qua STOMP connect headers)
        List<String> tokenHeader = accessor.getNativeHeader("token");
        if (tokenHeader != null && !tokenHeader.isEmpty()) {
            log.info("Token found in token header");
            return tokenHeader.get(0);
        }

        // 3. Thử lấy từ session attributes (được set từ HTTP handshake)
        if (accessor.getSessionAttributes() != null) {
            String sessionToken = (String) accessor.getSessionAttributes().get("token");
            if (sessionToken != null) {
                log.info("Token found in session attributes");
                return sessionToken;
            }
        }

        log.warn("No token found in WebSocket message");
        return null;
    }

    private void authenticateUser(String token, StompHeaderAccessor accessor) {
        try {
            String email = jwtUtils.getUsernameFromToken(token);
            List<String> roles = jwtUtils.getRolesFromToken(token);

            log.info("WebSocket authentication - Email: {}, Roles: {}", email, roles);

            // Load user from database
            User user = userRepository.findByEmail(email).orElse(null);

            if (user != null) {
                log.info("WebSocket user authenticated: ID={}, Email={}", user.getId(), user.getEmail());

                List<SimpleGrantedAuthority> authorities = roles.stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                        .collect(Collectors.toList());

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(user, null, authorities);

                accessor.setUser(authentication);

                // Lưu vào session để dùng cho các message tiếp theo
                if (accessor.getSessionAttributes() != null) {
                    accessor.getSessionAttributes().put("authenticated_user", user);
                }
            } else {
                log.warn("WebSocket user not found in database: {}", email);
            }
        } catch (Exception e) {
            log.error("Error authenticating WebSocket user: {}", e.getMessage(), e);
        }
    }
}
