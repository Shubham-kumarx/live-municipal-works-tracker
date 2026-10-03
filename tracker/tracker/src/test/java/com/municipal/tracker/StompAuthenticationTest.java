package com.municipal.tracker;

import com.municipal.tracker.config.JwtUtil;
import com.municipal.tracker.config.StompAuthInterceptor;
import com.municipal.tracker.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.municipal.tracker.model.User;
import com.municipal.tracker.model.Role;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThatCode;

class StompAuthenticationTest {
    private final StompAuthInterceptor interceptor = new StompAuthInterceptor(
            mock(JwtUtil.class), mock(UserRepository.class));

    @Test
    void connectWithoutBearerTokenIsRejected() {
        assertThatThrownBy(() -> interceptor.preSend(message(StompCommand.CONNECT), null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("bearer token");
    }

    @Test
    void everyClientSendFrameIsRejected() {
        assertThatThrownBy(() -> interceptor.preSend(message(StompCommand.SEND), null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("not supported");
    }

    @Test
    void connectAcceptsAndRevalidatesAuthenticatedHandshakePrincipal() {
        UserRepository users = mock(UserRepository.class);
        StompAuthInterceptor interceptor = new StompAuthInterceptor(mock(JwtUtil.class), users);
        User user = new User();
        user.setEmail("citizen@example.test"); user.setRole(Role.CITIZEN); user.setActive(true);
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setUser(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatCode(() -> interceptor.preSend(message, null)).doesNotThrowAnyException();
    }

    private Message<byte[]> message(StompCommand command) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
