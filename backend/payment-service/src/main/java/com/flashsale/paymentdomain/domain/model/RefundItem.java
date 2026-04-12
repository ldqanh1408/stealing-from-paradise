package com.flashsale.paymentdomain.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "refund_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "refund_id", nullable = false)
    private Long refundId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "refund_amount", nullable = false)
    private BigDecimal refundAmount;

    private String itemReason;
    private String status = "PENDING";  // PENDING | RETURNED | COMPLETED
    private String returnTrackingNumber;

    @Column(columnDefinition = "jsonb")
    private String returnEvidenceImages;

    private LocalDateTime returnedAt;
}

