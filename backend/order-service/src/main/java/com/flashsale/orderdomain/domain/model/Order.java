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
@Table(name = "orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_order_id", nullable = false)
    private Long parentOrderId;

    @Column(nullable = false)
    private Long sellerId;

    @Column(unique = true, nullable = false)
    private String orderCode;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private BigDecimal totalAmt;

    @Column(nullable = false)
    private BigDecimal finalAmt;

    @Column(nullable = false)
    private String status = "PENDING";  // PENDING | PAID | SHIPPED | DELIVERED | CANCELLED

    private String cancelledBy;
    private String cancelReason;
    private Boolean isFlashSale = false;

    @Column(columnDefinition = "jsonb")
    private String shippingAddress;

    private String trackingNumber;
    private LocalDateTime shippingDeadline;

    @Version
    private Integer version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items;

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

