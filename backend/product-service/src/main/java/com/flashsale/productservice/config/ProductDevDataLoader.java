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
    private final StockReservationRepository stockReservationRepository;
    private final DevDataProperties devDataProperties;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("[ProductDevDataLoader] Starting dev data seed for product-service...");

        if (devDataProperties.isReset()) {
            log.warn("[ProductDevDataLoader] RESET=true — wiping all product data...");
            stockReservationRepository.deleteAllInBatch();
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
        catIds.put("beauty",      seedCategory(null, "Beauty & Health", "beauty-health", 4));
        catIds.put("skincare",    seedCategory(catIds.get("beauty"), "Skincare", "skincare", 1));
        catIds.put("makeup",      seedCategory(catIds.get("beauty"), "Makeup", "makeup", 2));
        catIds.put("books",       seedCategory(null, "Books & Stationery", "books-stationery", 5));
        catIds.put("food",        seedCategory(null, "Food & Drinks", "food-drinks", 6));
        catIds.put("toys",        seedCategory(null, "Toys & Kids", "toys-kids", 7));
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

            // ---- HomeLiving (seller 4) - new products ----
            {4L, "kitchen",     "Robot hút bụi lau nhà Xiaomi S10", "xiaomi-s10-robot",
             "Robot hút bụi thông minh, kết hợp lau nhà, điều khiển qua app.",
             "SKU-XIAOMI-S10",      "5490000",  "6990000",  40},
            {4L, "home",        "Máy lọc nước RO Kangaroo 9 lõi", "kangaroo-ro-9",
             "Máy lọc nước RO 9 lõi, công nghệ lọc khuẩn, tiêu chuẩn QCVN.",
             "SKU-KANGAROO-RO9",    "4290000",  "5290000",  25},
            {4L, "home",        "Quạt điều hòa hơi nước Asia",   "asia-cool-fan",
             "Quạt điều hòa Asia 35L, công suất 200W, làm mát diện rộng.",
             "SKU-ASIA-COOL",       "1890000",  "2590000",  60},
            {4L, "kitchen",     "Bộ 3 nồi chảo chống dính Elmich", "elmich-pot-set-3",
             "Bộ 3 nồi chảo chống dính đáy từ cao cấp, dùng cho bếp từ.",
             "SKU-ELMICH-POT3",     "1490000",  "1990000",  45},

            // ---- LifeStyle Store (seller 6) — Books & Stationery ----
            {6L, "books",       "Nhà Giả Kim — Paulo Coelho",    "nha-gia-kim",
             "Cuốn sách bán chạy nhất mọi thời đại, hành trình tìm kiếm kho báu của chàng chăn cừu Santiago.",
             "SKU-BOOK-ALCHEMIST",  "85000",    "85000",    200},
            {6L, "books",       "Atomic Habits — James Clear",   "atomic-habits",
             "Thay đổi những thói quen nhỏ để tạo nên khác biệt lớn. Best-seller NY Times.",
             "SKU-BOOK-ATOMIC",     "120000",   "120000",   150},
            {6L, "books",       "Sổ tay bìa da A5 Lux",         "notebook-a5-lux",
             "Sổ tay bìa da cao cấp khổ A5, 200 trang giấy ivory, đóng sợi chỉ.",
             "SKU-NOTEBOOK-A5",     "180000",   "250000",   300},
            {6L, "books",       "Bút máy ngòi vàng Pilot",      "pilot-fountain-pen",
             "Bút máy cao cấp Pilot ngòi vàng 14K, viết êm, bao gồm hộp quà.",
             "SKU-PILOT-PEN",       "890000",   "1290000",  80},

            // ---- LifeStyle Store (seller 6) — Food & Drinks ----
            {6L, "food",        "Cà phê Arabica Đà Lạt 500g",   "dalat-arabica-500g",
             "Cà phê Arabica nguyên chất trồng tại vùng Cầu Đất Đà Lạt, hương vị chocolate.",
             "SKU-COFFEE-ARABICA",  "150000",   "220000",   200},
            {6L, "food",        "Trà Shan Tuyết cổ thụ 200g",   "shan-tuyet-tea-200g",
             "Trà xanh Shan Tuyết cổ thụ 300 năm tuổi từ Suối Giàng, Yên Bái.",
             "SKU-SHAN-TUYET",      "250000",   "350000",   80},
            {6L, "food",        "Bánh hộp Tết các loại 500g",   "tet-cookie-box-500g",
             "Hộp bánh quy Tết thượng hạng, 3 lớp nhân: chocolate, dâu tây, matcha.",
             "SKU-TET-BOX",         "320000",   "450000",   120},
            {6L, "food",        "Mật ong rừng nguyên chất 500ml", "wild-honey-500ml",
             "Mật ong rừng tự nhiên từ vùng U Minh, không pha tạp, giàu enzyme.",
             "SKU-WILD-HONEY",      "280000",   "350000",   100},

            // ---- LifeStyle Store (seller 6) — Beauty & Health ----
            {6L, "skincare",    "Kem chống nắng Anessa SPF50+ PA++++", "anessa-sunscreen",
             "Kem chống nắng bền nước, chống trôi, công nghệ Auto-Booster, bảo vệ hoàn hảo.",
             "SKU-ANESSA-SP50",     "420000",   "420000",   150},
            {6L, "skincare",    "Serum Vitamin C The Ordinary 30ml", "the-ordinary-vc30",
             "Serum Vitamin C 23% + HA Spheres, làm sáng da, mờ thâm nám hiệu quả.",
             "SKU-ORDINARY-VC30",   "320000",   "450000",   100},
            {6L, "makeup",      "Sữa rửa mặt Cerave 473ml",    "cerave-cleanser-473",
             "Sữa rửa mặt dịu nhẹ cho da dầu mụn, chứa ceramide và acid hyaluronic.",
             "SKU-CERAVE-CLEAN",    "380000",   "380000",   200},
            {6L, "beauty",      "Máy massage cầm tay Relax",    "relax-massage-gun",
             "Máy massage cầm tay 6 đầu, 6 tốc độ, pin 2500mAh, giảm đau nhức cơ.",
             "SKU-RELAX-MASSAGE",   "890000",   "1290000",  50},

            // ---- LifeStyle Store (seller 6) — Toys & Kids ----
            {6L, "toys",        "Lego Technic Porsche 911",     "lego-technic-porsche",
             "Mô hình lắp ráp Lego Technic Porsche 911 GT3 RS, 1580 mảnh, từ 9 tuổi.",
             "SKU-LEGO-PORSCHE",    "3290000",  "3990000",  30},
            {6L, "toys",         "Bộ xếp hình gỗ thông minh 100 miếng", "wooden-puzzle-100",
             "Bộ xếp hình gỗ rèn luyện tư duy logic cho bé 3-6 tuổi, an toàn 100%.",
             "SKU-WOODEN-PUZZLE",   "250000",   "350000",   180},
            {6L, "toys",        "Xe điều khiển từ xa RC Racing", "rc-racing-car",
             "Xe địa hình RC 4 bánh chủ động, tốc độ 30km/h, pin Li-Ion 7.4V.",
             "SKU-RC-CAR",          "450000",   "650000",   60},
            {6L, "toys",        "Bộ bút màu 48 màu Crayola",   "crayola-48-colors",
             "Bộ bút màu sáp 48 màu, an toàn, rửa được, cho bé từ 3 tuổi.",
             "SKU-CRAYOLA-48",      "180000",   "180000",   250},
        };

        // ---- Multi-variant products (FashionHub seller 2) ----
        // Each entry: {sellerId, categoryKey, name, slug, description,
        //              variants = [{sku, variantName, price, originalPrice, stock}]}
        Object[][] multiVariantCatalog = {
            {2L, "home", "Áo thun basic cotton nam", "ao-thun-basic-cotton",
             "Áo thun cotton 100% dày dặn, form regular, phù hợp mặc hàng ngày.",
             new Object[][]{
                 {"SKU-TSHIRT-S",  "Size S",  "149000", "199000", 80},
                 {"SKU-TSHIRT-M",  "Size M",  "149000", "199000", 150},
                 {"SKU-TSHIRT-L",  "Size L",  "149000", "199000", 200},
                 {"SKU-TSHIRT-XL", "Size XL", "149000", "199000", 100},
             }},
            {2L, "home", "Áo sơ mi trắng công sở", "ao-so-mi-trang",
             "Áo sơ mi trắng chất liệu poplin cao cấp, không nhăn, phù hợp môi trường công sở.",
             new Object[][]{
                 {"SKU-SOMI-S",  "Size S",  "299000", "299000", 40},
                 {"SKU-SOMI-M",  "Size M",  "299000", "299000", 100},
                 {"SKU-SOMI-L",  "Size L",  "299000", "299000", 120},
                 {"SKU-SOMI-XL", "Size XL", "299000", "299000", 60},
             }},
            {2L, "home", "Quần jeans ống suông nam", "quan-jeans-ong-suong",
             "Quần jeans denim cao cấp, ống suông thoải mái, phối được nhiều kiểu.",
             new Object[][]{
                 {"SKU-JEANS-28", "Size 28", "499000", "699000", 50},
                 {"SKU-JEANS-30", "Size 30", "499000", "699000", 120},
                 {"SKU-JEANS-32", "Size 32", "499000", "699000", 130},
                 {"SKU-JEANS-34", "Size 34", "499000", "699000", 70},
             }},
            {2L, "home", "Đầm suông nữ công sở", "dam-suong-cong-so",
             "Đầm suông nữ thiết kế thanh lịch, form suông tôn dáng, chất liệu linen cao cấp.",
             new Object[][]{
                 {"SKU-DAM-S",  "Size S",  "549000", "749000", 60},
                 {"SKU-DAM-M",  "Size M",  "549000", "749000", 100},
                 {"SKU-DAM-L",  "Size L",  "549000", "749000", 80},
             }},
            // ---- LifeStyle Store books with variant ----
            {6L, "books", "Sổ tay bìa da A5 cao cấp", "notebook-da-a5-premium",
             "Sổ tay bìa da thật khổ A5, 300 trang giấy nhập khẩu, dây đánh dấu.",
             new Object[][]{
                 {"SKU-NOTE-DA-XANH", "Màu xanh navy", "250000", "350000", 100},
                 {"SKU-NOTE-DA-DEN",  "Màu đen",       "250000", "350000", 120},
                 {"SKU-NOTE-DA-NAT",  "Màu nâu cognac","250000", "350000", 80},
             }},
            // ---- Food with variant ----
            {6L, "food", "Cà phê Arabica Đà Lạt rang xay", "ca-phe-arabica-rang-xay",
             "Cà phê Arabica Đà Lạt rang xay tươi, đóng gói hút chân không.",
             new Object[][]{
                 {"SKU-COFFEE-BEAN",  "Hạt nguyên chất",  "150000", "220000", 100},
                 {"SKU-COFFEE-GROUND","Rang xay sẵn",     "150000", "220000", 200},
             }},
        };

        int activeCount = 0;

        // Seed single-variant products
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

        // Seed multi-variant products
        for (Object[] row : multiVariantCatalog) {
            Long sellerId = (Long) row[0];
            UUID categoryId = catIds.get((String) row[1]);
            UUID productId = seedProduct(sellerId, categoryId,
                    (String) row[2], (String) row[3], (String) row[4],
                    ProductStatus.ACTIVE);
            Object[][] variants = (Object[][]) row[5];
            boolean first = true;
            for (Object[] v : variants) {
                seedVariant(productId, (String) v[0], (String) v[1],
                        new BigDecimal((String) v[2]),
                        new BigDecimal((String) v[3]),
                        (int) v[4]);
            }
            seedImage(productId, "https://picsum.photos/seed/" + variants[0][0] + "/600/600");
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
