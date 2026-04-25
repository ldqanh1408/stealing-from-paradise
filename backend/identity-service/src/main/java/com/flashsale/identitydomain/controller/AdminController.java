package com.flashsale.identitydomain.controller;

import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.dto.PageResponse;
import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.identitydomain.domain.model.Appeal;
import com.flashsale.identitydomain.domain.model.TrustScoreEventsConfig;
import com.flashsale.identitydomain.domain.model.User;
import com.flashsale.identitydomain.domain.repository.AppealRepository;
import com.flashsale.identitydomain.domain.repository.TrustScoreEventsConfigRepository;
import com.flashsale.identitydomain.domain.repository.UserBanHistoryRepository;
import com.flashsale.identitydomain.domain.repository.UserRepository;
import com.flashsale.identitydomain.dto.request.EventsConfigUpdateRequest;
import com.flashsale.identitydomain.dto.request.LockRequest;
import com.flashsale.identitydomain.dto.request.TrustScoreAdjustRequest;
import com.flashsale.identitydomain.dto.request.UnlockProductPostingRequest;
import com.flashsale.identitydomain.dto.request.UnlockRequest;
import com.flashsale.identitydomain.dto.response.BanHistoryResponse;
import com.flashsale.identitydomain.dto.response.EventsConfigResponse;
import com.flashsale.identitydomain.dto.response.TrustScoreLogResponse;
import com.flashsale.identitydomain.service.AppealService;
import com.flashsale.identitydomain.service.TrustScoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final UserRepository userRepository;
    private final UserBanHistoryRepository userBanHistoryRepository;
    private final TrustScoreService trustScoreService;
    private final TrustScoreEventsConfigRepository eventsConfigRepository;
    private final AppealRepository appealRepository;
    private final AppealService appealService;

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

        userBanHistoryRepository.save(
                com.flashsale.identitydomain.domain.model.UserBanHistory.builder()
                        .userId(userId)
                        .action("LOCKED")
                        .reason(request.getReason())
                        .performedBy("ADMIN")
                        .lockedUntil(request.getLockedUntil())
                        .build());

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

        userBanHistoryRepository.save(
                com.flashsale.identitydomain.domain.model.UserBanHistory.builder()
                        .userId(userId)
                        .action("UNLOCKED")
                        .reason(request.getReason())
                        .performedBy("ADMIN")
                        .build());

        log.info("Admin unlocked user {}: reason={}", userId, request.getReason());
        return ResponseEntity.ok(ApiResponse.success(null, "User unlocked"));
    }

    // ── Trust Score ─────────────────────────────────────────────────────────

    @PostMapping("/users/{userId}/trust-score")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TrustScoreLogResponse>> adjustTrustScore(
            @PathVariable Long userId,
            @RequestBody TrustScoreAdjustRequest request) {
        var logEntry = trustScoreService.applyTrustScoreDelta(
                userId, request.getDelta(), "ADMIN_ADJUST", request.getReason(), "ADMIN");

        TrustScoreLogResponse response = TrustScoreLogResponse.builder()
                .logId(logEntry.getId())
                .eventCode(logEntry.getEventCode())
                .delta(logEntry.getDelta())
                .changedBy(logEntry.getChangedBy())
                .reason(logEntry.getReason())
                .createdAt(logEntry.getCreatedAt())
                .build();

        return ResponseEntity.ok(ApiResponse.success(response, "Trust score adjusted"));
    }

    @GetMapping("/users/{userId}/trust-score/logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<TrustScoreLogResponse>>> getUserTrustScoreLogs(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return ResponseEntity.ok(ApiResponse.success(
                PageResponse.of(trustScoreService.getUserTrustScoreLogs(userId, pageable))));
    }

    @GetMapping("/users/{userId}/ban-history")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<java.util.List<BanHistoryResponse>>> getBanHistory(@PathVariable Long userId) {
        var history = userBanHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(h -> BanHistoryResponse.builder()
                        .id(h.getId())
                        .action(h.getAction())
                        .reason(h.getReason())
                        .performedBy(h.getPerformedBy())
                        .adminId(h.getAdminId())
                        .lockedUntil(h.getLockedUntil())
                        .createdAt(h.getCreatedAt())
                        .build())
                .toList();
        return ResponseEntity.ok(ApiResponse.success(history));
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

    // ── Trust Score Events Config ───────────────────────────────────────────

    @GetMapping("/trust-score-events-config")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<java.util.List<EventsConfigResponse>>> getEventsConfig() {
        var configs = eventsConfigRepository.findAll().stream()
                .map(c -> EventsConfigResponse.builder()
                        .id(c.getId())
                        .eventCode(c.getEventCode())
                        .delta(c.getDelta())
                        .description(c.getDescription())
                        .isActive(c.getIsActive())
                        .updatedAt(c.getUpdatedAt())
                        .build())
                .toList();
        return ResponseEntity.ok(ApiResponse.success(configs));
    }

    @PutMapping("/trust-score-events-config/{eventCode}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<ApiResponse<EventsConfigResponse>> updateEventsConfig(
            @PathVariable String eventCode,
            @RequestBody EventsConfigUpdateRequest request) {
        TrustScoreEventsConfig config = eventsConfigRepository.findByEventCode(eventCode)
                .orElseThrow(() -> new RuntimeException("Event code not found: " + eventCode));

        if (request.getDelta() != null) config.setDelta(request.getDelta());
        if (request.getDescription() != null) config.setDescription(request.getDescription());
        if (request.getIsActive() != null) config.setIsActive(request.getIsActive());

        eventsConfigRepository.save(config);

        EventsConfigResponse response = EventsConfigResponse.builder()
                .id(config.getId())
                .eventCode(config.getEventCode())
                .delta(config.getDelta())
                .description(config.getDescription())
                .isActive(config.getIsActive())
                .updatedAt(config.getUpdatedAt())
                .build();

        return ResponseEntity.ok(ApiResponse.success(response, "Config updated"));
    }

    // ── Appeals ─────────────────────────────────────────────────────────────

    @GetMapping("/appeals")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<java.util.Map<String, Object>>>> getAppeals(
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));

        Page<Appeal> appeals = "ALL".equalsIgnoreCase(status)
                ? appealRepository.findAll(pageable)
                : appealRepository.findByStatus(status, pageable);

        var content = appeals.map(a -> java.util.Map.<String, Object>of(
                "appealId", (Object) a.getId(),
                "userId", a.getUserId(),
                "trustScoreLogId", a.getTrustScoreLogId(),
                "reason", a.getReason(),
                "evidenceUrls", a.getEvidenceUrls() != null ? a.getEvidenceUrls() : "",
                "status", a.getStatus(),
                "reviewedBy", a.getReviewedBy() != null ? a.getReviewedBy() : "",
                "adminNote", a.getAdminNote() != null ? a.getAdminNote() : "",
                "reviewedAt", a.getReviewedAt() != null ? a.getReviewedAt() : "",
                "createdAt", a.getCreatedAt())).toList();

        Page<java.util.Map<String, Object>> pageResult = new org.springframework.data.domain.PageImpl<>(
                content, pageable, appeals.getTotalElements());

        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(pageResult)));
    }

    @PostMapping("/appeals/{appealId}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> resolveAppeal(
            @PathVariable Long appealId,
            @AuthenticationPrincipal UserDetailsImpl admin,
            @RequestBody java.util.Map<String, String> request) {
        String action = request.get("action");
        String adminNote = request.get("adminNote");

        try {
            if ("APPROVED".equalsIgnoreCase(action)) {
                appealService.approveAppeal(appealId, admin.getId(), adminNote, null);
            } else if ("REJECTED".equalsIgnoreCase(action)) {
                appealService.rejectAppeal(appealId, admin.getId(), adminNote);
            } else {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("INVALID_ACTION", "Action must be APPROVED or REJECTED"));
            }
            return ResponseEntity.ok(ApiResponse.success(null, "Appeal resolved"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("RESOLVE_FAILED", e.getMessage()));
        }
    }
}
