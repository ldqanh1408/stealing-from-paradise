package com.flashsale.chatservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Level 2 Tool: Order query -- calls order-service API with JWT delegation.
 * Requires user authentication (access token passed via ThreadLocal context).
 */
@Component
@Slf4j
public class OrderQueryTool {

    private final WebClient.Builder webClientBuilder;

    public OrderQueryTool(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    @Tool(description = "Look up a specific order by its ID. Use when the user asks about a specific order, delivery status, or tracking information.")
    public String lookupOrder(@ToolParam(description = "The ID of the order to look up") String orderId) {
        log.info("[OrderQueryTool] Looking up order: {}", orderId);
        String accessToken = ToolContext.getAccessToken();
        try {
            WebClient client = webClientBuilder.build();
            WebClient.RequestHeadersSpec<?> spec = client.get()
                    .uri("http://order-service/v1/orders/{orderId}", orderId);
            if (accessToken != null && !accessToken.isBlank()) {
                spec.header("X-Access-Token", accessToken);
            }
            String result = spec.retrieve()
                    .bodyToMono(String.class)
                    .onErrorReturn("{\"error\": \"Order service unavailable\"}")
                    .block();
            log.info("[OrderQueryTool] Order lookup completed for: {}", orderId);
            return result;
        } catch (Exception e) {
            log.error("[OrderQueryTool] Order lookup failed for: {}", orderId, e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    @Tool(description = "List all orders for the current user. Use when the user asks about their orders or order history.")
    public String listOrders() {
        log.info("[OrderQueryTool] Listing orders for current user");
        String accessToken = ToolContext.getAccessToken();
        try {
            WebClient client = webClientBuilder.build();
            WebClient.RequestHeadersSpec<?> spec = client.get()
                    .uri("http://order-service/v1/orders");
            if (accessToken != null && !accessToken.isBlank()) {
                spec.header("X-Access-Token", accessToken);
            }
            String result = spec.retrieve()
                    .bodyToMono(String.class)
                    .onErrorReturn("{\"error\": \"Order service unavailable\", \"orders\": []}")
                    .block();
            log.info("[OrderQueryTool] Order list completed");
            return result;
        } catch (Exception e) {
            log.error("[OrderQueryTool] Order list failed", e);
            return "{\"error\": \"" + e.getMessage() + "\", \"orders\": []}";
        }
    }
}
