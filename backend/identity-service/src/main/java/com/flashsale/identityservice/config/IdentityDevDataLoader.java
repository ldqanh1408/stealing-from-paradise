package com.flashsale.identityservice.config;

import com.flashsale.commonlib.config.DevDataProperties;
import com.flashsale.identityservice.domain.repository.AddressRepository;
import com.flashsale.identityservice.domain.repository.RoleRepository;
import com.flashsale.identityservice.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds FE test-dataset users (900001-900003) with roles and addresses for local development.
 * Password for ALL dev accounts is: {@code dev123}
 *
 * <ul>
 *   <li>900001 = fe_buyer (BUYER)</li>
 *   <li>900002 = fe_seller (SELLER)</li>
 *   <li>900003 = fe_admin (ADMIN)</li>
 * </ul>
 *
 * Reset: set dev-data.reset=true in identity-service's application-dev.yml
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "dev-data.enabled", havingValue = "true", matchIfMissing = false)
public class IdentityDevDataLoader implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AddressRepository addressRepository;
    private final PasswordEncoder passwordEncoder;
    private final DevDataProperties devDataProperties;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("[IdentityDevDataLoader] Starting dev data seed for identity-service...");

        if (devDataProperties.isReset()) {
            log.warn("[IdentityDevDataLoader] RESET=true — wiping all identity data...");
            addressRepository.deleteAllInBatch();
            roleRepository.deleteAllInBatch();
            userRepository.deleteAllInBatch();
            log.info("[IdentityDevDataLoader] All identity data wiped.");
        } else if (userRepository.count() > 0) {
            log.info("[IdentityDevDataLoader] Data already exists, skipping seed.");
            return;
        }

        seedFeData();

        log.info("[IdentityDevDataLoader] Dev data seed complete.");
    }

    /**
     * Seeds FE test-dataset users (900001-900003) with roles and addresses.
     * Idempotent via ON CONFLICT DO UPDATE.
     */
    private void seedFeData() {
        log.info("[IdentityDevDataLoader] Seeding FE test-dataset...");

        String hashed = passwordEncoder.encode("dev123");

        // ──────────────────────────────────────────────
        // 1. Users
        // ──────────────────────────────────────────────
        jdbcTemplate.update("""
            INSERT INTO identity.users (id, username, email, phone, password, full_name, status, role, created_at, updated_at)
            VALUES
                (900001, 'fe_buyer',  'fe_buyer@example.test',  '0999000001', ?, 'Nguyễn Văn Mua',  'ACTIVE', 'BUYER',  now() - interval '20 days', now()),
                (900002, 'fe_seller', 'fe_seller@example.test', '0999000002', ?, 'Trần Thị Bán',    'ACTIVE', 'SELLER', now() - interval '19 days', now()),
                (900003, 'fe_admin',  'fe_admin@example.test',  '0999000003', ?, 'Lê Admin',         'ACTIVE', 'ADMIN',  now() - interval '18 days', now())
            ON CONFLICT (id) DO UPDATE SET
                username   = EXCLUDED.username,
                email      = EXCLUDED.email,
                phone      = EXCLUDED.phone,
                password   = EXCLUDED.password,
                full_name  = EXCLUDED.full_name,
                status     = EXCLUDED.status,
                role       = EXCLUDED.role,
                updated_at = now()
            """, hashed, hashed, hashed);

        // ──────────────────────────────────────────────
        // 2. Roles
        // ──────────────────────────────────────────────
        jdbcTemplate.update("""
            INSERT INTO identity.roles (id, user_id, role_name, created_at, updated_at)
            VALUES
                (900001, 900001, 'BUYER',  now() - interval '20 days', now()),
                (900002, 900002, 'SELLER', now() - interval '19 days', now()),
                (900003, 900003, 'ADMIN',  now() - interval '18 days', now())
            ON CONFLICT (id) DO UPDATE SET
                user_id    = EXCLUDED.user_id,
                role_name  = EXCLUDED.role_name,
                updated_at = now()
            """);

        // ──────────────────────────────────────────────
        // 3. Addresses
        // ──────────────────────────────────────────────
        // 900001 (fe_buyer):  2 addresses — primary in HCMC, backup in Hanoi
        // 900002 (fe_seller): 2 addresses — warehouse in HCMC, return center in Hanoi
        // 900003 (fe_admin):  1 default address
        jdbcTemplate.update("""
            INSERT INTO identity.addresses (id, user_id, province_id, district_id, full_address, is_default, created_at, updated_at)
            VALUES
                (900001, 900001, 79, 1, '123 Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh',    true,  now() - interval '20 days', now()),
                (900002, 900001,  1, 1, '456 Trần Hưng Đạo, Hoàn Kiếm, Hà Nội',        false, now() - interval '19 days', now()),
                (900003, 900002, 79, 3, '789 Lê Văn Sỹ, Quận 3, TP. Hồ Chí Minh',      true,  now() - interval '18 days', now()),
                (900004, 900003,  1, 4, '321 Kim Mã, Ba Đình, Hà Nội',                  true,  now() - interval '17 days', now()),
                (900005, 900002,  1, 5, '654 Nguyễn Chí Thanh, Đống Đa, Hà Nội',        false, now() - interval '16 days', now())
            ON CONFLICT (id) DO UPDATE SET
                user_id      = EXCLUDED.user_id,
                province_id  = EXCLUDED.province_id,
                district_id  = EXCLUDED.district_id,
                full_address = EXCLUDED.full_address,
                is_default   = EXCLUDED.is_default,
                updated_at   = now()
            """);

        // ──────────────────────────────────────────────
        // 4. Reset sequences
        // ──────────────────────────────────────────────
        jdbcTemplate.queryForObject(
            "SELECT setval('identity.users_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM identity.users), 900003))",
            Long.class);
        jdbcTemplate.queryForObject(
            "SELECT setval('identity.roles_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM identity.roles), 900003))",
            Long.class);
        jdbcTemplate.queryForObject(
            "SELECT setval('identity.addresses_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM identity.addresses), 900005))",
            Long.class);

        log.info("[IdentityDevDataLoader] FE test-dataset seeded (3 users, 3 roles, 5 addresses).");
    }
}
