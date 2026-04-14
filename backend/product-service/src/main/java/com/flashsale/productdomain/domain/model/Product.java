package com.flashsale.productdomain.domain.model;

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
@CompoundIndex(name = "idx_seller_status", def = "{'seller_id': 1, 'status': 1}")
@CompoundIndex(name = "idx_category_status", def = "{'category_id': 1, 'status': 1}")
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
    private String categoryId;  // FK -> MG_CATEGORIES._id

    private String name;
    private String description;
    private Map<String, Object> attributes;
    private List<String> images;
    private Boolean isFlash;
    private String status;           // PENDING | APPROVED | REJECTED
    private String rejectReason;
    private Integer stockAvailable;
    private LocalDateTime deletedAt;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}

