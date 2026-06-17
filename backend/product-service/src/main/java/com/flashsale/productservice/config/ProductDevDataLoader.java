package com.flashsale.productservice.config;

import com.flashsale.commonlib.config.DevDataProperties;
import com.flashsale.productservice.entity.*;
import com.flashsale.productservice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Seeds the FE test-dataset (categories, products, variants, images, wishlist, cart)
 * for frontend E2E and integration testing.
 *
 * <p>All products belong to seller 900002 (fe_seller). All admin actions
 * (reviewed_by) reference 900003 (fe_admin). The fe_buyer (900001) receives
 * wishlist and cart items at the end.</p>
 *
 * <p>Idempotent via ON CONFLICT DO UPDATE — safe to run repeatedly.</p>
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
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("[ProductDevDataLoader] Starting dev data seed for product-service...");

        if (devDataProperties.isReset()) {
            log.warn("[ProductDevDataLoader] RESET=true -- wiping all product data...");
            stockReservationRepository.deleteAllInBatch();
            productImageRepository.deleteAllInBatch();
            productVariantRepository.deleteAllInBatch();
            productRepository.deleteAllInBatch();
            categoryRepository.deleteAllInBatch();
            log.info("[ProductDevDataLoader] All product data wiped.");
        } else if (productRepository.count() > 0) {
            log.info("[ProductDevDataLoader] Data already exists, skipping main seed.");
            seedFeData();
            log.info("[ProductDevDataLoader] Dev data seed complete.");
            return;
        }

        seedFeData();

        log.info("[ProductDevDataLoader] Dev data seed complete.");
    }

    private void seedFeData() {
        log.info("[ProductDevDataLoader] Seeding FE test-dataset...");

        // Check if FE data already exists
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM product.categories WHERE id = '90000000-0000-4000-8000-000000000001'", Integer.class);
        if (count != null && count > 0) {
            log.info("[ProductDevDataLoader] FE data already exists, skipping.");
            return;
        }

        // ========================================================================
        // 1. Categories
        // ========================================================================
        jdbcTemplate.update("INSERT INTO product.categories (id, parent_id, name, slug, description, image_url, sort_order, is_active, created_at, updated_at) VALUES " +
            "('90000000-0000-4000-8000-000000000001', null, 'FE Electronics', 'fe-electronics', 'Frontend fixture electronics root category', 'https://picsum.photos/seed/fe-electronics/600/400', 10, true, now() - interval '16 days', now()), " +
            "('90000000-0000-4000-8000-000000000002', '90000000-0000-4000-8000-000000000001', 'FE Phones', 'fe-phones', 'Frontend fixture phone category', 'https://picsum.photos/seed/fe-phones/600/400', 11, true, now() - interval '16 days', now()), " +
            "('90000000-0000-4000-8000-000000000003', '90000000-0000-4000-8000-000000000001', 'FE Audio', 'fe-audio', 'Frontend fixture audio category', 'https://picsum.photos/seed/fe-audio/600/400', 12, true, now() - interval '16 days', now()), " +
            "('90000000-0000-4000-8000-000000000004', '90000000-0000-4000-8000-000000000001', 'FE Laptops', 'fe-laptops', 'Frontend fixture laptop category', 'https://picsum.photos/seed/fe-laptops/600/400', 13, true, now() - interval '16 days', now()), " +
            "('90000000-0000-4000-8000-000000000005', null, 'FE Home', 'fe-home', 'Frontend fixture home category', 'https://picsum.photos/seed/fe-home/600/400', 14, true, now() - interval '16 days', now()), " +
            "('90000000-0000-4000-8000-000000000006', null, 'FE Fashion', 'fe-fashion', 'Frontend fixture fashion category', 'https://picsum.photos/seed/fe-fashion/600/400', 15, true, now() - interval '16 days', now()) " +
            "ON CONFLICT (id) DO UPDATE SET parent_id=EXCLUDED.parent_id,name=EXCLUDED.name,slug=EXCLUDED.slug,description=EXCLUDED.description,image_url=EXCLUDED.image_url,sort_order=EXCLUDED.sort_order,is_active=EXCLUDED.is_active,updated_at=now()");

        // ========================================================================
        // 2. Products (15 total)
        // ========================================================================
        jdbcTemplate.update("INSERT INTO product.products (id, category_id, seller_id, name, slug, description, attributes, status, reject_reason, reviewed_at, reviewed_by, reject_count, submitted_at, created_at, updated_at, published_at) VALUES " +
            // --- Existing 10 (keep as-is, status changes noted) ---
            "('90000000-0000-4000-8001-000000000101', '90000000-0000-4000-8000-000000000002', 900002, 'FE Phone Pro Camera Kit', 'fe-phone-pro-camera-kit', 'Active catalog product for search, detail, cart, checkout, and flash-sale tests.', '{\"brand\":\"FE\",\"screen\":\"6.1 inch\",\"coverage\":[\"catalog\",\"search\",\"cart\",\"checkout\"]}'::jsonb, 'ACTIVE', null, now() - interval '14 days', 900003, 0, now() - interval '15 days', now() - interval '15 days', now(), now() - interval '14 days'), " +
            "('90000000-0000-4000-8001-000000000102', '90000000-0000-4000-8000-000000000004', 900002, 'FE MacBook Air M3 Demo', 'fe-macbook-air-m3-demo', 'High-value seller product for payment and payout screens.', '{\"brand\":\"FE\",\"ram\":\"16GB\",\"coverage\":[\"seller-payments\",\"search\"]}'::jsonb, 'ACTIVE', null, now() - interval '13 days', 900003, 0, now() - interval '14 days', now() - interval '14 days', now(), now() - interval '13 days'), " +
            "('90000000-0000-4000-8001-000000000103', '90000000-0000-4000-8000-000000000003', 900002, 'FE AirPods Flash Combo', 'fe-airpods-flash-combo', 'Active product with live flash-sale mapping.', '{\"brand\":\"FE\",\"noiseCancellation\":true,\"coverage\":[\"flash-sale\",\"search\"]}'::jsonb, 'ACTIVE', null, now() - interval '12 days', 900003, 0, now() - interval '13 days', now() - interval '13 days', now(), now() - interval '12 days'), " +
            "('90000000-0000-4000-8001-000000000104', '90000000-0000-4000-8000-000000000001', 900002, 'FE USB-C Hub 8-in-1', 'fe-usb-c-hub-8-in-1', 'Low-price add-to-cart product for quantity update and remove tests.', '{\"brand\":\"FE\",\"ports\":8,\"coverage\":[\"cart-update\",\"cart-remove\"]}'::jsonb, 'ACTIVE', null, now() - interval '11 days', 900003, 0, now() - interval '12 days', now() - interval '12 days', now(), now() - interval '11 days'), " +
            "('90000000-0000-4000-8001-000000000105', '90000000-0000-4000-8000-000000000006', 900002, 'FE Pending Review Backpack', 'fe-pending-review-backpack', 'Pending product for admin moderation list and approve flow.', '{\"brand\":\"FE\",\"coverage\":[\"admin-products-pending\",\"approve-product\"]}'::jsonb, 'PENDING', null, null, null, 0, now() - interval '2 days', now() - interval '5 days', now(), null), " +
            "('90000000-0000-4000-8001-000000000106', '90000000-0000-4000-8000-000000000006', 900002, 'FE Rejected Sample Bag', 'fe-rejected-sample-bag', 'Rejected product for seller edit/resubmit and admin reject display.', '{\"brand\":\"FE\",\"coverage\":[\"rejected-product\",\"resubmit-product\"]}'::jsonb, 'REJECTED', 'Missing real product images and warranty details.', now() - interval '3 days', 900003, 1, now() - interval '4 days', now() - interval '6 days', now(), null), " +
            "('90000000-0000-4000-8001-000000000107', '90000000-0000-4000-8000-000000000005', 900002, 'FE Draft Smart Lamp', 'fe-draft-smart-lamp', 'Draft seller product for submit-for-review flow.', '{\"brand\":\"FE\",\"coverage\":[\"submit-product-review\"]}'::jsonb, 'DRAFT', null, null, null, 0, null, now() - interval '3 days', now(), null), " +
            "('90000000-0000-4000-8001-000000000108', '90000000-0000-4000-8000-000000000005', 900002, 'FE Approved Robot Vacuum', 'fe-approved-robot-vacuum', 'Approved but unpublished seller product for publish flow.', '{\"brand\":\"FE\",\"coverage\":[\"publish-product\"]}'::jsonb, 'APPROVED', null, now() - interval '2 days', 900003, 0, now() - interval '3 days', now() - interval '4 days', now(), null), " +
            "('90000000-0000-4000-8001-000000000109', '90000000-0000-4000-8000-000000000003', 900002, 'FE Out Of Stock Headphone', 'fe-out-of-stock-headphone', 'Out-of-stock product for inventory/restock tests.', '{\"brand\":\"FE\",\"coverage\":[\"inventory\",\"restock\"]}'::jsonb, 'OUT_OF_STOCK', null, now() - interval '8 days', 900003, 0, now() - interval '9 days', now() - interval '9 days', now(), now() - interval '8 days'), " +
            "('90000000-0000-4000-8001-000000000110', '90000000-0000-4000-8000-000000000005', 900002, 'FE Inactive Desk Setup', 'fe-inactive-desk-setup', 'Inactive product for seller unpublish/publish regression.', '{\"brand\":\"FE\",\"coverage\":[\"unpublish-product\",\"inactive-product\"]}'::jsonb, 'INACTIVE', null, now() - interval '7 days', 900003, 0, now() - interval '8 days', now() - interval '8 days', now(), null), " +
            // --- New 5 ---
            "('90000000-0000-4000-8001-000000000111', '90000000-0000-4000-8000-000000000006', 900002, 'FE Summer T-Shirt', 'fe-summer-t-shirt', 'Multi-variant fashion product with sizes S/M/L/XL for cart and order tests.', '{\"brand\":\"FE\",\"material\":\"cotton\",\"coverage\":[\"multi-variant\",\"cart\",\"order\"]}'::jsonb, 'ACTIVE', null, now() - interval '6 days', 900003, 0, now() - interval '7 days', now() - interval '7 days', now(), now() - interval '6 days'), " +
            "('90000000-0000-4000-8001-000000000112', '90000000-0000-4000-8000-000000000001', 900002, 'FE Wireless Charger Pad', 'fe-wireless-charger-pad', 'Standard active electronics product for search and detail tests.', '{\"brand\":\"FE\",\"power\":\"15W\",\"coverage\":[\"search\",\"product-detail\"]}'::jsonb, 'ACTIVE', null, now() - interval '5 days', 900003, 0, now() - interval '6 days', now() - interval '6 days', now(), now() - interval '5 days'), " +
            "('90000000-0000-4000-8001-000000000113', '90000000-0000-4000-8000-000000000005', 900002, 'FE Yoga Mat Premium', 'fe-yoga-mat-premium', 'Active home product for category browsing and stock validation.', '{\"brand\":\"FE\",\"thickness\":\"6mm\",\"coverage\":[\"category-browse\",\"stock\"]}'::jsonb, 'ACTIVE', null, now() - interval '4 days', 900003, 0, now() - interval '5 days', now() - interval '5 days', now(), now() - interval '4 days'), " +
            "('90000000-0000-4000-8001-000000000114', '90000000-0000-4000-8000-000000000006', 900002, 'FE Travel Backpack', 'fe-travel-backpack', 'Active fashion product for search and compare features.', '{\"brand\":\"FE\",\"capacity\":\"40L\",\"coverage\":[\"search\",\"compare\"]}'::jsonb, 'ACTIVE', null, now() - interval '3 days', 900003, 0, now() - interval '4 days', now() - interval '4 days', now(), now() - interval '3 days'), " +
            "('90000000-0000-4000-8001-000000000115', '90000000-0000-4000-8000-000000000003', 900002, 'FE Bluetooth Earbuds Pro', 'fe-bluetooth-earbuds-pro', 'Premium audio product for flash-sale and recommendation tests.', '{\"brand\":\"FE\",\"battery\":\"8h\",\"coverage\":[\"flash-sale\",\"recommendations\"]}'::jsonb, 'ACTIVE', null, now() - interval '2 days', 900003, 0, now() - interval '3 days', now() - interval '3 days', now(), now() - interval '2 days') " +
            "ON CONFLICT (id) DO UPDATE SET category_id=EXCLUDED.category_id,seller_id=EXCLUDED.seller_id,name=EXCLUDED.name,slug=EXCLUDED.slug,description=EXCLUDED.description,attributes=EXCLUDED.attributes,status=EXCLUDED.status,reject_reason=EXCLUDED.reject_reason,reviewed_at=EXCLUDED.reviewed_at,reviewed_by=EXCLUDED.reviewed_by,reject_count=EXCLUDED.reject_count,submitted_at=EXCLUDED.submitted_at,updated_at=now(),published_at=EXCLUDED.published_at");

        // ========================================================================
        // 3. Variants
        // ========================================================================
        jdbcTemplate.update("INSERT INTO product.product_variants (id, product_id, variant_code, variant_name, variant_attributes, price, original_price, stock_quantity, status, version, image_url, created_at, updated_at) VALUES " +
            // --- Existing 10 variants (101-110) ---
            "('90000000-0000-4000-9001-000000000101', '90000000-0000-4000-8001-000000000101', 'FE-SKU-PHONE-15PRO', 'Black / 256GB', '{\"color\":\"black\",\"storage\":\"256GB\"}'::jsonb, 23990000, 25990000, 25, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-phone-15pro/500/500', now() - interval '15 days', now()), " +
            "('90000000-0000-4000-9001-000000000102', '90000000-0000-4000-8001-000000000102', 'FE-SKU-LAPTOP-M3', 'Space Gray / 16GB', '{\"color\":\"gray\",\"ram\":\"16GB\"}'::jsonb, 27990000, 31990000, 12, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-laptop-m3/500/500', now() - interval '14 days', now()), " +
            "('90000000-0000-4000-9001-000000000103', '90000000-0000-4000-8001-000000000103', 'FE-SKU-AIRPODS-COMBO', 'USB-C Combo', '{\"connector\":\"USB-C\"}'::jsonb, 4990000, 6490000, 60, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-airpods-combo/500/500', now() - interval '13 days', now()), " +
            "('90000000-0000-4000-9001-000000000104', '90000000-0000-4000-8001-000000000104', 'FE-SKU-HUB-8IN1', 'Silver / 8 ports', '{\"color\":\"silver\",\"ports\":8}'::jsonb, 790000, 990000, 150, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-hub-8in1/500/500', now() - interval '12 days', now()), " +
            "('90000000-0000-4000-9001-000000000105', '90000000-0000-4000-8001-000000000105', 'FE-SKU-PENDING-BACKPACK', 'Default', '{}'::jsonb, 690000, 890000, 40, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-pending-backpack/500/500', now() - interval '5 days', now()), " +
            "('90000000-0000-4000-9001-000000000106', '90000000-0000-4000-8001-000000000106', 'FE-SKU-REJECTED-BAG', 'Default', '{}'::jsonb, 590000, 790000, 35, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-rejected-bag/500/500', now() - interval '6 days', now()), " +
            "('90000000-0000-4000-9001-000000000107', '90000000-0000-4000-8001-000000000107', 'FE-SKU-DRAFT-LAMP', 'Warm White', '{\"color\":\"warm-white\"}'::jsonb, 450000, 550000, 20, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-draft-lamp/500/500', now() - interval '3 days', now()), " +
            "('90000000-0000-4000-9001-000000000108', '90000000-0000-4000-8001-000000000108', 'FE-SKU-APPROVED-VACUUM', 'Standard', '{}'::jsonb, 3890000, 4590000, 18, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-approved-vacuum/500/500', now() - interval '4 days', now()), " +
            "('90000000-0000-4000-9001-000000000109', '90000000-0000-4000-8001-000000000109', 'FE-SKU-OOS-HEADPHONE', 'Midnight', '{\"color\":\"midnight\"}'::jsonb, 1290000, 1590000, 0, 'OUT_OF_STOCK', 1, 'https://picsum.photos/seed/fe-oos-headphone/500/500', now() - interval '9 days', now()), " +
            "('90000000-0000-4000-9001-000000000110', '90000000-0000-4000-8001-000000000110', 'FE-SKU-INACTIVE-DESK', 'Default', '{}'::jsonb, 1990000, 2490000, 10, 'INACTIVE', 1, 'https://picsum.photos/seed/fe-inactive-desk/500/500', now() - interval '8 days', now()), " +
            // --- Product 111 (multi-variant: S/M/L/XL) uses 111-114 ---
            "('90000000-0000-4000-9001-000000000111', '90000000-0000-4000-8001-000000000111', 'FE-SKU-TSHIRT-S', 'Size S', '{\"size\":\"S\",\"material\":\"cotton\"}'::jsonb, 149000, 199000, 50, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-tshirt-s/500/500', now() - interval '7 days', now()), " +
            "('90000000-0000-4000-9001-000000000112', '90000000-0000-4000-8001-000000000111', 'FE-SKU-TSHIRT-M', 'Size M', '{\"size\":\"M\",\"material\":\"cotton\"}'::jsonb, 149000, 199000, 80, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-tshirt-m/500/500', now() - interval '7 days', now()), " +
            "('90000000-0000-4000-9001-000000000113', '90000000-0000-4000-8001-000000000111', 'FE-SKU-TSHIRT-L', 'Size L', '{\"size\":\"L\",\"material\":\"cotton\"}'::jsonb, 149000, 199000, 60, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-tshirt-l/500/500', now() - interval '7 days', now()), " +
            "('90000000-0000-4000-9001-000000000114', '90000000-0000-4000-8001-000000000111', 'FE-SKU-TSHIRT-XL', 'Size XL', '{\"size\":\"XL\",\"material\":\"cotton\"}'::jsonb, 149000, 199000, 10, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-tshirt-xl/500/500', now() - interval '7 days', now()), " +
            // --- Single-variant for products 112-115 use 115, 201-203 ---
            "('90000000-0000-4000-9001-000000000115', '90000000-0000-4000-8001-000000000112', 'FE-SKU-CHARGER-PAD', 'White / 15W', '{\"color\":\"white\",\"power\":\"15W\"}'::jsonb, 450000, 590000, 80, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-charger-pad/500/500', now() - interval '6 days', now()), " +
            "('90000000-0000-4000-9001-000000000201', '90000000-0000-4000-8001-000000000113', 'FE-SKU-YOGA-MAT', 'Purple / 6mm', '{\"color\":\"purple\",\"thickness\":\"6mm\"}'::jsonb, 350000, 490000, 100, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-yoga-mat/500/500', now() - interval '5 days', now()), " +
            "('90000000-0000-4000-9001-000000000202', '90000000-0000-4000-8001-000000000114', 'FE-SKU-TRAVEL-BACKPACK', 'Gray / 40L', '{\"color\":\"gray\",\"capacity\":\"40L\"}'::jsonb, 890000, 1090000, 45, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-travel-backpack/500/500', now() - interval '4 days', now()), " +
            "('90000000-0000-4000-9001-000000000203', '90000000-0000-4000-8001-000000000115', 'FE-SKU-EARBUDS-PRO', 'Black / ANC', '{\"color\":\"black\",\"anc\":true}'::jsonb, 1590000, 1990000, 35, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-earbuds-pro/500/500', now() - interval '3 days', now()) " +
            "ON CONFLICT (id) DO UPDATE SET product_id=EXCLUDED.product_id,variant_code=EXCLUDED.variant_code,variant_name=EXCLUDED.variant_name,variant_attributes=EXCLUDED.variant_attributes,price=EXCLUDED.price,original_price=EXCLUDED.original_price,stock_quantity=EXCLUDED.stock_quantity,status=EXCLUDED.status,version=EXCLUDED.version,image_url=EXCLUDED.image_url,updated_at=now()");

        // ========================================================================
        // 4. Images
        // ========================================================================
        jdbcTemplate.update("INSERT INTO product.product_images (id, product_id, variant_id, url, sort_order, created_at) VALUES " +
            // --- Existing 10 images (101-110) ---
            "('90000000-0000-4000-a001-000000000101', '90000000-0000-4000-8001-000000000101', null, 'https://picsum.photos/seed/fe-phone-hero/800/800', 0, now() - interval '15 days'), " +
            "('90000000-0000-4000-a001-000000000102', '90000000-0000-4000-8001-000000000102', null, 'https://picsum.photos/seed/fe-laptop-hero/800/800', 0, now() - interval '14 days'), " +
            "('90000000-0000-4000-a001-000000000103', '90000000-0000-4000-8001-000000000103', null, 'https://picsum.photos/seed/fe-airpods-hero/800/800', 0, now() - interval '13 days'), " +
            "('90000000-0000-4000-a001-000000000104', '90000000-0000-4000-8001-000000000104', null, 'https://picsum.photos/seed/fe-hub-hero/800/800', 0, now() - interval '12 days'), " +
            "('90000000-0000-4000-a001-000000000105', '90000000-0000-4000-8001-000000000105', null, 'https://picsum.photos/seed/fe-pending-hero/800/800', 0, now() - interval '5 days'), " +
            "('90000000-0000-4000-a001-000000000106', '90000000-0000-4000-8001-000000000106', null, 'https://picsum.photos/seed/fe-rejected-hero/800/800', 0, now() - interval '6 days'), " +
            "('90000000-0000-4000-a001-000000000107', '90000000-0000-4000-8001-000000000107', null, 'https://picsum.photos/seed/fe-draft-hero/800/800', 0, now() - interval '3 days'), " +
            "('90000000-0000-4000-a001-000000000108', '90000000-0000-4000-8001-000000000108', null, 'https://picsum.photos/seed/fe-vacuum-hero/800/800', 0, now() - interval '4 days'), " +
            "('90000000-0000-4000-a001-000000000109', '90000000-0000-4000-8001-000000000109', null, 'https://picsum.photos/seed/fe-oos-hero/800/800', 0, now() - interval '9 days'), " +
            "('90000000-0000-4000-a001-000000000110', '90000000-0000-4000-8001-000000000110', null, 'https://picsum.photos/seed/fe-desk-hero/800/800', 0, now() - interval '8 days'), " +
            // --- New images for products 111-115 ---
            "('90000000-0000-4000-a001-000000000111', '90000000-0000-4000-8001-000000000111', null, 'https://picsum.photos/seed/fe-tshirt-hero/800/800', 0, now() - interval '7 days'), " +
            "('90000000-0000-4000-a001-000000000112', '90000000-0000-4000-8001-000000000112', null, 'https://picsum.photos/seed/fe-charger-hero/800/800', 0, now() - interval '6 days'), " +
            "('90000000-0000-4000-a001-000000000113', '90000000-0000-4000-8001-000000000113', null, 'https://picsum.photos/seed/fe-yoga-hero/800/800', 0, now() - interval '5 days'), " +
            "('90000000-0000-4000-a001-000000000114', '90000000-0000-4000-8001-000000000114', null, 'https://picsum.photos/seed/fe-backpack-hero/800/800', 0, now() - interval '4 days'), " +
            "('90000000-0000-4000-a001-000000000115', '90000000-0000-4000-8001-000000000115', null, 'https://picsum.photos/seed/fe-earbuds-hero/800/800', 0, now() - interval '3 days') " +
            "ON CONFLICT (id) DO UPDATE SET product_id=EXCLUDED.product_id,variant_id=EXCLUDED.variant_id,url=EXCLUDED.url,sort_order=EXCLUDED.sort_order");

        // ========================================================================
        // 5. Wishlist: fe_buyer (900001) wishlists products 101, 103, 104
        // ========================================================================
        jdbcTemplate.update("INSERT INTO product.wishlist_items (customer_id, product_id, created_at) VALUES " +
            "(900001, '90000000-0000-4000-8001-000000000101', now() - interval '10 days'), " +
            "(900001, '90000000-0000-4000-8001-000000000103', now() - interval '8 days'), " +
            "(900001, '90000000-0000-4000-8001-000000000104', now() - interval '6 days') " +
            "ON CONFLICT DO NOTHING");

        // ========================================================================
        // 6. Cart: fe_buyer (900001) has product 102 x1, product 104 x2
        // ========================================================================
        // Look up variant price/name/image snapshots from the seeded variants
        jdbcTemplate.update("INSERT INTO product.cart_items (customer_id, variant_id, quantity, price_snapshot, variant_name_snapshot, variant_image_snapshot, seller_id, created_at, updated_at) " +
            "SELECT 900001, id, 1, price, variant_name, image_url, 900002, now() - interval '5 days', now() " +
            "FROM product.product_variants WHERE id = '90000000-0000-4000-9001-000000000102' " +
            "ON CONFLICT DO NOTHING");
        jdbcTemplate.update("INSERT INTO product.cart_items (customer_id, variant_id, quantity, price_snapshot, variant_name_snapshot, variant_image_snapshot, seller_id, created_at, updated_at) " +
            "SELECT 900001, id, 2, price, variant_name, image_url, 900002, now() - interval '4 days', now() " +
            "FROM product.product_variants WHERE id = '90000000-0000-4000-9001-000000000104' " +
            "ON CONFLICT DO NOTHING");

        log.info("[ProductDevDataLoader] FE test-dataset seeded (6 categories, 15 products, 18 variants, 15 images, 3 wishlist items, 2 cart items).");
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
