package com.flashsale.apigateway.filter;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import com.flashsale.commonlib.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.nio.charset.StandardCharsets;

/**
 * JWT Authentication Web Filter - Xác thực JWT tại API Gateway
 *
 * ✅ Được dùng ở API Gateway (thay vì GatewayFilterFactory)
 *
 * Quy trình:
 * 1. Kiểm tra Authorization header ("Bearer <token>")
 * 2. Validate JWT token (hợp lệ, chưa hết hạn)
 * 3. Giải mã token: extract userId, email, role, jti
 * 4. Đặt decoded info vào headers để forward tới service (X-User-Id, X-User-Email, X-User-Role, X-Token-Jti)
 * 5. Service nhận headers → set vào SecurityContext
 * 6. Forward request tới downstream service
 *
 * @since 1.0.0
 * @author API Gateway Team
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthWebFilter implements WebFilter {

    private final JwtUtils jwtUtils;

    /**
     * Filter logic: Validate JWT and add user info to headers
     *
     * @param exchange Server web exchange
     * @param chain Filter chain
     * @return Mono<Void>
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return chain.filter(exchange);
        }

        String token = authHeader.substring(7);

        try {
            // API Gateway VALIDATE token
            if (!jwtUtils.isTokenValid(token)) {
                return onError(exchange, "AUTH_004", "Token không hợp lệ", HttpStatus.UNAUTHORIZED);
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
                .build();

            return chain.filter(exchange.mutate().request(mutated).build());

        } catch (Exception e) {
            return onError(exchange, "AUTH_004", "Token không hợp lệ", HttpStatus.UNAUTHORIZED);
        }
    }

    private Mono<Void> onError(ServerWebExchange exchange, String code, String message, HttpStatus status) {
        log.warn("[JwtAuthFilter] {} — {}", code, message);
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = String.format(
            "{\"success\":false,\"errorCode\":\"%s\",\"message\":\"%s\",\"timestamp\":%d}",
            code, message, System.currentTimeMillis());
        var buf = exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buf));
    }
}

