package com.flashsale.productservice.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

/**
 * MG_CARTS - Shopping cart container
 *
 * DESIGN DECISION:
 * - Cart stores metadata only (userId, totals, timestamps)
 * - Actual CartItems are stored in separate collection (cart_items)
 * - This allows independent query/update of cart items without loading entire cart
 * - Denormalized total_items field for fast cart preview queries
 */
@Document(collection = "carts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Cart {
    @Id
    private String id;

    @Indexed(unique = true)
    private Long userId;

    private Integer totalItems;  // Denormalized count for fast queries

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}

