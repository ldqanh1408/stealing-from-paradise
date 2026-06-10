package com.flashsale.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Stack sanity: gateway answers and every core service has registered itself
 * with Eureka and reports UP. Runs first (alphabetical run order) so the other
 * suites fail fast with a clear cause when the stack isn't ready.
 */
@DisplayName("E2E-A01: platform health")
class A01HealthE2eTest extends E2eSupport {

    private static final List<String> CORE_SERVICES = List.of(
            "API-GATEWAY", "IDENTITY-SERVICE", "PRODUCT-SERVICE",
            "ORDER-SERVICE", "PAYMENT-SERVICE", "REFUND-SERVICE");

    @Test
    @DisplayName("gateway is up")
    void gatewayHealth() {
        HttpResponse<String> resp = get(GATEWAY + "/actuator/health", null);
        assertEquals(200, resp.statusCode(), "gateway /actuator/health: " + resp.body());
        assertEquals("UP", text(json(resp), "status"), resp.body());
    }

    @Test
    @DisplayName("all core services registered UP in Eureka")
    void coreServicesRegistered() {
        await("Eureka registry contains all core services UP")
                .atMost(ASYNC_TIMEOUT).pollInterval(POLL)
                .until(() -> upServices().containsAll(CORE_SERVICES));
    }

    private Set<String> upServices() {
        HttpResponse<String> resp = get(EUREKA + "/eureka/apps", null);
        if (resp.statusCode() != 200) return Set.of();
        Set<String> up = new HashSet<>();
        JsonNode apps = find(json(resp), "application");
        if (apps == null) return up;
        Iterable<JsonNode> appList = apps.isArray() ? apps : List.of(apps);
        for (JsonNode app : appList) {
            JsonNode name = app.get("name");
            JsonNode status = find(app, "status");
            if (name != null && status != null && "UP".equals(status.asText())) {
                up.add(name.asText());
            }
        }
        return up;
    }
}
