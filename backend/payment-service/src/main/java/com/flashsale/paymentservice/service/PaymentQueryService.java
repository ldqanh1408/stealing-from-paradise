package com.flashsale.paymentservice.service;

import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.commonlib.stripe.StripeClientSecretExtractor;
import com.flashsale.paymentservice.domain.model.SellerTransfer;
import com.flashsale.paymentservice.domain.model.Transaction;
import com.flashsale.paymentservice.domain.repository.SellerTransferRepository;
import com.flashsale.paymentservice.domain.repository.TransactionRepository;
import com.flashsale.paymentservice.dto.response.ClientSecretResponse;
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
    private final com.flashsale.paymentservice.config.StripeConfig stripeConfig;

    @Transactional(readOnly = true)
    public TransactionDetailResponse getTransactionByParentOrder(Long parentOrderId) {
        List<Transaction> txs = transactionRepository.findAllByParentOrderId(parentOrderId);
        if (txs.isEmpty()) {
            throw new AppException(ErrorCode.NOT_FOUND,
                    "Không tìm thấy giao dịch cho parent order: " + parentOrderId);
        }

        Transaction primaryTx = txs.get(0);
        BigDecimal totalAmount = txs.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalAppFee = txs.stream()
                .map(Transaction::getApplicationFeeAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String overallStatus = "PENDING";
        boolean anyFailed = txs.stream().anyMatch(t -> "FAILED".equals(t.getStatus()));
        boolean anyPending = txs.stream().anyMatch(t -> "PENDING".equals(t.getStatus()));
        boolean allSuccess = txs.stream().allMatch(t -> "SUCCESS".equals(t.getStatus()));
        boolean allCancelled = txs.stream().allMatch(t -> "CANCELLED".equals(t.getStatus()));

        if (anyFailed) {
            overallStatus = "FAILED";
        } else if (anyPending) {
            overallStatus = "PENDING";
        } else if (allSuccess) {
            overallStatus = "SUCCESS";
        } else if (allCancelled) {
            overallStatus = "CANCELLED";
        } else {
            overallStatus = primaryTx.getStatus();
        }

        TransactionDetailResponse response = buildTransactionDetailResponse(primaryTx);
        response.setAmount(totalAmount);
        response.setApplicationFee(totalAppFee);
        response.setStatus(overallStatus);

        return response;
    }

    /**
     * Get the Stripe client_secret for an existing PaymentIntent.
     * The PaymentIntent is created during checkout.submit (via Kafka event).
     * Returns the client_secret so the frontend can render the Stripe PaymentElement.
     */
    @Transactional(readOnly = true)
    public ClientSecretResponse getClientSecret(Long parentOrderId) {
        List<Transaction> txs = transactionRepository.findAllByParentOrderId(parentOrderId);
        if (txs.isEmpty()) {
            throw new AppException(ErrorCode.NOT_FOUND,
                    "Không tìm thấy giao dịch cho parent order: " + parentOrderId);
        }

        boolean allPending = txs.stream().allMatch(tx -> "PENDING".equals(tx.getStatus()));
        if (!allPending) {
            Transaction nonPending = txs.stream().filter(tx -> !"PENDING".equals(tx.getStatus())).findFirst().orElse(txs.get(0));
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Giao dịch không ở trạng thái chờ thanh toán: " + nonPending.getStatus());
        }

        List<ClientSecretResponse.PaymentIntentItem> items = new java.util.ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        String firstClientSecret = null;

        for (Transaction tx : txs) {
            String clientSecret = StripeClientSecretExtractor.extract(tx.getRawResponse());
            if (clientSecret == null) {
                throw new AppException(ErrorCode.INTERNAL_ERROR,
                        "Chưa có PaymentIntent cho giao dịch này. Vui lòng thử lại.");
            }
            if (firstClientSecret == null) {
                firstClientSecret = clientSecret;
            }
            items.add(ClientSecretResponse.PaymentIntentItem.builder()
                    .clientSecret(clientSecret)
                    .stripeAccountId(tx.getStripeAccountId())
                    .sellerId(tx.getSellerId())
                    .amount(tx.getAmount())
                    .build());
            totalAmount = totalAmount.add(tx.getAmount());
        }

        return ClientSecretResponse.builder()
                .clientSecret(firstClientSecret)
                .parentOrderId(parentOrderId)
                .transactionId(txs.get(0).getId())
                .amount(totalAmount)
                .currency("vnd")
                .paymentIntents(items)
                .build();
    }

    private TransactionDetailResponse buildTransactionDetailResponse(Transaction tx) {
        List<SellerTransfer> transfers = sellerTransferRepository.findAllByParentOrderId(tx.getParentOrderId());

        List<SellerTransferInfo> sellerInfos = transfers.stream()
                .map(t -> {
                    BigDecimal fee = t.getPlatformCommissionAmount();
                    if (fee == null) {
                        double feePct = stripeConfig.getPlatformFeePercentage();
                        BigDecimal gross = t.getTransferAmount() != null ? t.getTransferAmount() : BigDecimal.ZERO;
                        fee = gross.multiply(BigDecimal.valueOf(feePct / 100.0)).setScale(0, java.math.RoundingMode.HALF_UP);
                    }
                    return SellerTransferInfo.builder()
                            .sellerId(t.getSellerId())
                            .orderId(t.getOrderId())
                            .amount(t.getTransferAmount())
                            .fee(fee)
                            .stripeTransferId(t.getStripeTransferId() != null ? t.getStripeTransferId() : t.getStripePayoutId())
                            .transferStatus(t.getStatus())
                            .build();
                })
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
