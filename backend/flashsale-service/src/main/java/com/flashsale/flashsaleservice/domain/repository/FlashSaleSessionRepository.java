package com.flashsale.flashsaleservice.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.flashsale.flashsaleservice.domain.model.FlashSaleSession;
import java.util.List;

@Repository
public interface FlashSaleSessionRepository extends JpaRepository<FlashSaleSession, Long> {
    List<FlashSaleSession> findByStatus(String status);
}

