package com.flashsale.wokerservice.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "shedlock")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShedLock {
    @Id
    private String name;

    @Column(name = "lock_until")
    private LocalDateTime lockUntil;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "locked_by")
    private String lockedBy;
}


