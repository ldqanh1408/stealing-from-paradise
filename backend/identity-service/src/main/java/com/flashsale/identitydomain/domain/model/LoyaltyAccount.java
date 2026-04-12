package com.flashsale.identitydomain.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "loyalty_accounts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoyaltyAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private Long userId;

    @Column(name = "total_earned_points", nullable = false)
    private Integer totalEarnedPoints = 0;

    @Column(name = "available_points", nullable = false)
    private Integer availablePoints = 0;

    @Column(name = "used_points", nullable = false)
    private Integer usedPoints = 0;

    @Column(name = "expired_points", nullable = false)
    private Integer expiredPoints = 0;

    @Version
    private Integer version;

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

