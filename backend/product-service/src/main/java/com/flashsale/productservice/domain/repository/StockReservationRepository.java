package com.flashsale.productservice.domain.repository;

import com.flashsale.productservice.domain.model.StockReservation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockReservationRepository extends MongoRepository<StockReservation, String> {

    // Find all reservations for a checkout session
    List<StockReservation> findBySessionId(String sessionId);

    // Find reservation by session + variant (for dedup)
    Optional<StockReservation> findBySessionIdAndSkuCode(String sessionId, String skuCode);

    // Find all expired PENDING reservations for cleanup job
    List<StockReservation> findByStatusAndExpiresAtBefore(String status, LocalDateTime expiresAt);

    // Count pending reservations for a variant
    long countByVariantIdAndStatus(String variantId, String status);

    // Find all reservations for a user session
    List<StockReservation> findBySessionIdAndStatus(String sessionId, String status);
}
