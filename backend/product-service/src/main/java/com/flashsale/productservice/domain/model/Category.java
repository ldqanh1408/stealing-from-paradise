package com.flashsale.productservice.domain.model;

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

import java.time.LocalDateTime;

@Document(collection = "categories")
@CompoundIndex(name = "idx_parent_level", def = "{'parent_id': 1, 'level': 1}")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category {

    @Id
    private String id;

    @Indexed(sparse = true)
    private String parentId;

    private String name;

    @Indexed(unique = true)
    private String slug;

    private String description;

    private String imageUrl;

    @Builder.Default
    private Boolean isActive = true;

    @Builder.Default
    private Integer sortOrder = 0;

    private Integer level;  // 0 = root, 1 = subcategory, etc.

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
