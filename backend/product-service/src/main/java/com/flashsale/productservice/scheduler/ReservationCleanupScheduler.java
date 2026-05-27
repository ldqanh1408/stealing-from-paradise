package com.flashsale.productservice.scheduler;

import com.flashsale.productservice.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationCleanupScheduler {

    private final InventoryService inventoryService;

    @Scheduled(fixedRateString = "${reservation.cleanup.interval-ms:180000}")
    public void cleanupExpiredReservations() {
        log.info("Starting expired reservation cleanup");
        try {
            inventoryService.cleanupExpiredReservations();
            log.info("Expired reservation cleanup completed successfully");
        } catch (Exception e) {
            log.error("Error during reservation cleanup", e);
        }
    }
}
