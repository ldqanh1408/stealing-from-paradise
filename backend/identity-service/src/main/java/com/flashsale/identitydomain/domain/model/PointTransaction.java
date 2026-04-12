package com.flashsale.identitydomain.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "point_transactions", indexes = {
    @Index(name = "idx_pt_user_id", columnList = "user_id"),
    @Index(name = "idx_pt_order_id", columnList = "order_id")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uq_point_txn_order_earned", columnNames = {"order_id", "type"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    private Long orderId;
    private String orderCode;
    private Integer delta;  // có thể âm (refund)
    private Integer remainingDelta;
    private String type;  // EARNED | USED | EXPIRED | REFUNDED
    private String status = "PENDING";  // PENDING | CONFIRMED
    private Integer balanceAfter;
    private String note;
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

