package com.flashsale.paymentdomain.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "refunds")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Refund {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false)
    private Long transactionId;

    @Column(nullable = false)
    private Long orderId;

    private String groupRef;
    private String type;  // FULL | PARTIAL | RTS
    private String initiatedBy;  // BUYER | SELLER | ADMIN
    private String refundReasonType;
    private BigDecimal amount;
    private String reason;
    private String status = "PENDING";  // PENDING | APPROVED | REJECTED | PROCESSED

    @Column(columnDefinition = "jsonb")
    private String evidenceImages;

    private String rejectReason;
    private String adminNote;
    private BigDecimal adjustAmount;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private String refundRef;

    @Column(columnDefinition = "jsonb")
    private String rawResponse;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

