package com.flashsale.identitydomain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoyaltyBalanceResponse {
    private Long userId;
    private Long loyaltyAccountId;
    private Integer availablePoints;
    private Integer pendingPoints;
    private Integer expiredPoints;
    private Integer totalEarned;
    private Integer totalUsed;
    private Integer conversionRate;
    private String note;
    private Integer maxUsablePerOrder;
    private Double maxUsablePercentage;
    private ExpiryPolicy expiryPolicy;
    private TierBenefits tierBenefits;
    private List<RecentTransaction> recentTransactions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExpiryPolicy {
        private Integer expiryDays;
        private String nextExpiryDate;
        private Integer pointsExpiringSoon;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TierBenefits {
        private String tier;
        private Integer trustScore;
        private String earningRate;
        private String maxDiscountRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentTransaction {
        private Long transactionId;
        private String type;
        private Integer delta;
        private String status;
        private Long orderId;
        private String orderCode;
        private LocalDateTime createdAt;
        private LocalDateTime expiresAt;
    }
}
