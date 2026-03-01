package com.example.FoodTourApp.config;

import com.example.FoodTourApp.config.JWTConfig.JwtUtils;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.repository.ConversationRepository;
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

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository; // ✅ thêm để kiểm tra participant

    /**
     * Custom token that returns userId as name so that Spring's STOMP CONNECTED frame
     * sends a plain "user-name: 3" header instead of the full User.toString(),
     * which would break the STOMP frame parser on the Android client.
     */
    private static class UserIdAuthenticationToken extends UsernamePasswordAuthenticationToken {
        private final String name;

        public UserIdAuthenticationToken(Object principal, Object credentials,
                                         Collection<? extends org.springframework.security.core.GrantedAuthority> authorities,
                                         String name) {
            super(principal, credentials, authorities);
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null) {
            log.info("WebSocket message - Command: {}, Destination: {}",
                    accessor.getCommand(), accessor.getDestination());

            if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                String token = extractToken(accessor);
                if (token != null && jwtUtils.validateToken(token)) {
                    authenticateUser(token, accessor);
                } else {
                    log.warn("WebSocket CONNECT - Invalid or missing token");
                }
            } else {
                // Restore user từ session cho các frame SEND, SUBSCRIBE, DISCONNECT...
                if (accessor.getUser() == null && accessor.getSessionAttributes() != null) {
                    UsernamePasswordAuthenticationToken auth =
                            (UsernamePasswordAuthenticationToken) accessor.getSessionAttributes()
                                    .get("spring_security_auth");
                    if (auth != null) {
                        accessor.setUser(auth);
                        log.debug("Restored auth from session for command: {}", accessor.getCommand());
                    }
                }

                // ✅ Kiểm tra SUBSCRIBE vào /topic/conversation/{id}
                if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    String destination = accessor.getDestination();
                    if (destination != null && destination.startsWith("/topic/conversation/")) {
                        if (!checkConversationSubscribeAccess(accessor, destination)) {
                            log.warn("❌ Blocked SUBSCRIBE to {} – user is not a participant", destination);
                            return null; // Block message – ngắt kết nối subscribe
                        }
                    }
                }
            }
        }

        return message;
    }

    /**
     * Kiểm tra user có phải participant của conversation không trước khi cho SUBSCRIBE.
     *
     * @return true nếu được phép, false nếu bị chặn
     */
    private boolean checkConversationSubscribeAccess(StompHeaderAccessor accessor, String destination) {
        try {
            // Trích xuất conversationId từ "/topic/conversation/123"
            String idStr = destination.substring("/topic/conversation/".length());
            Long conversationId = Long.parseLong(idStr);

            // Lấy user từ principal
            if (accessor.getUser() == null) {
                log.warn("SUBSCRIBE check – principal is null");
                return false;
            }

            UsernamePasswordAuthenticationToken auth =
                    (UsernamePasswordAuthenticationToken) accessor.getUser();
            if (!(auth.getPrincipal() instanceof User user)) {
                log.warn("SUBSCRIBE check – principal is not User");
                return false;
            }

            boolean isParticipant = conversationRepository.findById(conversationId)
                    .map(conv -> conv.getParticipants().stream()
                            .anyMatch(u -> u.getId().equals(user.getId())))
                    .orElse(false);

            if (!isParticipant) {
                log.warn("❌ User {} tried to SUBSCRIBE to conversation {} but is NOT a participant",
                        user.getId(), conversationId);
            }
            return isParticipant;

        } catch (NumberFormatException e) {
            log.warn("SUBSCRIBE check – invalid conversationId in destination: {}", destination);
            return false;
        } catch (Exception e) {
            log.error("SUBSCRIBE check error: {}", e.getMessage(), e);
            return false;
        }
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
            Integer userId = jwtUtils.getUserIdFromToken(token);
            List<String> roles = jwtUtils.getRolesFromToken(token);

            log.info("WebSocket authentication - UserId: {}, Roles: {}", userId, roles);

            User user = userRepository.findById(userId).orElse(null);

            if (user != null) {
                log.info("WebSocket user authenticated: ID={}, Email={}", user.getId(), user.getEmail());

                if (!user.getIsActive()) {
                    log.warn("❌ User {} is INACTIVE or BLOCKED - Rejecting WebSocket connection", userId);
                    return;
                }

                List<SimpleGrantedAuthority> authorities = roles.stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                        .collect(Collectors.toList());

                UserIdAuthenticationToken authentication =
                        new UserIdAuthenticationToken(user, null, authorities, userId.toString());

                accessor.setUser(authentication);

                if (accessor.getSessionAttributes() != null) {
                    accessor.getSessionAttributes().put("spring_security_auth", authentication);
                }
            } else {
                log.warn("WebSocket user not found in database for userId: {}", userId);
            }
        } catch (Exception e) {
            log.error("Error authenticating WebSocket user: {}", e.getMessage(), e);
        }
    }
}
