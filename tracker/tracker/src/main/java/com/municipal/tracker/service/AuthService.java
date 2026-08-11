package com.municipal.tracker.service;

import com.municipal.tracker.config.JwtUtil;
import com.municipal.tracker.dto.AuthResponse;
import com.municipal.tracker.dto.LoginRequest;
import com.municipal.tracker.dto.RegisterRequest;
import com.municipal.tracker.model.User;
import com.municipal.tracker.model.Ward;
import com.municipal.tracker.repository.UserRepository;
import com.municipal.tracker.repository.WardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final WardRepository wardRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    // ── REGISTER ──────────────────────────────────
    public AuthResponse register(RegisterRequest request) {

        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered: " + request.getEmail());
        }

        // Find ward if wardId provided
        Ward ward = null;
        if (request.getWardId() != null) {
            ward = wardRepository.findById(request.getWardId())
                    .orElseThrow(() -> new RuntimeException(
                            "Ward not found with id: " + request.getWardId()
                    ));
        }

        // Build user object
        User user = new User();
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPhone(request.getPhone());
        user.setRole(request.getRole());
        user.setWard(ward);

        // Save to database
        User savedUser = userRepository.save(user);

        // Generate JWT token
        String token = jwtUtil.generateToken(savedUser);

        // Return response
        return new AuthResponse(
                token,
                savedUser.getEmail(),
                savedUser.getFullName(),
                savedUser.getRole(),
                ward != null ? ward.getId() : null,
                "Registration successful"
        );
    }

    // ── LOGIN ──────────────────────────────────────
    public AuthResponse login(LoginRequest request) {

        // This line checks email + password automatically
        // Throws exception if credentials are wrong
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        // If we reach here — credentials are correct
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Generate JWT token
        String token = jwtUtil.generateToken(user);

        // Return response
        return new AuthResponse(
                token,
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getWard() != null ? user.getWard().getId() : null,
                "Login successful"
        );
    }
}
