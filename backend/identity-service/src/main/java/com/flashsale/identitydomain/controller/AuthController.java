package com.flashsale.identitydomain.controller;

import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.dto.AuthResponse;
import com.flashsale.commonlib.dto.LoginRequest;
import com.flashsale.identitydomain.domain.model.User;
import com.flashsale.identitydomain.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication Controller
 * Handles login, logout, registration, and token refresh
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    @Value("${jwt.expiration:3600}")
    private long accessTokenExpiration;

    /**
     * Login endpoint
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@RequestBody LoginRequest loginRequest) {
        log.info("Login attempt for user: {}", loginRequest.getUsername());
        try {
            AuthResponse authResponse = authService.authenticateUser(loginRequest);
            return ResponseEntity.ok(ApiResponse.success(authResponse, "Login successful"));
        } catch (Exception e) {
            log.warn("Login failed: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("AUTH_001", e.getMessage()));
        }
    }

    /**
     * Register endpoint
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@RequestBody LoginRequest registerRequest) {
        log.info("Registration attempt for username: {}", registerRequest.getUsername());
        try {
            // Validate input
            if (registerRequest.getUsername() == null || registerRequest.getUsername().isEmpty()) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("REG_001", "Username is required"));
            }
            if (registerRequest.getPassword() == null || registerRequest.getPassword().isEmpty()) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("REG_002", "Password is required"));
            }

            // Create user (assuming registerRequest.username is actually email or we need a dedicated endpoint)
            User newUser = authService.registerUser(
                    registerRequest.getUsername(),
                    registerRequest.getUsername(),  // Use username as email for now
                    registerRequest.getPassword()
            );

            // Generate tokens
            AuthResponse authResponse = AuthResponse.builder()
                    .userId(newUser.getId())
                    .username(newUser.getUsername())
                    .email(newUser.getEmail())
                    .role(newUser.getRole() != null ? newUser.getRole() : "BUYER")
                    .expiresIn(accessTokenExpiration)
                    .build();

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(ApiResponse.success(authResponse, "Registration successful"));
        } catch (Exception e) {
            log.warn("Registration failed: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("REG_003", e.getMessage()));
        }
    }

    /**
     * Refresh access token using refresh token
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("Token refresh requested");
        try {
            String refreshToken = extractTokenFromHeader(authHeader);
            if (refreshToken == null) {
                return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("AUTH_002", "Refresh token not provided"));
            }

            AuthResponse authResponse = authService.refreshAccessToken(refreshToken);
            return ResponseEntity.ok(ApiResponse.success(authResponse, "Token refreshed"));
        } catch (Exception e) {
            log.warn("Token refresh failed: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("AUTH_003", e.getMessage()));
        }
    }

    /**
     * Logout endpoint
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("Logout requested");
        try {
            String token = extractTokenFromHeader(authHeader);
            if (token != null) {
                // In production, add token to blacklist
                authService.logout(token);
            }
            return ResponseEntity.ok(ApiResponse.success(null, "Logged out successfully"));
        } catch (Exception e) {
            log.warn("Logout failed: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("AUTH_004", "Logout failed"));
        }
    }

    /**
     * Extract Bearer token from Authorization header
     */
    private String extractTokenFromHeader(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}

