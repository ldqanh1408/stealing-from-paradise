package com.flashsale.apigateway.config;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import lombok.extern.slf4j.Slf4j;

/**
 * API Gateway Route Configuration
 *
 * ✅ JWT Authentication is handled by JwtAuthWebFilter (@Component)
 *    No need to inject or configure filters here
 *
 * JwtAuthWebFilter automatically intercepts all requests at gateway level
 * and validates JWT tokens without explicit route configuration
 */
@Configuration
@Slf4j
public class RouteConfig {

    @Bean
    public RouteLocator routes(RouteLocatorBuilder b) {
        log.info("[Gateway] Initializing routes...");
        return b.routes()
            // ===== Identity Service =====
            .route("identity-public", r -> r
                .path("/api/v1/auth/**", "/api/v1/users/register")
                .uri("lb://identity-service"))

            .route("identity-protected", r -> r
                .path("/api/v1/users/**", "/api/v1/loyalty/**")
                .and().method(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE)
                .uri("lb://identity-service"))

            // ===== Product Service =====
            .route("product-read", r -> r
                .path("/api/v1/products/**", "/api/v1/categories/**")
                .and().method(HttpMethod.GET)
                .uri("lb://product-service"))

            .route("product-write", r -> r
                .path("/api/v1/products/**", "/api/v1/categories/**", "/api/v1/seller/products/**")
                .and().method(HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE)
                .uri("lb://product-service"))

            // ===== Cart Service (now part of Product Service - requires JWT) =====
            .route("cart", r -> r
                .path("/api/v1/cart/**")
                .uri("lb://product-service"))

            // ===== Order Service (requires JWT) =====
            .route("order", r -> r
                .path("/api/v1/orders/**")
                .uri("lb://order-service"))

            // ===== Payment Service =====
            .route("stripe-webhook", r -> r
                .path("/api/v1/stripe/webhook")
                .uri("lb://payment-service"))

            .route("stripe-onboarding", r -> r
                .path("/api/v1/stripe/onboarding/**")
                .uri("lb://payment-service"))

            .route("payment", r -> r
                .path("/api/v1/payments/**", "/api/v1/refunds/**")
                .uri("lb://payment-service"))

            // ===== FlashSale Service =====
            .route("fs-read", r -> r
                .path("/api/v1/flash-sales/**")
                .and().method(HttpMethod.GET)
                .uri("lb://flashsale-service"))

            .route("fs-buy", r -> r
                .path("/api/v1/flash-sales/*/buy")
                .and().method(HttpMethod.POST)
                .uri("lb://flashsale-service"))

            .route("fs-write", r -> r
                .path("/api/v1/flash-sales/**")
                .and().method(HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE)
                .uri("lb://flashsale-service"))

            // ===== Worker Service (requires JWT) =====
            .route("worker", r -> r
                .path("/api/v1/workers/**", "/api/v1/jobs/**")
                .uri("lb://worker-service"))

            // ===== Search Service =====
            .route("search", r -> r
                .path("/api/v1/search/**")
                .uri("lb://search-service"))

            // ===== Notification Service (requires JWT) =====
            .route("notification", r -> r
                .path("/api/v1/notifications/**")
                .uri("lb://notification-service"))

            .build();
    }
}