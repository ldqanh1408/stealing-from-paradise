package com.flashsale.identitydomain.config;

import com.flashsale.commonlib.config.DevDataProperties;
import com.flashsale.identitydomain.domain.model.Address;
import com.flashsale.identitydomain.domain.model.LoyaltyAccount;
import com.flashsale.identitydomain.domain.model.PointTransaction;
import com.flashsale.identitydomain.domain.model.Role;
import com.flashsale.identitydomain.domain.model.User;
import com.flashsale.identitydomain.domain.repository.AddressRepository;
import com.flashsale.identitydomain.domain.repository.LoyaltyAccountRepository;
import com.flashsale.identitydomain.domain.repository.PointTransactionRepository;
import com.flashsale.identitydomain.domain.repository.RoleRepository;
import com.flashsale.identitydomain.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Seeds the identity schema (users, roles, loyalty, addresses) for local development.
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
    private final LoyaltyAccountRepository loyaltyAccountRepository;
    private final PointTransactionRepository pointTransactionRepository;
    private final PasswordEncoder passwordEncoder;
    private final DevDataProperties devDataProperties;

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
            pointTransactionRepository.deleteAll();
            loyaltyAccountRepository.deleteAll();
            addressRepository.deleteAll();
            roleRepository.deleteAll();
            userRepository.deleteAll();
            log.info("[IdentityDevDataLoader] All identity data wiped.");
        } else if (userRepository.count() > 0) {
            log.info("[IdentityDevDataLoader] Data already exists, skipping. Set dev-data.reset=true to reload.");
            return;
        }

        seedUsersAndRoles();
        seedLoyaltyAccounts();
        seedPointTransactions();
        seedAddresses();

        log.info("[IdentityDevDataLoader] Dev data seed complete.");
    }

    private void seedUsersAndRoles() {
        String plainPw = "dev123";
        String hashedPw = passwordEncoder.encode(plainPw);

        List<User> users = List.of(
            // ---- SELLERS (IDs 1-5, can also buy) ----
            mkUser(1L, "techworld", "techworld@example.com", "0901111222",
                   "Nguyễn Văn Tèo", "https://i.pravatar.cc/150?img=1",
                   "ACTIVE", 85),
            mkUser(2L, "fashionhub", "fashionhub@example.com", "0902333444",
                   "Trần Thị Mỹ Linh", "https://i.pravatar.cc/150?img=5",
                   "ACTIVE", 90),
            mkUser(3L, "gadgetpro", "gadgetpro@example.com", "0903555666",
                   "Lê Hoàng Nam", "https://i.pravatar.cc/150?img=3",
                   "ACTIVE", 75),
            mkUser(4L, "homeliving", "homeliving@example.com", "0904777888",
                   "Phạm Minh Đức", "https://i.pravatar.cc/150?img=8",
                   "ACTIVE", 92),
            mkUser(5L, "sportoutdoor", "sportoutdoor@example.com", "0905999000",
                   "Võ Thị Hà My", "https://i.pravatar.cc/150?img=9",
                   "ACTIVE", 88),

            // ---- BUYERS (IDs 6-9 + 11-13, can also sell) ----
            mkUser(6L, "minhhoa", "minhhoa@example.com", "0906111222",
                   "Hoàng Minh Hòa", "https://i.pravatar.cc/150?img=11",
                   "ACTIVE", 80),
            mkUser(7L, "phuongthao", "phuongthao@example.com", "0907222333",
                   "Đặng Phương Thảo", "https://i.pravatar.cc/150?img=16",
                   "ACTIVE", 95),
            mkUser(8L, "ductran", "ductran@example.com", "0908333444",
                   "Trần Đức Anh", "https://i.pravatar.cc/150?img=12",
                   "ACTIVE", 70),
            mkUser(9L, "linhnguyen", "linhnguyen@example.com", "0909444555",
                   "Nguyễn Thị Linh", "https://i.pravatar.cc/150?img=20",
                   "ACTIVE", 60),

            // ---- ADMIN (ID 10) ----
            mkUser(10L, "admin", "admin@flashsale.dev", "0901000000",
                   "Quản Trị Viên", "https://i.pravatar.cc/150?img=33",
                   "ACTIVE", 100),

            // ---- EXTRA BUYERS (IDs 11-13, align with order-service parent orders) ----
            mkUser(11L, "huyenvu", "huyenvu@example.com", "0901555666",
                   "Vũ Thị Huyền", "https://i.pravatar.cc/150?img=25",
                   "ACTIVE", 82),
            mkUser(12L, "tuananh", "tuananh@example.com", "0902666777",
                   "Ngô Tuấn Anh", "https://i.pravatar.cc/150?img=15",
                   "ACTIVE", 78),
            mkUser(13L, "thanhhuyen", "thanhhuyen@example.com", "0903777888",
                   "Huyền Thanh", "https://i.pravatar.cc/150?img=27",
                   "ACTIVE", 85)
        );

        for (User u : users) {
            u.setPassword(hashedPw);
            u.setCreatedAt(LocalDateTime.now().minusDays(new Random().nextInt(60) + 1));
            u.setUpdatedAt(u.getCreatedAt());
            userRepository.save(u);
        }

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
                         String fullName, String avatarUrl, String status, int trustScore) {
        return User.builder()
                .id(id)
                .username(username)
                .email(email)
                .phone(phone)
                .fullName(fullName)
                .avatarUrl(avatarUrl)
                .status(status)
                .trustScore(trustScore)
                .productPostingSuspended(false)
                .appealCount(0)
                .reward10OrdersAccumulated(0)
                .build();
    }

    private void seedLoyaltyAccounts() {
        // Earning rate: 5% of order amount / 200 = points
        // Points per VND order: 0.00025
        // VND 250,000 → 62 pts | VND 1M → 250 pts | VND 10M → 2500 pts
        int[][] loyaltyData = {
            // { userId, totalEarned, available, used, expired }
            {1,  25000, 18000, 7000,  0},   // Seller, many orders
            {2,  18000, 12000, 6000,  0},   // Seller
            {3,  12000,  8000, 4000,  0},   // Seller
            {4,  15000, 10000, 5000,  0},   // Seller
            {5,  10000,  7000, 3000,  0},   // Seller
            {6,   5000,  3500, 1500,  0},   // Buyer, moderate
            {7,  20000, 15000, 5000,  0},   // Buyer, high value
            {8,   3000,  1000, 2000,  0},   // Buyer, low spend
            {9,   1000,   500,  500,  0},   // Buyer, new
            {10,     0,     0,    0,  0},   // Admin, no purchases
            {11,  8000,  5000, 3000,  0},   // Extra buyer
            {12,  4000,  2500, 1500,  0},   // Extra buyer
            {13,  6000,  4000, 2000,  0},   // Extra buyer
        };

        for (int[] d : loyaltyData) {
            LoyaltyAccount acc = LoyaltyAccount.builder()
                    .userId((long) d[0])
                    .totalEarnedPoints(d[1])
                    .availablePoints(d[2])
                    .usedPoints(d[3])
                    .expiredPoints(d[4])
                    .build();
            loyaltyAccountRepository.save(acc);
        }
        log.info("[IdentityDevDataLoader] Seeded {} loyalty accounts", loyaltyData.length);
    }

    private void seedPointTransactions() {
        Random rnd = new Random(42);
        List<PointTransaction> txns = new ArrayList<>();

        // Order IDs aligned with OrderDevDataLoader parent orders (1-10)
        // earning_rate = 0.05, conversion_rate = 200
        // points = floor(amount * 0.05 / 200) = floor(amount * 0.00025)
        Object[][] orders = {
            // { orderId, userId, orderCode,         amount(VND),   daysAgo }
            {1L,   1L,  "PO-20260422-001", new BigDecimal("250000.00"),  7},
            {2L,   2L,  "PO-20260424-002", new BigDecimal("1590000.00"), 5},
            {3L,   3L,  "PO-20260426-003", new BigDecimal("899000.00"),  3},
            {4L,   4L,  "PO-20260419-004", new BigDecimal("3450000.00"), 10},
            {5L,   5L,  "PO-20260428-005", new BigDecimal("459000.00"),  1},
            {6L,   6L,  "PO-20260425-006", new BigDecimal("6800000.00"), 4},
            {7L,   7L,  "PO-20260427-007", new BigDecimal("1200000.00"), 2},
            {8L,   8L,  "PO-20260421-008", new BigDecimal("4200000.00"), 8},
            {9L,   9L,  "PO-20260428-009", new BigDecimal("8500000.00"), 1},
            {10L, 11L,  "PO-20260423-010", new BigDecimal("550000.00"),  6},
            {11L, 12L,  "PO-20260420-011", new BigDecimal("320000.00"),  9},
            {12L, 13L,  "PO-20260418-012", new BigDecimal("1200000.00"), 11},
        };

        for (Object[] o : orders) {
            long orderId = (Long) o[0];
            long userId  = (Long) o[1];
            String code  = (String) o[2];
            BigDecimal amount = (BigDecimal) o[3];
            int daysAgo = (Integer) o[4];

            int earned = amount.multiply(new BigDecimal("0.00025")).intValue();
            int used   = rnd.nextInt(earned / 3); // some partial redemption
            int remaining = earned - used;

            LocalDateTime createdAt = LocalDateTime.now().minusDays(daysAgo);
            LocalDateTime expiresAt  = createdAt.plusDays(365);

            // Earning transaction
            txns.add(PointTransaction.builder()
                    .userId(userId)
                    .orderId(orderId)
                    .orderCode(code)
                    .delta(earned)
                    .remainingDelta(remaining)
                    .type("EARN")
                    .status("COMPLETED")
                    .balanceAfter(earned)
                    .note("Thưởng từ đơn hàng " + code)
                    .expiresAt(expiresAt)
                    .createdAt(createdAt)
                    .build());

            // Redemption transaction (if used > 0)
            if (used > 0) {
                txns.add(PointTransaction.builder()
                        .userId(userId)
                        .orderId(orderId + 100) // fake future order for redemption
                        .orderCode("PO-REDEEM-" + orderId)
                        .delta(-used)
                        .remainingDelta(0)
                        .type("REDEEM")
                        .status("COMPLETED")
                        .balanceAfter(remaining)
                        .note("Đổi điểm cho đơn hàng tiếp theo")
                        .createdAt(createdAt.plusHours(rnd.nextInt(72) + 1))
                        .build());
            }
        }

        pointTransactionRepository.saveAll(txns);
        log.info("[IdentityDevDataLoader] Seeded {} point transactions", txns.size());
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
}
