-- Frontend full-flow seed dataset.
-- Target database: flashsale_platform
-- Target schemas: identity, product, orders, payment, refund, flashsale.
--
-- The ID range 900xxx is reserved for this dataset so it can coexist with the
-- service dev-data loaders. The script is idempotent: reruns update the same
-- deterministic rows instead of duplicating them.

BEGIN;

-- ---------------------------------------------------------------------------
-- Identity accounts
-- Password for all frontend test accounts: dev123
-- ---------------------------------------------------------------------------

INSERT INTO identity.users
    (id, username, email, phone, password, full_name, status, role, created_at, updated_at)
VALUES
    (900001, 'fe_buyer',  'fe_buyer@example.test',  '0999000001',
     '$2a$10$S7ysf7nhlEneSxrI7gCTZuo8EX4KvK1jBd.NZ/1X7P9ZQ85agy1Zi',
     'Frontend Buyer', 'ACTIVE', 'BUYER', now() - interval '20 days', now()),
    (900002, 'fe_seller', 'fe_seller@example.test', '0999000002',
     '$2a$10$S7ysf7nhlEneSxrI7gCTZuo8EX4KvK1jBd.NZ/1X7P9ZQ85agy1Zi',
     'Frontend Seller', 'ACTIVE', 'SELLER', now() - interval '19 days', now()),
    (900003, 'fe_admin',  'fe_admin@example.test',  '0999000003',
     '$2a$10$S7ysf7nhlEneSxrI7gCTZuo8EX4KvK1jBd.NZ/1X7P9ZQ85agy1Zi',
     'Frontend Admin', 'ACTIVE', 'ADMIN', now() - interval '18 days', now())
ON CONFLICT (id) DO UPDATE SET
    username = EXCLUDED.username,
    email = EXCLUDED.email,
    phone = EXCLUDED.phone,
    password = EXCLUDED.password,
    full_name = EXCLUDED.full_name,
    status = EXCLUDED.status,
    role = EXCLUDED.role,
    updated_at = now();

INSERT INTO identity.roles (id, user_id, role_name, created_at, updated_at)
VALUES
    (900001, 900001, 'BUYER',  now() - interval '20 days', now()),
    (900002, 900002, 'SELLER', now() - interval '19 days', now()),
    (900003, 900003, 'ADMIN',  now() - interval '18 days', now())
ON CONFLICT (id) DO UPDATE SET
    user_id = EXCLUDED.user_id,
    role_name = EXCLUDED.role_name,
    updated_at = now();

INSERT INTO identity.addresses
    (id, user_id, province_id, district_id, full_address, is_default, created_at, updated_at)
VALUES
    (900001, 900001, 79, 760, '123 Frontend Test Street, District 1, Ho Chi Minh City', true,  now() - interval '20 days', now()),
    (900002, 900001, 1,  1,   '456 Backup Address, Ba Dinh, Ha Noi', false, now() - interval '19 days', now()),
    (900003, 900002, 79, 761, 'FE Seller Warehouse, District 3, Ho Chi Minh City', true, now() - interval '18 days', now()),
    (900004, 900001, 48, 490, '789 Da Nang Office, Hai Chau, Da Nang', false, now() - interval '17 days', now()),
    (900005, 900002, 1,  4,   'FE Seller Return Center, Dong Da, Ha Noi', false, now() - interval '16 days', now())
ON CONFLICT (id) DO UPDATE SET
    user_id = EXCLUDED.user_id,
    province_id = EXCLUDED.province_id,
    district_id = EXCLUDED.district_id,
    full_address = EXCLUDED.full_address,
    is_default = EXCLUDED.is_default,
    updated_at = now();

-- ---------------------------------------------------------------------------
-- Product catalog, seller lifecycle states, and buyer cart
-- ---------------------------------------------------------------------------

INSERT INTO product.categories
    (id, parent_id, name, slug, description, image_url, sort_order, is_active, created_at, updated_at)
VALUES
    ('90000000-0000-4000-8000-000000000001', null, 'FE Electronics', 'fe-electronics',
     'Frontend fixture electronics root category', 'https://picsum.photos/seed/fe-electronics/600/400', 10, true, now() - interval '16 days', now()),
    ('90000000-0000-4000-8000-000000000002', '90000000-0000-4000-8000-000000000001', 'FE Phones', 'fe-phones',
     'Frontend fixture phone category', 'https://picsum.photos/seed/fe-phones/600/400', 11, true, now() - interval '16 days', now()),
    ('90000000-0000-4000-8000-000000000003', '90000000-0000-4000-8000-000000000001', 'FE Audio', 'fe-audio',
     'Frontend fixture audio category', 'https://picsum.photos/seed/fe-audio/600/400', 12, true, now() - interval '16 days', now()),
    ('90000000-0000-4000-8000-000000000004', '90000000-0000-4000-8000-000000000001', 'FE Laptops', 'fe-laptops',
     'Frontend fixture laptop category', 'https://picsum.photos/seed/fe-laptops/600/400', 13, true, now() - interval '16 days', now()),
    ('90000000-0000-4000-8000-000000000005', null, 'FE Home', 'fe-home',
     'Frontend fixture home category', 'https://picsum.photos/seed/fe-home/600/400', 14, true, now() - interval '16 days', now()),
    ('90000000-0000-4000-8000-000000000006', null, 'FE Fashion', 'fe-fashion',
     'Frontend fixture fashion category', 'https://picsum.photos/seed/fe-fashion/600/400', 15, true, now() - interval '16 days', now())
ON CONFLICT (id) DO UPDATE SET
    parent_id = EXCLUDED.parent_id,
    name = EXCLUDED.name,
    slug = EXCLUDED.slug,
    description = EXCLUDED.description,
    image_url = EXCLUDED.image_url,
    sort_order = EXCLUDED.sort_order,
    is_active = EXCLUDED.is_active,
    updated_at = now();

