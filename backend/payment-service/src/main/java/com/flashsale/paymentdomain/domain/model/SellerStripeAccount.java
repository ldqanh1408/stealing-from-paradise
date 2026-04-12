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

    @Column(unique = true, nullable = false)
    private Long sellerId;

    @Column(nullable = false)
    private String stripeAccountId;

    @Column(nullable = false)
    private String accountStatus = "PENDING";  // PENDING | ACTIVE | RESTRICTED | INACTIVE

    @Column(nullable = false)
    private Boolean chargesEnabled = false;

    @Column(nullable = false)
    private Boolean payoutsEnabled = false;

    @Column(nullable = false)
    private Boolean detailsSubmitted = false;

    private String onboardingUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
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

