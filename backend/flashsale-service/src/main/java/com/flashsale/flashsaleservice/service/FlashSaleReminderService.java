package com.flashsale.flashsaleservice.service;

import com.flashsale.flashsaleservice.domain.model.FlashSaleReminder;
import com.flashsale.flashsaleservice.domain.repository.FlashSaleReminderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class FlashSaleReminderService {

    private final FlashSaleReminderRepository reminderRepo;

    public Mono<Void> setReminder(Long sessionId, Long userId) {
        return reminderRepo.findByCustomerIdAndSessionId(userId, sessionId)
                .switchIfEmpty(Mono.defer(() -> reminderRepo.save(
                        FlashSaleReminder.builder()
                                .customerId(userId)
                                .sessionId(sessionId)
                                .build()
                )))
                .then();
    }

    public Mono<Void> removeReminder(Long sessionId, Long userId) {
        return reminderRepo.findByCustomerIdAndSessionId(userId, sessionId)
                .flatMap(reminderRepo::delete)
                .then();
    }
}
