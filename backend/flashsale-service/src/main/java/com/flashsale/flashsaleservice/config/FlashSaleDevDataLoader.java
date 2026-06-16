package com.flashsale.flashsaleservice.config;

import com.flashsale.commonlib.config.DevDataProperties;
import com.flashsale.flashsaleservice.domain.model.FlashSaleItem;
import com.flashsale.flashsaleservice.domain.model.FlashSaleSession;
import com.flashsale.flashsaleservice.domain.repository.FlashSaleItemRepository;
import com.flashsale.flashsaleservice.domain.repository.FlashSaleSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Seeds flash-sale sessions and items for local dev.
 *
 * <p>flashsale-service is R2DBC reactive — we use blocking {@code .block()} since
 * this only runs once at startup. Three sessions are created:</p>
 * <ul>
 *   <li>Session 1 — <b>ENDED</b> (yesterday). Some items sold.</li>
 *   <li>Session 2 — <b>UPCOMING</b> (active now, ends in 2h) — note: scheduler hasn't fired
 *       so DB still says UPCOMING even though start time is in the past. Worker/fallback
 *       will flip it shortly after boot.</li>
 *   <li>Session 3 — <b>UPCOMING</b> (starts tomorrow, registration window still open).</li>
 * </ul>
 *
 * <p>{@code sku_code} values reference product-service variant_code (e.g. SKU-IPHONE-BLK-128).</p>
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "dev-data.enabled", havingValue = "true", matchIfMissing = false)
public class FlashSaleDevDataLoader implements CommandLineRunner {

    private final FlashSaleSessionRepository sessionRepository;
    private final FlashSaleItemRepository itemRepository;
    private final DevDataProperties devDataProperties;
    private final DatabaseClient databaseClient;

    @Override
    public void run(String... args) {
        log.info("[FlashSaleDevDataLoader] Starting dev data seed for flashsale-service...");

        if (devDataProperties.isReset()) {
            log.warn("[FlashSaleDevDataLoader] RESET=true — wiping flashsale data...");
            databaseClient.sql("DELETE FROM fs_items").then().block();
            databaseClient.sql("DELETE FROM fs_sessions").then().block();
            log.info("[FlashSaleDevDataLoader] All flashsale data wiped.");
        } else {
            Long count = sessionRepository.count().block();
            if (count != null && count > 0) {
                log.info("[FlashSaleDevDataLoader] Data already exists, skipping. Set dev-data.reset=true to reload.");
                return;
            }
        }

        LocalDateTime now = LocalDateTime.now();
        int regWindowMin = 30;

        // --- Session 1: ENDED (yesterday) ---
        FlashSaleSession s1 = sessionRepository.save(FlashSaleSession.builder()
                .name("Flash Sale Đầu Tuần — Kết Thúc")
                .startTime(now.minusDays(1).minusHours(4))
                .endTime(now.minusDays(1))
                .registrationDeadline(now.minusDays(1).minusHours(4).minusMinutes(regWindowMin))
                .status("ENDED")
                .build()).block();

        seedItem(s1.getId(), "SKU-IPHONE-BLK-128", "20990000", 5,  3,  "SOLD_OUT");
        seedItem(s1.getId(), "SKU-AIRPOD-PRO2",    "4990000",  20, 12, "ENDED");

        // --- Session 2: UPCOMING (active now, ends in 2h) ---
        // DB still says UPCOMING — worker/fallback will flip to ACTIVE within seconds of boot.
        FlashSaleSession s2 = sessionRepository.save(FlashSaleSession.builder()
                .name("Flash Sale Trưa Vàng — Sắp Diễn Ra")
                .startTime(now.minusHours(1))
                .endTime(now.plusHours(2))
                .registrationDeadline(now.minusHours(1).plusMinutes(regWindowMin))
                .status("UPCOMING")
                .build()).block();

        seedItem(s2.getId(), "SKU-MACBOOK-AIR-M3", "25990000", 10, 0, "APPROVED");
        seedItem(s2.getId(), "SKU-JBL-FLIP6-BLK",  "2290000",  30, 0, "APPROVED");
        seedItem(s2.getId(), "SKU-AIRFRY-55",      "1690000",  25, 0, "PENDING");

        // --- Session 3: UPCOMING (starts tomorrow, registration window still open) ---
        FlashSaleSession s3 = sessionRepository.save(FlashSaleSession.builder()
                .name("Flash Sale Cuối Tuần — Sắp Diễn Ra")
                .startTime(now.plusDays(1))
                .endTime(now.plusDays(1).plusHours(6))
                .registrationDeadline(now.plusDays(1).minusMinutes(regWindowMin))
                .status("UPCOMING")
                .build()).block();

        seedItem(s3.getId(), "SKU-NIKE-PEG40-42", "2790000", 15, 0, "APPROVED");
        seedItem(s3.getId(), "SKU-ANKER-20000",   "690000",  50, 0, "PENDING");

        log.info("[FlashSaleDevDataLoader] Seeded 3 sessions (1 ENDED, 2 UPCOMING) with registration_deadline.");
    }

    private void seedItem(Long sessionId, String sku, String price, int stock, int sold, String status) {
        // NOTE: do NOT set .version() — Spring Data R2DBC uses @Version != null as the
        // "entity already exists, UPDATE it" signal. Leaving version=null forces an INSERT.
        itemRepository.save(FlashSaleItem.builder()
                .sessionId(sessionId)
                .skuCode(sku)
                .flashPrice(new BigDecimal(price))
                .flashStock(stock)
                .soldQty(sold)
                .limitPerUser(2)
                .status(status)
                .build()).block();
    }
}
