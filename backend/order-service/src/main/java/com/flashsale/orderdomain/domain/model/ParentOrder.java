package com.flashsale.orderdomain.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "parent_orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParentOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "parentOrderSeq")
    @SequenceGenerator(name = "parentOrderSeq", sequenceName = "seq_parent_orders", allocationSize = 1)
    private Long id;

    @Column(name = "order_code", nullable = false, unique = true)
    private String orderCode;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "total_amt", nullable = false)
    private BigDecimal totalAmt;

    @Column(name = "loyalty_discount")
    private BigDecimal loyaltyDiscount = BigDecimal.ZERO;

    @Column(name = "loyalty_points_used")
    private Integer loyaltyPointsUsed = 0;

    @Column(name = "final_amt", nullable = false)
    private BigDecimal finalAmt;

    @Column(name = "address_id")
    private Long addressId;

    @Column(name = "timeout_at")
    private LocalDateTime timeoutAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Integer version;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "parent_order_id")
    private List<Order> orders;

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
