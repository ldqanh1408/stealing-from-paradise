package com.flashsale.identityservice.controller;

import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.identityservice.domain.model.User;
import com.flashsale.identityservice.domain.repository.UserRepository;
import com.flashsale.identityservice.dto.request.LockRequest;
import com.flashsale.identityservice.dto.request.UnlockRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final UserRepository userRepository;

    // ── Account Locking ─────────────────────────────────────────────────────

    @PostMapping("/users/{userId}/lock")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> lockUser(
            @PathVariable Long userId,
            @RequestBody LockRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if ("LOCKED".equals(user.getStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("ALREADY_LOCKED", "User is already locked"));
        }

        user.setStatus("LOCKED");
        userRepository.save(user);

        log.info("Admin locked user {}: reason={}", userId, request.getReason());
        return ResponseEntity.ok(ApiResponse.success(null, "User locked"));
    }

    @PostMapping("/users/{userId}/unlock")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> unlockUser(
            @PathVariable Long userId,
            @RequestBody UnlockRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setStatus("ACTIVE");
        userRepository.save(user);

        log.info("Admin unlocked user {}: reason={}", userId, request.getReason());
        return ResponseEntity.ok(ApiResponse.success(null, "User unlocked"));
    }
}
