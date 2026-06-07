package com.flashsale.notificationservice.config;

import com.flashsale.commonlib.config.DevDataProperties;
import com.flashsale.notificationservice.domain.model.Notification;
import com.flashsale.notificationservice.domain.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds notifications across buyer + seller accounts.
 *
 * <p>Covers the major notification {@code type} values from the catalog so the UI
 * can render every template variant. Mix of read / unread, multiple priorities.</p>
 *
 * <p>NotificationRepository is a ReactiveMongoRepository — we use {@code .block()}
 * since this runs once at startup.</p>
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "dev-data.enabled", havingValue = "true", matchIfMissing = false)
public class NotificationDevDataLoader implements CommandLineRunner {

    private final NotificationRepository notificationRepository;
    private final DevDataProperties devDataProperties;

    @Override
    public void run(String... args) {
        log.info("[NotificationDevDataLoader] Starting dev data seed for notification-service...");

        if (devDataProperties.isReset()) {
            log.warn("[NotificationDevDataLoader] RESET=true — wiping notifications...");
            notificationRepository.deleteAll().block();
            log.info("[NotificationDevDataLoader] All notifications wiped.");
        } else {
            Long count = notificationRepository.count().block();
            if (count != null && count > 0) {
                log.info("[NotificationDevDataLoader] Data already exists, skipping. Set dev-data.reset=true to reload.");
                return;
            }
        }

        LocalDateTime now = LocalDateTime.now();
        List<Notification> all = new ArrayList<>();

        // ---- Buyer notifications ----
        all.add(notif(1L, "ORDER_CREATED",   "Đơn hàng đã được tạo",
                "Đơn hàng ORD-1 của bạn đã được ghi nhận. Vui lòng thanh toán trong 30 phút.",
                "{\"order_id\":1,\"deeplink\":\"/orders/1\"}", "NORMAL", true,  now.minusDays(7)));
        all.add(notif(1L, "ORDER_PAID",      "Thanh toán thành công",
                "Đơn hàng ORD-1 đã thanh toán thành công 250.000đ qua Stripe.",
                "{\"order_id\":1,\"amount\":250000}", "NORMAL", true,  now.minusDays(7).plusMinutes(5)));
        all.add(notif(1L, "ORDER_DELIVERED", "Đơn hàng đã giao",
                "Đơn ORD-1 đã được giao thành công. Đánh giá ngay!",
                "{\"order_id\":1}", "HIGH",   false, now.minusDays(1)));

        all.add(notif(2L, "ORDER_SHIPPED",   "Đơn hàng đang vận chuyển",
                "Đơn ORD-2 đã được giao cho GHTK987654.",
                "{\"order_id\":2,\"tracking\":\"GHTK987654\"}", "NORMAL", false, now.minusDays(4)));
        all.add(notif(2L, "REFUND_REQUESTED", "Yêu cầu hoàn tiền của bạn đang được xem xét",
                "Yêu cầu hoàn 600.000đ cho ORD-2 đã gửi tới shop.",
                "{\"refund_id\":2}", "NORMAL", false, now.minusHours(6)));

        all.add(notif(4L, "REFUND_APPROVED", "Hoàn tiền thành công",
                "Đã hoàn 3.450.000đ cho ORD-4. Tiền sẽ về tài khoản trong 3-5 ngày làm việc.",
                "{\"refund_id\":3,\"amount\":3450000}", "HIGH", true,  now.minusDays(2)));

        all.add(notif(6L, "ORDER_CREATED",   "Đơn hàng đã được tạo",
                "Đơn ORD-7 (6.800.000đ) đã được tạo.",
                "{\"order_id\":7}", "NORMAL", false, now.minusDays(4)));
        all.add(notif(6L, "ORDER_SHIPPED",   "Đơn hàng đang vận chuyển",
                "Đơn ORD-7 đã giao GHTK112233.",
                "{\"order_id\":7}", "NORMAL", false, now.minusDays(2)));
        all.add(notif(6L, "FLASH_SALE_STARTING", "Flash Sale bắt đầu sau 1 giờ!",
                "MacBook Air M3 giảm còn 25.990.000đ — chỉ 10 suất!",
                "{\"session_id\":2}", "URGENT", false, now.minusMinutes(50)));

        all.add(notif(7L, "ORDER_DELIVERED", "Đơn hàng đã giao",
                "ORD-8 đã giao thành công.",
                "{\"order_id\":8}", "NORMAL", true, now.minusDays(1)));

        all.add(notif(8L, "REFUND_REJECTED", "Yêu cầu hoàn tiền bị từ chối",
                "Yêu cầu hoàn 1.200.000đ cho ORD-9 đã bị từ chối: thiếu bằng chứng.",
                "{\"refund_id\":4}", "HIGH", false, now.minusHours(3)));

        all.add(notif(9L, "PAYMENT_FAILED",  "Thanh toán không thành công",
                "Thanh toán ORD-10 thất bại. Vui lòng thử lại trong 24h.",
                "{\"order_id\":10}", "URGENT", false, now.minusHours(1)));

        // ---- Seller notifications ----
        all.add(notif(1L, "PRODUCT_APPROVED", "Sản phẩm được duyệt",
                "iPhone 15 Black 128GB đã được duyệt và lên sàn.",
                "{\"product_slug\":\"iphone-15-black-128\"}", "NORMAL", true, now.minusDays(5)));
        all.add(notif(2L, "PRODUCT_REJECTED", "Sản phẩm bị từ chối",
                "Túi xách thử nghiệm bị từ chối — vui lòng cập nhật mô tả.",
                "{\"product_slug\":\"tui-xach-test\"}", "HIGH", false, now.minusDays(2)));
        all.add(notif(3L, "TRANSFER_PAID_OUT", "Đã chuyển khoản doanh thu",
                "5.044.500đ đã được chuyển vào tài khoản Stripe của bạn.",
                "{\"transfer_id\":\"tr_test_xxx\"}", "NORMAL", false, now.minusDays(3)));

        Long inserted = notificationRepository.saveAll(all).count().block();
        log.info("[NotificationDevDataLoader] Seeded {} notifications.", inserted);
    }

    private Notification notif(Long userId, String type, String title, String body,
                                String metadata, String priority, boolean isRead,
                                LocalDateTime createdAt) {
        return Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .body(body)
                .metadata(metadata)
                .priority(priority)
                .isRead(isRead)
                .readAt(isRead ? createdAt.plusMinutes(10) : null)
                .createdAt(createdAt)
                .build();
    }
}
