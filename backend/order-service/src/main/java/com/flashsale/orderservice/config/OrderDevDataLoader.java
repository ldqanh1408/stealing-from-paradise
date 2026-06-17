package com.flashsale.orderservice.config;

import com.flashsale.commonlib.config.DevDataProperties;
import com.flashsale.orderservice.domain.model.*;
import com.flashsale.orderservice.domain.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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

@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "dev-data.enabled", havingValue = "true", matchIfMissing = false)
public class OrderDevDataLoader implements CommandLineRunner {

    private final ParentOrderRepository parentOrderRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final DevDataProperties devDataProperties;

    @PersistenceContext
    private EntityManager entityManager;

    // ------------------------------------------------------------------ //
    //  ID RANGES — aligned with identity-service and payment-service
    //  Identity:  users 1-10, sellers 1-5
    //  Order:     parent_orders 1-50, orders 1-100
    //  Payment:   transactions 1-50, refunds 1-20
    // ------------------------------------------------------------------ //

    private static final long[] USER_IDS   = {1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L};
    private static final long[] SELLER_IDS = {1L, 2L, 3L, 4L, 5L};

    private static final String SHIPPING_ADDRESS_1 = """
            {"fullName":"Nguyen Van A","phone":"0909123456","street":"123 Nguyen Trai","city":"Ho Chi Minh","district":"District 1","postalCode":"700000","country":"Vietnam"}""";

    private static final String SHIPPING_ADDRESS_2 = """
            {"fullName":"Tran Thi B","phone":"0912345678","street":"456 Le Duan","city":"Hanoi","district":"Ba Dinh","postalCode":"100000","country":"Vietnam"}""";

    @Override
    @Transactional
    public void run(String... args) {
        log.info("[OrderDevDataLoader] Starting dev data seed for order-service...");

        if (devDataProperties.isReset()) {
            log.warn("[OrderDevDataLoader] RESET=true — wiping all order data...");
            // Native DELETE in correct FK dependency order to avoid optimistic-lock conflicts
            entityManager.createNativeQuery("DELETE FROM order_items WHERE order_id IN (SELECT id FROM orders WHERE parent_order_id IN (SELECT id FROM parent_orders))").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM orders WHERE parent_order_id IN (SELECT id FROM parent_orders)").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM parent_orders").executeUpdate();
            entityManager.flush();
            entityManager.clear();
            log.info("[OrderDevDataLoader] All order data wiped.");
        } else if (parentOrderRepository.count() > 0) {
            log.info("[OrderDevDataLoader] Data already exists, skipping main seed.");

            seedFeData();

            log.info("[OrderDevDataLoader] Dev data seed complete.");
            return;
        }

        // Order 1: PAID, COMPLETED (shipped + delivered)
        ParentOrder po1 = createParentOrder(1L, new BigDecimal("250000.00"),
                LocalDateTime.now().minusDays(7));
        Order o1 = createSubOrder(po1.getId(), SELLER_IDS[0],
                "ORD-1", new BigDecimal("250000.00"), new BigDecimal("250000.00"),
                "COMPLETED", SHIPPING_ADDRESS_1, "VNPOST123456");
        createOrderItems(o1, "COMPLETED", null);

        // Order 2: PAID, SHIPPED (in transit)
        ParentOrder po2 = createParentOrder(2L, new BigDecimal("1590000.00"),
                LocalDateTime.now().minusDays(5));
        Order o2 = createSubOrder(po2.getId(), SELLER_IDS[1],
                "ORD-2", new BigDecimal("1590000.00"), new BigDecimal("1590000.00"),
                "SHIPPED", SHIPPING_ADDRESS_2, "GHTK987654");
        createOrderItems(o2, "SHIPPED", null);

        // Order 3: PAID, CONFIRMED (just paid, awaiting shipment)
        ParentOrder po3 = createParentOrder(3L, new BigDecimal("899000.00"),
                LocalDateTime.now().minusDays(3));
        Order o3 = createSubOrder(po3.getId(), SELLER_IDS[2],
                "ORD-3", new BigDecimal("899000.00"), new BigDecimal("899000.00"),
                "CONFIRMED", SHIPPING_ADDRESS_1, null);
        createOrderItems(o3, "CONFIRMED", null);

