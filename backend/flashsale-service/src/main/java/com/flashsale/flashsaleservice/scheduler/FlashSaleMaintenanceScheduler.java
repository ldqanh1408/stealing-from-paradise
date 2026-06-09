package com.flashsale.flashsaleservice.scheduler;

import com.flashsale.flashsaleservice.domain.model.FlashSaleItem;
import com.flashsale.flashsaleservice.domain.repository.FlashSaleItemRepository;
import com.flashsale.flashsaleservice.domain.repository.FlashSaleSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * JOB-08: hard-delete fs_sessions (and their items) soft-deleted >30 days.
 * JOB-21: reconcile Redis flash stock vs DB sold_qty for sessions that ended in last 24h.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FlashSaleMaintenanceScheduler {

    private final FlashSaleSessionRepository sessionRepo;
    private final FlashSaleItemRepository itemRepo;
    private final ReactiveStringRedisTemplate redisTemplate;

    @Scheduled(cron = "${flashsale.scheduler.cleanup-cron:0 0 3 * * *}")
    @SchedulerLock(name = "flashsale-cleanup-soft-deleted", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void cleanupSoftDeletedSessions() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        // Block so the ShedLock lock is held until the work completes.
        sessionRepo.findByDeletedAtIsNotNullAndDeletedAtBefore(cutoff)
                .flatMap(session -> itemRepo.deleteBySessionId(session.getId())
                        .then(sessionRepo.delete(session))
                        .thenReturn(session.getId()))
                .count()
                .doOnNext(n -> { if (n > 0) log.info("JOB-08: hard-deleted {} flash-sale sessions older than {}", n, cutoff); })
                .doOnError(e -> log.error("JOB-08 cleanup error: {}", e.getMessage(), e))
                .onErrorReturn(0L)
                .block();
    }

    @Scheduled(cron = "${flashsale.scheduler.reconcile-cron:0 0 4 * * *}")
    @SchedulerLock(name = "flashsale-reconcile-stock", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void reconcileStockForRecentlyEndedSessions() {
        LocalDateTime now = LocalDateTime.now();
        // Block so the ShedLock lock is held until the work completes.
        sessionRepo.findByStatusAndEndTimeBetween("ENDED", now.minusDays(1), now)
                .flatMap(session -> itemRepo.findBySessionId(session.getId())
                        .flatMap(this::reconcileItem))
                .count()
                .doOnNext(n -> { if (n > 0) log.info("JOB-21: reconciled {} flash-sale items", n); })
                .doOnError(e -> log.error("JOB-21 reconciliation error: {}", e.getMessage(), e))
                .onErrorReturn(0L)
                .block();
    }

    private Mono<FlashSaleItem> reconcileItem(FlashSaleItem item) {
        String stockKey = "fs:stock:" + item.getId();
        return redisTemplate.opsForValue().get(stockKey)
                .map(Integer::parseInt)
                .flatMap(redisStock -> {
                    int expectedSold = item.getFlashStock() - redisStock;
                    if (item.getSoldQty() != null && item.getSoldQty() != expectedSold) {
                        log.warn("JOB-21: drift on fsItemId={} dbSold={} expectedFromRedis={} — syncing to Redis",
                                item.getId(), item.getSoldQty(), expectedSold);
                        item.setSoldQty(expectedSold);
                        return itemRepo.save(item);
                    }
                    return Mono.just(item);
                })
                .switchIfEmpty(Mono.just(item))
                .flatMap(savedItem -> redisTemplate.delete(stockKey).thenReturn(savedItem));
    }
}
