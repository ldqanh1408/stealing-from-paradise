package com.flashsale.identitydomain.service;

import com.flashsale.identitydomain.domain.model.LoyaltyAccount;
import com.flashsale.identitydomain.domain.model.PointTransaction;
import com.flashsale.identitydomain.domain.model.User;
import com.flashsale.identitydomain.domain.repository.LoyaltyAccountRepository;
import com.flashsale.identitydomain.domain.repository.PointTransactionRepository;
import com.flashsale.identitydomain.domain.repository.UserRepository;
import com.flashsale.identitydomain.dto.response.LoyaltyBalanceResponse;
import com.flashsale.identitydomain.dto.response.LoyaltyEstimateResponse;
import com.flashsale.identitydomain.dto.response.PointTransactionSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoyaltyService {

    private final LoyaltyAccountRepository loyaltyAccountRepository;
    private final PointTransactionRepository pointTransactionRepository;
    private final UserRepository userRepository;

    @Value("${loyalty.conversion-rate:200}")
    private int conversionRate;

    @Value("${loyalty.earning-rate:0.05}")
    private double earningRate;

    @Value("${loyalty.max-usable-percentage:0.20}")
    private double maxUsablePercentage;

    @Value("${loyalty.expiry-days:365}")
    private int expiryDays;

    @Transactional(readOnly = true)
    public LoyaltyBalanceResponse getBalance(Long userId) {
        LoyaltyAccount account = getOrCreateAccount(userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String tier = UserService.computeTrustTier(user.getTrustScore());

        List<PointTransaction> recentTx = pointTransactionRepository
                .findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .limit(5)
                .map(tx -> PointTransaction.builder()
                        .id(tx.getId())
                        .userId(tx.getUserId())
                        .orderId(tx.getOrderId())
                        .orderCode(tx.getOrderCode())
                        .delta(tx.getDelta())
                        .type(tx.getType())
                        .status(tx.getStatus())
                        .createdAt(tx.getCreatedAt())
                        .expiresAt(tx.getExpiresAt())
                        .build())
                .toList();

        return LoyaltyBalanceResponse.builder()
                .userId(userId)
                .loyaltyAccountId(account.getId())
                .availablePoints(account.getAvailablePoints())
                .pendingPoints(account.getTotalEarnedPoints() - account.getAvailablePoints()
                        - account.getUsedPoints() - account.getExpiredPoints())
                .expiredPoints(account.getExpiredPoints())
                .totalEarned(account.getTotalEarnedPoints())
                .totalUsed(account.getUsedPoints())
                .conversionRate(conversionRate)
                .note("1 point = 1/" + conversionRate + " of 200,000 VND = 1,000 VND")
                .maxUsablePerOrder(calculateMaxUsablePerOrder(BigDecimal.valueOf(200000)))
                .maxUsablePercentage(maxUsablePercentage)
                .expiryPolicy(LoyaltyBalanceResponse.ExpiryPolicy.builder()
                        .expiryDays(expiryDays)
                        .nextExpiryDate(LocalDateTime.now().plusDays(expiryDays).toLocalDate().toString())
                        .pointsExpiringSoon(0)
                        .build())
                .tierBenefits(LoyaltyBalanceResponse.TierBenefits.builder()
                        .tier(tier)
                        .trustScore(user.getTrustScore())
                        .earningRate(String.format("%.0f%%", getEarningRateForTier(tier) * 100))
                        .maxDiscountRate(String.format("%.0f%%", maxUsablePercentage * 100))
                        .build())
                .recentTransactions(recentTx.stream()
                        .map(tx -> LoyaltyBalanceResponse.RecentTransaction.builder()
                                .transactionId(tx.getId())
                                .type(tx.getType())
                                .delta(tx.getDelta())
                                .status(tx.getStatus())
                                .orderId(tx.getOrderId())
                                .orderCode(tx.getOrderCode())
                                .createdAt(tx.getCreatedAt())
                                .expiresAt(tx.getExpiresAt())
                                .build())
                        .toList())
                .build();
    }

    @Transactional(readOnly = true)
    public Page<PointTransactionSummary> getTransactions(Long userId, String type, String status, Pageable pageable) {
        return pointTransactionRepository.findByUserIdWithFilters(userId, type, status, pageable)
                .map(tx -> PointTransactionSummary.builder()
                        .transactionId(tx.getId())
                        .type(tx.getType())
                        .delta(tx.getDelta())
                        .status(tx.getStatus())
                        .orderId(tx.getOrderId())
                        .orderCode(tx.getOrderCode())
                        .balanceAfter(tx.getBalanceAfter())
                        .note(tx.getNote())
                        .expiresAt(tx.getExpiresAt())
                        .createdAt(tx.getCreatedAt())
                        .build());
    }

    @Transactional(readOnly = true)
    public LoyaltyEstimateResponse getEstimate(BigDecimal orderAmount, Integer pointsToUse, Long userId) {
        int pointsToEarn = calculatePointsToEarn(orderAmount, userId);
        int maxUsable = calculateMaxUsablePerOrder(orderAmount);
        int requested = (pointsToUse != null) ? Math.min(pointsToUse, maxUsable) : 0;

        LoyaltyAccount account = getOrCreateAccount(userId);

        return LoyaltyEstimateResponse.builder()
                .orderAmount(orderAmount.longValue())
                .pointsToEarn(pointsToEarn)
                .pointsToEarnFormula(String.format(
                        "order_amount * %.0f%% / 1000 = %d * %.0f / 1000 = %d",
                        earningRate * 100, orderAmount.longValue(), earningRate * 100, pointsToEarn))
                .availablePoints(account.getAvailablePoints())
                .maxPointsUsable(maxUsable)
                .maxPointsUsableFormula(String.format(
                        "20%% of order_amount = %d * 0.20 / 1000 = %d",
                        orderAmount.longValue(), maxUsable))
                .conversionRate(conversionRate)
                .pointsRequested(requested)
                .discountIfUse50((long) requested * conversionRate)
                .capPercent(20)
                .build();
    }

    @Transactional
    public void earnPoints(Long userId, Long orderId, String orderCode, BigDecimal orderAmount) {
        LoyaltyAccount account = getOrCreateAccount(userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        String tier = UserService.computeTrustTier(user.getTrustScore());
        double earningRateForTier = getEarningRateForTier(tier);

        int points = (int) Math.floor(orderAmount.doubleValue() * earningRateForTier / 1000.0);
        if (points <= 0) return;

        pointTransactionRepository.findByOrderIdAndType(orderId, "EARNED")
                .ifPresent(existing -> {
                    throw new RuntimeException("Points already earned for this order");
                });

        int balanceAfter = account.getAvailablePoints() + points;

        PointTransaction tx = PointTransaction.builder()
                .userId(userId)
                .orderId(orderId)
                .orderCode(orderCode)
                .delta(points)
                .remainingDelta(points)
                .type("EARNED")
                .status("PENDING")
                .balanceAfter(balanceAfter)
                .note("Earned from order " + orderCode)
                .expiresAt(LocalDateTime.now().plusDays(expiryDays))
                .build();
        pointTransactionRepository.save(tx);

        account.setTotalEarnedPoints(account.getTotalEarnedPoints() + points);
        loyaltyAccountRepository.save(account);

        log.info("Earned {} points for user {} from order {}", points, userId, orderCode);
    }

    @Transactional
    public void confirmPointsForOrder(Long orderId) {
        List<PointTransaction> pending = pointTransactionRepository.findByOrderId(orderId).stream()
                .filter(tx -> "EARNED".equals(tx.getType()) && "PENDING".equals(tx.getStatus()))
                .toList();

        for (PointTransaction tx : pending) {
            tx.setStatus("CONFIRMED");
            pointTransactionRepository.save(tx);

            LoyaltyAccount account = loyaltyAccountRepository.findByUserId(tx.getUserId())
                    .orElseThrow(() -> new RuntimeException("Loyalty account not found"));
            account.setAvailablePoints(account.getAvailablePoints() + tx.getDelta());
            loyaltyAccountRepository.save(account);
        }

        log.info("Confirmed {} pending point transactions for order {}", pending.size(), orderId);
    }

    @Transactional
    public void returnPointsForCancellation(Long userId, Long orderId, int pointsToReturn) {
        List<PointTransaction> usedTx = pointTransactionRepository.findByOrderId(orderId).stream()
                .filter(tx -> "USED".equals(tx.getType()))
                .toList();

        if (usedTx.isEmpty()) return;

        int returned = 0;
        for (PointTransaction tx : usedTx) {
            int toReturn = Math.min(tx.getRemainingDelta(), pointsToReturn - returned);
            if (toReturn <= 0) break;

            tx.setRemainingDelta(tx.getRemainingDelta() - toReturn);
            pointTransactionRepository.save(tx);
            returned += toReturn;
        }

        LoyaltyAccount account = loyaltyAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Loyalty account not found"));
        account.setAvailablePoints(account.getAvailablePoints() + returned);
        account.setUsedPoints(account.getUsedPoints() - returned);
        loyaltyAccountRepository.save(account);

        PointTransaction refundTx = PointTransaction.builder()
                .userId(userId)
                .orderId(orderId)
                .delta(returned)
                .remainingDelta(returned)
                .type("REFUNDED")
                .status("CONFIRMED")
                .balanceAfter(account.getAvailablePoints())
                .note("Points returned due to order cancellation")
                .build();
        pointTransactionRepository.save(refundTx);

        log.info("Returned {} points to user {} due to order {} cancellation", returned, userId, orderId);
    }

    @Transactional
    public LoyaltyAccount getOrCreateAccount(Long userId) {
        return loyaltyAccountRepository.findByUserId(userId)
                .orElseGet(() -> {
                    LoyaltyAccount newAccount = LoyaltyAccount.builder()
                            .userId(userId)
                            .totalEarnedPoints(0)
                            .availablePoints(0)
                            .usedPoints(0)
                            .expiredPoints(0)
                            .build();
                    return loyaltyAccountRepository.save(newAccount);
                });
    }

    private int calculatePointsToEarn(BigDecimal orderAmount, Long userId) {
        double rate = earningRate;
        if (userId != null) {
            User user = userRepository.findById(userId).orElse(null);
            if (user != null) {
                String tier = UserService.computeTrustTier(user.getTrustScore());
                rate = getEarningRateForTier(tier);
            }
        }
        return (int) Math.floor(orderAmount.doubleValue() * rate / 1000.0);
    }

    private int calculateMaxUsablePerOrder(BigDecimal orderAmount) {
        return (int) Math.floor(orderAmount.doubleValue() * maxUsablePercentage / conversionRate);
    }

    private double getEarningRateForTier(String tier) {
        return switch (tier) {
            case "ELITE" -> 0.10;
            case "DIAMOND" -> 0.08;
            case "PLATINUM" -> 0.05;
            case "GOLD" -> 0.04;
            case "SILVER" -> 0.03;
            default -> 0.02;
        };
    }
}
