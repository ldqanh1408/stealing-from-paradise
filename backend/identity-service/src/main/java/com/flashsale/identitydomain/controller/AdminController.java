package com.flashsale.identitydomain.controller;

import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.identitydomain.domain.model.User;
import com.flashsale.identitydomain.domain.repository.UserRepository;
import com.flashsale.identitydomain.dto.request.LockRequest;
import com.flashsale.identitydomain.dto.request.UnlockProductPostingRequest;
import com.flashsale.identitydomain.dto.request.UnlockRequest;
import com.flashsale.identitydomain.service.TrustScoreService;
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
    private final TrustScoreService trustScoreService;

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
        user.setLockReason(request.getReason());
        user.setLockedUntil(request.getLockedUntil());
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
        user.setLockReason(null);
        user.setLockedUntil(null);
        userRepository.save(user);

        log.info("Admin unlocked user {}: reason={}", userId, request.getReason());
        return ResponseEntity.ok(ApiResponse.success(null, "User unlocked"));
    }

    // ── Trust Score ─────────────────────────────────────────────────────────

    @PostMapping("/users/{userId}/trust-score")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> adjustTrustScore(
            @PathVariable Long userId,
            @RequestBody com.flashsale.identitydomain.dto.request.TrustScoreAdjustRequest request) {
        trustScoreService.adjustTrustScore(
                userId, request.getDelta(), request.getReason(), "ADMIN");
        return ResponseEntity.ok(ApiResponse.success(null, "Trust score adjusted"));
    }

    // ── Product Posting ─────────────────────────────────────────────────────

    @PostMapping("/users/{userId}/unlock-product-posting")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> unlockProductPosting(
            @PathVariable Long userId,
            @RequestBody UnlockProductPostingRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setProductPostingSuspended(false);
        user.setLastPostingSuspensionAt(null);
        userRepository.save(user);

        log.info("Admin unlocked product posting for user {}: note={}", userId, request.getNote());
        return ResponseEntity.ok(ApiResponse.success(null, "Product posting unlocked"));
    }
}
