package com.flashsale.flashsaleservice.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.flashsale.flashsaleservice.domain.model.FlashSaleReminder;
import java.util.Optional;

@Repository
public interface FlashSaleReminderRepository extends JpaRepository<FlashSaleReminder, Long> {
    Optional<FlashSaleReminder> findByUserIdAndSessionId(Long userId, Long sessionId);
}