INSERT INTO product.products
    (id, category_id, seller_id, name, slug, description, attributes, status,
     reject_reason, reviewed_at, reviewed_by, reject_count, submitted_at,
     created_at, updated_at, published_at)
VALUES
    ('90000000-0000-4000-8001-000000000101', '90000000-0000-4000-8000-000000000002', 900002,
     'FE Phone Pro Camera Kit', 'fe-phone-pro-camera-kit',
     'Active catalog product for search, detail, cart, checkout, and flash-sale tests.',
     '{"brand":"FE","screen":"6.1 inch","coverage":["catalog","search","cart","checkout"]}'::jsonb,
     'ACTIVE', null, now() - interval '14 days', 900003, 0, now() - interval '15 days',
     now() - interval '15 days', now(), now() - interval '14 days'),
    ('90000000-0000-4000-8001-000000000102', '90000000-0000-4000-8000-000000000004', 900002,
     'FE MacBook Air M3 Demo', 'fe-macbook-air-m3-demo',
     'High-value seller product for payment and payout screens.',
     '{"brand":"FE","ram":"16GB","coverage":["seller-payments","search"]}'::jsonb,
     'ACTIVE', null, now() - interval '13 days', 900003, 0, now() - interval '14 days',
     now() - interval '14 days', now(), now() - interval '13 days'),
    ('90000000-0000-4000-8001-000000000103', '90000000-0000-4000-8000-000000000003', 900002,
     'FE AirPods Flash Combo', 'fe-airpods-flash-combo',
     'Active product with live flash-sale mapping.',
     '{"brand":"FE","noiseCancellation":true,"coverage":["flash-sale","search"]}'::jsonb,
     'ACTIVE', null, now() - interval '12 days', 900003, 0, now() - interval '13 days',
     now() - interval '13 days', now(), now() - interval '12 days'),
    ('90000000-0000-4000-8001-000000000104', '90000000-0000-4000-8000-000000000001', 900002,
     'FE USB-C Hub 8-in-1', 'fe-usb-c-hub-8-in-1',
     'Low-price add-to-cart product for quantity update and remove tests.',
     '{"brand":"FE","ports":8,"coverage":["cart-update","cart-remove"]}'::jsonb,
     'ACTIVE', null, now() - interval '11 days', 900003, 0, now() - interval '12 days',
     now() - interval '12 days', now(), now() - interval '11 days'),
    ('90000000-0000-4000-8001-000000000105', '90000000-0000-4000-8000-000000000006', 900002,
     'FE Pending Review Backpack', 'fe-pending-review-backpack',
     'Pending product for admin moderation list and approve flow.',
     '{"brand":"FE","coverage":["admin-products-pending","approve-product"]}'::jsonb,
     'PENDING', null, null, null, 0, now() - interval '2 days',
     now() - interval '5 days', now(), null),
    ('90000000-0000-4000-8001-000000000106', '90000000-0000-4000-8000-000000000006', 900002,
     'FE Rejected Sample Bag', 'fe-rejected-sample-bag',
     'Rejected product for seller edit/resubmit and admin reject display.',
     '{"brand":"FE","coverage":["rejected-product","resubmit-product"]}'::jsonb,
     'REJECTED', 'Missing real product images and warranty details.', now() - interval '3 days', 900003, 1, now() - interval '4 days',
     now() - interval '6 days', now(), null),
    ('90000000-0000-4000-8001-000000000107', '90000000-0000-4000-8000-000000000005', 900002,
     'FE Draft Smart Lamp', 'fe-draft-smart-lamp',
     'Draft seller product for submit-for-review flow.',
     '{"brand":"FE","coverage":["submit-product-review"]}'::jsonb,
     'DRAFT', null, null, null, 0, null,
     now() - interval '3 days', now(), null),
    ('90000000-0000-4000-8001-000000000108', '90000000-0000-4000-8000-000000000005', 900002,
     'FE Approved Robot Vacuum', 'fe-approved-robot-vacuum',
     'Approved but unpublished seller product for publish flow.',
     '{"brand":"FE","coverage":["publish-product"]}'::jsonb,
     'APPROVED', null, now() - interval '2 days', 900003, 0, now() - interval '3 days',
     now() - interval '4 days', now(), null),
    ('90000000-0000-4000-8001-000000000109', '90000000-0000-4000-8000-000000000003', 900002,
     'FE Out Of Stock Headphone', 'fe-out-of-stock-headphone',
     'In-stock product for inventory/restock tests.',
     '{"brand":"FE","coverage":["inventory","restock"]}'::jsonb,
     'ACTIVE', null, now() - interval '8 days', 900003, 0, now() - interval '9 days',
     now() - interval '9 days', now(), now() - interval '8 days'),
    ('90000000-0000-4000-8001-000000000110', '90000000-0000-4000-8000-000000000005', 900002,
     'FE Inactive Desk Setup', 'fe-inactive-desk-setup',
     'Inactive product for seller unpublish/publish regression.',
     '{"brand":"FE","coverage":["unpublish-product","inactive-product"]}'::jsonb,
     'INACTIVE', null, now() - interval '7 days', 900003, 0, now() - interval '8 days',
     now() - interval '8 days', now(), null)
ON CONFLICT (id) DO UPDATE SET
    category_id = EXCLUDED.category_id,
    seller_id = EXCLUDED.seller_id,
    name = EXCLUDED.name,
    slug = EXCLUDED.slug,
    description = EXCLUDED.description,
    attributes = EXCLUDED.attributes,
    status = EXCLUDED.status,
    reject_reason = EXCLUDED.reject_reason,
    reviewed_at = EXCLUDED.reviewed_at,
    reviewed_by = EXCLUDED.reviewed_by,
    reject_count = EXCLUDED.reject_count,
    submitted_at = EXCLUDED.submitted_at,
    updated_at = now(),
    published_at = EXCLUDED.published_at;

