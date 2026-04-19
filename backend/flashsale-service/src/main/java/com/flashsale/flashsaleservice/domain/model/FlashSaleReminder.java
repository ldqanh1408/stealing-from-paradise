package com.flashsale.flashsaleservice.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("fs_reminders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlashSaleReminder {

    @Id
    private Long id;

    @Column("user_id")
    private Long userId;

    @Column("session_id")
    private Long sessionId;

    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;
}
