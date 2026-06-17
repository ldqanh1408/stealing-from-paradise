package com.flashsale.identityservice.config;

import com.flashsale.commonlib.config.DevDataProperties;
import com.flashsale.identityservice.domain.model.Address;
import com.flashsale.identityservice.domain.model.Role;
import com.flashsale.identityservice.domain.model.User;
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

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Seeds the identity schema (users, roles, addresses) for local development.
 * Password for ALL dev accounts is: {@code dev123}
 * IDs 1-5  = sellers (can also buy)
 * IDs 6-9  = buyers (customers)
 * ID  10   = admin
 * IDs 11-13 = more buyers (needed because order-service seeds 10 parent orders for users 1-10)
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

    // All 13 users (IDs 1-10 used by product/order/payment seeders, 11-13 for extra buyers)

    private static final String[][] ADDRESSES = {
        {"123 Nguyễn Trãi, Quận 1", "TP. Hồ Chí Minh", "1"},
        {"456 Lê Đại Hành, Quận 11", "TP. Hồ Chí Minh", "1"},
        {"789 Hoàng Quốc Việt, Quận Cầu Giấy", "Hà Nội", "2"},
        {"101 Phạm Hùng, Quận Nam Từ Liêm", "Hà Nội", "2"},
        {"202 Điện Biên Phủ, Quận Thanh Khê", "Đà Nẵng", "3"},
        {"303 Ba Hué, Quận Hải Châu", "Đà Nẵng", "3"},
        {"404 Lê Lợi, Quận Hồng Bàng", "Hải Phòng", "4"},
        {"505 Nguyễn Văn Linh, Quận Ninh Kiều", "Cần Thơ", "5"},
        {"606 Đại lộ Bình Dương, Thủ Dầu Một", "Bình Dương", "6"},
        {"707 Ngô Mây, TP. Biên Hòa", "Đồng Nai", "7"},
        {"808 Lê Hồng Phong, Quận 5", "TP. Hồ Chí Minh", "1"},
        {"909 Trần Hưng Đạo, Quận 1", "TP. Hồ Chí Minh", "1"},
        {"1010 Âu Cơ, Quận Tân Bình", "TP. Hồ Chí Minh", "1"},
    };

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
            log.info("[IdentityDevDataLoader] Data already exists, skipping main seed.");

            seedFeData();

            log.info("[IdentityDevDataLoader] Dev data seed complete.");
            return;
        }

        seedUsersAndRoles();
        seedAddresses();

        seedFeData();

        log.info("[IdentityDevDataLoader] Dev data seed complete.");
    }

    private void seedUsersAndRoles() {
        String plainPw = "dev123";
        String hashedPw = passwordEncoder.encode(plainPw);

        List<User> users = List.of(
            // ---- SELLERS (IDs 1-5, can also buy) ----
            mkUser(1L, "techworld", "techworld@example.com", "0901111222",
                   "Nguyễn Văn Tèo", "ACTIVE"),
            mkUser(2L, "fashionhub", "fashionhub@example.com", "0902333444",
                   "Trần Thị Mỹ Linh", "ACTIVE"),
            mkUser(3L, "gadgetpro", "gadgetpro@example.com", "0903555666",
                   "Lê Hoàng Nam", "ACTIVE"),
            mkUser(4L, "homeliving", "homeliving@example.com", "0904777888",
                   "Phạm Minh Đức", "ACTIVE"),
            mkUser(5L, "sportoutdoor", "sportoutdoor@example.com", "0905999000",
                   "Võ Thị Hà My", "ACTIVE"),

            // ---- BUYERS (IDs 6-9 + 11-13, can also sell) ----
            mkUser(6L, "minhhoa", "minhhoa@example.com", "0906111222",
                   "Hoàng Minh Hòa", "ACTIVE"),
            mkUser(7L, "phuongthao", "phuongthao@example.com", "0907222333",
                   "Đặng Phương Thảo", "ACTIVE"),
            mkUser(8L, "ductran", "ductran@example.com", "0908333444",
                   "Trần Đức Anh", "ACTIVE"),
            mkUser(9L, "linhnguyen", "linhnguyen@example.com", "0909444555",
                   "Nguyễn Thị Linh", "ACTIVE"),

            // ---- ADMIN (ID 10) ----
            mkUser(10L, "admin", "admin@flashsale.dev", "0901000000",
                   "Quản Trị Viên", "ACTIVE"),

            // ---- EXTRA BUYERS (IDs 11-13, align with order-service parent orders) ----
            mkUser(11L, "huyenvu", "huyenvu@example.com", "0901555666",
                   "Vũ Thị Huyền", "ACTIVE"),
            mkUser(12L, "tuananh", "tuananh@example.com", "0902666777",
                   "Ngô Tuấn Anh", "ACTIVE"),
            mkUser(13L, "thanhhuyen", "thanhhuyen@example.com", "0903777888",
                   "Huyền Thanh", "ACTIVE")
        );

        for (User u : users) {
            u.setPassword(hashedPw);
            u.setCreatedAt(LocalDateTime.now().minusDays(new Random().nextInt(60) + 1));
            u.setUpdatedAt(u.getCreatedAt());

            jdbcTemplate.update(
                "INSERT INTO identity.users (id, username, email, phone, password, full_name,"
                    + " status, created_at, updated_at)"
                    + " VALUES (?,?,?,?,?,?,?,?,?)"
                    + " ON CONFLICT (id) DO NOTHING",
                u.getId(), u.getUsername(), u.getEmail(), u.getPhone(), u.getPassword(),
                u.getFullName(), u.getStatus(),
                Timestamp.valueOf(u.getCreatedAt()), Timestamp.valueOf(u.getUpdatedAt()));
        }

        // Reset sequence to max(id) so future auto-generated IDs don't conflict
        jdbcTemplate.queryForObject(
            "SELECT setval('identity.users_id_seq', (SELECT COALESCE(MAX(id), 1) FROM identity.users))",
            Long.class);

        // Assign roles
        String[] roles = {
            "SELLER", "SELLER", "SELLER", "SELLER", "SELLER",  // IDs 1-5
            "BUYER",  "BUYER",  "BUYER",  "BUYER",  "ADMIN",  // IDs 6-10
            "BUYER",  "BUYER",  "BUYER"                           // IDs 11-13
        };
        for (int i = 0; i < users.size(); i++) {
            Role role = Role.builder()
                    .userId(users.get(i).getId())
                    .roleName(roles[i])
                    .build();
            roleRepository.save(role);
        }

        log.info("[IdentityDevDataLoader] Seeded {} users with roles", users.size());
    }

    private User mkUser(Long id, String username, String email, String phone,
                         String fullName, String status) {
        return User.builder()
                .id(id)
                .username(username)
                .email(email)
                .phone(phone)
                .fullName(fullName)
                .status(status)
                .build();
    }

    private void seedAddresses() {
        List<Address> addresses = new ArrayList<>();
        long[] allUserIds = {1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L};

        for (int i = 0; i < allUserIds.length; i++) {
            long userId = allUserIds[i];
            String[] addr = ADDRESSES[i % ADDRESSES.length];

            addresses.add(Address.builder()
                    .userId(userId)
                    .provinceId(Integer.parseInt(addr[2]))
                    .districtId(1)
                    .fullAddress(addr[0] + ", " + addr[1])
                    .isDefault(true)
                    .createdAt(LocalDateTime.now().minusMinutes(new Random().nextInt(10000)))
                    .updatedAt(LocalDateTime.now().minusMinutes(new Random().nextInt(1000)))
                    .build());

            if (i < 5) {
                addresses.add(Address.builder()
                        .userId(userId)
                        .provinceId(Integer.parseInt(addr[2]) + 1)
                        .districtId(2)
                        .fullAddress("Số " + (100 + new Random().nextInt(900)) + " Đường Phụ, " + addr[1])
                        .isDefault(false)
                        .createdAt(LocalDateTime.now().minusMinutes(new Random().nextInt(10000)))
                        .updatedAt(LocalDateTime.now().minusMinutes(new Random().nextInt(1000)))
                        .build());
            }
        }

        addressRepository.saveAll(addresses);
        log.info("[IdentityDevDataLoader] Seeded {} addresses for {} users", addresses.size(), allUserIds.length);
    }

    /**
     * Seeds FE test-dataset users (900001-900003) with roles and addresses.
     * Idempotent via ON CONFLICT DO UPDATE.
     */
    private void seedFeData() {
        log.info("[IdentityDevDataLoader] Seeding FE test-dataset...");

        String hashed = passwordEncoder.encode("dev123");

        // Users
        jdbcTemplate.update("INSERT INTO identity.users (id, username, email, phone, password, full_name, status, role, created_at, updated_at) VALUES " +
            "(900001, 'fe_buyer', 'fe_buyer@example.test', '0999000001', ?, 'Frontend Buyer', 'ACTIVE', 'BUYER', now() - interval '20 days', now()), " +
            "(900002, 'fe_seller', 'fe_seller@example.test', '0999000002', ?, 'Frontend Seller', 'ACTIVE', 'SELLER', now() - interval '19 days', now()), " +
            "(900003, 'fe_admin', 'fe_admin@example.test', '0999000003', ?, 'Frontend Admin', 'ACTIVE', 'ADMIN', now() - interval '18 days', now()) " +
            "ON CONFLICT (id) DO UPDATE SET username=EXCLUDED.username,email=EXCLUDED.email,phone=EXCLUDED.phone,password=EXCLUDED.password,full_name=EXCLUDED.full_name,status=EXCLUDED.status,role=EXCLUDED.role,updated_at=now()",
            hashed, hashed, hashed);

        // Roles
        jdbcTemplate.update("INSERT INTO identity.roles (id, user_id, role_name, created_at, updated_at) VALUES " +
            "(900001, 900001, 'BUYER', now() - interval '20 days', now()), " +
            "(900002, 900002, 'SELLER', now() - interval '19 days', now()), " +
            "(900003, 900003, 'ADMIN', now() - interval '18 days', now()) " +
            "ON CONFLICT (id) DO UPDATE SET user_id=EXCLUDED.user_id,role_name=EXCLUDED.role_name,updated_at=now()");

        // Addresses
        jdbcTemplate.update("INSERT INTO identity.addresses (id, user_id, province_id, district_id, full_address, is_default, created_at, updated_at) VALUES " +
            "(900001, 900001, 79, 760, '123 Frontend Test Street, District 1, Ho Chi Minh City', true, now() - interval '20 days', now()), " +
            "(900002, 900001, 1, 1, '456 Backup Address, Ba Dinh, Ha Noi', false, now() - interval '19 days', now()), " +
            "(900003, 900002, 79, 761, 'FE Seller Warehouse, District 3, Ho Chi Minh City', true, now() - interval '18 days', now()), " +
            "(900004, 900001, 48, 490, '789 Da Nang Office, Hai Chau, Da Nang', false, now() - interval '17 days', now()), " +
            "(900005, 900002, 1, 4, 'FE Seller Return Center, Dong Da, Ha Noi', false, now() - interval '16 days', now()) " +
            "ON CONFLICT (id) DO UPDATE SET user_id=EXCLUDED.user_id,province_id=EXCLUDED.province_id,district_id=EXCLUDED.district_id,full_address=EXCLUDED.full_address,is_default=EXCLUDED.is_default,updated_at=now()");

        // Reset sequences
        jdbcTemplate.queryForObject("SELECT setval('identity.users_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM identity.users), 900003))", Long.class);
        jdbcTemplate.queryForObject("SELECT setval('identity.roles_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM identity.roles), 900003))", Long.class);
        jdbcTemplate.queryForObject("SELECT setval('identity.addresses_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM identity.addresses), 900005))", Long.class);

        log.info("[IdentityDevDataLoader] FE test-dataset seeded (3 users, 3 roles, 5 addresses).");
    }
}
