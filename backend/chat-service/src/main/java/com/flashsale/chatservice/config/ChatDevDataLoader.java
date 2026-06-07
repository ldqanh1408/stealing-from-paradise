package com.flashsale.chatservice.config;

import com.flashsale.commonlib.config.DevDataProperties;
import com.flashsale.chatservice.domain.model.ChatMessage;
import com.flashsale.chatservice.domain.model.ChatSession;
import com.flashsale.chatservice.domain.repository.ChatMessageRepository;
import com.flashsale.chatservice.domain.repository.ChatSessionRepository;
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
 * Seeds AI chat sessions and messages for local dev.
 *
 * <p>Three sessions covering: an active product-discovery chat (user 6),
 * a closed order-status chat (user 7), and an active chat with a TOOL_CALL
 * sequence (user 8).</p>
 *
 * <p>ChatSessionRepository / ChatMessageRepository are ReactiveMongoRepository
 * — we use {@code .block()} since this only runs once at startup.</p>
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "dev-data.enabled", havingValue = "true", matchIfMissing = false)
public class ChatDevDataLoader implements CommandLineRunner {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final DevDataProperties devDataProperties;

    @Override
    public void run(String... args) {
        log.info("[ChatDevDataLoader] Starting dev data seed for chat-service...");

        if (devDataProperties.isReset()) {
            log.warn("[ChatDevDataLoader] RESET=true — wiping chat data...");
            messageRepository.deleteAll().block();
            sessionRepository.deleteAll().block();
            log.info("[ChatDevDataLoader] All chat data wiped.");
        } else {
            Long count = sessionRepository.count().block();
            if (count != null && count > 0) {
                log.info("[ChatDevDataLoader] Data already exists, skipping. Set dev-data.reset=true to reload.");
                return;
            }
        }

        LocalDateTime now = LocalDateTime.now();
        List<ChatMessage> messages = new ArrayList<>();

        // ---- Session A: user 6 — ACTIVE product discovery ----
        ChatSession a = sessionRepository.save(ChatSession.builder()
                .userId(6L)
                .status("ACTIVE")
                .contextSummary("User đang tìm điện thoại tầm 25 triệu, ưu tiên camera.")
                .createdAt(now.minusHours(2))
                .updatedAt(now.minusMinutes(5))
                .build()).block();
        messages.add(msg(a.getId(), "USER", "Mình tìm điện thoại khoảng 25 triệu, chụp ảnh đẹp",
                null, 1, null, now.minusHours(2)));
        messages.add(msg(a.getId(), "ASSISTANT",
                "Trong tầm giá đó iPhone 15 (22.99tr) là lựa chọn rất tốt: chip A16, camera 48MP. Bạn có muốn xem thêm vài tuỳ chọn khác không?",
                null, 2, 120, now.minusHours(2).plusSeconds(15)));
        messages.add(msg(a.getId(), "USER", "Ok cho mình xem iPhone 15 chi tiết",
                null, 3, null, now.minusHours(1).minusMinutes(50)));
        messages.add(msg(a.getId(), "ASSISTANT",
                "iPhone 15 Black 128GB — giá 22.990.000đ (giảm từ 24.990.000đ), còn 50 chiếc. Bạn muốn thêm vào giỏ?",
                null, 4, 95, now.minusHours(1).minusMinutes(49)));
        messages.add(msg(a.getId(), "USER", "Thêm 1 chiếc giúp mình",
                null, 5, null, now.minusMinutes(5)));

        // ---- Session B: user 7 — CLOSED order status ----
        ChatSession b = sessionRepository.save(ChatSession.builder()
                .userId(7L)
                .status("CLOSED")
                .contextSummary("User hỏi tiến độ giao hàng đơn ORD-8.")
                .createdAt(now.minusDays(2))
                .updatedAt(now.minusDays(2).plusMinutes(15))
                .closedAt(now.minusDays(2).plusMinutes(15))
                .build()).block();
        messages.add(msg(b.getId(), "USER", "Đơn ORD-8 của mình giờ tới đâu rồi?",
                null, 1, null, now.minusDays(2)));
        messages.add(msg(b.getId(), "TOOL_CALL", "{\"order_code\":\"ORD-8\"}",
                "get_order_status", 2, null, now.minusDays(2).plusSeconds(2)));
        messages.add(msg(b.getId(), "TOOL_RESULT",
                "{\"status\":\"CONFIRMED\",\"tracking\":null,\"eta_days\":3}",
                "get_order_status", 3, null, now.minusDays(2).plusSeconds(3)));
        messages.add(msg(b.getId(), "ASSISTANT",
                "Đơn ORD-8 đang ở trạng thái CONFIRMED, dự kiến shop sẽ giao đơn cho ĐVVC trong 3 ngày tới. Mình sẽ thông báo khi có cập nhật.",
                null, 4, 78, now.minusDays(2).plusSeconds(4)));

        // ---- Session C: user 8 — ACTIVE with pending tool call ----
        ChatSession c = sessionRepository.save(ChatSession.builder()
                .userId(8L)
                .status("ACTIVE")
                .contextSummary("User muốn huỷ đơn ORD-9 — cần xác nhận trước khi gọi tool huỷ.")
                .createdAt(now.minusMinutes(30))
                .updatedAt(now.minusMinutes(2))
                .build()).block();
        messages.add(msg(c.getId(), "USER", "Huỷ đơn ORD-9 giúp mình",
                null, 1, null, now.minusMinutes(30)));
        messages.add(msg(c.getId(), "ASSISTANT",
                "Bạn chắc chắn muốn huỷ đơn ORD-9 (4.200.000đ)? Đơn này đã DELIVERED, mình sẽ chuyển thành yêu cầu hoàn tiền nhé.",
                null, 2, 65, now.minusMinutes(29)));
        messages.add(msg(c.getId(), "USER", "Ok, huỷ và hoàn tiền cho mình",
                null, 3, null, now.minusMinutes(2)));

        Long inserted = messageRepository.saveAll(messages).count().block();
        log.info("[ChatDevDataLoader] Seeded 3 chat sessions + {} messages.", inserted);
    }

    private ChatMessage msg(String sessionId, String role, String content, String toolName,
                             int seq, Integer tokens, LocalDateTime createdAt) {
        return ChatMessage.builder()
                .sessionId(sessionId)
                .role(role)
                .content(content)
                .toolName(toolName)
                .sequenceNo(seq)
                .tokensUsed(tokens)
                .createdAt(createdAt)
                .build();
    }
}
