package com.municipal.tracker;

import com.municipal.tracker.config.JwtUtil;
import com.municipal.tracker.dto.LoginRequest;
import com.municipal.tracker.dto.RegisterRequest;
import com.municipal.tracker.model.Role;
import com.municipal.tracker.model.User;
import com.municipal.tracker.repository.UserRepository;
import com.municipal.tracker.repository.WardRepository;
import com.municipal.tracker.service.AuthService;
import com.municipal.tracker.service.LoginAttemptService;
import com.municipal.tracker.service.AuthCookieService;
import com.municipal.tracker.repository.LoginAttemptRepository;
import com.municipal.tracker.model.LoginAttempt;
import com.municipal.tracker.exception.TooManyLoginAttemptsException;
import tools.jackson.databind.json.JsonMapper;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthenticationSecurityTest {

    @Test
    void repeatedFailuresActivatePersistentThrottle() {
        LoginAttemptRepository repository = mock(LoginAttemptRepository.class);
        AtomicReference<LoginAttempt> stored = new AtomicReference<>();
        when(repository.findByLoginKey(anyString())).thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(repository.save(any(LoginAttempt.class))).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return stored.get();
        });
        LoginAttemptService attempts = new LoginAttemptService(repository, 2, 15, 15);

        attempts.recordFailure("Citizen@Example.Test", "127.0.0.1");
        attempts.recordFailure("citizen@example.test", "127.0.0.1");

        assertThatThrownBy(() -> attempts.checkAllowed("citizen@example.test", "127.0.0.1"))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    void authenticationCookieIsHttpOnlyAndTokenIsNotSerialized() {
        AuthCookieService cookies = new AuthCookieService("municipal_auth", true, 60_000L);
        String cookie = cookies.authenticationCookie("secret-token").toString();
        var response = new com.municipal.tracker.dto.AuthResponse(
                "secret-token", "citizen@example.test", "Citizen", Role.CITIZEN, 1L, "ok");

        assertThat(cookie).contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/");
        assertThat(JsonMapper.builder().build().writeValueAsString(response))
                .doesNotContain("secret-token", "token");
    }

    @Test
    void registrationStoresABcryptHashInsteadOfTheRawPassword() {
        UserRepository users = mock(UserRepository.class);
        JwtUtil jwt = mock(JwtUtil.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        AuthService service = new AuthService(users, mock(WardRepository.class), encoder, jwt,
                mock(AuthenticationManager.class), mock(LoginAttemptService.class));
        RegisterRequest request = request("citizen@example.test", "plain-secret");
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwt.generateToken(any(User.class))).thenReturn("token");

        service.register(request);

        var captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(users).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isNotEqualTo("plain-secret");
        assertThat(encoder.matches("plain-secret", captor.getValue().getPassword())).isTrue();
    }

    @Test
    void authenticationFailureDoesNotQueryOrIssueAToken() {
        UserRepository users = mock(UserRepository.class);
        JwtUtil jwt = mock(JwtUtil.class);
        AuthenticationManager manager = mock(AuthenticationManager.class);
        LoginAttemptService attempts = mock(LoginAttemptService.class);
        AuthService service = new AuthService(users, mock(WardRepository.class),
                new BCryptPasswordEncoder(), jwt, manager, attempts);
        LoginRequest request = new LoginRequest();
        request.setEmail("unknown@example.test");
        request.setPassword("wrong-secret");
        doThrow(new BadCredentialsException("Bad credentials")).when(manager).authenticate(any());

        assertThatThrownBy(() -> service.login(request, "127.0.0.1"))
                .isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(users, jwt);
        verify(attempts).recordFailure(request.getEmail(), "127.0.0.1");
    }

    @Test
    void successfulLoginUsesTheDatabaseUserAfterAuthentication() {
        UserRepository users = mock(UserRepository.class);
        JwtUtil jwt = mock(JwtUtil.class);
        AuthenticationManager manager = mock(AuthenticationManager.class);
        LoginAttemptService attempts = mock(LoginAttemptService.class);
        AuthService service = new AuthService(users, mock(WardRepository.class),
                new BCryptPasswordEncoder(), jwt, manager, attempts);
        LoginRequest request = new LoginRequest();
        request.setEmail("citizen@example.test");
        request.setPassword("secret");
        User user = new User();
        user.setEmail(request.getEmail());
        user.setFullName("Citizen");
        user.setRole(Role.CITIZEN);
        user.setActive(true);
        when(users.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(jwt.generateToken(user)).thenReturn("token");

        assertThat(service.login(request, "127.0.0.1").getToken()).isEqualTo("token");
        verify(jwt).generateToken(user);
        verify(attempts).recordSuccess(request.getEmail(), "127.0.0.1");
    }

    private RegisterRequest request(String email, String password) {
        RegisterRequest request = new RegisterRequest();
        request.setEmail(email);
        request.setPassword(password);
        request.setFullName("Citizen");
        request.setPhone("9999999999");
        request.setRole(Role.CITIZEN);
        return request;
    }
}
