package com.flashsale.orderdomain.config;

import com.flashsale.commonlib.config.DevDataProperties;
import com.flashsale.orderdomain.domain.model.*;
import com.flashsale.orderdomain.domain.repository.*;
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

    // ------------------------------------------------------------------ //
    //  ID RANGES — aligned with identity-service and payment-service
    //  Identity:  users 1-10, sellers 1-5
    //  Order:     parent_orders 1-50, orders 1-100
    //  Payment:   transactions 1-50, refunds 1-20
    // ------------------------------------------------------------------ //

    private static final long[] USER_IDS   = {1L, 2L, 3L, 4L, 5L};
    private static final long[] SELLER_IDS = {1L, 2L, 3L};

    private static final String[] SELLER_NAMES = {"TechWorld Store", "Fashion Hub", "Gadget Pro"};

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
            orderItemRepository.deleteAll();
            orderRepository.deleteAll();
            parentOrderRepository.deleteAll();
            log.info("[OrderDevDataLoader] All order data wiped.");
        } else if (parentOrderRepository.count() > 0) {
            log.info("[OrderDevDataLoader] Data already exists, skipping. Set dev-data.reset=true to reload.");
            return;
        }

        // Order 1: PAID, COMPLETED (shipped + delivered)
        ParentOrder po1 = createParentOrder(1L, 1L, new BigDecimal("250000.00"),
                LocalDateTime.now().minusDays(7), LocalDateTime.now().minusDays(6));
        Order o1 = createSubOrder(po1.getId(), SELLER_IDS[0], "SELLER_1",
                "ORD-1", 1L, new BigDecimal("250000.00"), new BigDecimal("250000.00"),
                "COMPLETED", SHIPPING_ADDRESS_1, "VNPOST123456");
        createOrderItems(o1, "COMPLETED", null);

        // Order 2: PAID, SHIPPED (in transit)
        ParentOrder po2 = createParentOrder(2L, 2L, new BigDecimal("1590000.00"),
                LocalDateTime.now().minusDays(5), LocalDateTime.now().minusDays(4));
        Order o2 = createSubOrder(po2.getId(), SELLER_IDS[1], "SELLER_2",
                "ORD-2", 2L, new BigDecimal("1590000.00"), new BigDecimal("1590000.00"),
                "SHIPPED", SHIPPING_ADDRESS_2, "GHTK987654");
        createOrderItems(o2, "SHIPPED", null);

        // Order 3: PAID, CONFIRMED (just paid, awaiting shipment)
        ParentOrder po3 = createParentOrder(3L, 3L, new BigDecimal("899000.00"),
                LocalDateTime.now().minusDays(3), LocalDateTime.now().minusDays(2));
        Order o3 = createSubOrder(po3.getId(), SELLER_IDS[2], "SELLER_3",
                "ORD-3", 3L, new BigDecimal("899000.00"), new BigDecimal("899000.00"),
                "CONFIRMED", SHIPPING_ADDRESS_1, null);
        createOrderItems(o3, "CONFIRMED", null);

        // Order 4: DELIVERED (delivered + return in progress)
        ParentOrder po4 = createParentOrder(4L, 4L, new BigDecimal("3450000.00"),
                LocalDateTime.now().minusDays(10), LocalDateTime.now().minusDays(9));
        Order o4 = createSubOrder(po4.getId(), SELLER_IDS[0], "SELLER_1",
                "ORD-4", 4L, new BigDecimal("3450000.00"), new BigDecimal("3450000.00"),
                "DELIVERED", SHIPPING_ADDRESS_2, "GHN555666");
        o4.setDeliveredAt(LocalDateTime.now().minusDays(2));
        orderRepository.save(o4);
        createOrderItems(o4, "DELIVERED", null);

        // Order 5: PENDING (awaiting payment — no transaction yet in payment-service)
        ParentOrder po5 = createParentOrder(5L, 5L, new BigDecimal("459000.00"),
                LocalDateTime.now().minusHours(1), null);
        Order o5 = createSubOrder(po5.getId(), SELLER_IDS[1], "SELLER_2",
                "ORD-5", 5L, new BigDecimal("459000.00"), new BigDecimal("459000.00"),
                "PENDING", SHIPPING_ADDRESS_1, null);
        createOrderItems(o5, "PENDING", null);

        // Order 6: CANCELLED (buyer cancelled before payment)
        ParentOrder po6 = createParentOrder(6L, 1L, new BigDecimal("1299000.00"),
                LocalDateTime.now().minusDays(2), null);
        Order o6 = createSubOrder(po6.getId(), SELLER_IDS[2], "SELLER_3",
                "ORD-6", 6L, new BigDecimal("1299000.00"), new BigDecimal("1299000.00"),
                "CANCELLED", SHIPPING_ADDRESS_2, null);
        o6.setCancelledBy("BUYER");
        o6.setCancelReason("Đổi ý không mua nữa");
        o6.setCancelledAt(LocalDateTime.now().minusDays(2));
        orderRepository.save(o6);
        createOrderItems(o6, "CANCELLED", "BUYER");

        log.info("[OrderDevDataLoader] Seeded {} parent_orders + sub_orders", 6);
    }

    private ParentOrder createParentOrder(Long id, Long userId, BigDecimal totalAmt,
                                          LocalDateTime createdAt, LocalDateTime timeoutAt) {
        ParentOrder po = ParentOrder.builder()
                .id(id)
                .orderCode("PO-" + java.time.LocalDate.now().toString().replace("-", "") + "-" + id)
                .userId(userId)
                .totalAmt(totalAmt)
                .finalAmt(totalAmt)
                .loyaltyDiscount(BigDecimal.ZERO)
                .loyaltyPointsUsed(0)
                .timeoutAt(timeoutAt)
                .build();
        return parentOrderRepository.save(po);
    }

    private Order createSubOrder(Long parentOrderId, Long sellerId, String sellerName,
                                 String orderCode, Long id, BigDecimal totalAmt,
                                 BigDecimal finalAmt, String status,
                                 String shippingAddress, String trackingNumber) {
        Order order = Order.builder()
                .id(id)
                .parentOrderId(parentOrderId)
                .sellerId(sellerId)
                .sellerName(sellerName)
                .orderCode(orderCode)
                .userId(USER_IDS[(int) (parentOrderId - 1) % USER_IDS.length])
                .totalAmt(totalAmt)
                .finalAmt(finalAmt)
                .status(status)
                .isFlashSale(false)
                .shippingAddress(shippingAddress)
                .trackingNumber(trackingNumber)
                .shippingDeadline(LocalDateTime.now().plusDays(3))
                .version(0)
                .build();
        return orderRepository.save(order);
    }

    private void createOrderItems(Order order, String orderStatus, String cancelledBy) {
        List<OrderItem> items = new ArrayList<>();

        switch (order.getId().intValue()) {
            case 1 -> {
                items.add(createItem(order, "SKU-IPHONE-BLK-128", null, "iPhone 15 Black 128GB", "Đen / 128GB",
                        new BigDecimal("250000.00"), 1));
            }
            case 2 -> {
                items.add(createItem(order, "SKU-AIRPOD-PRO2", null, "AirPods Pro 2", "Default",
                        new BigDecimal("590000.00"), 1));
                items.add(createItem(order, "SKU-MAGSAFE", null, "MagSafe Charger", "Default",
                        new BigDecimal("300000.00"), 2));
                items.add(createItem(order, "SKU-WATCH-SE", null, "Apple Watch SE", "Silver 40mm",
                        new BigDecimal("700000.00"), 1));
            }
            case 3 -> {
                items.add(createItem(order, "SKU-IPAD-10", null, "iPad 10th Gen", "Blue 64GB",
                        new BigDecimal("899000.00"), 1));
            }
            case 4 -> {
                items.add(createItem(order, "SKU-MACBOOK-AIR-M3", null, "MacBook Air M3", "Silver 256GB",
                        new BigDecimal("2450000.00"), 1));
                items.add(createItem(order, "SKU-MOUSE-MX3", null, "Logitech MX Master 3S", "Graphite",
                        new BigDecimal("1000000.00"), 1));
            }
            case 5 -> {
                items.add(createItem(order, "SKU-CABLE-TYPE-C", null, "Cable USB-C 1m", "White",
                        new BigDecimal("150000.00"), 1));
                items.add(createItem(order, "SKU-HUB-7IN1", null, "Hub USB-C 7-in-1", "Silver",
                        new BigDecimal("309000.00"), 1));
            }
            case 6 -> {
                items.add(createItem(order, "SKU-SAMSUNG-BUDS2", null, "Samsung Galaxy Buds2", "Lavender",
                        new BigDecimal("1299000.00"), 1));
            }
            default -> {
            }
        }

        orderItemRepository.saveAll(items);
    }

    private OrderItem createItem(Order order, String skuCode, String variantId,
                                  String name, String variantName,
                                  BigDecimal price, Integer quantity) {
        return OrderItem.builder()
                .order(order)
                .productId("prod_dev_" + skuCode.toLowerCase())
                .skuCode(skuCode)
                .variantId(variantId)
                .nameSnapshot(name)
                .variantName(variantName)
                .imageSnapshot("https://picsum.photos/seed/" + skuCode + "/400/400")
                .priceSnapshot(price)
                .quantity(quantity)
                .refundedQuantity(0)
                .fsItemId(null)
                .build();
    }
}
