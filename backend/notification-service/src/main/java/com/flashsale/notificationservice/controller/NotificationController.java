package com.flashsale.notificationservice.controller;

import com.flashsale.notificationservice.domain.model.Notification;
import com.flashsale.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/v1/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * SSE real-time notification stream.
     * Client connects and receives push notifications as Server-Sent Events.
     * Supports Last-Event-ID for reconnection replay.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Notification> stream(@RequestHeader("X-User-Id") Long userId,
                                      @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        log.info("SSE stream connected: userId={}, lastEventId={}", userId, lastEventId);
        return notificationService.getNotificationStream(userId)
                .delayElements(Duration.ofMillis(100))
                .doOnCancel(() -> {
                    notificationService.removeSink(userId);
                    log.info("SSE stream disconnected: userId={}", userId);
                })
                .doOnComplete(() -> log.info("SSE stream completed: userId={}", userId));
    }

    /**
     * Paginated notification history.
     */
    @GetMapping
    public Flux<Notification> getNotifications(@RequestHeader("X-User-Id") Long userId,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return notificationService.getNotifications(userId, page, size);
    }

    /**
     * Mark a single notification as read.
     */
    @PatchMapping("/{notifId}/read")
    public Mono<Notification> markAsRead(@PathVariable String notifId,
                                          @RequestHeader("X-User-Id") Long userId) {
        return notificationService.markAsRead(notifId, userId);
    }

    /**
     * Mark all notifications as read for the current user.
     */
    @PatchMapping("/read-all")
    public Mono<ResponseEntity<Map<String, Object>>> markAllAsRead(@RequestHeader("X-User-Id") Long userId) {
        return notificationService.markAllAsRead(userId)
                .map(count -> ResponseEntity.ok(Map.of(
                        "success", true,
                        "updated_count", count,
                        "user_id", userId
                )));
    }

    /**
     * Get unread notification count.
     */
    @GetMapping("/unread-count")
    public Mono<ResponseEntity<Map<String, Object>>> getUnreadCount(@RequestHeader("X-User-Id") Long userId) {
        return notificationService.getUnreadCount(userId)
                .map(count -> ResponseEntity.ok(Map.of(
                        "user_id", userId,
                        "unread_count", count
                )));
    }
}
