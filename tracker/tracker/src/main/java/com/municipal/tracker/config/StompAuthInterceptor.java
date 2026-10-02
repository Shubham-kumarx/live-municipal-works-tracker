package com.municipal.tracker.config;

import com.municipal.tracker.model.Role;
import com.municipal.tracker.model.User;
import com.municipal.tracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class StompAuthInterceptor implements ChannelInterceptor {
    private static final Pattern WARD_TOPIC = Pattern.compile("^/topic/ward/(\\d+)/projects$");
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (accessor.getCommand() == StompCommand.CONNECT) authenticate(accessor);
        if (accessor.getCommand() == StompCommand.SUBSCRIBE) authorizeSubscription(accessor);
        if (accessor.getCommand() == StompCommand.SEND
                && accessor.getDestination() != null
                && accessor.getDestination().startsWith("/topic/")) {
            throw new AccessDeniedException("Clients cannot publish to broker topics");
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new AccessDeniedException("A bearer token is required");
        }
        try {
            String token = header.substring(7);
            User user = userRepository.findByEmail(jwtUtil.extractEmail(token))
                    .filter(User::isEnabled).orElseThrow(() -> new AccessDeniedException("Invalid user"));
            if (!jwtUtil.isTokenValid(token, user)) throw new AccessDeniedException("Invalid token");
            accessor.setUser(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        } catch (AccessDeniedException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AccessDeniedException("Invalid token");
        }
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof UsernamePasswordAuthenticationToken authentication)
                || !(authentication.getPrincipal() instanceof User user)) {
            throw new AccessDeniedException("Authentication required");
        }
        Matcher matcher = WARD_TOPIC.matcher(String.valueOf(accessor.getDestination()));
        if (!matcher.matches()) throw new AccessDeniedException("Unsupported subscription destination");
        long wardId = Long.parseLong(matcher.group(1));
        if (user.getRole() == Role.MUNICIPAL_ADMIN || user.getRole() == Role.AUDITOR) return;
        if (user.getWard() == null || !user.getWard().getId().equals(wardId)) {
            throw new AccessDeniedException("Ward subscription denied");
        }
    }
}
