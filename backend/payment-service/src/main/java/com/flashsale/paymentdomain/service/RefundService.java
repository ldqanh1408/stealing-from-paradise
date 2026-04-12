package com.flashsale.paymentdomain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.paymentdomain.domain.repository.RefundRepository;
import com.flashsale.paymentdomain.domain.model.Refund;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundService {

    private final RefundRepository refundRepository;

    @KafkaListener(topics = KafkaTopics.REFUND_REQUESTED, groupId = "payment-service-group")
    public void onRefundRequested(String message) {
        log.info("Refund requested: {}", message);
        // TODO: Create refund record with PENDING status
    }

    @KafkaListener(topics = KafkaTopics.REFUND_ADMIN_APPROVED, groupId = "payment-service-group")
    public void onRefundApproved(String message) {
        log.info("Refund approved by admin: {}", message);
        // TODO: Update refund status to APPROVED
    }

    public void requestRefund(Long orderId, String reason, String initiatedBy) {
        log.info("Requesting refund for order: {}, reason: {}", orderId, reason);
        Refund refund = Refund.builder()
            .orderId(orderId)
            .reason(reason)
            .initiatedBy(initiatedBy)
            .status("PENDING")
            .build();
        refundRepository.save(refund);
    }

    public void approveRefund(Long refundId, String adminNote) {
        log.info("Approving refund: {}", refundId);
        var refund = refundRepository.findById(refundId);
        if (refund.isPresent()) {
            Refund r = refund.get();
            r.setStatus("APPROVED");
            r.setAdminNote(adminNote);
            refundRepository.save(r);
        }
    }

    public void rejectRefund(Long refundId, String rejectReason) {
        log.info("Rejecting refund: {}", refundId);
        var refund = refundRepository.findById(refundId);
        if (refund.isPresent()) {
            Refund r = refund.get();
            r.setStatus("REJECTED");
            r.setRejectReason(rejectReason);
            refundRepository.save(r);
        }
    }
}

