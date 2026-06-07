package com.flashsale.productservice.config;

import com.flashsale.commonlib.config.DevDataProperties;
import com.flashsale.productservice.entity.*;
import com.flashsale.productservice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Seeds the product catalog (categories, products, variants, images) for local dev.
 *
 * <p>Crucially this seeds the {@code variant_code} values that the order-service
 * seeder already references via SKU codes (SKU-IPHONE-BLK-128, etc.). Without this,
 * cart / checkout flows that look up products by SKU will fail in dev.</p>
 *
 * <p>Seller IDs 1-5 match the identity-service seeder:
 *  1=techworld, 2=fashionhub, 3=gadgetpro, 4=homeliving, 5=sportoutdoor.</p>
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "dev-data.enabled", havingValue = "true", matchIfMissing = false)
public class ProductDevDataLoader implements CommandLineRunner {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductImageRepository productImageRepository;
    private final DevDataProperties devDataProperties;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("[ProductDevDataLoader] Starting dev data seed for product-service...");

        if (devDataProperties.isReset()) {
            log.warn("[ProductDevDataLoader] RESET=true — wiping all product data...");
            productImageRepository.deleteAllInBatch();
            productVariantRepository.deleteAllInBatch();
            productRepository.deleteAllInBatch();
            categoryRepository.deleteAllInBatch();
            log.info("[ProductDevDataLoader] All product data wiped.");
        } else if (productRepository.count() > 0) {
            log.info("[ProductDevDataLoader] Data already exists, skipping. Set dev-data.reset=true to reload.");
            return;
        }

        // --- 1. Categories ----------------------------------------------------
        Map<String, UUID> catIds = new HashMap<>();
        catIds.put("electronics", seedCategory(null, "Electronics", "electronics", 1));
        catIds.put("phones",      seedCategory(catIds.get("electronics"), "Phones & Tablets", "phones-tablets", 1));
        catIds.put("laptops",     seedCategory(catIds.get("electronics"), "Laptops", "laptops", 2));
        catIds.put("audio",       seedCategory(catIds.get("electronics"), "Audio", "audio", 3));
        catIds.put("accessories", seedCategory(catIds.get("electronics"), "Accessories", "accessories", 4));
        catIds.put("wearables",   seedCategory(catIds.get("electronics"), "Wearables", "wearables", 5));
        catIds.put("home",        seedCategory(null, "Home & Living", "home-living", 2));
        catIds.put("kitchen",     seedCategory(catIds.get("home"), "Kitchen Appliances", "kitchen", 1));
        catIds.put("sports",      seedCategory(null, "Sports & Outdoor", "sports-outdoor", 3));
        log.info("[ProductDevDataLoader] Seeded {} categories", catIds.size());