        // Order 4: DELIVERED (delivered + return in progress)
        ParentOrder po4 = createParentOrder(4L, new BigDecimal("3450000.00"),
                LocalDateTime.now().minusDays(10));
        Order o4 = createSubOrder(po4.getId(), SELLER_IDS[0],
                "ORD-4", new BigDecimal("3450000.00"), new BigDecimal("3450000.00"),
                "DELIVERED", SHIPPING_ADDRESS_2, "GHN555666");
        createOrderItems(o4, "DELIVERED", null);

        // Order 5: PENDING (awaiting payment — no transaction yet in payment-service)
        ParentOrder po5 = createParentOrder(5L, new BigDecimal("459000.00"),
                LocalDateTime.now().minusHours(1));
        Order o5 = createSubOrder(po5.getId(), SELLER_IDS[1],
                "ORD-5", new BigDecimal("459000.00"), new BigDecimal("459000.00"),
                "PENDING", SHIPPING_ADDRESS_1, null);
        createOrderItems(o5, "PENDING", null);

        // Order 6: CANCELLED (buyer cancelled before payment)
        ParentOrder po6 = createParentOrder(1L, new BigDecimal("1299000.00"),
                LocalDateTime.now().minusDays(2));
        Order o6 = createSubOrder(po6.getId(), SELLER_IDS[2],
                "ORD-6", new BigDecimal("1299000.00"), new BigDecimal("1299000.00"),
                "CANCELLED", SHIPPING_ADDRESS_2, null);
        o6.setCancelledBy("BUYER");
        o6.setCancelReason("Đổi ý không mua nữa");
        orderRepository.save(o6);
        createOrderItems(o6, "CANCELLED", "BUYER");

        // Order 7: PAID, SHIPPING — User 6, Home & Living
        ParentOrder po7 = createParentOrder(6L, new BigDecimal("6800000.00"),
                LocalDateTime.now().minusDays(4));
        Order o7 = createSubOrder(po7.getId(), SELLER_IDS[3],
                "ORD-7", new BigDecimal("6800000.00"), new BigDecimal("6800000.00"),
                "SHIPPING", SHIPPING_ADDRESS_1, "GHTK112233");
        createOrderItems(o7, "SHIPPING", null);

        // Order 8: PAID, CONFIRMED — User 7, Sport & Outdoor
        ParentOrder po8 = createParentOrder(7L, new BigDecimal("1200000.00"),
                LocalDateTime.now().minusDays(2));
        Order o8 = createSubOrder(po8.getId(), SELLER_IDS[4],
                "ORD-8", new BigDecimal("1200000.00"), new BigDecimal("1200000.00"),
                "CONFIRMED", SHIPPING_ADDRESS_2, null);
        createOrderItems(o8, "CONFIRMED", null);

        // Order 9: PAID, DELIVERED — User 8, TechWorld
        ParentOrder po9 = createParentOrder(8L, new BigDecimal("4200000.00"),
                LocalDateTime.now().minusDays(8));
        Order o9 = createSubOrder(po9.getId(), SELLER_IDS[0],
                "ORD-9", new BigDecimal("4200000.00"), new BigDecimal("4200000.00"),
                "DELIVERED", SHIPPING_ADDRESS_1, "VNPOST778899");
        createOrderItems(o9, "DELIVERED", null);

        // Order 10: PENDING — User 9, Gadget Pro
        ParentOrder po10 = createParentOrder(9L, new BigDecimal("8500000.00"),
                LocalDateTime.now().minusHours(2));
        Order o10 = createSubOrder(po10.getId(), SELLER_IDS[2],
                "ORD-10", new BigDecimal("8500000.00"), new BigDecimal("8500000.00"),
                "PENDING", SHIPPING_ADDRESS_2, null);
        createOrderItems(o10, "PENDING", null);

        log.info("[OrderDevDataLoader] Seeded {} parent_orders + sub_orders", 10);

        seedFeData();

