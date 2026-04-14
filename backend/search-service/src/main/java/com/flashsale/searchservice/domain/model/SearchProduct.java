package com.flashsale.searchservice.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * ES_PRODUCTS_INDEX - Elasticsearch document cho Full-Text Search
 * Được đồng bộ từ product-service thông qua Kafka
 */
@Document(indexName = "products", versionType = Document.VersionType.EXTERNAL, createIndex = false)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchProduct {
    @Id
    private String id;  // Mongo ObjectId

    @Field(type = FieldType.Keyword)
    private String name;

    @MultiField(
        mainField = @Field(type = FieldType.Text, analyzer = "standard"),
        otherFields = {
            @InnerField(suffix = "keyword", type = FieldType.Keyword)
        }
    )
    private String description;

    @Field(type = FieldType.Long)
    private Long sellerId;

    @Field(type = FieldType.Keyword)
    private String sellerName;  // Denormalized từ USERS.full_name

    @Field(type = FieldType.Keyword)
    private String categoryId;  // FK -> MG_CATEGORIES._id

    @Field(type = FieldType.Keyword)
    private String categoryName;  // Denormalized category name

    @Field(type = FieldType.Double)
    private Double priceMin;

    @Field(type = FieldType.Double)
    private Double priceMax;

    @Field(type = FieldType.Integer)
    private Integer stockAvailable;

    @Field(type = FieldType.Boolean)
    private Boolean isFlash;

    @Field(type = FieldType.Keyword)
    private String status;  // PENDING | APPROVED | REJECTED

    @Field(type = FieldType.Keyword)
    private List<String> images;

    @Field(type = FieldType.Nested)
    private List<Map<String, Object>> attributes;

    @Field(type = FieldType.Keyword)
    private List<String> tags;  // Tag tìm kiếm để tăng relevance

    @Field(type = FieldType.Date)
    private LocalDateTime createdAt;

    @Field(type = FieldType.Date)
    private LocalDateTime updatedAt;
}

