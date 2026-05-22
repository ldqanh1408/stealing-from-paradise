package com.flashsale.productservice.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * MG_INVENTORY_LOGS - Audit trail for inventory adjustments (restock, adjust).
 * Created every time InventoryManagementService modifies stock quantities.
 */
@Document(collection = "inventory_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryLog {
    @Id
    private String id;

    @Indexed
    private String skuCode;

    @Indexed
    private String variantCode;

    private Integer delta;

    private String reason;

    private Long sellerId;

    private String type;  // RESTOCK / ADJUSTMENT / SALE_RETURN / EXPIRED_RESERVATION / etc

    private String orderId;  // Optional FK to order

    @CreatedDate
    private LocalDateTime createdAt;
}
