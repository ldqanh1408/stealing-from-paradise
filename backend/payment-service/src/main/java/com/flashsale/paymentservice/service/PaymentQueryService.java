package com.flashsale.paymentservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.paymentservice.domain.model.SellerTransfer;
import com.flashsale.paymentservice.domain.model.Transaction;
import com.flashsale.paymentservice.domain.repository.SellerTransferRepository;
import com.flashsale.paymentservice.domain.repository.TransactionRepository;
import com.flashsale.paymentservice.dto.response.SellerTransferInfo;
import com.flashsale.paymentservice.dto.response.TransactionDetailResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentQueryService {

    private final TransactionRepository transactionRepository;
    private final SellerTransferRepository sellerTransferRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public TransactionDetailResponse getTransactionByParentOrder(Long parentOrderId) {
        Transaction tx = transactionRepository.findByParentOrderId(parentOrderId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                        "Không tìm thấy giao dịch cho parent order: " + parentOrderId));
        return buildTransactionDetailResponse(tx);
    }

    /**
     * Parse the Stripe client_secret from the Transaction's rawResponse.
     * The rawResponse is the full JSON from PaymentIntent.create().
     */
    @Transactional(readOnly = true)
    public String getClientSecret(Long parentOrderId) {
        Transaction tx = transactionRepository.findByParentOrderId(parentOrderId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                        "Không tìm thấy giao dịch cho parent order: " + parentOrderId));

        if (tx.getRawResponse() == null) {
            throw new AppException(ErrorCode.NOT_FOUND,
                    "Chưa có PaymentIntent cho đơn hàng này");
        }

        try {
            JsonNode root = objectMapper.readTree(tx.getRawResponse());
            if (root.has("client_secret") && !root.get("client_secret").isNull()) {
                return root.get("client_secret").asText();
            }
            throw new AppException(ErrorCode.INTERNAL_ERROR,
                    "client_secret not found in PaymentIntent response");
        } catch (Exception e) {
            if (e instanceof AppException) throw (AppException) e;
            log.error("Failed to parse client_secret from rawResponse for parentOrderId={}: {}",
                    parentOrderId, e.getMessage());
            throw new AppException(ErrorCode.INTERNAL_ERROR,
                    "Không thể đọc client_secret từ PaymentIntent");
        }
    }

    private TransactionDetailResponse buildTransactionDetailResponse(Transaction tx) {
        List<SellerTransfer> transfers = sellerTransferRepository.findAllByParentOrderId(tx.getParentOrderId());

        List<SellerTransferInfo> sellerInfos = transfers.stream()
                .map(t -> SellerTransferInfo.builder()
                        .sellerId(t.getSellerId())
                        .orderId(t.getOrderId())
                        .amount(t.getTransferAmount())
                        .fee(BigDecimal.ZERO)
                        .stripeTransferId(t.getStripeTransferId())
                        .transferStatus(t.getStatus())
                        .build())
                .collect(Collectors.toList());

        Long remainingSeconds = null;
        if ("PENDING".equals(tx.getStatus()) && tx.getCreatedAt() != null) {
            long elapsed = java.time.Duration.between(tx.getCreatedAt(), LocalDateTime.now()).getSeconds();
            remainingSeconds = Math.max(0, 600 - elapsed);
        }

        return TransactionDetailResponse.builder()
                .transactionId(tx.getId())
                .parentOrderId(tx.getParentOrderId())
                .amount(tx.getAmount())
                .status(tx.getStatus())
                .applicationFee(tx.getApplicationFeeAmount())
                .transRef(tx.getTransRef())
                .paidAt(tx.getPayAt() != null ? tx.getPayAt().toInstant(ZoneOffset.UTC) : null)
                .remainingSeconds(remainingSeconds)
                .sellers(sellerInfos)
                .build();
    }
}
