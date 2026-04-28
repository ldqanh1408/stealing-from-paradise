package com.flashsale.productdomain.messaging;

import com.flashsale.productdomain.config.RedisKeys;
import com.flashsale.productdomain.domain.model.ProductStatus;
import com.flashsale.productdomain.domain.model.ReservationStatus;
import com.flashsale.productdomain.domain.model.Sku;
import com.flashsale.productdomain.domain.model.SkuStatus;
import com.flashsale.productdomain.domain.model.StockReservation;
import com.flashsale.productdomain.domain.repository.ProductRepository;
import com.flashsale.productdomain.domain.repository.SkuRepository;
import com.flashsale.productdomain.domain.repository.StockReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final StockReservationRepository stockReservationRepository;
    private final SkuRepository skuRepository;
    private final ProductRepository productRepository;
    private final StringRedisTemplate redisTemplate;

    @KafkaListener(topics = "order.confirmed", groupId = "product-service-group")
    @Transactional
    public void handleOrderConfirmed(String orderIdStr) {
        log.info("Received order.confirmed for orderId: {}", orderIdStr);
        UUID orderId = UUID.fromString(orderIdStr);
        List<StockReservation> reservations = stockReservationRepository.findByOrderId(orderId);
        for (StockReservation res : reservations) {
            res.setStatus(ReservationStatus.CONFIRMED);
        }
        stockReservationRepository.saveAll(reservations);
    }

    @KafkaListener(topics = "order.failed", groupId = "product-service-group")
    @Transactional
    public void handleOrderFailed(String orderIdStr) {
        log.info("Received order.failed for orderId: {}", orderIdStr);
        UUID orderId = UUID.fromString(orderIdStr);
        List<StockReservation> reservations = stockReservationRepository.findByOrderId(orderId);

        Set<UUID> affectedProductIds = reservations.stream()
                .map(res -> res.getSku().getProduct().getId())
                .collect(Collectors.toSet());

        for (StockReservation res : reservations) {
            if (res.getStatus() == ReservationStatus.PENDING) {
                res.setStatus(ReservationStatus.RELEASED);

                // Refund DB
                skuRepository.incrementStock(res.getSku().getId(), res.getQuantity());

                // Refund Redis
                String stockKey = RedisKeys.stockKey(res.getSku().getId());
                redisTemplate.opsForValue().increment(stockKey, res.getQuantity());

                log.debug("Released reservation {} - SKU: {}, quantity: {}",
                        res.getId(), res.getSku().getId(), res.getQuantity());
            }
        }
        stockReservationRepository.saveAll(reservations);

        // Recalculate product status for affected products
        recalculateProductStatuses(affectedProductIds);
    }

    private void recalculateProductStatuses(Set<UUID> productIds) {
        for (UUID productId : productIds) {
            productRepository.findById(productId).ifPresent(product -> {
                if (product.getStatus() != ProductStatus.INACTIVE) {
                    List<Sku> skus = skuRepository.findByProductId(productId);

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
                    log.debug("Updated product {} status to {} after order failed", productId, product.getStatus());
                }
            });
        }
    }
}
