package com.flashsale.productdomain.domain.repository;

import com.flashsale.productdomain.domain.model.ReservationStatus;
import com.flashsale.productdomain.domain.model.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface StockReservationRepository extends JpaRepository<StockReservation, UUID> {
    List<StockReservation> findByStatusAndExpiresAtBefore(ReservationStatus status, LocalDateTime expiresAt);

    List<StockReservation> findByOrderId(UUID orderId);
}
