package com.flashsale.notificationservice.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "notifications")
@CompoundIndex(name = "idx_user_read", def = "{'user_id': 1, 'is_read': 1}")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {
    @Id
    private String id;

    @Indexed
    private Long userId;

    private String type;  // ORDER_CREATED | PAYMENT_SUCCESS | REFUND_APPROVED | etc.
    private String title;
    private String body;
    private String metadata;

    @Builder.Default
    private Boolean isRead = false;

    @Indexed(expireAfterSeconds = 7776000)  // 90 days TTL
    private LocalDateTime createdAt;
}

