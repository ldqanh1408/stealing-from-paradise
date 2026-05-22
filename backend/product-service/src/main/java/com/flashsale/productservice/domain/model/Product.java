package com.flashsale.productservice.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Document(collection = "products")
@CompoundIndexes({
    @CompoundIndex(name = "idx_seller_status", def = "{'sellerId': 1, 'status': 1}"),
    @CompoundIndex(name = "idx_category_status", def = "{'categoryId': 1, 'status': 1}"),
    @CompoundIndex(name = "idx_product_status", def = "{'status': 1}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    private String id;

    @Indexed
    private Long sellerId;

    @Indexed
    private String categoryId;

    private String name;

    @Indexed(unique = true)
    private String slug;

    private String description;

    private Map<String, Object> attributes;

    private List<String> images;

    private Boolean isFlashSale;

    private String status;

    private String rejectReason;

    private LocalDateTime reviewedAt;

    private Long reviewedBy;

    @Builder.Default
    private Integer rejectCount = 0;

    private LocalDateTime deletedAt;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public enum ProductStatus {
        DRAFT,
        PENDING,
        APPROVED,
        REJECTED,
        ACTIVE,
        OUT_OF_STOCK,
        INACTIVE
    }
}
