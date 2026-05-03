package com.flashsale.identitydomain.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;

@Entity
@Table(name = "users", indexes = {
    @Index(columnList = "email", unique = true),
    @Index(columnList = "username", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    private String phone;
    private String password;
    @Column(name = "full_name")
    private String fullName;
    @Column(name = "avatar_url")
    private String avatarUrl;
    private String status;
    @Column(name = "trust_score")
    private Integer trustScore = 80;
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;
    @Column(name = "lock_reason")
    private String lockReason;
     @Column(name = "appeal_count")
     @Default
     private Integer appealCount = 0;
     @Column(name = "product_posting_suspended", nullable = false)
     @Default
     private Boolean productPostingSuspended = false;

    @Column(name = "last_cancellation_penalty_at")
    private LocalDateTime lastCancellationPenaltyAt;

    @Column(name = "last_warning_at")
    private LocalDateTime lastWarningAt;

    @Column(name = "last_posting_suspension_at")
    private LocalDateTime lastPostingSuspensionAt;

    @Column(name = "reward_10_orders_accumulated")
    @Default
    private Integer reward10OrdersAccumulated = 0;

    @Version
    @Default
    private Integer version = 0;

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

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singleton(new SimpleGrantedAuthority("ROLE_BUYER"));
    }

    @Override
    public String getPassword() {
        return this.password;
    }

    @Override
    public String getUsername() {
        return this.username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !("LOCKED".equals(this.status) || (this.lockedUntil != null && this.lockedUntil.isAfter(LocalDateTime.now())));
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return "ACTIVE".equals(this.status);
    }
}