        // --- 2. Products + variants (SKU codes MUST match order-service seeder) ---
        // {sellerId, categoryKey, name, slug, description, sku, price, originalPrice, stock}
        Object[][] catalog = {
            // ---- TechWorld (seller 1) ----
            {1L, "phones",      "iPhone 15 Black 128GB",      "iphone-15-black-128",  "Apple iPhone 15 — chip A16 Bionic, USB-C, camera 48MP.",
             "SKU-IPHONE-BLK-128", "22990000", "24990000", 50},
            {1L, "audio",       "AirPods Pro 2 (USB-C)",      "airpods-pro-2",         "Tai nghe chống ồn chủ động ANC, sạc MagSafe.",
             "SKU-AIRPOD-PRO2",     "5990000",  "6490000",  100},
            {1L, "accessories", "MagSafe Charger 1m",          "magsafe-charger",       "Sạc không dây 15W chính hãng Apple.",
             "SKU-MAGSAFE",         "990000",   "1290000",  200},
            {1L, "wearables",   "Apple Watch SE GPS 40mm",     "apple-watch-se",        "Đồng hồ thông minh theo dõi sức khoẻ, GPS.",
             "SKU-WATCH-SE",        "6490000",  "7290000",  80},
            {1L, "phones",      "iPad 10th Gen 64GB Wi-Fi",    "ipad-10",               "iPad gen 10, chip A14, màn 10.9 inch Liquid Retina.",
             "SKU-IPAD-10",         "9990000",  "11990000", 60},
            {1L, "laptops",     "MacBook Air M3 13\" 256GB",   "macbook-air-m3",        "Chip Apple M3, RAM 8GB, SSD 256GB, màn Retina.",
             "SKU-MACBOOK-AIR-M3",  "27990000", "31990000", 30},
            {1L, "audio",       "Samsung Galaxy Buds2",        "samsung-galaxy-buds2",  "Tai nghe true wireless ANC giá tốt.",
             "SKU-SAMSUNG-BUDS2",   "2290000",  "2990000",  120},
            {1L, "audio",       "Loa Bluetooth JBL Flip 6 Black", "jbl-flip-6-black",   "Loa di động chống nước IP67, pin 12h.",
             "SKU-JBL-FLIP6-BLK",   "2790000",  "3290000",  90},

            // ---- GadgetPro (seller 3) ----
            {3L, "accessories", "Logitech MX Master 3S",       "mx-master-3s",          "Chuột wireless ergo, scroll điện từ MagSpeed.",
             "SKU-MOUSE-MX3",       "2490000",  "2890000",  150},
            {3L, "accessories", "Balo laptop Tomtoc 15.6\"",   "tomtoc-backpack-156",   "Balo chống sốc, ngăn laptop chuyên dụng.",
             "SKU-BACKPACK-TOM",    "1290000",  "1590000",  80},
            {3L, "accessories", "Bàn phím cơ Akko 3098B",      "akko-3098b",            "Bàn phím cơ wireless 3 mode, switch Akko V3.",
             "SKU-AKKO-3098B-R",    "2190000",  "2590000",  60},

            // ---- HomeLiving (seller 4) ----
            {4L, "accessories", "Cable USB-C to USB-C 1m PD",  "cable-usb-c-1m",        "Cáp sạc nhanh Type-C 60W, dài 1m.",
             "SKU-CABLE-TYPE-C",    "150000",   "250000",   500},
            {4L, "accessories", "Hub USB-C 7-in-1",            "hub-usb-c-7in1",        "Hub đa năng HDMI 4K + USB 3.0 + SD/TF + Type-C PD.",
             "SKU-HUB-7IN1",        "459000",   "699000",   200},
            {4L, "kitchen",     "Nồi chiên không dầu 5.5L",    "airfryer-55l",          "Nồi chiên không dầu Lock&Lock dung tích 5.5L, công suất 1500W.",
             "SKU-AIRFRY-55",       "1990000",  "2790000",  70},
            {4L, "kitchen",     "Bình đun siêu tốc Lock&Lock 1.7L", "kettle-locknlock", "Bình đun siêu tốc inox 304, dung tích 1.7L.",
             "SKU-KETTLE-LNL",      "550000",   "790000",   150},

            // ---- SportOutdoor (seller 5) ----
            {5L, "sports",      "Giày Nike Air Zoom Pegasus 40 size 42", "nike-pegasus-40-42",
             "Giày chạy bộ Nike Pegasus 40, đệm React, size 42.",
             "SKU-NIKE-PEG40-42",   "3290000",  "3990000",  40},
            {5L, "sports",      "Thảm yoga PU 6mm",           "yoga-mat-6mm",          "Thảm yoga 2 lớp PU + cao su, dày 6mm chống trượt.",
             "SKU-YOGAMAT-6MM",     "490000",   "690000",   100},
            {5L, "accessories", "Pin dự phòng Anker 20000mAh", "anker-powercore-20k",   "Pin dự phòng PowerCore 20000mAh, 2 cổng USB-A + USB-C PD 18W.",
             "SKU-ANKER-20000",     "890000",   "1290000",  120},
        };

