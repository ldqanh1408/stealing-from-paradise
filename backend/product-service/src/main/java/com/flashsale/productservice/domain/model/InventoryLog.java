package com.flashsale.productservice.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
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

    private Integer delta;

    private String reason;

    private Long sellerId;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
