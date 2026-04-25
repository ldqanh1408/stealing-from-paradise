package com.flashsale.identitydomain.service;

import com.flashsale.identitydomain.domain.model.Appeal;
import com.flashsale.identitydomain.domain.model.TrustScoreLog;
import com.flashsale.identitydomain.domain.model.User;
import com.flashsale.identitydomain.domain.repository.AppealRepository;
import com.flashsale.identitydomain.domain.repository.TrustScoreLogRepository;
import com.flashsale.identitydomain.domain.repository.UserRepository;
import com.flashsale.identitydomain.exception.AppealLimitExceededException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppealService {

    private final AppealRepository appealRepository;
    private final TrustScoreLogRepository trustScoreLogRepository;
    private final UserRepository userRepository;

    private static final int MAX_APPEALS_PER_YEAR = 3;

    @Transactional(readOnly = true)
    public List<Appeal> getUserAppeals(Long userId) {
        return appealRepository.findByUserId(userId);
    }

    @Transactional
    public Appeal createAppeal(Long userId, Long trustScoreLogId, String reason, List<String> evidenceUrls) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getAppealCount() >= MAX_APPEALS_PER_YEAR) {
            throw new AppealLimitExceededException(
                    "Appeal limit exceeded. Maximum " + MAX_APPEALS_PER_YEAR + " appeals per year.");
        }

        TrustScoreLog logEntry = trustScoreLogRepository.findById(trustScoreLogId)
                .filter(l -> l.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("Trust score log not found or does not belong to user"));

        boolean duplicate = appealRepository.findByUserId(userId).stream()
                .anyMatch(a -> a.getTrustScoreLogId().equals(trustScoreLogId) && "PENDING".equals(a.getStatus()));
        if (duplicate) {
            throw new RuntimeException("An appeal for this trust score log is already pending");
        }

        Appeal appeal = Appeal.builder()
                .userId(userId)
                .trustScoreLogId(trustScoreLogId)
                .reason(reason)
                .status("PENDING")
                .build();

        if (evidenceUrls != null && !evidenceUrls.isEmpty()) {
            appeal.setEvidenceUrls(String.join(",", evidenceUrls));
        }

        user.setAppealCount(user.getAppealCount() + 1);
        userRepository.save(user);

        log.info("Appeal created for user {} on log {}: reason={}", userId, trustScoreLogId, reason);
        return appealRepository.save(appeal);
    }

    @Transactional
    public Appeal approveAppeal(Long appealId, Long adminId, String adminNote, Integer trustScoreRestore) {
        Appeal appeal = appealRepository.findById(appealId)
                .orElseThrow(() -> new RuntimeException("Appeal not found"));

        if (!"PENDING".equals(appeal.getStatus())) {
            throw new RuntimeException("Appeal is not pending");
        }

        appeal.setStatus("APPROVED");
        appeal.setReviewedBy(adminId);
        appeal.setReviewedAt(java.time.LocalDateTime.now());
        appeal.setAdminNote(adminNote);

        if (trustScoreRestore != null && trustScoreRestore != 0) {
            User user = userRepository.findById(appeal.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            int newScore = Math.min(100, user.getTrustScore() + trustScoreRestore);
            user.setTrustScore(newScore);

            if (newScore >= 10 && "LOCKED".equals(user.getStatus())) {
                user.setStatus("ACTIVE");
                user.setLockReason(null);
                user.setLockedUntil(null);
            }
            userRepository.save(user);

            trustScoreLogRepository.save(TrustScoreLog.builder()
                    .userId(appeal.getUserId())
                    .delta(trustScoreRestore)
                    .eventCode("APPEAL_APPROVED")
                    .reason("Appeal approved by admin: " + adminNote)
                    .changedBy("ADMIN")
                    .build());
        }

        log.info("Appeal {} approved by admin {}: restore={}", appealId, adminId, trustScoreRestore);
        return appealRepository.save(appeal);
    }

    @Transactional
    public Appeal rejectAppeal(Long appealId, Long adminId, String adminNote) {
        Appeal appeal = appealRepository.findById(appealId)
                .orElseThrow(() -> new RuntimeException("Appeal not found"));

        if (!"PENDING".equals(appeal.getStatus())) {
            throw new RuntimeException("Appeal is not pending");
        }

        appeal.setStatus("REJECTED");
        appeal.setReviewedBy(adminId);
        appeal.setReviewedAt(java.time.LocalDateTime.now());
        appeal.setAdminNote(adminNote);

        log.info("Appeal {} rejected by admin {}: note={}", appealId, adminId, adminNote);
        return appealRepository.save(appeal);
    }
}
