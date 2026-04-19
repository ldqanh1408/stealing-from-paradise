package com.flashsale.flashsaleservice.domain.repository;

import com.flashsale.flashsaleservice.domain.model.FlashSaleReminder;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface FlashSaleReminderRepository extends ReactiveCrudRepository<FlashSaleReminder, Long> {
    Mono<FlashSaleReminder> findByUserIdAndSessionId(Long userId, Long sessionId);
}
