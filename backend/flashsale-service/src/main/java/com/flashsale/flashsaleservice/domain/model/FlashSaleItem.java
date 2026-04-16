package com.flashsale.flashsaleservice.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "fs_items", indexes = {
    @Index(columnList = "session_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlashSaleItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "sku_code", nullable = false)
    private String skuCode;

    @Column(name = "flash_price", nullable = false)
    private BigDecimal flashPrice;

    @Column(name = "flash_stock", nullable = false)
    private Integer flashStock;

    @Column(name = "limit_per_user")
    private Integer limitPerUser = 1;

    @Column(name = "sold_qty")
    private Integer soldQty = 0;

    @Column(nullable = false)
    private String status = "PENDING";

    @Version
    private Integer version;

    @Column(name = "created_at", nullable = false)
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

