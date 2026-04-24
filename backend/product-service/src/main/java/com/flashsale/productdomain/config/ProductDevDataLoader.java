package com.flashsale.productdomain.config;

import com.flashsale.commonlib.config.DevDataProperties;
import com.flashsale.productdomain.domain.model.*;
import com.flashsale.productdomain.domain.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "dev-data.enabled", havingValue = "true", matchIfMissing = false)
public class ProductDevDataLoader implements CommandLineRunner {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryRepository inventoryRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final MongoTemplate mongoTemplate;
    private final DevDataProperties devDataProperties;

    // ------------------------------------------------------------------ //
    //  ID RANGES — aligned with identity-service
    //  Identity:  users 1-10, sellers 1-5
    //  Order:     parent_orders 1-50, orders 1-100
    // ------------------------------------------------------------------ //

    private static final long[] SELLER_IDS = {1L, 2L, 3L};
    private static final long[] USER_IDS   = {1L, 2L, 3L, 4L, 5L};

    @Override
    public void run(String... args) {
        log.info("[ProductDevDataLoader] Starting dev data seed for product-service...");

        if (devDataProperties.isReset()) {
            log.warn("[ProductDevDataLoader] RESET=true — wiping all product data...");
            mongoTemplate.dropCollection(CartItem.class);
            mongoTemplate.dropCollection(Cart.class);
            mongoTemplate.dropCollection(Inventory.class);
            mongoTemplate.dropCollection(ProductVariant.class);
            mongoTemplate.dropCollection(Product.class);
            mongoTemplate.dropCollection(Category.class);
            log.info("[ProductDevDataLoader] All product data wiped.");
        } else if (categoryRepository.count() > 0) {
            log.info("[ProductDevDataLoader] Data already exists, skipping. Set dev-data.reset=true to reload.");
            return;
        }

        List<Category> categories = seedCategories();
        List<Product> products = seedProducts(categories);
        seedVariantsAndInventory(products);
        seedCarts();

        log.info("[ProductDevDataLoader] Dev data seed complete.");
    }

    private List<Category> seedCategories() {
        List<Category> categories = new ArrayList<>();

        // Root categories
        Category electronics = createCategory(null, 0, "Điện tử", "dien-tu");
        Category fashion = createCategory(null, 0, "Thời trang", "thoi-trang");
        Category home = createCategory(null, 0, "Nhà cửa", "nha-cua");

        categories.addAll(Arrays.asList(electronics, fashion, home));

        // Level-1 subcategories
        Category phones = createCategory(electronics.getId(), 1, "Điện thoại", "dien-thoai");
        Category laptops = createCategory(electronics.getId(), 1, "Laptop", "laptop");
        Category audio = createCategory(electronics.getId(), 1, "Âm thanh", "am-thanh");
        Category accessories = createCategory(electronics.getId(), 1, "Phụ kiện", "phu-kien");

        Category men = createCategory(fashion.getId(), 1, "Nam", "nam");
        Category women = createCategory(fashion.getId(), 1, "Nữ", "nu");

        categories.addAll(Arrays.asList(phones, laptops, audio, accessories, men, women));

        return categoryRepository.saveAll(categories);
    }

    private Category createCategory(String parentId, int level, String name, String slug) {
        return Category.builder()
                .id(UUID.randomUUID().toString().substring(0, 16))
                .name(name)
                .slug(slug)
                .parentId(parentId)
                .level(level)
                .build();
    }

