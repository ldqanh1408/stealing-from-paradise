package com.flashsale.identitydomain.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_ban_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBanHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "action", nullable = false)
    private String action; // LOCKED | UNLOCKED

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "performed_by", nullable = false)
    private String performedBy; // ADMIN | SYSTEM

    @Column(name = "admin_id")
    private Long adminId;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

