package com.flashsale.chatservice.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple in-memory rate limiter using a sliding-window counter.
 *
 * TODO: Replace with Redis-based rate limiting (key pattern: rate:{userId}:{type})
 *       for production use — shared state across instances + TTL support.
 */
public final class RateLimiter {

    private static final int CHAT_MAX_PER_MINUTE = 20;
    private static final int TOOL_MAX_PER_MINUTE = 10;

    private final Map<Long, Window> chatWindows = new ConcurrentHashMap<>();
    private final Map<Long, Window> toolWindows = new ConcurrentHashMap<>();

    public boolean tryAcquireChat(Long userId) {
        return tryAcquire(chatWindows, userId, CHAT_MAX_PER_MINUTE);
    }

    public boolean tryAcquireTool(Long userId) {
        return tryAcquire(toolWindows, userId, TOOL_MAX_PER_MINUTE);
    }

    private boolean tryAcquire(Map<Long, Window> windows, Long userId, int maxPerMinute) {
        long now = System.currentTimeMillis();
        Window window = windows.compute(userId, (key, w) -> {
            if (w == null || now - w.windowStart > 60_000) {
                return new Window(now, new AtomicInteger(1));
            }
            return w;
        });
        if (now - window.windowStart > 60_000) {
            window.windowStart = now;
            window.counter.set(1);
            return true;
        }
        return window.counter.incrementAndGet() <= maxPerMinute;
    }

    private static class Window {
        volatile long windowStart;
        final AtomicInteger counter;

        Window(long windowStart, AtomicInteger counter) {
            this.windowStart = windowStart;
            this.counter = counter;
        }
    }
}
