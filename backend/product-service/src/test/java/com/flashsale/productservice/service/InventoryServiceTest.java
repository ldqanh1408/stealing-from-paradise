package com.flashsale.productservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.productservice.dto.cart.ReservationResponse;
import com.flashsale.productservice.dto.inventory.InventoryResponse;
import com.flashsale.productservice.entity.*;
import com.flashsale.productservice.repository.ProductRepository;
import com.flashsale.productservice.repository.ProductVariantRepository;
import com.flashsale.productservice.repository.StockReservationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryServiceTest {

    @Mock
    private ProductVariantRepository variantRepository;
    @Mock
    private StockReservationRepository reservationRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private InventoryService inventoryService;

    @Test
    void getInventoryShouldReturnResponseForValidVariant() {
        UUID variantId = UUID.randomUUID();
        ProductVariant variant = ProductVariant.builder()
                .id(variantId)
                .variantCode("SKU-001")
                .variantName("Test Variant")
                .price(new BigDecimal("100"))
                .stockQuantity(50)
                .status(VariantStatus.ACTIVE)
                .build();
        when(variantRepository.findByVariantCode("SKU-001"))
                .thenReturn(Optional.of(variant));
        when(reservationRepository.sumQuantityByVariantIdAndStatus(any(), any()))
                .thenReturn(0);

        ApiResponse<InventoryResponse> response = inventoryService.getInventory("SKU-001");

        assertNotNull(response.getData());
        assertEquals(variantId, response.getData().getVariantId());
        assertEquals(50, response.getData().getStockAvailable());
    }

    @Test
    void getInventoryShouldThrowWhenVariantNotFound() {
        when(variantRepository.findByVariantCode("MISSING")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class,
                () -> inventoryService.getInventory("MISSING"));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void reserveStockShouldThrowWhenQuantityNotPositive() {
        AppException ex = assertThrows(AppException.class,
                () -> inventoryService.reserveStock(UUID.randomUUID(), 0, "session-1"));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    void reserveStockShouldThrowWhenInsufficientStock() {
        UUID variantId = UUID.randomUUID();
        ProductVariant variant = ProductVariant.builder()
                .id(variantId)
                .stockQuantity(5)
                .build();
        when(variantRepository.findByIdWithPessimisticLock(variantId))
                .thenReturn(Optional.of(variant));

        AppException ex = assertThrows(AppException.class,
                () -> inventoryService.reserveStock(variantId, 10, "session-1"));
        assertEquals(ErrorCode.INSUFFICIENT_STOCK, ex.getErrorCode());
    }

    @Test
    void reserveStockShouldSucceedAndDeductStock() throws Exception {
        UUID variantId = UUID.randomUUID();
        ProductVariant variant = ProductVariant.builder()
                .id(variantId)
                .variantCode("SKU-001")
                .productId(UUID.randomUUID())
                .stockQuantity(50)
                .status(VariantStatus.ACTIVE)
                .price(new BigDecimal("100"))
                .build();
        when(variantRepository.findByIdWithPessimisticLock(variantId))
                .thenReturn(Optional.of(variant));
        UUID reservationId = UUID.randomUUID();
        StockReservation savedReservation = StockReservation.builder()
                .id(reservationId)
                .variantId(variantId)
                .sessionId("session-1")
                .quantity(3)
                .status(ReservationStatus.PENDING)
                .build();
        when(reservationRepository.save(any(StockReservation.class)))
                .thenReturn(savedReservation);

        ApiResponse<ReservationResponse> response =
                inventoryService.reserveStock(variantId, 3, "session-1");

        assertNotNull(response.getData());
        assertEquals(3, response.getData().getQuantity());
        assertEquals(ReservationStatus.PENDING.name(), response.getData().getStatus());
        assertEquals(47, variant.getStockQuantity());
    }

    @Test
    void adjustStockShouldThrowWhenNegativeResult() {
        UUID variantId = UUID.randomUUID();
        ProductVariant variant = ProductVariant.builder()
                .id(variantId)
                .productId(UUID.randomUUID())
                .stockQuantity(10)
                .status(VariantStatus.ACTIVE)
                .build();
        when(variantRepository.findByVariantCode("SKU-001"))
                .thenReturn(Optional.of(variant));
        Product product = Product.builder()
                .id(variant.getProductId())
                .sellerId(1L)
                .build();
        when(productRepository.findById(variant.getProductId()))
                .thenReturn(Optional.of(product));

        UserDetailsImpl user = UserDetailsImpl.builder().id(1L).build();

        AppException ex = assertThrows(AppException.class,
                () -> inventoryService.adjustStock("SKU-001", -20, null, "MANUAL", user));
        assertEquals(ErrorCode.INSUFFICIENT_STOCK, ex.getErrorCode());
    }

    @Test
    void adjustStockShouldThrowWhenVersionMismatch() {
        UUID variantId = UUID.randomUUID();
        ProductVariant variant = ProductVariant.builder()
                .id(variantId)
                .productId(UUID.randomUUID())
                .stockQuantity(10)
                .version(5)
                .build();
        when(variantRepository.findByVariantCode("SKU-001"))
                .thenReturn(Optional.of(variant));
        Product product = Product.builder()
                .id(variant.getProductId())
                .sellerId(1L)
                .build();
        when(productRepository.findById(variant.getProductId()))
                .thenReturn(Optional.of(product));

        UserDetailsImpl user = UserDetailsImpl.builder().id(1L).build();

        AppException ex = assertThrows(AppException.class,
                () -> inventoryService.adjustStock("SKU-001", 5, 3, "MANUAL", user));
        assertEquals(ErrorCode.OPTIMISTIC_LOCK, ex.getErrorCode());
    }

    @Test
    void adjustStockShouldThrowWhenNotSeller() {
        UUID variantId = UUID.randomUUID();
        ProductVariant variant = ProductVariant.builder()
                .id(variantId)
                .productId(UUID.randomUUID())
                .stockQuantity(10)
                .build();
        when(variantRepository.findByVariantCode("SKU-001"))
                .thenReturn(Optional.of(variant));
        Product product = Product.builder()
                .id(variant.getProductId())
                .sellerId(5L)
                .build();
        when(productRepository.findById(variant.getProductId()))
                .thenReturn(Optional.of(product));

        UserDetailsImpl user = UserDetailsImpl.builder().id(1L).build();

        AppException ex = assertThrows(AppException.class,
                () -> inventoryService.adjustStock("SKU-001", 5, null, "MANUAL", user));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void releaseReservationShouldRestoreStock() throws Exception {
        UUID reservationId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        StockReservation reservation = StockReservation.builder()
                .id(reservationId)
                .variantId(variantId)
                .quantity(5)
                .status(ReservationStatus.PENDING)
                .build();
        when(reservationRepository.findById(reservationId))
                .thenReturn(Optional.of(reservation));
        ProductVariant variant = ProductVariant.builder()
                .id(variantId)
                .productId(UUID.randomUUID())
                .stockQuantity(45)
                .status(VariantStatus.OUT_OF_STOCK)
                .build();
        when(variantRepository.findByIdWithPessimisticLock(variantId))
                .thenReturn(Optional.of(variant));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        ApiResponse<Void> response = inventoryService.releaseReservation(reservationId);

        assertNotNull(response);
        assertEquals(50, variant.getStockQuantity());
        assertEquals(VariantStatus.ACTIVE, variant.getStatus());
    }

    @Test
    void confirmReservationShouldThrowWhenNotPending() {
        UUID reservationId = UUID.randomUUID();
        StockReservation reservation = StockReservation.builder()
                .id(reservationId)
                .status(ReservationStatus.CONFIRMED)
                .build();
        when(reservationRepository.findById(reservationId))
                .thenReturn(Optional.of(reservation));

        AppException ex = assertThrows(AppException.class,
                () -> inventoryService.confirmReservation(reservationId));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }
}
