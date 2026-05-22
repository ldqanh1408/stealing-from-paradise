package com.flashsale.productservice.service;

import com.flashsale.commonlib.exception.AppException;
import com.flashsale.productservice.domain.model.ProductVariant;
import com.flashsale.productservice.domain.model.StockReservation;
import com.flashsale.productservice.domain.repository.InventoryRepository;
import com.flashsale.productservice.domain.repository.ProductVariantRepository;
import com.flashsale.productservice.domain.repository.StockReservationRepository;
import com.flashsale.productservice.domain.util.InventoryOperations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockReservationServiceTest {

    @Mock private StockReservationRepository reservationRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private ProductVariantRepository variantRepository;
    @Mock private InventoryOperations inventoryOps;
    @Mock private MongoTemplate mongoTemplate;
    @Mock private KafkaProducerService kafkaProducer;
    @InjectMocks private StockReservationService reservationService;

    private ProductVariant variant;

    @BeforeEach
    void setUp() {
        variant = ProductVariant.builder()
                .id("var-1")
                .productId("prod-1")
                .variantCode("SKU-001")
                .variantName("iPhone 15 Black 256GB")
                .price(BigDecimal.valueOf(999))
                .build();
    }

    // ─── reserveStock tests ──────────────────────────────────────────────────

    @Test
    void reserveStock_variantNotFound_throwsException() {
        when(variantRepository.findByVariantCode("SKU-001")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.reserveStock("session-1", "SKU-001", 2))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("SKU không tồn tại");
    }

    @Test
    void reserveStock_insufficientStock_throwsException() {
        when(variantRepository.findByVariantCode("SKU-001")).thenReturn(Optional.of(variant));
        when(inventoryOps.lockStock("SKU-001", 2)).thenReturn(false);

        assertThatThrownBy(() -> reservationService.reserveStock("session-1", "SKU-001", 2))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Không đủ hàng");
    }

    @Test
    void reserveStock_success_createsReservation() {
        when(variantRepository.findByVariantCode("SKU-001")).thenReturn(Optional.of(variant));
        when(inventoryOps.lockStock("SKU-001", 2)).thenReturn(true);
        when(reservationRepository.findBySessionIdAndSkuCode("session-1", "SKU-001"))
                .thenReturn(Optional.empty());
        when(reservationRepository.save(any(StockReservation.class))).thenAnswer(inv -> {
            StockReservation r = inv.getArgument(0);
            r.setId("res-1");
            return r;
        });

        String reservationId = reservationService.reserveStock("session-1", "SKU-001", 2);

        assertThat(reservationId).isEqualTo("res-1");
        
        ArgumentCaptor<StockReservation> captor = ArgumentCaptor.forClass(StockReservation.class);
        verify(reservationRepository).save(captor.capture());
        
        StockReservation saved = captor.getValue();
        assertThat(saved.getSkuCode()).isEqualTo("SKU-001");
        assertThat(saved.getQuantity()).isEqualTo(2);
        assertThat(saved.getSessionId()).isEqualTo("session-1");
        assertThat(saved.getStatus().toLowerCase()).isEqualTo("pending");
        assertThat(saved.getExpiresAt()).isNotNull();
    }

    @Test
    void reserveStock_existingReservation_incrementsQuantity() {
        StockReservation existing = StockReservation.builder()
                .id("res-1")
                .variantId("var-1")
                .skuCode("SKU-001")
                .sessionId("session-1")
                .quantity(2)
                .status("pending")
                .build();

        when(variantRepository.findByVariantCode("SKU-001")).thenReturn(Optional.of(variant));
        when(inventoryOps.lockStock("SKU-001", 3)).thenReturn(true);
        when(reservationRepository.findBySessionIdAndSkuCode("session-1", "SKU-001"))
                .thenReturn(Optional.of(existing));
        when(reservationRepository.save(any(StockReservation.class))).thenAnswer(inv -> inv.getArgument(0));

        reservationService.reserveStock("session-1", "SKU-001", 3);

        assertThat(existing.getQuantity()).isEqualTo(5); // 2 + 3
    }

    // ─── releaseReservation tests ────────────────────────────────────────────

    @Test
    void releaseReservation_pendingReservations_releasesAndPublishesEvent() {
        StockReservation r = StockReservation.builder()
                .id("res-1")
                .skuCode("SKU-001")
                .sessionId("session-1")
                .quantity(2)
                .status("pending")
                .build();

        when(reservationRepository.findBySessionIdAndStatus("session-1", "pending"))
                .thenReturn(List.of(r));
        when(inventoryOps.unlockStock("SKU-001", 2)).thenReturn(true);
        when(reservationRepository.save(any(StockReservation.class))).thenAnswer(inv -> inv.getArgument(0));

        reservationService.releaseReservation("session-1");

        verify(inventoryOps).unlockStock("SKU-001", 2);
        verify(kafkaProducer).publish(eq("stock.reservation.released"), any());
        assertThat(r.getStatus().toLowerCase()).isEqualTo("released");
    }

    // ─── confirmReservation tests ────────────────────────────────────────────

    @Test
    void confirmReservation_consumesLockedStock() {
        StockReservation r = StockReservation.builder()
                .id("res-1")
                .skuCode("SKU-001")
                .sessionId("session-1")
                .quantity(2)
                .status("pending")
                .build();

        when(reservationRepository.findBySessionIdAndStatus("session-1", "pending"))
                .thenReturn(List.of(r));
        when(inventoryOps.consumeLockedStock("SKU-001", 2)).thenReturn(true);
        when(reservationRepository.save(any(StockReservation.class))).thenAnswer(inv -> inv.getArgument(0));

        reservationService.confirmReservation("session-1");

        verify(inventoryOps).consumeLockedStock("SKU-001", 2);
        verify(kafkaProducer).publish(eq("stock.reservation.confirmed"), any());
        assertThat(r.getStatus().toLowerCase()).isEqualTo("confirmed");
    }

    // ─── cleanupExpiredReservations tests ───────────────────────────────────

    @Test
    void cleanupExpiredReservations_noExpiredReservations_doesNothing() {
        when(reservationRepository.findByStatusAndExpiresAtBefore(anyString(), any()))
                .thenReturn(List.of());

        reservationService.cleanupExpiredReservations();

        verify(inventoryOps, never()).unlockStock(anyString(), anyInt());
        verify(kafkaProducer, never()).publish(eq("stock.reservation.expired"), any());
    }

    @Test
    void cleanupExpiredReservations_releasesExpiredReservations() {
        StockReservation expired = StockReservation.builder()
                .id("res-expired")
                .skuCode("SKU-001")
                .sessionId("session-expired")
                .quantity(3)
                .status("pending")
                .build();

        when(reservationRepository.findByStatusAndExpiresAtBefore(eq("pending"), any()))
                .thenReturn(List.of(expired));
        when(inventoryOps.unlockStock("SKU-001", 3)).thenReturn(true);
        when(reservationRepository.save(any(StockReservation.class))).thenAnswer(inv -> inv.getArgument(0));

        reservationService.cleanupExpiredReservations();

        verify(inventoryOps).unlockStock("SKU-001", 3);
        verify(kafkaProducer).publish(eq("stock.reservation.expired"), any());
        assertThat(expired.getStatus().toLowerCase()).isEqualTo("released");
    }
}