        log.info("[OrderDevDataLoader] Dev data seed complete.");
    }

    private ParentOrder createParentOrder(Long userId, BigDecimal totalAmt,
                                          LocalDateTime createdAt) {
        ParentOrder po = ParentOrder.builder()
                .customerId(userId)
                .totalAmt(totalAmt)
                .finalAmt(totalAmt)
                .build();
        return parentOrderRepository.saveAndFlush(po);
    }

    private Order createSubOrder(Long parentOrderId, Long sellerId,
                                 String orderCode, BigDecimal totalAmt,
                                 BigDecimal finalAmt, String status,
                                 String shippingAddress, String trackingNumber) {
        LocalDateTime deliveredAt = null;
        if ("DELIVERED".equals(status)) {
            deliveredAt = LocalDateTime.now().minusDays(1);
        }
        Order order = Order.builder()
                .parentOrderId(parentOrderId)
                .sellerId(sellerId)
                .orderCode(orderCode)
                .customerId(USER_IDS[(int) (parentOrderId - 1) % USER_IDS.length])
                .totalAmt(totalAmt)
                .finalAmt(finalAmt)
                .status(status)
                .isFlashSale(false)
                .shippingAddress(shippingAddress)
                .trackingNumber(trackingNumber)
                .shippingDeadline(LocalDateTime.now().plusDays(3))
                .deliveredAt(deliveredAt)
                .build();
        return orderRepository.save(order);
    }

    private void createOrderItems(Order order, String orderStatus, String cancelledBy) {
        List<OrderItem> items = new ArrayList<>();

        switch (order.getId().intValue()) {
            case 1 -> {
                items.add(createItem(order, "SKU-IPHONE-BLK-128", null, "iPhone 15 Black 128GB",
                        new BigDecimal("250000.00"), 1));
            }
            case 2 -> {
                items.add(createItem(order, "SKU-AIRPOD-PRO2", null, "AirPods Pro 2",
                        new BigDecimal("590000.00"), 1));
                items.add(createItem(order, "SKU-MAGSAFE", null, "MagSafe Charger",
                        new BigDecimal("300000.00"), 2));
                items.add(createItem(order, "SKU-WATCH-SE", null, "Apple Watch SE",
                        new BigDecimal("700000.00"), 1));
            }
            case 3 -> {
                items.add(createItem(order, "SKU-IPAD-10", null, "iPad 10th Gen",
                        new BigDecimal("899000.00"), 1));
            }
            case 4 -> {
                items.add(createItem(order, "SKU-MACBOOK-AIR-M3", null, "MacBook Air M3",
                        new BigDecimal("2450000.00"), 1));
                items.add(createItem(order, "SKU-MOUSE-MX3", null, "Logitech MX Master 3S",
                        new BigDecimal("1000000.00"), 1));
            }
            case 5 -> {
                items.add(createItem(order, "SKU-CABLE-TYPE-C", null, "Cable USB-C 1m",
                        new BigDecimal("150000.00"), 1));
                items.add(createItem(order, "SKU-HUB-7IN1", null, "Hub USB-C 7-in-1",
                        new BigDecimal("309000.00"), 1));
            }
            case 6 -> {
                items.add(createItem(order, "SKU-SAMSUNG-BUDS2", null, "Samsung Galaxy Buds2",
                        new BigDecimal("1299000.00"), 1));
            }
            case 7 -> {
                items.add(createItem(order, "SKU-AIRFRY-55", null, "Nồi chiên không dầu 5.5L",
                        new BigDecimal("3200000.00"), 1));
                items.add(createItem(order, "SKU-KETTLE-LNL", null, "Bình đun siêu tốc LocknLock",
                        new BigDecimal("550000.00"), 2));
            }
            case 8 -> {
                items.add(createItem(order, "SKU-NIKE-PEG40-42", null, "Giày chạy Nike Air Zoom Pegasus 40",
                        new BigDecimal("4200000.00"), 1));
                items.add(createItem(order, "SKU-YOGAMAT-6MM", null, "Thảm yoga cao cấp 6mm",
                        new BigDecimal("280000.00"), 1));
            }
            case 9 -> {
                items.add(createItem(order, "SKU-JBL-FLIP6-BLK", null, "Loa Bluetooth JBL Flip 6",
                        new BigDecimal("3800000.00"), 1));
                items.add(createItem(order, "SKU-BACKPACK-TOM", null, "Balo laptop Tomtoc 15.6 inch",
                        new BigDecimal("1200000.00"), 1));
            }
            case 10 -> {
                items.add(createItem(order, "SKU-AKKO-3098B-R", null, "Bàn phím cơ Akko 3098B",
                        new BigDecimal("2100000.00"), 1));
                items.add(createItem(order, "SKU-ANKER-20000", null, "Pin dự phòng Anker 20000mAh",
                        new BigDecimal("890000.00"), 1));
            }
            default -> {
            }
        }

        orderItemRepository.saveAll(items);
    }

    private OrderItem createItem(Order order, String skuCode, String variantId,
                                  String name, BigDecimal price, Integer quantity) {
        return OrderItem.builder()
                .order(order)
                .skuCode(skuCode)
                .variantId(variantId)
                .nameSnapshot(name)
                .imageSnapshot("https://picsum.photos/seed/" + skuCode + "/400/400")
                .priceSnapshot(price)
                .quantity(quantity)
                .refundedQuantity(0)
                .fsItemId(null)
                .build();
    }

    /**
     * Seeds FE test-dataset orders (900101-900109) via EntityManager native queries.
     */
    private void seedFeData() {
        log.info("[OrderDevDataLoader] Seeding FE test-dataset...");

        // Check if FE data already exists
        Long count = (Long) entityManager.createNativeQuery(
            "SELECT COUNT(*) FROM orders.parent_orders WHERE id >= 900101")
            .getSingleResult();
        if (count != null && count > 0) {
            log.info("[OrderDevDataLoader] FE data already exists, skipping.");
            return;
        }

        // Parent orders
        entityManager.createNativeQuery(
            "INSERT INTO orders.parent_orders (id, customer_id, session_id, total_amt, final_amt, created_at, updated_at) VALUES " +
            "(900101, 900001, 'fe-session-pending', 23990000, 23990000, now() - interval '1 hour', now()), " +
            "(900102, 900001, 'fe-session-paid', 23990000, 23990000, now() - interval '1 day', now()), " +
            "(900103, 900001, 'fe-session-shipping', 4990000, 4990000, now() - interval '2 days', now()), " +
            "(900104, 900001, 'fe-session-delivered', 790000, 790000, now() - interval '4 days', now()), " +
            "(900105, 900001, 'fe-session-cancelled', 790000, 790000, now() - interval '5 days', now()), " +
            "(900106, 900001, 'fe-session-partial-refund', 4990000, 4990000, now() - interval '6 days', now()), " +
            "(900107, 900001, 'fe-session-refunded', 4990000, 4990000, now() - interval '7 days', now()), " +
            "(900108, 900001, 'fe-session-returned', 27990000, 27990000, now() - interval '8 days', now()), " +
            "(900109, 900001, 'fe-session-paid-out', 27990000, 27990000, now() - interval '20 days', now()) " +
            "ON CONFLICT (id) DO UPDATE SET customer_id=EXCLUDED.customer_id,session_id=EXCLUDED.session_id,total_amt=EXCLUDED.total_amt,final_amt=EXCLUDED.final_amt,updated_at=now()")
            .executeUpdate();

        // Sub-orders
        entityManager.createNativeQuery(
            "INSERT INTO orders.orders (id, parent_order_id, seller_id, order_code, customer_id, total_amt, final_amt, status, cancelled_by, cancel_reason, is_flash_sale, shipping_address, tracking_number, shipping_deadline, delivered_at, version, created_at, updated_at) VALUES " +
            "(900101, 900101, 900002, 'FE-ORD-PENDING-900101', 900001, 23990000, 23990000, 'PENDING', null, null, false, '{\"full_address\":\"123 Frontend Test Street, District 1, Ho Chi Minh City\",\"province_id\":79,\"district_id\":760}'::jsonb, null, now() + interval '3 days', null, 0, now() - interval '1 hour', now()), " +
            "(900102, 900102, 900002, 'FE-ORD-PAID-900102', 900001, 23990000, 23990000, 'PAID', null, null, false, '{\"full_address\":\"123 Frontend Test Street, District 1, Ho Chi Minh City\",\"province_id\":79,\"district_id\":760}'::jsonb, null, now() + interval '3 days', null, 0, now() - interval '1 day', now()), " +
            "(900103, 900103, 900002, 'FE-ORD-SHIPPING-900103', 900001, 4990000, 4990000, 'SHIPPING', null, null, true, '{\"full_address\":\"123 Frontend Test Street, District 1, Ho Chi Minh City\",\"province_id\":79,\"district_id\":760}'::jsonb, 'FE-GHN-900103', now() + interval '2 days', null, 0, now() - interval '2 days', now()), " +
            "(900104, 900104, 900002, 'FE-ORD-DELIVERED-900104', 900001, 790000, 790000, 'DELIVERED', null, null, false, '{\"full_address\":\"123 Frontend Test Street, District 1, Ho Chi Minh City\",\"province_id\":79,\"district_id\":760}'::jsonb, 'FE-VNPOST-900104', now() - interval '1 day', now() - interval '2 days', 0, now() - interval '4 days', now()), " +
            "(900105, 900105, 900002, 'FE-ORD-CANCELLED-900105', 900001, 790000, 790000, 'CANCELLED', 'BUYER', 'Changed mind before shipment', false, '{\"full_address\":\"123 Frontend Test Street, District 1, Ho Chi Minh City\",\"province_id\":79,\"district_id\":760}'::jsonb, null, null, null, 0, now() - interval '5 days', now()), " +
            "(900106, 900106, 900002, 'FE-ORD-PARTIAL-REFUND-900106', 900001, 4990000, 4990000, 'PARTIALLY_REFUNDED', null, null, false, '{\"full_address\":\"123 Frontend Test Street, District 1, Ho Chi Minh City\",\"province_id\":79,\"district_id\":760}'::jsonb, 'FE-GHTK-900106', now() - interval '2 days', now() - interval '3 days', 0, now() - interval '6 days', now()), " +
            "(900107, 900107, 900002, 'FE-ORD-REFUNDED-900107', 900001, 4990000, 4990000, 'REFUNDED', null, null, false, '{\"full_address\":\"123 Frontend Test Street, District 1, Ho Chi Minh City\",\"province_id\":79,\"district_id\":760}'::jsonb, 'FE-GHTK-900107', now() - interval '3 days', now() - interval '4 days', 0, now() - interval '7 days', now()), " +
            "(900108, 900108, 900002, 'FE-ORD-RETURNED-900108', 900001, 27990000, 27990000, 'RETURNED', null, null, false, '{\"full_address\":\"123 Frontend Test Street, District 1, Ho Chi Minh City\",\"province_id\":79,\"district_id\":760}'::jsonb, 'FE-GHN-900108', now() - interval '4 days', now() - interval '5 days', 0, now() - interval '8 days', now()), " +
            "(900109, 900109, 900002, 'FE-ORD-PAIDOUT-900109', 900001, 27990000, 27990000, 'DELIVERED', null, null, false, '{\"full_address\":\"123 Frontend Test Street, District 1, Ho Chi Minh City\",\"province_id\":79,\"district_id\":760}'::jsonb, 'FE-GHN-900109', now() - interval '10 days', now() - interval '12 days', 0, now() - interval '20 days', now()) " +
            "ON CONFLICT (id) DO UPDATE SET parent_order_id=EXCLUDED.parent_order_id,seller_id=EXCLUDED.seller_id,order_code=EXCLUDED.order_code,customer_id=EXCLUDED.customer_id,total_amt=EXCLUDED.total_amt,final_amt=EXCLUDED.final_amt,status=EXCLUDED.status,cancelled_by=EXCLUDED.cancelled_by,cancel_reason=EXCLUDED.cancel_reason,is_flash_sale=EXCLUDED.is_flash_sale,shipping_address=EXCLUDED.shipping_address,tracking_number=EXCLUDED.tracking_number,shipping_deadline=EXCLUDED.shipping_deadline,delivered_at=EXCLUDED.delivered_at,version=EXCLUDED.version,updated_at=now()")
            .executeUpdate();

        // Order items
        entityManager.createNativeQuery(
            "INSERT INTO orders.order_items (id, order_id, sku_code, variant_id, name_snapshot, image_snapshot, price_snapshot, quantity, refunded_quantity, fs_item_id, created_at) VALUES " +
            "(900101, 900101, 'FE-SKU-PHONE-15PRO', '90000000-0000-4000-9001-000000000101', 'FE Phone Pro Camera Kit', 'https://picsum.photos/seed/fe-phone-15pro/500/500', 23990000, 1, 0, null, now() - interval '1 hour'), " +
            "(900102, 900102, 'FE-SKU-PHONE-15PRO', '90000000-0000-4000-9001-000000000101', 'FE Phone Pro Camera Kit', 'https://picsum.photos/seed/fe-phone-15pro/500/500', 23990000, 1, 0, null, now() - interval '1 day'), " +
            "(900103, 900103, 'FE-SKU-AIRPODS-COMBO', '90000000-0000-4000-9001-000000000103', 'FE AirPods Flash Combo', 'https://picsum.photos/seed/fe-airpods-combo/500/500', 4990000, 1, 0, 900001, now() - interval '2 days'), " +
            "(900104, 900104, 'FE-SKU-HUB-8IN1', '90000000-0000-4000-9001-000000000104', 'FE USB-C Hub 8-in-1', 'https://picsum.photos/seed/fe-hub-8in1/500/500', 790000, 1, 0, null, now() - interval '4 days'), " +
            "(900105, 900105, 'FE-SKU-HUB-8IN1', '90000000-0000-4000-9001-000000000104', 'FE USB-C Hub 8-in-1', 'https://picsum.photos/seed/fe-hub-8in1/500/500', 790000, 1, 0, null, now() - interval '5 days'), " +
            "(900106, 900106, 'FE-SKU-AIRPODS-COMBO', '90000000-0000-4000-9001-000000000103', 'FE AirPods Flash Combo', 'https://picsum.photos/seed/fe-airpods-combo/500/500', 4990000, 1, 1, null, now() - interval '6 days'), " +
            "(900107, 900107, 'FE-SKU-AIRPODS-COMBO', '90000000-0000-4000-9001-000000000103', 'FE AirPods Flash Combo', 'https://picsum.photos/seed/fe-airpods-combo/500/500', 4990000, 1, 1, null, now() - interval '7 days'), " +
            "(900108, 900108, 'FE-SKU-LAPTOP-M3', '90000000-0000-4000-9001-000000000102', 'FE MacBook Air M3 Demo', 'https://picsum.photos/seed/fe-laptop-m3/500/500', 27990000, 1, 1, null, now() - interval '8 days'), " +
            "(900109, 900109, 'FE-SKU-LAPTOP-M3', '90000000-0000-4000-9001-000000000102', 'FE MacBook Air M3 Demo', 'https://picsum.photos/seed/fe-laptop-m3/500/500', 27990000, 1, 0, null, now() - interval '20 days') " +
            "ON CONFLICT (id) DO UPDATE SET order_id=EXCLUDED.order_id,sku_code=EXCLUDED.sku_code,variant_id=EXCLUDED.variant_id,name_snapshot=EXCLUDED.name_snapshot,image_snapshot=EXCLUDED.image_snapshot,price_snapshot=EXCLUDED.price_snapshot,quantity=EXCLUDED.quantity,refunded_quantity=EXCLUDED.refunded_quantity,fs_item_id=EXCLUDED.fs_item_id")
            .executeUpdate();

        // Reset sequences
        entityManager.createNativeQuery("SELECT setval('orders.parent_orders_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM orders.parent_orders), 900109))")
            .getSingleResult();
        entityManager.createNativeQuery("SELECT setval('orders.orders_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM orders.orders), 900109))")
            .getSingleResult();
        entityManager.createNativeQuery("SELECT setval('orders.order_items_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM orders.order_items), 900109))")
            .getSingleResult();

        log.info("[OrderDevDataLoader] FE test-dataset seeded (9 parent_orders, 9 orders, 9 order_items).");
    }
}
