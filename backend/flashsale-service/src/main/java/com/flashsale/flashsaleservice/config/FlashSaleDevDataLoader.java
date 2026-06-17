package com.flashsale.flashsaleservice.config;

import com.flashsale.commonlib.config.DevDataProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

/**
 * Seeds flash-sale sessions, items, and reminders for local dev.
 *
 * <p>flashsale-service is R2DBC reactive — we use blocking {@code .block()} since
 * this only runs once at startup. All data references FE product catalog entities
 * (seller=900002, FE-SKU-* variant codes).</p>
 *
 * <p>Three FE sessions:</p>
 * <ul>
 *   <li>900001 — <b>FE Live Flash Sale</b> (LIVE, started 30 min ago, ends in 2h)</li>
 *   <li>900002 — <b>FE Upcoming Weekend Sale</b> (UPCOMING, starts in 1 day, 6h duration)</li>
 *   <li>900003 — <b>FE Ended Morning Sale</b> (ENDED, ended 2 days ago)</li>
 * </ul>
 *
 * <p>All inserts use {@code ON CONFLICT DO UPDATE} for idempotency.</p>
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "dev-data.enabled", havingValue = "true", matchIfMissing = false)
public class FlashSaleDevDataLoader implements CommandLineRunner {

    private final DevDataProperties devDataProperties;
    private final DatabaseClient databaseClient;

    @Override
    public void run(String... args) {
        log.info("[FlashSaleDevDataLoader] Starting dev data seed for flashsale-service...");

        if (devDataProperties.isReset()) {
            log.warn("[FlashSaleDevDataLoader] RESET=true — wiping flashsale data...");
            databaseClient.sql("DELETE FROM fs_reminders").then().block();
            databaseClient.sql("DELETE FROM fs_items").then().block();
            databaseClient.sql("DELETE FROM fs_sessions").then().block();
            log.info("[FlashSaleDevDataLoader] All flashsale data wiped.");
        }

        seedFeData();

        log.info("[FlashSaleDevDataLoader] Dev data seed complete.");
    }

    private void seedFeData() {
        log.info("[FlashSaleDevDataLoader] Seeding FE test-dataset...");

        Long count = databaseClient.sql("SELECT COUNT(*) FROM fs_sessions WHERE id = 900001")
            .map((row, meta) -> row.get(0, Long.class)).first().block();
        if (count != null && count > 0) {
            log.info("[FlashSaleDevDataLoader] FE data already exists, skipping.");
            return;
        }

        databaseClient.sql("""
            INSERT INTO fs_sessions (id, name, start_time, end_time, registration_deadline, status, deleted_at, created_at, updated_at) VALUES
            (900001, 'FE Live Flash Sale', now() - interval '30 minutes', now() + interval '2 hours', now() - interval '1 hour', 'LIVE', null, now() - interval '1 day', now()),
            (900002, 'FE Upcoming Weekend Sale', now() + interval '1 day', now() + interval '1 day 6 hours', now() + interval '1 day', 'UPCOMING', null, now() - interval '1 day', now()),
            (900003, 'FE Ended Morning Sale', now() - interval '2 days', now() - interval '1 day 20 hours', now() - interval '2 days', 'ENDED', null, now() - interval '3 days', now())
            ON CONFLICT (id) DO UPDATE SET
                name=EXCLUDED.name, start_time=EXCLUDED.start_time, end_time=EXCLUDED.end_time,
                registration_deadline=EXCLUDED.registration_deadline, status=EXCLUDED.status,
                deleted_at=EXCLUDED.deleted_at, updated_at=now()
            """).then().block();

        databaseClient.sql("""
            INSERT INTO fs_items (id, session_id, seller_id, sku_code, flash_price, flash_stock, limit_per_user, sold_qty, status, version, created_at, updated_at) VALUES
            (900001, 900001, 900002, 'FE-SKU-AIRPODS-COMBO', 3990000, 30, 2, 6, 'LIVE', 0, now() - interval '1 day', now()),
            (900002, 900001, 900002, 'FE-SKU-PHONE-15PRO', 21990000, 10, 1, 2, 'LIVE', 0, now() - interval '1 day', now()),
            (900003, 900002, 900002, 'FE-SKU-LAPTOP-M3', 25990000, 8, 1, 0, 'APPROVED', 0, now() - interval '1 day', now()),
            (900004, 900002, 900002, 'FE-SKU-HUB-8IN1', 590000, 100, 3, 0, 'APPROVED', 0, now() - interval '1 day', now()),
            (900005, 900003, 900002, 'FE-SKU-AIRPODS-COMBO', 3790000, 20, 2, 20, 'SOLD_OUT', 0, now() - interval '3 days', now())
            ON CONFLICT (id) DO UPDATE SET
                session_id=EXCLUDED.session_id, seller_id=EXCLUDED.seller_id, sku_code=EXCLUDED.sku_code,
                flash_price=EXCLUDED.flash_price, flash_stock=EXCLUDED.flash_stock,
                limit_per_user=EXCLUDED.limit_per_user, sold_qty=EXCLUDED.sold_qty,
                status=EXCLUDED.status, version=EXCLUDED.version, updated_at=now()
            """).then().block();

        databaseClient.sql("""
            INSERT INTO fs_reminders (id, customer_id, session_id, created_at) VALUES
            (900001, 900001, 900002, now() - interval '12 hours'),
            (900002, 900001, 900001, now() - interval '2 hours'),
            (900003, 900001, 900003, now() - interval '3 days')
            ON CONFLICT (customer_id, session_id) DO NOTHING
            """).then().block();

        log.info("[FlashSaleDevDataLoader] FE test-dataset seeded (3 sessions, 5 items, 3 reminders).");
    }
}
