package com.flashsale.flashsaleservice.domain.repository;

import com.flashsale.flashsaleservice.domain.model.FlashSaleSession;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface FlashSaleSessionRepository extends ReactiveCrudRepository<FlashSaleSession, Long> {
    Flux<FlashSaleSession> findByStatus(String status);
}
