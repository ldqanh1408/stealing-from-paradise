package com.flashsale.identitydomain.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "point_transactions", indexes = {
    @Index(columnList = "user_id"),
    @Index(columnList = "order_id")
}, uniqueConstraints = {
    @UniqueConstraint(columnNames = {"order_id", "type"})
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

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "order_code")
    private String orderCode;

    @Column(nullable = false)
    private Integer delta;

    @Column(name = "remaining_delta")
    private Integer remainingDelta;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String status = "PENDING";

    @Column(name = "balance_after")
    private Integer balanceAfter;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

