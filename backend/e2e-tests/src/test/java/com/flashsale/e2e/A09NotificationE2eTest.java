package com.flashsale.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for notification workflows.
 * Covers UC-NOTIF-001, UC-NOTIF-002, UC-NOTIF-003.
 */
@DisplayName("E2E-A09: notification flows")
class A09NotificationE2eTest extends E2eSupport {

    @Test
    @DisplayName("check unread count → get notifications history → mark all as read → check unread count is 0")
    void notificationFlow() {
        String token = login(BUYER);

        // 1. Get unread count
        HttpResponse<String> countResp = get("/api/v1/notifications/unread-count", token);
        assertEquals(200, countResp.statusCode(), countResp.body());
        JsonNode countData = json(countResp);
        assertNotNull(find(countData, "unread_count"));

        // 2. Get notification history
        HttpResponse<String> historyResp = get("/api/v1/notifications/history", token);
        assertEquals(200, historyResp.statusCode(), historyResp.body());
        JsonNode historyData = json(historyResp);
        assertTrue(historyData.isArray(), "Expected notification history to return a JSON array directly");

        // 3. Mark all notifications as read
        HttpResponse<String> readAllResp = put("/api/v1/notifications/read-all", token, Map.of());
        assertEquals(200, readAllResp.statusCode(), readAllResp.body());

        // 4. Verify unread count is now 0
        HttpResponse<String> countRespAfter = get("/api/v1/notifications/unread-count", token);
        assertEquals(200, countRespAfter.statusCode(), countRespAfter.body());
        JsonNode countDataAfter = json(countRespAfter);
        assertEquals(0, find(countDataAfter, "unread_count").asInt());
    }
}
