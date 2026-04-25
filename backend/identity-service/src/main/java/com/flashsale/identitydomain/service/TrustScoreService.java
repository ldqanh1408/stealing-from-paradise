package com.flashsale.identitydomain.service;

import com.flashsale.identitydomain.domain.model.TrustScoreLog;
import com.flashsale.identitydomain.domain.model.User;
import com.flashsale.identitydomain.domain.model.UserBanHistory;
import com.flashsale.identitydomain.domain.repository.TrustScoreLogRepository;
import com.flashsale.identitydomain.domain.repository.UserBanHistoryRepository;
import com.flashsale.identitydomain.domain.repository.UserRepository;
import com.flashsale.identitydomain.dto.response.TrustScoreLogResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrustScoreService {

    private final TrustScoreLogRepository trustScoreLogRepository;
    private final UserBanHistoryRepository userBanHistoryRepository;
    private final UserRepository userRepository;

    private static final int AUTO_LOCK_THRESHOLD = 10;
    private static final int WARNING_THRESHOLD = 60;

    @Transactional(readOnly = true)
    public Page<TrustScoreLogResponse> getUserTrustScoreLogs(Long userId, Pageable pageable) {
        return trustScoreLogRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(tx -> TrustScoreLogResponse.builder()
                        .logId(tx.getId())
                        .eventCode(tx.getEventCode())
                        .delta(tx.getDelta())
                        .scoreAfter(null)
                        .changedBy(tx.getChangedBy())
                        .reason(tx.getReason())
                        .createdAt(tx.getCreatedAt())
                        .build());
    }

    @Transactional
    public TrustScoreLog applyTrustScoreDelta(Long userId, Integer delta, String eventCode,
                                              String reason, String changedBy) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        TrustScoreLog logEntry = TrustScoreLog.builder()
                .userId(userId)
                .delta(delta)
                .eventCode(eventCode)
                .reason(reason)
                .changedBy(changedBy)
                .build();
        trustScoreLogRepository.save(logEntry);

        int newScore = Math.max(0, Math.min(100, user.getTrustScore() + delta));
        user.setTrustScore(newScore);

        if ("ADMIN".equals(changedBy)) {
            userBanHistoryRepository.save(UserBanHistory.builder()
                    .userId(userId)
                    .action(delta < 0 ? "LOCKED" : "UNLOCKED")
                    .reason(reason)
                    .performedBy(changedBy)
                    .build());
        }

        if (newScore < AUTO_LOCK_THRESHOLD && "SYSTEM".equals(changedBy)) {
            user.setStatus("LOCKED");
            user.setLockReason("Trust score below " + AUTO_LOCK_THRESHOLD + ". Appeal at /support/trust-score-appeal");
            user.setLockedUntil(LocalDateTime.now().plusDays(30));
        } else if (newScore >= AUTO_LOCK_THRESHOLD && "LOCKED".equals(user.getStatus())) {
            user.setStatus("ACTIVE");
            user.setLockReason(null);
            user.setLockedUntil(null);
        }

        if (newScore < WARNING_THRESHOLD && user.getTrustScore() >= WARNING_THRESHOLD && delta < 0) {
            log.warn("Trust score warning for user {}: score dropped to {}", userId, newScore);
        }

        userRepository.save(user);
        log.info("Trust score updated for user {}: delta={}, newScore={}", userId, delta, newScore);

        return logEntry;
    }
}
