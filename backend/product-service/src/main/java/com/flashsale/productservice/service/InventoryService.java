package com.flashsale.productservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.productservice.dto.cart.ReservationResponse;
import com.flashsale.productservice.dto.inventory.InventoryResponse;
import com.flashsale.productservice.entity.*;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.productservice.repository.ProductRepository;
import com.flashsale.productservice.repository.ProductVariantRepository;
import com.flashsale.productservice.repository.StockReservationRepository;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private static final String STOCK_RESERVED_KEY_PREFIX = "stock:reserved:";
    private static final int RESERVATION_TTL_MINUTES = 15;

    private final ProductVariantRepository variantRepository;
    private final StockReservationRepository reservationRepository;
    private final ProductRepository productRepository;
    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final InventorySyncService inventorySyncService;

    @Transactional(readOnly = true)
    public ApiResponse<InventoryResponse> getInventory(String variantCode) {
        ProductVariant variant = variantRepository.findByVariantCode(variantCode)
                .filter(v -> v.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Variant not found"));

        return ApiResponse.success(toInventoryResponse(variant));
    }

    @Transactional
    public ApiResponse<InventoryResponse> restock(String variantCode, int quantity, UserDetailsImpl user) {
        ProductVariant variant = variantRepository.findByVariantCode(variantCode)
                .filter(v -> v.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Variant not found"));

        Product product = productRepository.findById(variant.getProductId())
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Product not found"));

        if (!product.getSellerId().equals(user.getId())) {
            throw new AppException(ErrorCode.FORBIDDEN, "You don't have permission to restock this variant");
        }

        variant.setStockQuantity(variant.getStockQuantity() + quantity);
        if (variant.getStatus() == VariantStatus.OUT_OF_STOCK && variant.getStockQuantity() > 0) {
            variant.setStatus(VariantStatus.ACTIVE);
        }
        variantRepository.save(variant);

        inventorySyncService.updateVariantRedisStock(variant.getId(), variant.getStockQuantity());
        recomputeProductStatus(variant.getProductId());

        emitEvent(KafkaTopics.INVENTORY_ADJUSTED, variant.getId().toString(),
                Map.of("variantId", variant.getId(), "delta", quantity, "reason", "RESTOCK"));
        emitEvent(KafkaTopics.VARIANT_STOCK_UPDATED, variant.getId().toString(),
                Map.ofEntries(
                        Map.entry("variantId", variant.getId()),
                        Map.entry("productId", variant.getProductId()),
                        Map.entry("stockQuantity", variant.getStockQuantity()),
                        Map.entry("status", variant.getStatus().name()),
                        Map.entry("stockStatus", getVariantStockStatus(variant.getStatus())),
                        Map.entry("timestamp", LocalDateTime.now().toString())
                ));

        return ApiResponse.success(toInventoryResponse(variant));
    }

    @Transactional
    public ApiResponse<InventoryResponse> adjustStock(String variantCode, int delta, String source, UserDetailsImpl user) {
        ProductVariant variant = variantRepository.findByVariantCode(variantCode)
                .filter(v -> v.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Variant not found"));

        Product product = productRepository.findById(variant.getProductId())
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Product not found"));

        if (!product.getSellerId().equals(user.getId())) {
            throw new AppException(ErrorCode.FORBIDDEN, "You don't have permission to adjust stock for this variant");
        }

        try {
            int newQuantity = variant.getStockQuantity() + delta;
            if (newQuantity < 0) {
                throw new AppException(ErrorCode.INSUFFICIENT_STOCK, "Stock adjustment would result in negative quantity");
            }
            variant.setStockQuantity(newQuantity);

            if (variant.getStatus() != VariantStatus.INACTIVE) {
                if (newQuantity == 0) {
                    variant.setStatus(VariantStatus.OUT_OF_STOCK);
                } else if (variant.getStatus() == VariantStatus.OUT_OF_STOCK) {
                    variant.setStatus(VariantStatus.ACTIVE);
                }
            }

            variant = variantRepository.saveAndFlush(variant);
        } catch (OptimisticLockException | ObjectOptimisticLockingFailureException e) {
            throw new AppException(ErrorCode.OPTIMISTIC_LOCK, "Variant was modified by another request. Please retry.");
        }

        inventorySyncService.updateVariantRedisStock(variant.getId(), variant.getStockQuantity());
        recomputeProductStatus(variant.getProductId());

        emitEvent(KafkaTopics.INVENTORY_ADJUSTED, variant.getId().toString(),
                Map.of("variantId", variant.getId(), "delta", delta, "reason", source != null ? source : "MANUAL"));
        emitEvent(KafkaTopics.VARIANT_STOCK_UPDATED, variant.getId().toString(),
                Map.ofEntries(
                        Map.entry("variantId", variant.getId()),
                        Map.entry("productId", variant.getProductId()),
                        Map.entry("stockQuantity", variant.getStockQuantity()),
                        Map.entry("status", variant.getStatus().name()),
                        Map.entry("stockStatus", getVariantStockStatus(variant.getStatus())),
                        Map.entry("timestamp", LocalDateTime.now().toString())
                ));

        return ApiResponse.success(toInventoryResponse(variant));
    }

    @Transactional
    public ApiResponse<ReservationResponse> reserveStock(UUID variantId, int quantity, String sessionId) {
        if (quantity <= 0) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Quantity must be positive");
        }

        ProductVariant variant = variantRepository.findById(variantId)
                .filter(v -> v.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Variant not found"));

        String redisKey = STOCK_RESERVED_KEY_PREFIX + variantId;
        Long currentReserved = redisTemplate.opsForValue().decrement(redisKey, quantity);

        if (currentReserved != null && currentReserved < 0) {
            redisTemplate.opsForValue().increment(redisKey, quantity);
            throw new AppException(ErrorCode.INSUFFICIENT_STOCK, "Insufficient stock available");
        }

        try {
            StockReservation reservation = StockReservation.builder()
                    .variantId(variantId)
                    .sessionId(sessionId)
                    .quantity(quantity)
                    .status(ReservationStatus.PENDING)
                    .expiresAt(LocalDateTime.now().plusMinutes(RESERVATION_TTL_MINUTES))
                    .build();

            reservation = reservationRepository.save(reservation);

            variant.setStockQuantity(variant.getStockQuantity() - quantity);
            if (variant.getStockQuantity() <= 0) {
                variant.setStatus(VariantStatus.OUT_OF_STOCK);
            }
            variantRepository.save(variant);

            inventorySyncService.updateVariantRedisStock(variantId, variant.getStockQuantity());
            recomputeProductStatus(variant.getProductId());

            return ApiResponse.success(toReservationResponse(reservation));
        } catch (Exception e) {
            redisTemplate.opsForValue().increment(redisKey, quantity);
            throw e;
        }
    }

    @Transactional
    public ApiResponse<Void> releaseReservation(UUID reservationId) {
        StockReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Reservation not found"));

        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Reservation is not in pending status");
        }

        reservation.setStatus(ReservationStatus.RELEASED);
        reservationRepository.save(reservation);

        String redisKey = STOCK_RESERVED_KEY_PREFIX + reservation.getVariantId();
        redisTemplate.opsForValue().increment(redisKey, reservation.getQuantity());

        ProductVariant variant = variantRepository.findById(reservation.getVariantId())
                .filter(v -> v.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Variant not found"));

        variant.setStockQuantity(variant.getStockQuantity() + reservation.getQuantity());
        if (variant.getStatus() == VariantStatus.OUT_OF_STOCK && variant.getStockQuantity() > 0) {
            variant.setStatus(VariantStatus.ACTIVE);
        }
        variantRepository.save(variant);

        emitEvent(KafkaTopics.VARIANT_STOCK_UPDATED, variant.getId().toString(),
                Map.ofEntries(
                        Map.entry("variantId", variant.getId()),
                        Map.entry("productId", variant.getProductId()),
                        Map.entry("stockQuantity", variant.getStockQuantity()),
                        Map.entry("status", variant.getStatus().name()),
                        Map.entry("stockStatus", getVariantStockStatus(variant.getStatus())),
                        Map.entry("timestamp", LocalDateTime.now().toString())
                ));

        inventorySyncService.updateVariantRedisStock(variant.getId(), variant.getStockQuantity());
        recomputeProductStatus(variant.getProductId());

        return ApiResponse.success(null);
    }

    @Transactional
    public ApiResponse<Void> confirmReservation(UUID reservationId) {
        StockReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Reservation not found"));

        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Reservation is not in pending status");
        }

        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservationRepository.save(reservation);

        return ApiResponse.success(null);
    }

    public void cleanupExpiredReservations() {
        List<StockReservation> expiredReservations = reservationRepository
                .findByStatusAndExpiresAtBefore(ReservationStatus.PENDING, LocalDateTime.now());

        for (StockReservation reservation : expiredReservations) {
            try {
                releaseReservation(reservation.getId());
                log.info("Cleaned up expired reservation: {}", reservation.getId());
            } catch (Exception e) {
                log.error("Failed to cleanup reservation: {}", reservation.getId(), e);
            }
        }
    }

    @Transactional
    public void restoreStockOnReturn(UUID variantId, int quantity) {
        ProductVariant variant = variantRepository.findById(variantId)
                .filter(v -> v.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Variant not found"));

        variant.setStockQuantity(variant.getStockQuantity() + quantity);
        if (variant.getStatus() == VariantStatus.OUT_OF_STOCK && variant.getStockQuantity() > 0) {
            variant.setStatus(VariantStatus.ACTIVE);
        }
        variantRepository.save(variant);

        emitEvent(KafkaTopics.VARIANT_STOCK_UPDATED, variant.getId().toString(),
                Map.ofEntries(
                        Map.entry("variantId", variant.getId()),
                        Map.entry("productId", variant.getProductId()),
                        Map.entry("stockQuantity", variant.getStockQuantity()),
                        Map.entry("status", variant.getStatus().name()),
                        Map.entry("stockStatus", getVariantStockStatus(variant.getStatus())),
                        Map.entry("timestamp", LocalDateTime.now().toString()),
                        Map.entry("reason", "ORDER_RETURN")
                ));

        inventorySyncService.updateVariantRedisStock(variantId, variant.getStockQuantity());
        recomputeProductStatus(variant.getProductId());
    }

    public void initializeVariantRedisStock(UUID variantId, int stockQuantity) {
        inventorySyncService.initializeVariantStock(variantId, stockQuantity);
    }

    public void recomputeProductStatus(UUID productId) {
        List<ProductVariant> variants = variantRepository.findByProductIdAndDeletedAtIsNull(productId);
        if (variants.isEmpty()) {
            return;
        }

        boolean hasActiveVariantWithStock = variants.stream()
                .anyMatch(v -> v.getStatus() == VariantStatus.ACTIVE && v.getStockQuantity() > 0);
        boolean allOutOfStock = variants.stream()
                .allMatch(v -> v.getStatus() == VariantStatus.OUT_OF_STOCK);

        productRepository.findById(productId).ifPresent(product -> {
            if (product.getDeletedAt() != null) {
                return;
            }
            ProductStatus current = product.getStatus();
            if (current == ProductStatus.ACTIVE ||
                    current == ProductStatus.OUT_OF_STOCK ||
                    current == ProductStatus.INACTIVE) {
                if (hasActiveVariantWithStock) {
                    product.setStatus(ProductStatus.ACTIVE);
                } else if (allOutOfStock) {
                    product.setStatus(ProductStatus.OUT_OF_STOCK);
                }
                productRepository.save(product);
            }
        });
    }

    private InventoryResponse toInventoryResponse(ProductVariant variant) {
        Integer lockedQuantity = reservationRepository.sumQuantityByVariantIdAndStatus(
                variant.getId(), ReservationStatus.PENDING);
        int locked = lockedQuantity != null ? lockedQuantity : 0;

        return InventoryResponse.builder()
                .variantId(variant.getId())
                .variantCode(variant.getVariantCode())
                .stockTotal(variant.getStockQuantity() + locked)
                .stockLocked(locked)
                .stockAvailable(variant.getStockQuantity())
                .stockFlashReserved(0)
                .build();
    }

    private ReservationResponse toReservationResponse(StockReservation reservation) {
        return ReservationResponse.builder()
                .reservationId(reservation.getId())
                .variantId(reservation.getVariantId())
                .quantity(reservation.getQuantity())
                .expiresAt(reservation.getExpiresAt())
                .status(reservation.getStatus().name())
                .build();
    }

    private void emitEvent(String topic, String key, Map<String, Object> payload) {
        try {
            String value = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(topic, key, value);
        } catch (Exception e) {
            log.error("Failed to emit Kafka event: topic={}, key={}", topic, key, e);
        }
    }

    private String getVariantStockStatus(VariantStatus status) {
        if (status == null) {
            return "unknown";
        }
        return switch (status) {
            case ACTIVE -> "in_stock";
            case OUT_OF_STOCK -> "out_of_stock";
            case INACTIVE -> "unavailable";
        };
    }
}
