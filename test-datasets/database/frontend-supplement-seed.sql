-- ===========================================================================
-- Frontend supplement seed — bổ sung business flows còn thiếu
-- Target: accounts fe_buyer (900001), fe_seller (900002), fe_admin (900003)
-- Works independently — reference existing data from dev-data-loader.
-- Idempotent: rerun là UPDATE/upsert.
-- ===========================================================================
BEGIN;

-- ===========================================================================
-- 1. Wishlist — fe_buyer đã thả tim 3 sản phẩm
-- Wishlist uses composite PK (customer_id, product_id), không có id column.
-- ===========================================================================
INSERT INTO product.wishlist_items (customer_id, product_id, created_at)
SELECT 900001, p.id, now() - interval '10 days'
FROM product.products p WHERE p.slug = 'iphone-15-black-128'
  AND NOT EXISTS (SELECT 1 FROM product.wishlist_items WHERE customer_id = 900001 AND product_id = p.id);

INSERT INTO product.wishlist_items (customer_id, product_id, created_at)
SELECT 900001, p.id, now() - interval '5 days'
FROM product.products p WHERE p.slug = 'airpods-pro-2'
  AND NOT EXISTS (SELECT 1 FROM product.wishlist_items WHERE customer_id = 900001 AND product_id = p.id);

INSERT INTO product.wishlist_items (customer_id, product_id, created_at)
SELECT 900001, p.id, now() - interval '2 days'
FROM product.products p WHERE p.slug = 'hub-usb-c-7in1'
  AND NOT EXISTS (SELECT 1 FROM product.wishlist_items WHERE customer_id = 900001 AND product_id = p.id);

-- ===========================================================================
-- 2. Multi-seller cart — thêm MacBook Air vào cart fe_buyer
-- ===========================================================================
INSERT INTO product.cart_items
    (customer_id, variant_id, quantity, price_snapshot, variant_name_snapshot,
     variant_image_snapshot, seller_id, created_at, updated_at)
SELECT 900001, pv.id, 1, pv.price, pv.variant_name,
       pv.image_url, pp.seller_id, now() - interval '12 hours', now()
FROM product.product_variants pv
JOIN product.products pp ON pp.id = pv.product_id
WHERE pv.variant_code = 'SKU-MACBOOK-AIR-M3'
  AND NOT EXISTS (
    SELECT 1 FROM product.cart_items ci
    WHERE ci.customer_id = 900001 AND ci.variant_id = pv.id
  );

-- ===========================================================================
-- 3. Multi-variant product — dùng sản phẩm "Áo thun basic cotton nam"
--    (seed từ dev-data-loader với 4 variants S/M/L/XL)
--    Thêm thêm 1 cart item và 1 wishlist item cho variant nhỏ nhất
-- ===========================================================================
INSERT INTO product.cart_items
    (customer_id, variant_id, quantity, price_snapshot, variant_name_snapshot,
     variant_image_snapshot, seller_id, created_at, updated_at)
SELECT 900001, pv.id, 2, pv.price, pv.variant_name,
       pv.image_url, pp.seller_id, now() - interval '6 hours', now()
FROM product.product_variants pv
JOIN product.products pp ON pp.id = pv.product_id
WHERE pv.variant_code = 'SKU-TSHIRT-M'
  AND NOT EXISTS (
    SELECT 1 FROM product.cart_items ci
    WHERE ci.customer_id = 900001 AND ci.variant_id = pv.id
  );

INSERT INTO product.wishlist_items (customer_id, product_id, created_at)
SELECT 900001, pp.id, now() - interval '1 day'
FROM product.products pp
WHERE pp.slug = 'ao-thun-basic-cotton'
  AND NOT EXISTS (SELECT 1 FROM product.wishlist_items WHERE customer_id = 900001 AND product_id = pp.id);

-- ===========================================================================
-- 4. FE test products — active + pending + rejected để test seller & admin flows
-- ===========================================================================
INSERT INTO product.products
    (name, slug, category_id, seller_id, description, attributes, status,
     reject_reason, reject_count, submitted_at, created_at, updated_at, published_at)
