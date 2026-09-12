package com.municipal.tracker.controller;

import com.municipal.tracker.dto.AuthResponse;
import com.municipal.tracker.dto.LoginRequest;
import com.municipal.tracker.dto.RegisterRequest;
import com.municipal.tracker.model.Role;
import com.municipal.tracker.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;

    // PUBLIC — anyone can self-register, but ALWAYS as CITIZEN
    // POST /api/auth/register
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request) {
        request.setRole(Role.CITIZEN); // force role — ignore whatever was sent
        try {
            AuthResponse response = authService.register(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(new AuthResponse(null, null, null, null, null, e.getMessage()));
        }
    }

    // PROTECTED — only Admin/Ward Officer can create staff accounts
    // POST /api/auth/register-staff
    @PostMapping("/register-staff")
    @PreAuthorize("hasAnyRole('MUNICIPAL_ADMIN','WARD_OFFICER')")
    public ResponseEntity<AuthResponse> registerStaff(
            @Valid @RequestBody RegisterRequest request) {
        // Officers/Admins can only create these two roles from this endpoint
        if (request.getRole() != Role.FIELD_WORKER
                && request.getRole() != Role.WARD_OFFICER) {
            return ResponseEntity.badRequest()
                    .body(new AuthResponse(null, null, null, null, null,
                            "This endpoint can only create FIELD_WORKER or WARD_OFFICER accounts"));
        }
        try {
            AuthResponse response = authService.register(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(new AuthResponse(null, null, null, null, null, e.getMessage()));
        }
    }

    // POST /api/auth/login
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request) {
        try {
            AuthResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthResponse(null, null, null, null, null,
                            "Invalid email or password"));
        }
    }
}