        int activeCount = 0;
        for (Object[] row : catalog) {
            Long sellerId = (Long) row[0];
            UUID categoryId = catIds.get((String) row[1]);
            UUID productId = seedProduct(sellerId, categoryId,
                    (String) row[2], (String) row[3], (String) row[4],
                    ProductStatus.ACTIVE);
            seedVariant(productId, (String) row[5], (String) row[2],
                    new BigDecimal((String) row[6]),
                    new BigDecimal((String) row[7]),
                    (int) row[8]);
            seedImage(productId, "https://picsum.photos/seed/" + row[5] + "/600/600");
            activeCount++;
        }

        // --- 3. A few products in non-APPROVED states (for admin workflow demo) ---
        UUID pendingId = seedProduct(2L, catIds.get("home"),
                "Khăn lụa Fashion Hub mẫu mới",
                "khan-lua-fh-2026",
                "Khăn lụa cao cấp, đang chờ duyệt.",
                ProductStatus.PENDING);
        seedVariant(pendingId, "SKU-SCARF-FH-2026", "Khăn lụa Fashion Hub mẫu mới",
                new BigDecimal("450000"), new BigDecimal("590000"), 30);

        UUID rejectedId = seedProduct(2L, catIds.get("home"),
                "Túi xách thử nghiệm",
                "tui-xach-test",
                "Sản phẩm test bị từ chối do thiếu mô tả.",
                ProductStatus.REJECTED);
        Product rejected = productRepository.findById(rejectedId).orElseThrow();
        rejected.setRejectReason("Mô tả không đủ chi tiết, ảnh không rõ ràng.");
        rejected.setRejectCount(1);
        rejected.setReviewedBy(10L); // admin
        rejected.setReviewedAt(LocalDateTime.now().minusDays(2));
        productRepository.save(rejected);
        seedVariant(rejectedId, "SKU-BAG-TEST", "Túi xách thử nghiệm",
                new BigDecimal("799000"), new BigDecimal("999000"), 0);

        log.info("[ProductDevDataLoader] Seeded {} ACTIVE products + 1 PENDING + 1 REJECTED",
                activeCount);
        log.info("[ProductDevDataLoader] Dev data seed complete.");
    }

    private UUID seedCategory(UUID parentId, String name, String slug, int sortOrder) {
        Category c = Category.builder()
                .parentId(parentId)
                .name(name)
                .slug(slug)
                .description(name + " category")
                .sortOrder(sortOrder)
                .isActive(true)
                .build();
        return categoryRepository.save(c).getId();
    }

    private UUID seedProduct(Long sellerId, UUID categoryId, String name, String slug,
                             String description, ProductStatus status) {
        LocalDateTime now = LocalDateTime.now();
        boolean reviewed = status == ProductStatus.APPROVED
                || status == ProductStatus.ACTIVE
                || status == ProductStatus.OUT_OF_STOCK;
        boolean published = status == ProductStatus.ACTIVE
                || status == ProductStatus.OUT_OF_STOCK;

        Product p = Product.builder()
                .sellerId(sellerId)
                .categoryId(categoryId)
                .name(name)
                .slug(slug)
                .description(description)
                .attributes("{}")
                .status(status)
                .rejectCount(0)
                .submittedAt(now.minusDays(new Random().nextInt(30) + 1))
                .publishedAt(published ? now.minusDays(1) : null)
                .reviewedBy(reviewed ? 10L : null)
                .reviewedAt(reviewed ? now.minusDays(1) : null)
                .build();
        return productRepository.save(p).getId();
    }

    private void seedVariant(UUID productId, String sku, String variantName,
                             BigDecimal price, BigDecimal originalPrice, int stock) {
        ProductVariant v = ProductVariant.builder()
                .productId(productId)
                .variantCode(sku)
                .variantName(variantName)
                .variantAttributes("{}")
                .price(price)
                .originalPrice(originalPrice)
                .stockQuantity(stock)
                .status(stock > 0 ? VariantStatus.ACTIVE : VariantStatus.OUT_OF_STOCK)
                .imageUrl("https://picsum.photos/seed/" + sku + "/400/400")
                .build();
        productVariantRepository.save(v);
    }

    private void seedImage(UUID productId, String url) {
        ProductImage img = ProductImage.builder()
                .productId(productId)
                .url(url)
                .sortOrder(0)
                .build();
        productImageRepository.save(img);
    }
}
