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

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthWebFilter implements WebFilter {

    private final JwtUtils jwtUtils;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return chain.filter(exchange);
        }

        String token = authHeader.substring(7);

        try {
            if (!jwtUtils.isTokenValid(token)) {
                return onError(exchange, "AUTH_004", "Token không hợp lệ", HttpStatus.UNAUTHORIZED);
            }

            String userId = jwtUtils.extractUserId(token);
            String email  = jwtUtils.extractEmail(token);
            String role   = jwtUtils.extractRole(token);
            String jti    = jwtUtils.extractJti(token);

            // Forward user info qua headers tới downstream service
            var mutated = exchange.getRequest().mutate()
                .header("X-User-Id",   userId)
                .header("X-User-Role", role)
                .header("X-User-Email", email)
                .header("X-Token-Jti",  jti)
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

