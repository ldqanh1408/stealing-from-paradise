package com.flashsale.productdomain.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Document(collection = "product_variants")
@CompoundIndex(name = "idx_product_sku", def = "{'product_id': 1, 'sku_code': 1}")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVariant {
    @Id
    private String id;

    @Indexed
    private String productId;  // FK -> MG_PRODUCTS._id

    @Indexed(unique = true)
    private String skuCode;  // Unique SKU code (e.g., "SKU-IPHONE-BLACK-256GB")

    private String tierName;  // Tier/variant name (e.g., "Black - 256GB")

    private BigDecimal price;  // Selling price

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}

