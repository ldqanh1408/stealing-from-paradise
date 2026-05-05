package com.flashsale.productservice.config;

import com.flashsale.productservice.domain.model.Category;
import com.flashsale.productservice.domain.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.Arrays;
import java.util.List;

/**
 * Category initialization — only runs in dev profile, AND ONLY when dev-data.enabled=false.
 * When dev-data.enabled=true (the default), ProductDevDataLoader handles ALL seeding
 * (categories + products + variants + carts).
 */
@Configuration
@Profile("dev")
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "dev-data.enabled", havingValue = "false", matchIfMissing = true)
public class MongoInitializationConfig {

    @Bean
    public CommandLineRunner initializeCategories(CategoryRepository categoryRepository) {
        return args -> {
            long categoryCount = categoryRepository.count();
            if (categoryCount > 0) {
                log.info("[MongoInit] Categories already initialized. ProductDevDataLoader will use them.");
                return;
            }

            log.info("[MongoInit] Initializing default categories...");

            List<Category> rootCategories = Arrays.asList(
                Category.builder()
                    .name("Điện Thoại & Máy Tính Bảng")
                    .slug("dien-thoai-may-tinh-bang")
                    .parentId(null)
                    .level(0)
                    .build(),
                Category.builder()
                    .name("Thời Trang & Phụ Kiện")
                    .slug("thoi-trang-phu-kien")
                    .parentId(null)
                    .level(0)
                    .build(),
                Category.builder()
                    .name("Nhà Sách & Trò Chơi")
                    .slug("nha-sach-tro-choi")
                    .parentId(null)
                    .level(0)
                    .build()
            );

            List<Category> savedRoots = categoryRepository.saveAll(rootCategories);
            log.info("[MongoInit] Created {} root categories", savedRoots.size());

            String phoneId = savedRoots.get(0).getId();
            List<Category> subCategories = Arrays.asList(
                Category.builder()
                    .name("Điện Thoại Thông Minh")
                    .slug("dien-thoai-thong-minh")
                    .parentId(phoneId)
                    .level(1)
                    .build(),
                Category.builder()
                    .name("Máy Tính Bảng")
                    .slug("may-tinh-bang")
                    .parentId(phoneId)
                    .level(1)
                    .build()
            );

            categoryRepository.saveAll(subCategories);
            log.info("[MongoInit] Category initialization completed successfully");
        };
    }
}

