package com.municipal.tracker.controller;

import com.municipal.tracker.dto.AuthResponse;
import com.municipal.tracker.dto.LoginRequest;
import com.municipal.tracker.dto.RegisterRequest;
import com.municipal.tracker.dto.CitizenRegisterRequest;
import com.municipal.tracker.model.User;
import com.municipal.tracker.service.AuthService;
import com.municipal.tracker.service.AuthCookieService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthCookieService authCookieService;

    // PUBLIC — anyone can self-register, but ALWAYS as CITIZEN
    // POST /api/auth/register
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody CitizenRegisterRequest request) {
        AuthResponse response = authService.register(request.toRegisterRequest());
        return withCookie(response, HttpStatus.CREATED);
    }

    // PROTECTED — only Admin/Ward Officer can create staff accounts
    // POST /api/auth/register-staff
    @PostMapping("/register-staff")
    @PreAuthorize("hasAnyRole('MUNICIPAL_ADMIN','WARD_OFFICER')")
    public ResponseEntity<AuthResponse> registerStaff(
            @Valid @RequestBody RegisterRequest request,
            @AuthenticationPrincipal User currentUser) {
        AuthResponse response = authService.registerStaff(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // POST /api/auth/login
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {
        return withCookie(authService.login(request, servletRequest.getRemoteAddr()), HttpStatus.OK);
    }

    @GetMapping("/session")
    public ResponseEntity<AuthResponse> session(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(new AuthResponse(null, currentUser.getEmail(), currentUser.getFullName(),
                currentUser.getRole(), currentUser.getWard() == null ? null : currentUser.getWard().getId(),
                "Authenticated"));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, authCookieService.expiredCookie().toString())
                .build();
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken csrfToken) {
        return Map.of("token", csrfToken.getToken(), "headerName", csrfToken.getHeaderName());
    }

    private ResponseEntity<AuthResponse> withCookie(AuthResponse response, HttpStatus status) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE,
                        authCookieService.authenticationCookie(response.getToken()).toString())
                .body(response);
    }
}