    private List<Product> seedProducts(List<Category> categories) {
        Map<String, Category> catMap = new HashMap<>();
        for (Category c : categories) catMap.put(c.getSlug(), c);

        List<Product> products = new ArrayList<>();

        // Seller 1: TechWorld Store
        products.add(createProduct("PROD-001", SELLER_IDS[0],
                catMap.get("dien-thoai").getId(),
                "iPhone 15 Pro Max 256GB", "iPhone 15 Pro Max — Chip A17 Pro, Camera 48MP, Titan tự nhiên.",
                Arrays.asList(
                        "https://picsum.photos/seed/iphone15pm1/800/800",
                        "https://picsum.photos/seed/iphone15pm2/800/800"),
                true, "APPROVED", 45));

        products.add(createProduct("PROD-002", SELLER_IDS[0],
                catMap.get("laptop").getId(),
                "MacBook Air M3 13 inch 256GB", "MacBook Air M3 — Nhẹ, mỏng, chip M3 tiết kiệm pin.",
                Arrays.asList(
                        "https://picsum.photos/seed/mba13m3/800/800",
                        "https://picsum.photos/seed/mba13m3b/800/800"),
                false, "APPROVED", 20));

        products.add(createProduct("PROD-003", SELLER_IDS[0],
                catMap.get("phu-kien").getId(),
                "USB-C Hub 7-in-1", "Hub chuyển đổi USB-C 7 cổng: HDMI 4K, USB-A 3.0, SD card...",
                Arrays.asList("https://picsum.photos/seed/usbchub7/800/800"),
                false, "APPROVED", 150));

        products.add(createProduct("PROD-004", SELLER_IDS[0],
                catMap.get("phu-kien").getId(),
                "Cable USB-C sạc nhanh 100W 1m", "Cable USB-C to USB-C 100W, hỗ trợ PD 3.0, sạc nhanh.",
                Arrays.asList("https://picsum.photos/seed/usbcable1m/800/800"),
                false, "APPROVED", 300));

        products.add(createProduct("PROD-005", SELLER_IDS[0],
                catMap.get("am-thanh").getId(),
                "AirPods Pro 2", "Tai nghe AirPods Pro thế hệ 2 — Chống ồn chủ động, Spatial Audio.",
                Arrays.asList(
                        "https://picsum.photos/seed/airpods2a/800/800",
                        "https://picsum.photos/seed/airpods2b/800/800"),
                true, "APPROVED", 30));

        products.add(createProduct("PROD-006", SELLER_IDS[0],
                catMap.get("am-thanh").getId(),
                "Apple Watch SE 40mm", "Apple Watch SE — Màn hình Retina, theo dõi sức khỏe, GPS.",
                Arrays.asList(
                        "https://picsum.photos/seed/watchse1/800/800",
                        "https://picsum.photos/seed/watchse2/800/800"),
                true, "APPROVED", 15));

        // Seller 2: Fashion Hub
        products.add(createProduct("PROD-007", SELLER_IDS[1],
                catMap.get("nam").getId(),
                "Áo Polo nam cao cấp", "Áo Polo nam, vải pique cotton, thoáng mát, nhiều màu.",
                Arrays.asList("https://picsum.photos/seed/poloman1/800/800"),
                false, "APPROVED", 200));

        products.add(createProduct("PROD-008", SELLER_IDS[1],
                catMap.get("nu").getId(),
                "Váy hoa nhí dáng A", "Váy hoa nhí dáng A, chất liệu voan nhẹ, phong cách Hàn Quốc.",
                Arrays.asList(
                        "https://picsum.photos/seed/vayhoa1/800/800",
                        "https://picsum.photos/seed/vayhoa2/800/800"),
                false, "APPROVED", 80));

        products.add(createProduct("PROD-009", SELLER_IDS[1],
                catMap.get("nam").getId(),
                "Quần Jeans nam slim fit", "Quần Jeans nam ống slim fit, co giãn, phong cách trẻ trung.",
                Arrays.asList("https://picsum.photos/seed/jeansnam1/800/800"),
                false, "APPROVED", 120));

        // Seller 3: Gadget Pro
        products.add(createProduct("PROD-010", SELLER_IDS[2],
                catMap.get("phu-kien").getId(),
                "Samsung Galaxy Buds2", "Tai nghe Samsung Galaxy Buds2 — Nhẹ, chống ồn, pin 5h.",
                Arrays.asList(
                        "https://picsum.photos/seed/buds21/800/800",
                        "https://picsum.photos/seed/buds22/800/800"),
                false, "APPROVED", 50));

        products.add(createProduct("PROD-011", SELLER_IDS[2],
                catMap.get("phu-kien").getId(),
                "Logitech MX Master 3S", "Chuột không dây Logitech MX Master 3S — Sensor 8K, cuộn MagSpeed.",
                Arrays.asList("https://picsum.photos/seed/mx3s1/800/800"),
                false, "APPROVED", 25));

        products.add(createProduct("PROD-012", SELLER_IDS[2],
                catMap.get("phu-kien").getId(),
                "MagSafe Charger 15W", "Sạc MagSafe Apple 15W — Nam châm snap, sạc không dây.",
                Arrays.asList("https://picsum.photos/seed/magsafe1/800/800"),
                false, "APPROVED", 100));

        products.add(createProduct("PROD-013", SELLER_IDS[2],
                catMap.get("nha-cua").getId(),
                "Bộ dao nhà bếp 5 món", "Bộ dao nhà bếp cao cấp 5 món, lưỡi thép không gỉ, cầm chắc tay.",
                Arrays.asList("https://picsum.photos/seed/daobep1/800/800"),
                false, "APPROVED", 60));

        // Pending products
        products.add(createProduct("PROD-PEND-1", SELLER_IDS[0],
                catMap.get("dien-thoai").getId(),
                "iPhone 14 128GB (Refurbished)", "iPhone 14 màn hình 6.1 inch, camera kép, Pin 3279mAh.",
                Arrays.asList("https://picsum.photos/seed/iphone14r1/800/800"),
                false, "PENDING", 10));

        return productRepository.saveAll(products);
    }

