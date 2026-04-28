package com.flashsale.identitydomain.config;

import com.flashsale.commonlib.config.DevDataProperties;
import com.flashsale.identitydomain.domain.model.Address;
import com.flashsale.identitydomain.domain.repository.AddressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "dev-data.enabled", havingValue = "true", matchIfMissing = false)
public class IdentityDevDataLoader implements CommandLineRunner {

    private final AddressRepository addressRepository;
    private final DevDataProperties devDataProperties;

    private static final long[] USER_IDS = {1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L};

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
    };

    @Override
    public void run(String... args) {
        log.info("[IdentityDevDataLoader] Starting dev data seed for identity-service...");

        if (devDataProperties.isReset()) {
            log.warn("[IdentityDevDataLoader] RESET=true — wiping all address data...");
            addressRepository.deleteAll();
            log.info("[IdentityDevDataLoader] All address data wiped.");
        } else if (addressRepository.count() > 0) {
            log.info("[IdentityDevDataLoader] Data already exists, skipping. Set dev-data.reset=true to reload.");
            return;
        }

        List<Address> addresses = new ArrayList<>();
        for (int i = 0; i < USER_IDS.length; i++) {
            long userId = USER_IDS[i];
            String[] addr = ADDRESSES[i % ADDRESSES.length];

            // Primary address (default)
            addresses.add(Address.builder()
                    .userId(userId)
                    .provinceId(Integer.parseInt(addr[2]))
                    .districtId(1)
                    .fullAddress(addr[0] + ", " + addr[1])
                    .isDefault(true)
                    .createdAt(LocalDateTime.now().minusMinutes(new Random().nextInt(10000)))
                    .updatedAt(LocalDateTime.now().minusMinutes(new Random().nextInt(1000)))
                    .build());

            // Secondary address (non-default) for users 1-5
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
        log.info("[IdentityDevDataLoader] Seeded {} addresses for {} users", addresses.size(), USER_IDS.length);
    }
}
