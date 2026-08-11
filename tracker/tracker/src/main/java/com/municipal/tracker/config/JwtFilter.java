package com.municipal.tracker.config;

import com.municipal.tracker.model.User;
import com.municipal.tracker.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // Step 1 - Get Authorization header
        final String authHeader = request.getHeader("Authorization");

        // Step 2 - If no header or doesn't start with "Bearer ", skip filter
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Step 3 - Extract the token (remove "Bearer " prefix)
        final String token = authHeader.substring(7);

        // Step 4 - Extract email from token
        final String email;
        try {
            email = jwtUtil.extractEmail(token);
        } catch (Exception e) {
            // Token is malformed or tampered
            filterChain.doFilter(request, response);
            return;
        }

        // Step 5 - If email found and user not already authenticated
        if (email != null &&
                SecurityContextHolder.getContext().getAuthentication() == null) {

            // Step 6 - Load user from database
            User user = userRepository.findByEmail(email).orElse(null);

            // Step 7 - Validate token
            if (user != null && jwtUtil.isTokenValid(token, user)) {

                // Step 8 - Create authentication object
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                user,
                                null,
                                user.getAuthorities()
                        );

                authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                // Step 9 - Set authentication in security context
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        // Step 10 - Continue to next filter / controller
        filterChain.doFilter(request, response);
    }
}
