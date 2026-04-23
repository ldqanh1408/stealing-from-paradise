package com.flashsale.paymentdomain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.paymentdomain.config.StripeConfig;
import com.flashsale.paymentdomain.domain.model.SellerTransfer;
import com.flashsale.paymentdomain.domain.model.Transaction;
import com.flashsale.paymentdomain.domain.repository.RefundRepository;
import com.flashsale.paymentdomain.domain.repository.SellerStripeAccountRepository;
import com.flashsale.paymentdomain.domain.repository.SellerTransferRepository;
import com.flashsale.paymentdomain.domain.repository.TransactionRepository;
import com.flashsale.paymentdomain.dto.response.TransactionDetailResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private RefundRepository refundRepository;
    @Mock
    private SellerTransferRepository sellerTransferRepository;
    @Mock
    private SellerStripeAccountRepository sellerStripeAccountRepository;
    @Mock
    private StripeConfig stripeConfig;
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                transactionRepository,
                refundRepository,
                sellerTransferRepository,
                sellerStripeAccountRepository,
                stripeConfig,
                kafkaTemplate,
                new ObjectMapper()
        );
    }

    @Test
    void getTransactionByParentOrder_returnsMappedResponseWithRemainingSeconds() {
        Long parentOrderId = 123L;
        LocalDateTime createdAt = LocalDateTime.now().minusSeconds(200);
        Transaction tx = Transaction.builder()
                .id(10L)
                .parentOrderId(parentOrderId)
                .amount(BigDecimal.valueOf(200_000))
                .method("STRIPE")
                .status("PENDING")
                .stripePiId("pi_1")
                .createdAt(createdAt)
                .build();
        SellerTransfer transfer = SellerTransfer.builder()
                .orderId(200L)
                .sellerId(2L)
                .sellerName("Seller A")
                .transferAmount(BigDecimal.valueOf(100_000))
                .feeAmount(BigDecimal.valueOf(5_000))
                .netAmount(BigDecimal.valueOf(95_000))
                .stripeTransferId("tr_1")
                .status("PENDING")
                .build();

        when(transactionRepository.findByParentOrderId(parentOrderId)).thenReturn(Optional.of(tx));
        when(sellerTransferRepository.findAllByParentOrderId(parentOrderId)).thenReturn(List.of(transfer));

        TransactionDetailResponse response = paymentService.getTransactionByParentOrder(parentOrderId);

        assertEquals(10L, response.getTransactionId());
        assertEquals(parentOrderId, response.getParentOrderId());
        assertEquals(1, response.getSellers().size());
        assertNotNull(response.getRemainingSeconds());
        assertTrue(Math.abs(response.getRemainingSeconds() - 400L) <= 2L);
    }

    @Test
    void getTransactionByParentOrder_throwsNotFoundWhenMissing() {
        when(transactionRepository.findByParentOrderId(999L)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class,
                () -> paymentService.getTransactionByParentOrder(999L));

        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void getClientSecret_throwsNotFoundWhenSecretIsNull() {
        Transaction tx = Transaction.builder()
                .id(1L)
                .parentOrderId(10L)
                .status("PENDING")
                .build();
        when(transactionRepository.findByParentOrderId(10L)).thenReturn(Optional.of(tx));

        AppException ex = assertThrows(AppException.class,
                () -> paymentService.getClientSecret(10L));

        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void onPaymentRequested_ignoresInvalidPayload() throws Exception {
        String message = new ObjectMapper().writeValueAsString(Map.of(
                "parent_order_id", 1,
                "total_amount", 0
        ));

        paymentService.onPaymentRequested(message);

        verify(transactionRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void onPaymentRequested_skipsWhenPendingTransactionAlreadyExists() throws Exception {
        Long parentOrderId = 33L;
        Transaction existing = Transaction.builder()
                .id(99L)
                .parentOrderId(parentOrderId)
                .status("PENDING")
                .build();
        when(transactionRepository.findByParentOrderId(parentOrderId)).thenReturn(Optional.of(existing));

        String message = new ObjectMapper().writeValueAsString(Map.of(
                "parent_order_id", parentOrderId,
                "user_id", 7,
                "total_amount", 100000
        ));

        paymentService.onPaymentRequested(message);

        verify(transactionRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void onOrderCancelled_publishesPaymentFailedWhenTransactionMissing() throws Exception {
        Long parentOrderId = 77L;
        when(transactionRepository.findByParentOrderId(parentOrderId)).thenReturn(Optional.empty());

        String message = new ObjectMapper().writeValueAsString(Map.of("parent_order_id", parentOrderId));

        paymentService.onOrderCancelled(message);

        verify(kafkaTemplate).send(
                eq(KafkaTopics.PAYMENT_FAILED),
                eq(String.valueOf(parentOrderId)),
                argThat(payload ->
                        payload.contains("\"parent_order_id\":" + parentOrderId)
                                && payload.contains("Order cancelled before payment"))
        );
        verify(transactionRepository, never()).save(any());
    }
}
