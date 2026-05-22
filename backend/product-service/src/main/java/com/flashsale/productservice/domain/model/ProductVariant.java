package com.flashsale.productservice.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "product_variants")
@CompoundIndex(name = "idx_product_sku", def = "{'product_id': 1, 'variant_code': 1}")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVariant {

    @Id
    private String id;

    @Indexed
    private String productId;

    @Indexed(unique = true)
    private String variantCode;

    private String variantName;

    private Map<String, Object> variantAttributes;

    private BigDecimal price;

    private BigDecimal originalPrice;

    @Builder.Default
    private Integer stockQuantity = 0;

    @Indexed
    private String status;

    @Version
    @Builder.Default
    private Integer version = 1;

    private String imageUrl;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public enum VariantStatus {
        ACTIVE,
        OUT_OF_STOCK,
        INACTIVE
    }
}