    private Product createProduct(String id, Long sellerId, String categoryId,
                                   String name, String description, List<String> images,
                                   boolean isFlash, String status, int stock) {
        return Product.builder()
                .id(id)
                .sellerId(sellerId)
                .categoryId(categoryId)
                .name(name)
                .description(description)
                .attributes(Map.of("origin", "Chính hãng", "warranty", "12 tháng"))
                .images(images)
                .isFlash(isFlash)
                .status(status)
                .stockAvailable(stock)
                .build();
    }

    private void seedVariantsAndInventory(List<Product> products) {
        for (Product product : products) {
            List<ProductVariant> variants = new ArrayList<>();
            List<Inventory> inventories = new ArrayList<>();

            switch (product.getId()) {
                case "PROD-001" -> {
                    variants.add(createVariant(product.getId(), "SKU-IPHONE-BLK-128", "Đen tự nhiên / 128GB", new BigDecimal("25000000")));
                    variants.add(createVariant(product.getId(), "SKU-IPHONE-WHT-256", "Trắng tự nhiên / 256GB", new BigDecimal("28000000")));
                    variants.add(createVariant(product.getId(), "SKU-IPHONE-TIT-512", "Titan xanh / 512GB", new BigDecimal("33000000")));
                }
                case "PROD-002" -> {
                    variants.add(createVariant(product.getId(), "SKU-MBA-M3-13-256", "Midnight / 256GB", new BigDecimal("28900000")));
                    variants.add(createVariant(product.getId(), "SKU-MBA-M3-13-512", "Starlight / 512GB", new BigDecimal("32900000")));
                }
                case "PROD-003" -> {
                    variants.add(createVariant(product.getId(), "SKU-HUB-7IN1", "Silver", new BigDecimal("890000")));
                    variants.add(createVariant(product.getId(), "SKU-HUB-7IN1-G", "Graphite", new BigDecimal("890000")));
                }
                case "PROD-004" -> {
                    variants.add(createVariant(product.getId(), "SKU-CABLE-TYPE-C", "Trắng / 1m", new BigDecimal("150000")));
                    variants.add(createVariant(product.getId(), "SKU-CABLE-TYPE-C-2M", "Đen / 2m", new BigDecimal("200000")));
                }
                case "PROD-005" -> {
                    variants.add(createVariant(product.getId(), "SKU-AIRPOD-PRO2", "Default (Không gian)", new BigDecimal("5900000")));
                }
                case "PROD-006" -> {
                    variants.add(createVariant(product.getId(), "SKU-WATCH-SE-40-S", "Bạc / 40mm", new BigDecimal("7900000")));
                    variants.add(createVariant(product.getId(), "SKU-WATCH-SE-44-M", "Midnight / 44mm", new BigDecimal("8500000")));
                }
                case "PROD-007" -> {
                    variants.add(createVariant(product.getId(), "SKU-POLO-NAM-S", "Size S / Xanh Navy", new BigDecimal("450000")));
                    variants.add(createVariant(product.getId(), "SKU-POLO-NAM-M", "Size M / Xanh Navy", new BigDecimal("450000")));
                    variants.add(createVariant(product.getId(), "SKU-POLO-NAM-L", "Size L / Trắng", new BigDecimal("450000")));
                }
                case "PROD-008" -> {
                    variants.add(createVariant(product.getId(), "SKU-VAY-HOA-S", "S / Hoa nhí đỏ", new BigDecimal("320000")));
                    variants.add(createVariant(product.getId(), "SKU-VAY-HOA-M", "M / Hoa nhí hồng", new BigDecimal("320000")));
                }
                case "PROD-009" -> {
                    variants.add(createVariant(product.getId(), "SKU-JEANS-29", "29 / Slim fit", new BigDecimal("650000")));
                    variants.add(createVariant(product.getId(), "SKU-JEANS-31", "31 / Slim fit", new BigDecimal("650000")));
                }
                case "PROD-010" -> {
                    variants.add(createVariant(product.getId(), "SKU-SAMSUNG-BUDS2", "Lavender", new BigDecimal("2900000")));
                    variants.add(createVariant(product.getId(), "SKU-SAMSUNG-BUDS2-W", "Trắng", new BigDecimal("2900000")));
                }
                case "PROD-011" -> {
                    variants.add(createVariant(product.getId(), "SKU-MOUSE-MX3", "Graphite", new BigDecimal("3500000")));
                }
                case "PROD-012" -> {
                    variants.add(createVariant(product.getId(), "SKU-MAGSAFE", "Trắng", new BigDecimal("1350000")));
                }
                case "PROD-013" -> {
                    variants.add(createVariant(product.getId(), "SKU-DAO-5MOC", "5 món cao cấp", new BigDecimal("850000")));
                }
                default -> {
                    variants.add(createVariant(product.getId(), "SKU-" + product.getId().replace("-", "") + "-DEF", "Default",
                            new BigDecimal("500000")));
                }
            }

            variantRepository.saveAll(variants);

            for (ProductVariant variant : variants) {
                Inventory inv = Inventory.builder()
                        .skuCode(variant.getSkuCode())
                        .productId(product.getId())
                        .stockTotal(product.getStockAvailable())
                        .stockLocked(0)
                        .stockAvailable(product.getStockAvailable())
                        .stockFlashReserved(0)
                        .build();
                inventories.add(inv);
            }

            inventoryRepository.saveAll(inventories);
        }

        log.info("[ProductDevDataLoader] Seeded variants and inventory for {} products", products.size());
    }

