package com.flashsale.productdomain.scheduler;

import com.flashsale.productdomain.config.RedisKeys;
import com.flashsale.productdomain.domain.model.ProductStatus;
import com.flashsale.productdomain.domain.model.ReservationStatus;
import com.flashsale.productdomain.domain.model.SkuStatus;
import com.flashsale.productdomain.domain.model.StockReservation;
import com.flashsale.productdomain.domain.repository.ProductRepository;
import com.flashsale.productdomain.domain.repository.SkuRepository;
import com.flashsale.productdomain.domain.repository.StockReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationCleanupJob {

    private final StockReservationRepository stockReservationRepository;
    private final SkuRepository skuRepository;
    private final ProductRepository productRepository;
    private final StringRedisTemplate redisTemplate;

    /**
     * Cleanup expired reservations every 1 minute.
     * Releases reserved stock back to Redis and DB.
     */
    @Scheduled(fixedRate = 60000) // 1 minute
    @Transactional
    public void cleanupExpiredReservations() {
        log.info("Starting expired reservation cleanup job");

        LocalDateTime now = LocalDateTime.now();
        List<StockReservation> expiredReservations = stockReservationRepository
                .findByStatusAndExpiresAtBefore(ReservationStatus.PENDING, now);

        if (expiredReservations.isEmpty()) {
            log.info("No expired reservations found");
            return;
        }

        log.info("Found {} expired reservations to cleanup", expiredReservations.size());

        // Track affected product IDs for status recalculation
        Set<UUID> affectedProductIds = expiredReservations.stream()
                .map(res -> res.getSku().getProduct().getId())
                .collect(Collectors.toSet());

        for (StockReservation reservation : expiredReservations) {
            try {
                // Update reservation status to RELEASED
                reservation.setStatus(ReservationStatus.RELEASED);
                stockReservationRepository.save(reservation);

                // Refund stock to Redis
                String stockKey = RedisKeys.stockKey(reservation.getSku().getId());
                redisTemplate.opsForValue().increment(stockKey, reservation.getQuantity());
                log.debug("Released {} units for SKU {} to Redis",
                        reservation.getQuantity(), reservation.getSku().getId());

                // Refund stock to DB
                skuRepository.incrementStock(reservation.getSku().getId(), reservation.getQuantity());
                log.debug("Released {} units for SKU {} to DB",
                        reservation.getQuantity(), reservation.getSku().getId());

            } catch (Exception e) {
                log.error("Failed to cleanup reservation {}: {}", reservation.getId(), e.getMessage(), e);
            }
        }

        // Recalculate product status for affected products
        recalculateProductStatuses(affectedProductIds);

        log.info("Completed expired reservation cleanup job. Processed {} reservations", expiredReservations.size());
    }

    private void recalculateProductStatuses(Set<UUID> productIds) {
        for (UUID productId : productIds) {
            productRepository.findById(productId).ifPresent(product -> {
                if (product.getStatus() != ProductStatus.INACTIVE) {
                    List<com.flashsale.productdomain.domain.model.Sku> skus =
                            skuRepository.findByProductId(productId);

                    boolean hasActiveAndStock = skus.stream()
                            .anyMatch(s -> s.getStatus() == SkuStatus.ACTIVE && s.getStockQuantity() > 0);
                    boolean allOutOfStock = skus.stream()
                            .allMatch(s -> s.getStockQuantity() == 0);

                    if (hasActiveAndStock) {
                        product.setStatus(ProductStatus.ACTIVE);
                    } else if (allOutOfStock) {
                        product.setStatus(ProductStatus.OUT_OF_STOCK);
                    } else {
                        product.setStatus(ProductStatus.OUT_OF_STOCK);
                    }
                    productRepository.save(product);
                    log.debug("Updated product {} status to {}", productId, product.getStatus());
                }
            });
        }
    }
}
