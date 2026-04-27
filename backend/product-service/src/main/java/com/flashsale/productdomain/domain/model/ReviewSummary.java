package com.flashsale.productdomain.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "review_summary")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewSummary {
    @Id
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "product_id", columnDefinition = "uuid")
    private UUID productId;

    @Column(name = "avg_rating")
    private Double avgRating = 0.0;

    @Column(name = "total_count")
    private Long totalCount = 0L;

    @Column(name = "count_5star")
    private Long count5Star = 0L;

    @Column(name = "count_4star")
    private Long count4Star = 0L;

    @Column(name = "count_3star")
    private Long count3Star = 0L;

    @Column(name = "count_2star")
    private Long count2Star = 0L;

    @Column(name = "count_1star")
    private Long count1Star = 0L;

    @Column(name = "count_with_media")
    private Long countWithMedia = 0L;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
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
