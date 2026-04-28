package com.flashsale.productdomain.scheduler;

import com.flashsale.productdomain.config.RedisKeys;
import com.flashsale.productdomain.domain.model.Sku;
import com.flashsale.productdomain.domain.model.SkuStatus;
import com.flashsale.productdomain.domain.repository.SkuRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryReconciliationJob {

    private static final Duration STOCK_KEY_TTL = Duration.ofHours(1);

    private final SkuRepository skuRepository;
    private final StringRedisTemplate redisTemplate;

    /**
     * Reconciliation job runs every 5 minutes to sync DB stock with Redis.
     * This is the "self-healing" mechanism to correct any drift between
     * Redis and Database.
     *
     * Design principles:
     * - Database is the source of truth
     * - Redis is a fast read cache for stock
     * - Reconciliation corrects any discrepancies
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void reconcileInventory() {
        log.info("Starting inventory reconciliation job");

        try {
            // Get all active SKUs from database
            List<Sku> activeSkus = skuRepository.findByStatus(SkuStatus.ACTIVE);
            int syncedCount = 0;
            int errorCount = 0;

            for (Sku sku : activeSkus) {
                try {
                    String stockKey = RedisKeys.stockKey(sku.getId());
                    int dbStock = sku.getStockQuantity();

                    // Set stock in Redis with TTL (1 hour)
                    // This overwrites any stale data and ensures Redis matches DB
                    redisTemplate.opsForValue().set(stockKey, String.valueOf(dbStock));
                    redisTemplate.expire(stockKey, STOCK_KEY_TTL);

                    syncedCount++;

                    if (syncedCount % 100 == 0) {
                        log.debug("Reconciliation progress: {} SKUs synced", syncedCount);
                    }
                } catch (Exception e) {
                    errorCount++;
                    log.error("Failed to reconcile SKU {}: {}", sku.getId(), e.getMessage());
                }
            }

            log.info("Inventory reconciliation completed. Synced: {}, Errors: {}", syncedCount, errorCount);

        } catch (Exception e) {
            log.error("Inventory reconciliation job failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Initialize stock in Redis for a specific SKU.
     * Called when a new SKU is created.
     */
    public void initializeStockInRedis(Sku sku) {
        String stockKey = RedisKeys.stockKey(sku.getId());
        redisTemplate.opsForValue().set(stockKey, String.valueOf(sku.getStockQuantity()));
        redisTemplate.expire(stockKey, STOCK_KEY_TTL);
        log.info("Initialized stock in Redis for SKU {}: {}", sku.getId(), sku.getStockQuantity());
    }

    /**
     * Manually trigger reconciliation for a specific SKU.
     * Useful after bulk operations or manual corrections.
     */
    public void reconcileSingleSku(Sku sku) {
        try {
            String stockKey = RedisKeys.stockKey(sku.getId());
            redisTemplate.opsForValue().set(stockKey, String.valueOf(sku.getStockQuantity()));
            redisTemplate.expire(stockKey, STOCK_KEY_TTL);
            log.debug("Reconciled single SKU {}: {}", sku.getId(), sku.getStockQuantity());
        } catch (Exception e) {
            log.error("Failed to reconcile single SKU {}: {}", sku.getId(), e.getMessage());
        }
    }
}
