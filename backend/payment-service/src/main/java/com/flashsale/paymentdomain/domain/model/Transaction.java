package com.flashsale.paymentdomain.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_order_id", nullable = false)
    private Long parentOrderId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String method = "STRIPE";

    private String transRef;
    private String stripeTransferId;
    private BigDecimal applicationFeeAmount;
    private String stripeConnectMode;
    private String status;  // PENDING | SUCCESS | FAILED | REFUNDED

    @Column(columnDefinition = "jsonb")
    private String rawResponse;

    private LocalDateTime payAt;

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

