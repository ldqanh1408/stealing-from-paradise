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
                log.info("[FlashSaleDevDataLoader] Data already exists, skipping main seed.");

                seedFeData();

                log.info("[FlashSaleDevDataLoader] Dev data seed complete.");
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

        seedFeData();
    }

    private void seedFeData() {
        log.info("[FlashSaleDevDataLoader] Seeding FE test-dataset...");

        Long count = databaseClient.sql("SELECT COUNT(*) FROM fs_sessions WHERE id = 900001")
            .map((row, meta) -> row.get(0, Long.class)).first().block();
        if (count != null && count > 0) {
            log.info("[FlashSaleDevDataLoader] FE data already exists, skipping.");
            return;
        }

        databaseClient.sql("INSERT INTO fs_sessions (id, name, start_time, end_time, registration_deadline, status, deleted_at, created_at, updated_at) VALUES " +
            "(900001, 'FE Live Flash Sale', now() - interval '30 minutes', now() + interval '2 hours', now() - interval '1 hour', 'LIVE', null, now() - interval '1 day', now()), " +
            "(900002, 'FE Upcoming Weekend Sale', now() + interval '1 day', now() + interval '1 day 6 hours', now() + interval '1 day', 'UPCOMING', null, now() - interval '1 day', now()), " +
            "(900003, 'FE Ended Morning Sale', now() - interval '2 days', now() - interval '1 day 20 hours', now() - interval '2 days', 'ENDED', null, now() - interval '3 days', now()) " +
            "ON CONFLICT (id) DO UPDATE SET name=EXCLUDED.name,start_time=EXCLUDED.start_time,end_time=EXCLUDED.end_time,registration_deadline=EXCLUDED.registration_deadline,status=EXCLUDED.status,deleted_at=EXCLUDED.deleted_at,updated_at=now()")
            .then().block();

        databaseClient.sql("INSERT INTO fs_items (id, session_id, seller_id, sku_code, flash_price, flash_stock, limit_per_user, sold_qty, status, version, created_at, updated_at) VALUES " +
            "(900001, 900001, 900002, 'FE-SKU-AIRPODS-COMBO', 3990000, 30, 2, 6, 'LIVE', 0, now() - interval '1 day', now()), " +
            "(900002, 900001, 900002, 'FE-SKU-PHONE-15PRO', 21990000, 10, 1, 2, 'LIVE', 0, now() - interval '1 day', now()), " +
            "(900003, 900002, 900002, 'FE-SKU-LAPTOP-M3', 25990000, 8, 1, 0, 'APPROVED', 0, now() - interval '1 day', now()), " +
            "(900004, 900002, 900002, 'FE-SKU-HUB-8IN1', 590000, 100, 3, 0, 'APPROVED', 0, now() - interval '1 day', now()), " +
            "(900005, 900003, 900002, 'FE-SKU-AIRPODS-COMBO', 3790000, 20, 2, 20, 'SOLD_OUT', 0, now() - interval '3 days', now()) " +
            "ON CONFLICT (id) DO UPDATE SET session_id=EXCLUDED.session_id,seller_id=EXCLUDED.seller_id,sku_code=EXCLUDED.sku_code,flash_price=EXCLUDED.flash_price,flash_stock=EXCLUDED.flash_stock,limit_per_user=EXCLUDED.limit_per_user,sold_qty=EXCLUDED.sold_qty,status=EXCLUDED.status,version=EXCLUDED.version,updated_at=now()")
            .then().block();

        databaseClient.sql("INSERT INTO fs_reminders (id, customer_id, session_id, created_at) VALUES " +
            "(900001, 900001, 900002, now() - interval '12 hours'), " +
            "(900002, 900001, 900001, now() - interval '2 hours'), " +
            "(900003, 900001, 900003, now() - interval '3 days') " +
            "ON CONFLICT (customer_id, session_id) DO NOTHING")
            .then().block();

        log.info("[FlashSaleDevDataLoader] FE test-dataset seeded (3 sessions, 5 items, 3 reminders).");
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
