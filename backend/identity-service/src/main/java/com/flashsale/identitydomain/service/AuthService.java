package com.flashsale.identitydomain.service;

import com.flashsale.commonlib.dto.AuthResponse;
import com.flashsale.commonlib.dto.LoginRequest;
import com.flashsale.commonlib.security.JwtUtils;
import com.flashsale.identitydomain.domain.model.User;
import com.flashsale.identitydomain.domain.repository.UserRepository;
import com.flashsale.identitydomain.domain.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final TokenBlacklistService tokenBlacklistService;

    @Value("${jwt.expiration:3600}")
    private long accessTokenExpiration;

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public User registerUser(String username, String email, String password) {
        log.info("Registering user: {}", username);

        if (userRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("Username already exists: " + username);
        }

        if (userRepository.findByEmail(email).isPresent()) {
            throw new RuntimeException("Email already exists: " + email);
        }

        String hashedPassword = passwordEncoder.encode(password);

        User user = User.builder()
                .username(username)
                .email(email)
                .password(hashedPassword)
                .status("ACTIVE")
                .trustScore(80)
                .build();

        return userRepository.save(user);
    }

    public User registerUserWithRole(String username, String email, String password, String roleParam) {
        log.info("Registering user: {} with role: {}", username, roleParam);

        User user = registerUser(username, email, password);

        // Create role entry in roles table
        String assignedRole = (roleParam != null && !roleParam.isEmpty()) ? roleParam : "BUYER";
        com.flashsale.identitydomain.domain.model.Role role = com.flashsale.identitydomain.domain.model.Role.builder()
                .userId(user.getId())
                .roleName(assignedRole)
                .build();
        roleRepository.save(role);

        return user;
    }



    /**
     * Authenticate user with domain detection
     * Seller domain -> SELLER role
     * Admin domain -> ADMIN role
     * Other -> use role from roles table or BUYER
     */
    public AuthResponse authenticateUser(LoginRequest loginRequest, String domain) {
        log.info("Attempting to authenticate user: {} from domain: {}", loginRequest.getUsername(), domain);

        User user = userRepository.findByUsername(loginRequest.getUsername())
                .or(() -> userRepository.findByEmail(loginRequest.getUsername()))
                .orElseThrow(() -> new RuntimeException("Invalid username or password"));

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new RuntimeException("Account is " + user.getStatus());
        }

        if (!validatePassword(loginRequest.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid username or password");
        }

        // Fetch role from roles table
        String dbRole = roleRepository.findByUserId(user.getId())
                .map(role -> role.getRoleName())
                .orElse("BUYER");

        // Determine role based on domain
        String roleName = determineRoleFromDomain(domain, dbRole);

        String accessToken = jwtUtils.generateAccessToken(
                user.getId().toString(),
                user.getEmail(),
                roleName
        );

        String refreshToken = jwtUtils.generateRefreshToken(user.getId().toString());

        log.info("User authenticated successfully: {} with role: {}", user.getUsername(), roleName);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(roleName)
                .expiresIn(accessTokenExpiration)
                .build();
    }

    /**
     * Determine role based on domain
     * seller.* domain -> SELLER
     * admin.* domain -> ADMIN
     * customer/app domain -> BUYER
     * other -> user's existing role or BUYER
     */
    private String determineRoleFromDomain(String domain, String userRole) {
        if (domain == null || domain.isEmpty()) {
            return (userRole != null && !userRole.isEmpty()) ? userRole : "BUYER";
        }

        String domainLower = domain.toLowerCase();

        if (domainLower.contains("seller")) {
            return "SELLER";
        } else if (domainLower.contains("admin")) {
            return "ADMIN";
        } else if (domainLower.contains("customer") || domainLower.contains("app")) {
            return "BUYER";
        }

        return (userRole != null && !userRole.isEmpty()) ? userRole : "BUYER";
    }

    public boolean validatePassword(String rawPassword, String hashedPassword) {
        return passwordEncoder.matches(rawPassword, hashedPassword);
    }

    public AuthResponse refreshAccessToken(String refreshToken) {
        log.info("Attempting to refresh access token");

        if (!jwtUtils.isTokenValid(refreshToken)) {
            throw new RuntimeException("Invalid or expired refresh token");
        }

        String userId = jwtUtils.extractUserId(refreshToken);
        if (userId == null) {
            throw new RuntimeException("Could not extract user ID from refresh token");
        }

        User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Fetch role from roles table using user ID
        String roleName = roleRepository.findByUserId(user.getId())
                .map(role -> role.getRoleName())
                .orElse("BUYER"); // Default to BUYER if no role found

        String newAccessToken = jwtUtils.generateAccessToken(
                user.getId().toString(),
                user.getEmail(),
                roleName
        );

        log.info("Access token refreshed for user: {}", user.getUsername());

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(roleName)
                .expiresIn(accessTokenExpiration)
                .build();
    }

    public void logout(String token) {
        if (token != null && !token.isEmpty()) {
            tokenBlacklistService.blacklistToken(token);
            String userId = jwtUtils.extractUserId(token);
            log.info("User logged out - userId: {}", userId);
        }
    }

    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