    private ProductVariant createVariant(String productId, String skuCode,
                                          String tierName, BigDecimal price) {
        return ProductVariant.builder()
                .productId(productId)
                .skuCode(skuCode)
                .tierName(tierName)
                .price(price)
                .build();
    }

    private void seedCarts() {
        for (Long userId : USER_IDS) {
            Cart cart = Cart.builder()
                    .id("cart_dev_" + userId)
                    .userId(userId)
                    .totalItems(0)
                    .build();
            cart = cartRepository.save(cart);

            List<CartItem> items = new ArrayList<>();

            if (userId == 1L) {
                items.add(createCartItem(cart.getId(), userId, "SKU-IPHONE-BLK-128", null, new BigDecimal("25000000"), 1));
                items.add(createCartItem(cart.getId(), userId, "SKU-AIRPOD-PRO2", null, new BigDecimal("5900000"), 1));
                items.add(createCartItem(cart.getId(), userId, "SKU-CABLE-TYPE-C", null, new BigDecimal("150000"), 2));
            } else if (userId == 2L) {
                items.add(createCartItem(cart.getId(), userId, "SKU-MBA-M3-13-256", null, new BigDecimal("28900000"), 1));
                items.add(createCartItem(cart.getId(), userId, "SKU-MOUSE-MX3", null, new BigDecimal("3500000"), 1));
            } else if (userId == 3L) {
                items.add(createCartItem(cart.getId(), userId, "SKU-HUB-7IN1", null, new BigDecimal("890000"), 1));
                items.add(createCartItem(cart.getId(), userId, "SKU-MAGSAFE", null, new BigDecimal("1350000"), 1));
            } else if (userId == 4L) {
                items.add(createCartItem(cart.getId(), userId, "SKU-POLO-NAM-M", null, new BigDecimal("450000"), 2));
            } else if (userId == 5L) {
                items.add(createCartItem(cart.getId(), userId, "SKU-SAMSUNG-BUDS2", null, new BigDecimal("2900000"), 1));
            }

            if (!items.isEmpty()) {
                cartItemRepository.saveAll(items);
                cart.setTotalItems(items.size());
                cartRepository.save(cart);
            }
        }

        log.info("[ProductDevDataLoader] Seeded {} carts with items for {} users", USER_IDS.length, USER_IDS.length);
    }

    private CartItem createCartItem(String cartId, Long userId, String skuCode,
                                     Long fsItemId, BigDecimal priceSnapshot, Integer quantity) {
        return CartItem.builder()
                .cartId(cartId)
                .userId(userId)
                .skuCode(skuCode)
                .fsItemId(fsItemId)
                .priceSnapshot(priceSnapshot)
                .quantity(quantity)
                .addedAt(LocalDateTime.now().minusMinutes(new Random().nextInt(1440)))
                .build();
    }
}
