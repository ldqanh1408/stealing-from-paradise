package com.flashsale.apigateway.config;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import com.flashsale.apigateway.filter.JwtAuthGatewayFilterFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
@Configuration
@RequiredArgsConstructor
@Slf4j
public class RouteConfig {
    private final JwtAuthGatewayFilterFactory jwtAuthFilterFactory;
    @Bean
    public RouteLocator routes(RouteLocatorBuilder b) {
        log.info("[Gateway] Initializing routes...");
        return b.routes()
            .route("identity-public", r -> r
                .path("/api/v1/auth/**", "/api/v1/users/register")
                .uri("lb://identity-service"))
            .route("identity-protected", r -> r
                .path("/api/v1/users/**", "/api/v1/loyalty/**")
                .and().method(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE)
                .filters(f -> f.filter(jwtAuthFilterFactory.apply(new JwtAuthGatewayFilterFactory.Config())))
                .uri("lb://identity-service"))
            .route("product-read", r -> r
                .path("/api/v1/products/**", "/api/v1/categories/**")
                .and().method(HttpMethod.GET)
                .uri("lb://product-service"))
            .route("product-write", r -> r
                .path("/api/v1/products/**", "/api/v1/categories/**", "/api/v1/seller/products/**")
                .and().method(HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE)
                .filters(f -> f.filter(jwtAuthFilterFactory.apply(new JwtAuthGatewayFilterFactory.Config())))
                .uri("lb://product-service"))
            .route("cart", r -> r
                .path("/api/v1/cart/**")
                .filters(f -> f.filter(jwtAuthFilterFactory.apply(new JwtAuthGatewayFilterFactory.Config())))
                .uri("lb://cart-service"))
            .route("order", r -> r
                .path("/api/v1/orders/**")
                .filters(f -> f.filter(jwtAuthFilterFactory.apply(new JwtAuthGatewayFilterFactory.Config())))
                .uri("lb://order-service"))
            .route("stripe-webhook", r -> r
                .path("/api/v1/stripe/webhook")
                .uri("lb://payment-service"))
            .route("stripe-onboarding", r -> r
                .path("/api/v1/stripe/onboarding/**")
                .filters(f -> f.filter(jwtAuthFilterFactory.apply(new JwtAuthGatewayFilterFactory.Config())))
                .uri("lb://payment-service"))
            .route("payment", r -> r
                .path("/api/v1/payments/**", "/api/v1/refunds/**")
                .filters(f -> f.filter(jwtAuthFilterFactory.apply(new JwtAuthGatewayFilterFactory.Config())))
                .uri("lb://payment-service"))
            .route("fs-read", r -> r
                .path("/api/v1/flash-sales/**")
                .and().method(HttpMethod.GET)
                .uri("lb://flashsale-service"))
            .route("fs-buy", r -> r
                .path("/api/v1/flash-sales/*/buy")
                .and().method(HttpMethod.POST)
                .filters(f -> f.filter(jwtAuthFilterFactory.apply(new JwtAuthGatewayFilterFactory.Config())))
                .uri("lb://flashsale-service"))
            .route("fs-write", r -> r
                .path("/api/v1/flash-sales/**")
                .and().method(HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE)
                .filters(f -> f.filter(jwtAuthFilterFactory.apply(new JwtAuthGatewayFilterFactory.Config())))
                .uri("lb://flashsale-service"))
            .route("worker", r -> r
                .path("/api/v1/workers/**", "/api/v1/jobs/**")
                .filters(f -> f.filter(jwtAuthFilterFactory.apply(new JwtAuthGatewayFilterFactory.Config())))
                .uri("lb://worker-service"))
            .route("search", r -> r
                .path("/api/v1/search/**")
                .uri("lb://search-service"))
            .route("notification", r -> r
                .path("/api/v1/notifications/**")
                .filters(f -> f.filter(jwtAuthFilterFactory.apply(new JwtAuthGatewayFilterFactory.Config())))
                .uri("lb://notification-service"))
            .build();
    }
}