INSERT INTO product.product_variants
    (id, product_id, variant_code, variant_name, variant_attributes, price, original_price,
     stock_quantity, status, version, image_url, created_at, updated_at)
VALUES
    ('90000000-0000-4000-9001-000000000101', '90000000-0000-4000-8001-000000000101',
     'FE-SKU-PHONE-15PRO', 'Black / 256GB', '{"color":"black","storage":"256GB"}'::jsonb,
     23990000, 25990000, 25, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-phone-15pro/500/500', now() - interval '15 days', now()),
    ('90000000-0000-4000-9001-000000000102', '90000000-0000-4000-8001-000000000102',
     'FE-SKU-LAPTOP-M3', 'Space Gray / 16GB', '{"color":"gray","ram":"16GB"}'::jsonb,
     27990000, 31990000, 12, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-laptop-m3/500/500', now() - interval '14 days', now()),
    ('90000000-0000-4000-9001-000000000103', '90000000-0000-4000-8001-000000000103',
     'FE-SKU-AIRPODS-COMBO', 'USB-C Combo', '{"connector":"USB-C"}'::jsonb,
     4990000, 6490000, 60, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-airpods-combo/500/500', now() - interval '13 days', now()),
    ('90000000-0000-4000-9001-000000000104', '90000000-0000-4000-8001-000000000104',
     'FE-SKU-HUB-8IN1', 'Silver / 8 ports', '{"color":"silver","ports":8}'::jsonb,
     790000, 990000, 150, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-hub-8in1/500/500', now() - interval '12 days', now()),
    ('90000000-0000-4000-9001-000000000105', '90000000-0000-4000-8001-000000000105',
     'FE-SKU-PENDING-BACKPACK', 'Default', '{}'::jsonb,
     690000, 890000, 40, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-pending-backpack/500/500', now() - interval '5 days', now()),
    ('90000000-0000-4000-9001-000000000106', '90000000-0000-4000-8001-000000000106',
     'FE-SKU-REJECTED-BAG', 'Default', '{}'::jsonb,
     590000, 790000, 35, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-rejected-bag/500/500', now() - interval '6 days', now()),
    ('90000000-0000-4000-9001-000000000107', '90000000-0000-4000-8001-000000000107',
     'FE-SKU-DRAFT-LAMP', 'Warm White', '{"color":"warm-white"}'::jsonb,
     450000, 550000, 20, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-draft-lamp/500/500', now() - interval '3 days', now()),
    ('90000000-0000-4000-9001-000000000108', '90000000-0000-4000-8001-000000000108',
     'FE-SKU-APPROVED-VACUUM', 'Standard', '{}'::jsonb,
     3890000, 4590000, 18, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-approved-vacuum/500/500', now() - interval '4 days', now()),
    ('90000000-0000-4000-9001-000000000109', '90000000-0000-4000-8001-000000000109',
     'FE-SKU-OOS-HEADPHONE', 'Midnight', '{"color":"midnight"}'::jsonb,
     1290000, 1590000, 15, 'ACTIVE', 1, 'https://picsum.photos/seed/fe-oos-headphone/500/500', now() - interval '9 days', now()),
    ('90000000-0000-4000-9001-000000000110', '90000000-0000-4000-8001-000000000110',
     'FE-SKU-INACTIVE-DESK', 'Default', '{}'::jsonb,
     1990000, 2490000, 10, 'INACTIVE', 1, 'https://picsum.photos/seed/fe-inactive-desk/500/500', now() - interval '8 days', now())
ON CONFLICT (id) DO UPDATE SET
    product_id = EXCLUDED.product_id,
    variant_code = EXCLUDED.variant_code,
    variant_name = EXCLUDED.variant_name,
    variant_attributes = EXCLUDED.variant_attributes,
    price = EXCLUDED.price,
    original_price = EXCLUDED.original_price,
    stock_quantity = EXCLUDED.stock_quantity,
    status = EXCLUDED.status,
    version = EXCLUDED.version,
    image_url = EXCLUDED.image_url,
    updated_at = now();

INSERT INTO product.product_images (id, product_id, variant_id, url, sort_order, created_at)
VALUES
    ('90000000-0000-4000-a001-000000000101', '90000000-0000-4000-8001-000000000101', null, 'https://picsum.photos/seed/fe-phone-hero/800/800', 0, now() - interval '15 days'),
    ('90000000-0000-4000-a001-000000000102', '90000000-0000-4000-8001-000000000102', null, 'https://picsum.photos/seed/fe-laptop-hero/800/800', 0, now() - interval '14 days'),
    ('90000000-0000-4000-a001-000000000103', '90000000-0000-4000-8001-000000000103', null, 'https://picsum.photos/seed/fe-airpods-hero/800/800', 0, now() - interval '13 days'),
    ('90000000-0000-4000-a001-000000000104', '90000000-0000-4000-8001-000000000104', null, 'https://picsum.photos/seed/fe-hub-hero/800/800', 0, now() - interval '12 days'),
    ('90000000-0000-4000-a001-000000000105', '90000000-0000-4000-8001-000000000105', null, 'https://picsum.photos/seed/fe-pending-hero/800/800', 0, now() - interval '5 days'),
    ('90000000-0000-4000-a001-000000000106', '90000000-0000-4000-8001-000000000106', null, 'https://picsum.photos/seed/fe-rejected-hero/800/800', 0, now() - interval '6 days'),
    ('90000000-0000-4000-a001-000000000107', '90000000-0000-4000-8001-000000000107', null, 'https://picsum.photos/seed/fe-draft-hero/800/800', 0, now() - interval '3 days'),
    ('90000000-0000-4000-a001-000000000108', '90000000-0000-4000-8001-000000000108', null, 'https://picsum.photos/seed/fe-vacuum-hero/800/800', 0, now() - interval '4 days'),
    ('90000000-0000-4000-a001-000000000109', '90000000-0000-4000-8001-000000000109', null, 'https://picsum.photos/seed/fe-oos-hero/800/800', 0, now() - interval '9 days'),
    ('90000000-0000-4000-a001-000000000110', '90000000-0000-4000-8001-000000000110', null, 'https://picsum.photos/seed/fe-desk-hero/800/800', 0, now() - interval '8 days')
ON CONFLICT (id) DO UPDATE SET
    product_id = EXCLUDED.product_id,
    variant_id = EXCLUDED.variant_id,
    url = EXCLUDED.url,
    sort_order = EXCLUDED.sort_order;

INSERT INTO product.carts (id, customer_id, status, created_at, updated_at)
VALUES
    ('90000000-0000-4000-b001-000000000001', 900001, 'ACTIVE', now() - interval '1 day', now())
ON CONFLICT (customer_id) DO UPDATE SET
    status = EXCLUDED.status,
    updated_at = now();

INSERT INTO product.cart_items
    (customer_id, variant_id, quantity, price_snapshot, variant_name_snapshot,
     variant_image_snapshot, seller_id, created_at, updated_at)
VALUES
    (900001, '90000000-0000-4000-9001-000000000101', 1, 23990000, 'Black / 256GB',
     'https://picsum.photos/seed/fe-phone-15pro/500/500', 900002, now() - interval '1 day', now()),
    (900001, '90000000-0000-4000-9001-000000000104', 2, 790000, 'Silver / 8 ports',
     'https://picsum.photos/seed/fe-hub-8in1/500/500', 900002, now() - interval '1 day', now())
ON CONFLICT (customer_id, variant_id) DO UPDATE SET
    quantity = EXCLUDED.quantity,
    price_snapshot = EXCLUDED.price_snapshot,
    variant_name_snapshot = EXCLUDED.variant_name_snapshot,
    variant_image_snapshot = EXCLUDED.variant_image_snapshot,
    seller_id = EXCLUDED.seller_id,
    updated_at = now();

-- ---------------------------------------------------------------------------
-- Orders covering frontend-visible lifecycle states
-- ---------------------------------------------------------------------------

INSERT INTO orders.parent_orders (id, customer_id, session_id, total_amt, final_amt, created_at, updated_at)
VALUES
    (900101, 900001, 'fe-session-pending',       23990000, 23990000, now() - interval '1 hour',  now()),
    (900102, 900001, 'fe-session-paid',          23990000, 23990000, now() - interval '1 day',   now()),
    (900103, 900001, 'fe-session-shipping',       4990000,  4990000, now() - interval '2 days',  now()),
    (900104, 900001, 'fe-session-delivered',       790000,   790000, now() - interval '4 days',  now()),
    (900105, 900001, 'fe-session-cancelled',       790000,   790000, now() - interval '5 days',  now()),
    (900106, 900001, 'fe-session-partial-refund', 4990000,  4990000, now() - interval '6 days',  now()),
    (900107, 900001, 'fe-session-refunded',       4990000,  4990000, now() - interval '7 days',  now()),
    (900108, 900001, 'fe-session-returned',      27990000, 27990000, now() - interval '8 days',  now()),
    (900109, 900001, 'fe-session-paid-out',      27990000, 27990000, now() - interval '20 days', now())
ON CONFLICT (id) DO UPDATE SET
    customer_id = EXCLUDED.customer_id,
    session_id = EXCLUDED.session_id,
    total_amt = EXCLUDED.total_amt,
    final_amt = EXCLUDED.final_amt,
    updated_at = now();

INSERT INTO orders.orders
    (id, parent_order_id, seller_id, order_code, customer_id, total_amt, final_amt,
     status, cancelled_by, cancel_reason, is_flash_sale, shipping_address,
     tracking_number, shipping_deadline, delivered_at, version, created_at, updated_at)
VALUES
    (900101, 900101, 900002, 'FE-ORD-PENDING-900101', 900001, 23990000, 23990000,
     'PENDING', null, null, false,
     '{"full_address":"123 Frontend Test Street, District 1, Ho Chi Minh City","province_id":79,"district_id":760}'::jsonb,
     null, now() + interval '3 days', null, 0, now() - interval '1 hour', now()),
    (900102, 900102, 900002, 'FE-ORD-PAID-900102', 900001, 23990000, 23990000,
     'PAID', null, null, false,
     '{"full_address":"123 Frontend Test Street, District 1, Ho Chi Minh City","province_id":79,"district_id":760}'::jsonb,
     null, now() + interval '3 days', null, 0, now() - interval '1 day', now()),
    (900103, 900103, 900002, 'FE-ORD-SHIPPING-900103', 900001, 4990000, 4990000,
     'SHIPPING', null, null, true,
     '{"full_address":"123 Frontend Test Street, District 1, Ho Chi Minh City","province_id":79,"district_id":760}'::jsonb,
     'FE-GHN-900103', now() + interval '2 days', null, 0, now() - interval '2 days', now()),
    (900104, 900104, 900002, 'FE-ORD-DELIVERED-900104', 900001, 790000, 790000,
     'DELIVERED', null, null, false,
     '{"full_address":"123 Frontend Test Street, District 1, Ho Chi Minh City","province_id":79,"district_id":760}'::jsonb,
     'FE-VNPOST-900104', now() - interval '1 day', now() - interval '2 days', 0, now() - interval '4 days', now()),
    (900105, 900105, 900002, 'FE-ORD-CANCELLED-900105', 900001, 790000, 790000,
     'CANCELLED', 'BUYER', 'Changed mind before shipment', false,
     '{"full_address":"123 Frontend Test Street, District 1, Ho Chi Minh City","province_id":79,"district_id":760}'::jsonb,
     null, null, null, 0, now() - interval '5 days', now()),
    (900106, 900106, 900002, 'FE-ORD-PARTIAL-REFUND-900106', 900001, 4990000, 4990000,
     'PARTIALLY_REFUNDED', null, null, false,
     '{"full_address":"123 Frontend Test Street, District 1, Ho Chi Minh City","province_id":79,"district_id":760}'::jsonb,
     'FE-GHTK-900106', now() - interval '2 days', now() - interval '3 days', 0, now() - interval '6 days', now()),
    (900107, 900107, 900002, 'FE-ORD-REFUNDED-900107', 900001, 4990000, 4990000,
     'REFUNDED', null, null, false,
     '{"full_address":"123 Frontend Test Street, District 1, Ho Chi Minh City","province_id":79,"district_id":760}'::jsonb,
     'FE-GHTK-900107', now() - interval '3 days', now() - interval '4 days', 0, now() - interval '7 days', now()),
    (900108, 900108, 900002, 'FE-ORD-RETURNED-900108', 900001, 27990000, 27990000,
     'RETURNED', null, null, false,
     '{"full_address":"123 Frontend Test Street, District 1, Ho Chi Minh City","province_id":79,"district_id":760}'::jsonb,
     'FE-GHN-900108', now() - interval '4 days', now() - interval '5 days', 0, now() - interval '8 days', now()),
    (900109, 900109, 900002, 'FE-ORD-PAIDOUT-900109', 900001, 27990000, 27990000,
     'DELIVERED', null, null, false,
     '{"full_address":"123 Frontend Test Street, District 1, Ho Chi Minh City","province_id":79,"district_id":760}'::jsonb,
     'FE-GHN-900109', now() - interval '10 days', now() - interval '12 days', 0, now() - interval '20 days', now())
ON CONFLICT (id) DO UPDATE SET
    parent_order_id = EXCLUDED.parent_order_id,
    seller_id = EXCLUDED.seller_id,
    order_code = EXCLUDED.order_code,
    customer_id = EXCLUDED.customer_id,
    total_amt = EXCLUDED.total_amt,
    final_amt = EXCLUDED.final_amt,
    status = EXCLUDED.status,
    cancelled_by = EXCLUDED.cancelled_by,
    cancel_reason = EXCLUDED.cancel_reason,
    is_flash_sale = EXCLUDED.is_flash_sale,
    shipping_address = EXCLUDED.shipping_address,
    tracking_number = EXCLUDED.tracking_number,
    shipping_deadline = EXCLUDED.shipping_deadline,
    delivered_at = EXCLUDED.delivered_at,
    version = EXCLUDED.version,
    updated_at = now();

INSERT INTO orders.order_items
    (id, order_id, sku_code, variant_id, name_snapshot, image_snapshot,
     price_snapshot, quantity, refunded_quantity, fs_item_id, created_at)
VALUES
    (900101, 900101, 'FE-SKU-PHONE-15PRO', '90000000-0000-4000-9001-000000000101',
     'FE Phone Pro Camera Kit', 'https://picsum.photos/seed/fe-phone-15pro/500/500', 23990000, 1, 0, null, now() - interval '1 hour'),
    (900102, 900102, 'FE-SKU-PHONE-15PRO', '90000000-0000-4000-9001-000000000101',
     'FE Phone Pro Camera Kit', 'https://picsum.photos/seed/fe-phone-15pro/500/500', 23990000, 1, 0, null, now() - interval '1 day'),
    (900103, 900103, 'FE-SKU-AIRPODS-COMBO', '90000000-0000-4000-9001-000000000103',
     'FE AirPods Flash Combo', 'https://picsum.photos/seed/fe-airpods-combo/500/500', 4990000, 1, 0, 900001, now() - interval '2 days'),
    (900104, 900104, 'FE-SKU-HUB-8IN1', '90000000-0000-4000-9001-000000000104',
     'FE USB-C Hub 8-in-1', 'https://picsum.photos/seed/fe-hub-8in1/500/500', 790000, 1, 0, null, now() - interval '4 days'),
    (900105, 900105, 'FE-SKU-HUB-8IN1', '90000000-0000-4000-9001-000000000104',
     'FE USB-C Hub 8-in-1', 'https://picsum.photos/seed/fe-hub-8in1/500/500', 790000, 1, 0, null, now() - interval '5 days'),
    (900106, 900106, 'FE-SKU-AIRPODS-COMBO', '90000000-0000-4000-9001-000000000103',
     'FE AirPods Flash Combo', 'https://picsum.photos/seed/fe-airpods-combo/500/500', 4990000, 1, 1, null, now() - interval '6 days'),
    (900107, 900107, 'FE-SKU-AIRPODS-COMBO', '90000000-0000-4000-9001-000000000103',
     'FE AirPods Flash Combo', 'https://picsum.photos/seed/fe-airpods-combo/500/500', 4990000, 1, 1, null, now() - interval '7 days'),
    (900108, 900108, 'FE-SKU-LAPTOP-M3', '90000000-0000-4000-9001-000000000102',
     'FE MacBook Air M3 Demo', 'https://picsum.photos/seed/fe-laptop-m3/500/500', 27990000, 1, 1, null, now() - interval '8 days'),
    (900109, 900109, 'FE-SKU-LAPTOP-M3', '90000000-0000-4000-9001-000000000102',
     'FE MacBook Air M3 Demo', 'https://picsum.photos/seed/fe-laptop-m3/500/500', 27990000, 1, 0, null, now() - interval '20 days')
ON CONFLICT (id) DO UPDATE SET
    order_id = EXCLUDED.order_id,
    sku_code = EXCLUDED.sku_code,
    variant_id = EXCLUDED.variant_id,
    name_snapshot = EXCLUDED.name_snapshot,
    image_snapshot = EXCLUDED.image_snapshot,
    price_snapshot = EXCLUDED.price_snapshot,
    quantity = EXCLUDED.quantity,
    refunded_quantity = EXCLUDED.refunded_quantity,
    fs_item_id = EXCLUDED.fs_item_id;

-- ---------------------------------------------------------------------------
-- Payment, Stripe onboarding, seller earnings
-- ---------------------------------------------------------------------------

INSERT INTO payment.seller_stripe_accounts
    (id, seller_id, stripe_account_id, account_status, charges_enabled, payouts_enabled,
     details_submitted, onboarding_url, onboarding_url_expires_at, express_dashboard_url,
     created_at, updated_at)
VALUES
    (900002, 900002, 'acct_fe_seller_900002', 'ACTIVE', true, true, true,
     null, null, 'https://dashboard.stripe.com/test/connect/accounts/acct_fe_seller_900002',
     now() - interval '15 days', now()),
    (900003, 900003, 'acct_fe_admin_900003', 'REQUIREMENTS_DUE', false, false, false,
     'https://connect.stripe.com/setup/e/acct_fe_admin_900003', now() + interval '7 days', null,
     now() - interval '1 day', now())
ON CONFLICT (seller_id) DO UPDATE SET
    stripe_account_id = EXCLUDED.stripe_account_id,
    account_status = EXCLUDED.account_status,
    charges_enabled = EXCLUDED.charges_enabled,
    payouts_enabled = EXCLUDED.payouts_enabled,
    details_submitted = EXCLUDED.details_submitted,
    onboarding_url = EXCLUDED.onboarding_url,
    onboarding_url_expires_at = EXCLUDED.onboarding_url_expires_at,
    express_dashboard_url = EXCLUDED.express_dashboard_url,
    updated_at = now();

INSERT INTO payment.transactions
    (id, parent_order_id, amount, trans_ref, stripe_transfer_id, application_fee_amount,
     stripe_connect_mode, status, raw_response, pay_at, created_at, updated_at)
VALUES
    (900101, 900101, 23990000, 'FE-TX-PENDING-900101', null, 1199500, 'DESTINATION',
     'PENDING',
     '{"id":"pi_fe_pending_900101","object":"payment_intent","client_secret":"pi_fe_pending_900101_secret_test","status":"requires_payment_method"}'::jsonb,
     null, now() - interval '1 hour', now()),
    (900102, 900102, 23990000, 'FE-TX-PAID-900102', null, 1199500, 'DESTINATION',
     'PAID', '{"id":"pi_fe_paid_900102","object":"payment_intent","status":"succeeded"}'::jsonb,
     now() - interval '23 hours', now() - interval '1 day', now()),
    (900103, 900103, 4990000, 'FE-TX-PAID-900103', null, 249500, 'DESTINATION',
     'PAID', '{"id":"pi_fe_paid_900103","object":"payment_intent","status":"succeeded"}'::jsonb,
     now() - interval '2 days', now() - interval '2 days', now()),
    (900104, 900104, 790000, 'FE-TX-PAID-900104', null, 39500, 'DESTINATION',
     'PAID', '{"id":"pi_fe_paid_900104","object":"payment_intent","status":"succeeded"}'::jsonb,
     now() - interval '4 days', now() - interval '4 days', now()),
    (900105, 900105, 790000, 'FE-TX-FAILED-900105', null, 0, 'DESTINATION',
     'FAILED', '{"id":"pi_fe_failed_900105","object":"payment_intent","status":"canceled"}'::jsonb,
     null, now() - interval '5 days', now()),
    (900106, 900106, 4990000, 'FE-TX-PARTIAL-900106', null, 249500, 'DESTINATION',
     'PARTIALLY_REFUNDED', '{"id":"pi_fe_partial_900106","object":"payment_intent","status":"succeeded"}'::jsonb,
     now() - interval '6 days', now() - interval '6 days', now()),
    (900107, 900107, 4990000, 'FE-TX-REFUNDED-900107', null, 249500, 'DESTINATION',
     'REFUNDED', '{"id":"pi_fe_refunded_900107","object":"payment_intent","status":"succeeded"}'::jsonb,
     now() - interval '7 days', now() - interval '7 days', now()),
    (900108, 900108, 27990000, 'FE-TX-RETURNED-900108', null, 1399500, 'DESTINATION',
     'REFUNDED', '{"id":"pi_fe_returned_900108","object":"payment_intent","status":"succeeded"}'::jsonb,
     now() - interval '8 days', now() - interval '8 days', now()),
    (900109, 900109, 27990000, 'FE-TX-PAIDOUT-900109', 'tr_fe_paidout_900109', 1399500, 'DESTINATION',
     'PAID', '{"id":"pi_fe_paidout_900109","object":"payment_intent","status":"succeeded"}'::jsonb,
     now() - interval '20 days', now() - interval '20 days', now())
ON CONFLICT (id) DO UPDATE SET
    parent_order_id = EXCLUDED.parent_order_id,
    amount = EXCLUDED.amount,
    trans_ref = EXCLUDED.trans_ref,
    stripe_transfer_id = EXCLUDED.stripe_transfer_id,
    application_fee_amount = EXCLUDED.application_fee_amount,
    stripe_connect_mode = EXCLUDED.stripe_connect_mode,
    status = EXCLUDED.status,
    raw_response = EXCLUDED.raw_response,
    pay_at = EXCLUDED.pay_at,
    updated_at = now();

INSERT INTO payment.seller_transfers
    (id, order_id, parent_order_id, seller_id, transfer_amount, stripe_transfer_id, status,
     delivered_at, payout_eligible_at, platform_commission_amt, payout_at,
     payout_retry_count, created_at, updated_at)
VALUES
    (900102, 900102, 900102, 900002, 23990000, null, 'AWAITING_DELIVERY',
     null, null, 1199500, null, 0, now() - interval '23 hours', now()),
    (900103, 900103, 900103, 900002, 4990000, null, 'AWAITING_DELIVERY',
     null, null, 249500, null, 0, now() - interval '2 days', now()),
    (900104, 900104, 900104, 900002, 790000, null, 'RETURN_WINDOW',
     now() - interval '2 days', now() + interval '5 days', 39500, null, 0, now() - interval '4 days', now()),
    (900106, 900106, 900106, 900002, 4990000, null, 'READY_FOR_PAYOUT',
     now() - interval '10 days', now() - interval '3 days', 249500, null, 0, now() - interval '6 days', now()),
    (900107, 900107, 900107, 900002, 4990000, null, 'REFUNDED',
     now() - interval '4 days', now() + interval '3 days', 249500, null, 0, now() - interval '7 days', now()),
    (900108, 900108, 900108, 900002, 27990000, null, 'SKIPPED',
     now() - interval '5 days', now() + interval '2 days', 1399500, null, 0, now() - interval '8 days', now()),
    (900109, 900109, 900109, 900002, 27990000, 'tr_fe_paidout_900109', 'PAID_OUT',
     now() - interval '12 days', now() - interval '5 days', 1399500, now() - interval '4 days', 0, now() - interval '20 days', now())
ON CONFLICT (id) DO UPDATE SET
    order_id = EXCLUDED.order_id,
    parent_order_id = EXCLUDED.parent_order_id,
    seller_id = EXCLUDED.seller_id,
    transfer_amount = EXCLUDED.transfer_amount,
    stripe_transfer_id = EXCLUDED.stripe_transfer_id,
    status = EXCLUDED.status,
    delivered_at = EXCLUDED.delivered_at,
    payout_eligible_at = EXCLUDED.payout_eligible_at,
    platform_commission_amt = EXCLUDED.platform_commission_amt,
    payout_at = EXCLUDED.payout_at,
    payout_retry_count = EXCLUDED.payout_retry_count,
    updated_at = now();

-- ---------------------------------------------------------------------------
-- Refund admin queue and buyer refund history
-- ---------------------------------------------------------------------------

INSERT INTO refund.refunds
    (id, transaction_id, order_id, user_id, group_ref, type, initiated_by,
     refund_reason_type, amount, reason, status, evidence_images, reject_reason,
     admin_note, reviewed_by, reviewed_at, refund_ref, raw_response, created_at, updated_at)
VALUES
    (900201, 900106, 900106, 900001, '90000000-0000-4000-c001-000000000201', 'PARTIAL', 'BUYER',
     'MISSING_ITEM', 1990000, 'One accessory was missing from the package.', 'PENDING',
     '["https://picsum.photos/seed/fe-refund-pending/600/400"]'::jsonb,
     null, null, null, null, null, '{}'::jsonb, now() - interval '6 hours', now()),
    (900202, 900107, 900107, 900001, '90000000-0000-4000-c001-000000000202', 'FULL', 'BUYER',
     'ITEM_BROKEN', 4990000, 'Product stopped working after delivery.', 'COMPLETED',
     '["https://picsum.photos/seed/fe-refund-completed/600/400"]'::jsonb,
     null, 'Approved and refunded by admin fixture.', 900003, now() - interval '2 days',
     're_fe_completed_900202', '{"status":"succeeded"}'::jsonb, now() - interval '3 days', now()),
    (900203, 900104, 900104, 900001, '90000000-0000-4000-c001-000000000203', 'PARTIAL', 'BUYER',
     'ITEM_NOT_AS_DESCRIBED', 390000, 'Requested refund after normal usage.', 'REJECTED',
     '["https://picsum.photos/seed/fe-refund-rejected/600/400"]'::jsonb,
     'Evidence does not show seller fault.', 'Reject fixture for admin screen.', 900003, now() - interval '1 day',
     null, '{}'::jsonb, now() - interval '2 days', now()),
    (900204, 900108, 900108, 900001, '90000000-0000-4000-c001-000000000204', 'FULL', 'SYSTEM',
     'RETURN_TO_SENDER', 27990000, 'Carrier returned package to seller.', 'PROCESSING',
     '["https://picsum.photos/seed/fe-refund-processing/600/400"]'::jsonb,
     null, 'RTS automatic refund is processing.', 900003, now() - interval '12 hours',
     null, '{}'::jsonb, now() - interval '1 day', now())
ON CONFLICT (id) DO UPDATE SET
    transaction_id = EXCLUDED.transaction_id,
    order_id = EXCLUDED.order_id,
    user_id = EXCLUDED.user_id,
    group_ref = EXCLUDED.group_ref,
    type = EXCLUDED.type,
    initiated_by = EXCLUDED.initiated_by,
    refund_reason_type = EXCLUDED.refund_reason_type,
    amount = EXCLUDED.amount,
    reason = EXCLUDED.reason,
    status = EXCLUDED.status,
    evidence_images = EXCLUDED.evidence_images,
    reject_reason = EXCLUDED.reject_reason,
    admin_note = EXCLUDED.admin_note,
    reviewed_by = EXCLUDED.reviewed_by,
    reviewed_at = EXCLUDED.reviewed_at,
    refund_ref = EXCLUDED.refund_ref,
    raw_response = EXCLUDED.raw_response,
    updated_at = now();

INSERT INTO refund.refund_items
    (id, refund_id, item_id, quantity, refund_amount, item_reason, status,
     return_tracking_number, return_evidence_images, returned_at)
VALUES
    (900201, 900201, 900106, 1, 1990000, 'Missing item in box', 'PENDING',
     null, null, null),
    (900202, 900202, 900107, 1, 4990000, 'Broken item returned', 'COMPLETED',
     'FE-RTS-900202', '["https://picsum.photos/seed/fe-return-completed/600/400"]'::jsonb, now() - interval '2 days'),
    (900203, 900203, 900104, 1, 390000, 'Evidence rejected', 'REJECTED',
     null, null, null),
    (900204, 900204, 900108, 1, 27990000, 'Carrier returned to sender', 'PROCESSING',
     'FE-RTS-900204', '["https://picsum.photos/seed/fe-return-processing/600/400"]'::jsonb, now() - interval '1 day')
ON CONFLICT (id) DO UPDATE SET
    refund_id = EXCLUDED.refund_id,
    item_id = EXCLUDED.item_id,
    quantity = EXCLUDED.quantity,
    refund_amount = EXCLUDED.refund_amount,
    item_reason = EXCLUDED.item_reason,
    status = EXCLUDED.status,
    return_tracking_number = EXCLUDED.return_tracking_number,
    return_evidence_images = EXCLUDED.return_evidence_images,
    returned_at = EXCLUDED.returned_at;

-- ---------------------------------------------------------------------------
-- Flash sale sessions, items, reminders
-- ---------------------------------------------------------------------------

INSERT INTO flashsale.fs_sessions
    (id, name, start_time, end_time, status, deleted_at, created_at, updated_at)
VALUES
    (900001, 'FE Live Flash Sale', now() - interval '30 minutes', now() + interval '2 hours', 'LIVE', null, now() - interval '1 day', now()),
    (900002, 'FE Upcoming Weekend Sale', now() + interval '1 day', now() + interval '1 day 6 hours', 'UPCOMING', null, now() - interval '1 day', now()),
    (900003, 'FE Ended Morning Sale', now() - interval '2 days', now() - interval '1 day 20 hours', 'ENDED', null, now() - interval '3 days', now())
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    start_time = EXCLUDED.start_time,
    end_time = EXCLUDED.end_time,
    status = EXCLUDED.status,
    deleted_at = EXCLUDED.deleted_at,
    updated_at = now();

INSERT INTO flashsale.fs_items
    (id, session_id, seller_id, sku_code, flash_price, flash_stock, limit_per_user,
     sold_qty, status, version, created_at, updated_at)
VALUES
    (900001, 900001, 900002, 'FE-SKU-AIRPODS-COMBO', 3990000, 30, 2, 6, 'LIVE', 0, now() - interval '1 day', now()),
    (900002, 900001, 900002, 'FE-SKU-PHONE-15PRO', 21990000, 10, 1, 2, 'LIVE', 0, now() - interval '1 day', now()),
    (900003, 900002, 900002, 'FE-SKU-LAPTOP-M3', 25990000, 8, 1, 0, 'APPROVED', 0, now() - interval '1 day', now()),
    (900004, 900002, 900002, 'FE-SKU-HUB-8IN1', 590000, 100, 3, 0, 'APPROVED', 0, now() - interval '1 day', now()),
    (900005, 900003, 900002, 'FE-SKU-AIRPODS-COMBO', 3790000, 20, 2, 20, 'SOLD_OUT', 0, now() - interval '3 days', now())
ON CONFLICT (id) DO UPDATE SET
    session_id = EXCLUDED.session_id,
    seller_id = EXCLUDED.seller_id,
    sku_code = EXCLUDED.sku_code,
    flash_price = EXCLUDED.flash_price,
    flash_stock = EXCLUDED.flash_stock,
    limit_per_user = EXCLUDED.limit_per_user,
    sold_qty = EXCLUDED.sold_qty,
    status = EXCLUDED.status,
    version = EXCLUDED.version,
    updated_at = now();

INSERT INTO flashsale.fs_reminders (id, customer_id, session_id, created_at)
VALUES
    (900001, 900001, 900002, now() - interval '12 hours'),
    (900002, 900001, 900001, now() - interval '2 hours'),
    (900003, 900001, 900003, now() - interval '3 days')
ON CONFLICT (customer_id, session_id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Keep sequences above fixture IDs for future app-created rows.
-- ---------------------------------------------------------------------------

SELECT setval('identity.users_id_seq',    GREATEST((SELECT COALESCE(MAX(id), 1) FROM identity.users), 900003));
SELECT setval('identity.roles_id_seq',    GREATEST((SELECT COALESCE(MAX(id), 1) FROM identity.roles), 900003));
SELECT setval('identity.addresses_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM identity.addresses), 900005));

SELECT setval('orders.parent_orders_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM orders.parent_orders), 900109));
SELECT setval('orders.orders_id_seq',        GREATEST((SELECT COALESCE(MAX(id), 1) FROM orders.orders), 900109));
SELECT setval('orders.order_items_id_seq',   GREATEST((SELECT COALESCE(MAX(id), 1) FROM orders.order_items), 900109));
SELECT setval('orders.seq_parent_orders',    GREATEST((SELECT COALESCE(MAX(id), 100) FROM orders.parent_orders), 900109));

SELECT setval('payment.seller_stripe_accounts_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM payment.seller_stripe_accounts), 900003));
SELECT setval('payment.transactions_id_seq',            GREATEST((SELECT COALESCE(MAX(id), 1) FROM payment.transactions), 900109));
SELECT setval('payment.seller_transfers_id_seq',        GREATEST((SELECT COALESCE(MAX(id), 1) FROM payment.seller_transfers), 900109));

SELECT setval('refund.refunds_id_seq',      GREATEST((SELECT COALESCE(MAX(id), 1) FROM refund.refunds), 900204));
SELECT setval('refund.refund_items_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM refund.refund_items), 900204));

SELECT setval('flashsale.fs_sessions_id_seq',  GREATEST((SELECT COALESCE(MAX(id), 1) FROM flashsale.fs_sessions), 900003));
SELECT setval('flashsale.fs_items_id_seq',     GREATEST((SELECT COALESCE(MAX(id), 1) FROM flashsale.fs_items), 900005));
SELECT setval('flashsale.fs_reminders_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM flashsale.fs_reminders), 900003));

COMMIT;
