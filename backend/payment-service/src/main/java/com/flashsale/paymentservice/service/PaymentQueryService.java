package com.flashsale.paymentservice.service;

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

    @Transactional(readOnly = true)
    public TransactionDetailResponse getTransactionByParentOrder(Long parentOrderId) {
        Transaction tx = transactionRepository.findByParentOrderId(parentOrderId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                        "Không tìm thấy giao dịch cho parent order: " + parentOrderId));
        return buildTransactionDetailResponse(tx);
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
