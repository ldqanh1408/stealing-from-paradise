package com.flashsale.apigateway.filter;

import com.flashsale.commonlib.security.JwtUtils;
import com.flashsale.apigateway.service.TokenBlacklistCheckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * JWT Authentication Web Filter - Xác thực JWT tại API Gateway
 *
 * ✅ Được dùng ở API Gateway (thay vì GatewayFilterFactory)
 *
 * Quy trình:
 * 1. Skip các public endpoints (không cần JWT)
 * 2. Nếu không có Authorization header → cho qua (WebFilterChain) để downstream service xử lý
 * 3. Validate JWT token (hợp lệ, chưa hết hạn)
 * 4. Giải mã token: extract userId, email, role, jti
 * 5. Đặt decoded info vào headers để forward tới service (X-User-Id, X-User-Email, X-User-Role, X-Token-Jti)
 * 6. Forward request tới downstream service
 *
 * @since 1.0.0
 * @author API Gateway Team
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class JwtAuthWebFilter implements WebFilter {

    private static final List<String> PUBLIC_PATHS = List.of(
        "/api/v1/auth/login",
        "/api/v1/auth/register",
        "/api/v1/auth/refresh",
        "/api/v1/auth/forgot-password",
        "/api/v1/auth/reset-password",
        "/api/v1/stripe/webhook",
        "/actuator/health",
        "/actuator/info"
    );

    private final JwtUtils jwtUtils;
    private final TokenBlacklistCheckService tokenBlacklistCheckService;

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    /**
     * Filter logic: Validate JWT and add user info to headers
     *
     * @param exchange Server web exchange
     * @param chain Filter chain
     * @return Mono<Void>
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestPath = exchange.getRequest().getPath().value();
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        String method = exchange.getRequest().getMethod().name();

        log.info("[JwtAuth] {} {} | public={} | hasAuth={}", method, requestPath, isPublicPath(requestPath), authHeader != null);

        // Skip JWT validation for public endpoints entirely
        if (isPublicPath(requestPath)) {
            return chain.filter(exchange);
        }

        // No token present — let downstream service decide (some endpoints may be optional-auth)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return chain.filter(exchange);
        }

        String token = authHeader.substring(7);

        try {
            // API Gateway VALIDATE token
            if (!jwtUtils.isTokenValid(token)) {
                return onError(exchange, "AUTH_004", "Token không hợp lệ", HttpStatus.UNAUTHORIZED);
            }

            // Check if token is blacklisted
            if (tokenBlacklistCheckService.isTokenBlacklisted(token)) {
                return onError(exchange, "AUTH_005", "Token đã bị hủy (logout)", HttpStatus.UNAUTHORIZED);
            }

            // API Gateway DECODE token - extract user info
            String userId = jwtUtils.extractUserId(token);
            String email = jwtUtils.extractEmail(token);
            String role = jwtUtils.extractRole(token);
            String jti = jwtUtils.extractJti(token);

            // Đặt decoded info vào headers để gửi đến service
            // Services sẽ đọc các headers này và đặt vào SecurityContext
            var mutated = exchange.getRequest().mutate()
                .header("X-User-Id", userId != null ? userId : "")
                .header("X-User-Email", email != null ? email : "")
                .header("X-User-Role", role != null ? role : "")
                .header("X-Token-Jti", jti != null ? jti : "")
                .header("X-Access-Token", token)  // Forward the access token for logout/blacklist
                .build();

            return chain.filter(exchange.mutate().request(mutated).build());

        } catch (Exception e) {
            return onError(exchange, "AUTH_004", "Token không hợp lệ", HttpStatus.UNAUTHORIZED);
        }
    }

    private Mono<Void> onError(ServerWebExchange exchange, String code, String message, HttpStatus status) {
        log.warn("[JwtAuthFilter] {} {} — {} | status={}", exchange.getRequest().getMethod(), exchange.getRequest().getPath().value(), message, status);
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = String.format(
            "{\"success\":false,\"errorCode\":\"%s\",\"message\":\"%s\",\"timestamp\":%d}",
            code, message, System.currentTimeMillis());
        var buf = exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buf));
    }
}