VALUES
    ('FE Supplement Wireless Charger', 'fe-supp-wireless-charger',
     (SELECT id FROM product.categories WHERE slug = 'accessories' LIMIT 1), 900002,
     'Wireless charger for E2E supplement test.',
     '{"brand":"FE","coverage":["supplement-search"]}'::jsonb,
     'ACTIVE', null, 0,
     now() - interval '10 days', now() - interval '10 days', now(), now() - interval '10 days'),

    ('FE Supplement Pending Watch', 'fe-supp-pending-watch',
     (SELECT id FROM product.categories WHERE slug = 'wearables' LIMIT 1), 900002,
     'Pending product for admin approve flow (supplement).',
     '{"brand":"FE","coverage":["supplement-pending","admin-approve"]}'::jsonb,
     'PENDING', null, 0,
     now() - interval '2 days', now() - interval '5 days', now(), null)
ON CONFLICT (slug) DO UPDATE SET status = EXCLUDED.status, updated_at = now();

-- Variant for FE Supplement Wireless Charger
INSERT INTO product.product_variants
    (product_id, variant_code, variant_name, variant_attributes, price, original_price,
     stock_quantity, status, version, image_url, created_at, updated_at)
SELECT p.id, 'FE-SKU-SUPP-CHARGER', 'Standard',
       '{"color":"white"}'::jsonb, 490000, 690000, 100, 'ACTIVE', 1,
       'https://picsum.photos/seed/fe-supp-charger/500/500',
       now() - interval '10 days', now()
FROM product.products p WHERE p.slug = 'fe-supp-wireless-charger'
  AND NOT EXISTS (
    SELECT 1 FROM product.product_variants WHERE variant_code = 'FE-SKU-SUPP-CHARGER'
  );

-- Variant for FE Supplement Pending Watch
INSERT INTO product.product_variants
    (product_id, variant_code, variant_name, variant_attributes, price, original_price,
     stock_quantity, status, version, image_url, created_at, updated_at)
SELECT p.id, 'FE-SKU-SUPP-WATCH', 'Standard',
       '{}'::jsonb, 2490000, 2990000, 30, 'ACTIVE', 1,
       'https://picsum.photos/seed/fe-supp-watch/500/500',
       now() - interval '5 days', now()
FROM product.products p WHERE p.slug = 'fe-supp-pending-watch'
  AND NOT EXISTS (
    SELECT 1 FROM product.product_variants WHERE variant_code = 'FE-SKU-SUPP-WATCH'
  );

-- Images
INSERT INTO product.product_images (product_id, variant_id, url, sort_order, created_at)
SELECT p.id, null, 'https://picsum.photos/seed/fe-supp-charger/800/800', 0, now()
FROM product.products p WHERE p.slug = 'fe-supp-wireless-charger'
  AND NOT EXISTS (SELECT 1 FROM product.product_images pi WHERE pi.product_id = p.id);

INSERT INTO product.product_images (product_id, variant_id, url, sort_order, created_at)
SELECT p.id, null, 'https://picsum.photos/seed/fe-supp-watch/800/800', 0, now()
FROM product.products p WHERE p.slug = 'fe-supp-pending-watch'
  AND NOT EXISTS (SELECT 1 FROM product.product_images pi WHERE pi.product_id = p.id);

-- ===========================================================================
-- 5. Stock reservation — fe_buyer có 1 reservation ACTIVE
--    Dùng variant SKU-IPHONE-BLK-128 (từ dev-data-loader)
-- ===========================================================================
INSERT INTO product.stock_reservations
    (variant_id, session_id, quantity, status, expires_at, created_at)
SELECT pv.id, 'fe-supp-session-pending', 1, 'ACTIVE', now() + interval '15 minutes', now() - interval '1 hour'
FROM product.product_variants pv WHERE pv.variant_code = 'SKU-IPHONE-BLK-128'
  AND NOT EXISTS (SELECT 1 FROM product.stock_reservations sr WHERE sr.variant_id = pv.id);

-- ===========================================================================
-- 6. Thêm dữ liệu cho seller dashboard: 1 order completed thêm
--    fe_seller bán thêm được 1 đơn nữa
-- ===========================================================================
-- (Orders cần nhiều schema phức tạp — tạm skip, main seed đã cover lifecycle)

-- ===========================================================================
-- 7. Thông báo kết quả
-- ===========================================================================
DO $$
DECLARE
    w_count INT; c_count INT; feat_count INT;
BEGIN
    SELECT COUNT(*) INTO w_count FROM product.wishlist_items WHERE customer_id = 900001;
    SELECT COUNT(*) INTO c_count FROM product.cart_items WHERE customer_id = 900001;
    SELECT COUNT(*) INTO feat_count FROM product.products WHERE slug LIKE 'fe-supp-%';
    RAISE INFO '[Supplement] Wishlist: % | Cart: % | FE products: %', w_count, c_count, feat_count;
END;
$$;

COMMIT;
