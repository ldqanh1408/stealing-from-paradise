package com.flashsale.paymentdomain.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "seller_stripe_accounts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerStripeAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", unique = true, nullable = false)
    private Long sellerId;

    @Column(name = "stripe_account_id", nullable = false)
    private String stripeAccountId;

    @Column(name = "account_status", nullable = false)
    private String accountStatus = "PENDING";

    @Column(name = "charges_enabled", nullable = false)
    private Boolean chargesEnabled = false;

    @Column(name = "payouts_enabled", nullable = false)
    private Boolean payoutsEnabled = false;

    @Column(name = "details_submitted", nullable = false)
    private Boolean detailsSubmitted = false;

    @Column(name = "onboarding_url", columnDefinition = "TEXT")
    private String onboardingUrl;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
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

