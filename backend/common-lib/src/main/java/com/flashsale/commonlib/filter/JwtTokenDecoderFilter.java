package com.flashsale.commonlib.filter;

import com.flashsale.commonlib.security.UserDetailsImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT Token Decoder Filter — Decodes X-User-* headers into SecurityContext.
 *
 * These headers are set by the API Gateway after validating the Bearer JWT token.
 * This filter populates the SecurityContext BEFORE Spring Security's AuthorizationFilter
 * runs, so @PreAuthorize annotations work correctly.
 *
 * Must run at HIGHEST_PRECEDENCE + 10 to precede AuthorizationFilter (order ~-100).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@Slf4j
public class JwtTokenDecoderFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String userId = request.getHeader("X-User-Id");
        String email = request.getHeader("X-User-Email");
        String role = request.getHeader("X-User-Role");
        String jti = request.getHeader("X-Token-Jti");

        if (userId != null && !userId.isBlank()) {
            try {
                var authorities = (role != null && !role.isEmpty())
                        ? Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                        : Collections.<org.springframework.security.core.GrantedAuthority>emptyList();

                UserDetailsImpl userDetails = UserDetailsImpl.builder()
                        .id(Long.parseLong(userId))
                        .username(userId)
                        .email(email)
                        .role(role)
                        .enabled(true)
                        .build();

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, authorities);

                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("[JwtTokenDecoder] Set SecurityContext - userId: {}, email: {}, role: {}", userId, email, role);
            } catch (Exception e) {
                log.warn("[JwtTokenDecoder] Failed to set SecurityContext: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }
}
