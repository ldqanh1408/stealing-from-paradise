package com.flashsale.productservice.service;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.productservice.domain.model.Inventory;
import com.flashsale.productservice.domain.model.ProductVariant;
import com.flashsale.productservice.domain.model.StockReservation;
import com.flashsale.productservice.domain.repository.InventoryRepository;
import com.flashsale.productservice.domain.repository.ProductVariantRepository;
import com.flashsale.productservice.domain.repository.StockReservationRepository;
import com.flashsale.productservice.domain.util.InventoryOperations;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockReservationService {

    private static final int RESERVATION_TTL_MINUTES = 15;
    
    private final StockReservationRepository reservationRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryOperations inventoryOps;
    private final MongoTemplate mongoTemplate;
    private final KafkaProducerService kafkaProducer;

    /**
     * Phase 1: Reserve stock for checkout (2-layer: Redis fast + DB persistence)
     * Called when order.created event is received.
     * 
     * Returns reservation ID if successful, throws AppException if stock insufficient.
     */
    public String reserveStock(String sessionId, String skuCode, int quantity) {
        // Validate variant exists
        ProductVariant variant = variantRepository.findByVariantCode(skuCode)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "SKU không tồn tại: " + skuCode));

        // Layer 1: Redis fast decrement (if Redis were integrated)
        // For now, we rely on MongoDB atomic operations
        // inventoryOps.lockStock(skuCode, quantity);
        
        // Layer 2: MongoDB atomic - decrement stock_available, increment stock_locked
        boolean locked = inventoryOps.lockStock(skuCode, quantity);
        if (!locked) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Không đủ hàng để đặt chỗ: " + skuCode);
        }

        // Check for existing reservation (upsert behavior)
        Optional<StockReservation> existing = reservationRepository.findBySessionIdAndSkuCode(sessionId, skuCode);
        StockReservation reservation;
        
        if (existing.isPresent()) {
            reservation = existing.get();
            reservation.setQuantity(reservation.getQuantity() + quantity);
            reservation.setExpiresAt(LocalDateTime.now().plusMinutes(RESERVATION_TTL_MINUTES));
        } else {
            reservation = StockReservation.builder()
                    .id(UUID.randomUUID().toString())
                    .variantId(variant.getId())
                    .skuCode(skuCode)
                    .sessionId(sessionId)
                    .quantity(quantity)
                    .status(StockReservation.ReservationStatus.PENDING.name().toLowerCase())
                    .expiresAt(LocalDateTime.now().plusMinutes(RESERVATION_TTL_MINUTES))
                    .build();
        }

        reservation = reservationRepository.save(reservation);
        
        log.info("Stock reserved: session={}, sku={}, qty={}, reservationId={}", 
                sessionId, skuCode, quantity, reservation.getId());
        
        return reservation.getId();
    }

    /**
     * Phase 2a: Confirm reservation (payment success)
     * Move from locked -> confirmed, decrement stock_total
     */
    public void confirmReservation(String sessionId) {
        List<StockReservation> reservations = reservationRepository.findBySessionIdAndStatus(
                sessionId, StockReservation.ReservationStatus.PENDING.name().toLowerCase());

        for (StockReservation r : reservations) {
            // Consume locked stock: decrement stock_total and stock_locked
            inventoryOps.consumeLockedStock(r.getSkuCode(), r.getQuantity());
            
            // Update reservation status
            r.setStatus(StockReservation.ReservationStatus.CONFIRMED.name().toLowerCase());
            reservationRepository.save(r);

            kafkaProducer.publish(KafkaTopics.STOCK_RESERVATION_CONFIRMED, buildReservationEvent(r));
            
            log.info("Reservation confirmed: reservationId={}, sku={}, qty={}", 
                    r.getId(), r.getSkuCode(), r.getQuantity());
        }
    }

    /**
     * Phase 2b: Release reservation (payment failed or timeout)
     * Restore stock_available, mark as released
     */
    public void releaseReservation(String sessionId) {
        List<StockReservation> reservations = reservationRepository.findBySessionIdAndStatus(
                sessionId, StockReservation.ReservationStatus.PENDING.name().toLowerCase());

        for (StockReservation r : reservations) {
            // Restore stock
            inventoryOps.unlockStock(r.getSkuCode(), r.getQuantity());
            
            // Update reservation status
            r.setStatus(StockReservation.ReservationStatus.RELEASED.name().toLowerCase());
            reservationRepository.save(r);

            kafkaProducer.publish(KafkaTopics.STOCK_RESERVATION_RELEASED, buildReservationEvent(r));
            
            log.info("Reservation released: reservationId={}, sku={}, qty={}", 
                    r.getId(), r.getSkuCode(), r.getQuantity());
        }
    }

    /**
     * Cleanup job: Release all expired pending reservations
     * Runs every 1-5 minutes per BR-007
     */
    @Scheduled(fixedDelayString = "${reservation.cleanup.interval-ms:180000}") // default 3 minutes
    public void cleanupExpiredReservations() {
        LocalDateTime cutoff = LocalDateTime.now();
        
        List<StockReservation> expired = reservationRepository.findByStatusAndExpiresAtBefore(
                StockReservation.ReservationStatus.PENDING.name().toLowerCase(), cutoff);

        if (expired.isEmpty()) {
            return;
        }

        log.info("Found {} expired reservations to release", expired.size());
        
        for (StockReservation r : expired) {
            try {
                // Restore stock
                inventoryOps.unlockStock(r.getSkuCode(), r.getQuantity());
                
                // Update status
                r.setStatus(StockReservation.ReservationStatus.RELEASED.name().toLowerCase());
                reservationRepository.save(r);

                kafkaProducer.publish(KafkaTopics.STOCK_RESERVATION_EXPIRED, buildReservationEvent(r));
                
                log.debug("Expired reservation released: reservationId={}", r.getId());
            } catch (Exception e) {
                log.error("Failed to release expired reservation {}: {}", r.getId(), e.getMessage());
            }
        }
        
        log.info("Released {} expired reservations", expired.size());
    }

    /**
     * Restore stock when order is returned
     */
    public void restoreStockOnReturn(String skuCode, int quantity) {
        // Increment both stock_total and stock_available
        Query query = Query.query(Criteria.where("skuCode").is(skuCode));
        Update update = new Update()
                .inc("stockTotal", quantity)
                .inc("stockAvailable", quantity);
        mongoTemplate.updateFirst(query, update, Inventory.class);
        
        log.info("Stock restored on return: sku={}, qty={}", skuCode, quantity);
    }

    private java.util.Map<String, Object> buildReservationEvent(StockReservation r) {
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("reservation_id", r.getId());
        payload.put("sku_code", r.getSkuCode());
        payload.put("quantity", r.getQuantity());
        payload.put("session_id", r.getSessionId());
        payload.put("status", r.getStatus());
        payload.put("timestamp", System.currentTimeMillis());
        return payload;
    }
}
