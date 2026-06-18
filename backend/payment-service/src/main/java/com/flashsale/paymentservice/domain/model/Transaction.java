package com.flashsale.paymentservice.domain.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions", indexes = {
    @Index(columnList = "parent_order_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_order_id", nullable = false)
    private Long parentOrderId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "trans_ref")
    private String transRef;

    @Column(name = "stripe_transfer_id")
    private String stripeTransferId;

    @Column(name = "application_fee_amount")
    private BigDecimal applicationFeeAmount;

    @Column(name = "stripe_connect_mode")
    private String stripeConnectMode;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "seller_id")
    private Long sellerId;

    @Column(name = "stripe_account_id")
    private String stripeAccountId;

    @Column(name = "stripe_payment_intent_id")
    private String stripePaymentIntentId;

    @Column(name = "stripe_charge_id")
    private String stripeChargeId;

    @Column(name = "gross_amount")
    private BigDecimal grossAmount;

    @Column(name = "seller_net_amount")
    private BigDecimal sellerNetAmount;

    @Column(name = "currency")
    private String currency;

    @Column(nullable = false)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_response", columnDefinition = "jsonb")
    private String rawResponse;

    @Column(name = "pay_at")
    private LocalDateTime payAt;

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
