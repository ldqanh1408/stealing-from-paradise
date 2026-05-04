package com.flashsale.identitydomain.service;

import com.flashsale.identitydomain.domain.model.User;
import com.flashsale.identitydomain.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrustScoreService {

    private final UserRepository userRepository;

    @Transactional
    public void adjustTrustScore(Long userId, Integer delta, String reason, String changedBy) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        int newScore = Math.max(0, Math.min(100, user.getTrustScore() + delta));
        user.setTrustScore(newScore);
        userRepository.save(user);

        log.info("Trust score adjusted for user {}: delta={}, newScore={} by {}", userId, delta, newScore, changedBy);
    }
}
