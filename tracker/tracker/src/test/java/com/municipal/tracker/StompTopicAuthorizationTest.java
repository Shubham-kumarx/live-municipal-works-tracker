package com.municipal.tracker;

import com.municipal.tracker.config.JwtUtil;
import com.municipal.tracker.config.StompAuthInterceptor;
import com.municipal.tracker.model.Role;
import com.municipal.tracker.model.User;
import com.municipal.tracker.model.Ward;
import com.municipal.tracker.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class StompTopicAuthorizationTest {
    private final StompAuthInterceptor interceptor = new StompAuthInterceptor(
            mock(JwtUtil.class), mock(UserRepository.class));

    @Test
    void wardUserCanSubscribeOnlyToOwnWardProjectTopic() {
        User citizen = user(Role.CITIZEN, 4L);
        assertThatCode(() -> interceptor.preSend(subscription(citizen,
                "/topic/ward/4/projects"), null)).doesNotThrowAnyException();
        assertThatThrownBy(() -> interceptor.preSend(subscription(citizen,
                "/topic/ward/5/projects"), null)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void unsupportedTopicIsRejectedEvenForAdministrator() {
        assertThatThrownBy(() -> interceptor.preSend(subscription(
                user(Role.MUNICIPAL_ADMIN, null), "/topic/all"), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    private Message<byte[]> subscription(User user, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setUser(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private User user(Role role, Long wardId) {
        User user = new User();
        user.setRole(role);
        user.setActive(true);
        if (wardId != null) {
            Ward ward = new Ward(); ward.setId(wardId); user.setWard(ward);
        }
        return user;
    }
}
