package com.flashsale.commonlib.filter;

import com.flashsale.commonlib.security.UserDetailsImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT Token Decoder Filter - Đặt decoded user info vào SecurityContext tại các MVC microservices
 *
 * ✅ Được dùng ở các MVC service (order-service, product-service, cart-service, etc.)
 * ✅ Chỉ hoạt động khi API Gateway forward decoded user info qua headers
 *
 * Quy trình:
 * 1. Kiểm tra security headers từ API Gateway (X-User-Id, X-User-Email, X-User-Role, X-Token-Jti)
 * 2. Tạo UserDetails từ headers
 * 3. Đặt vào Spring SecurityContext
 * 4. Business logic có thể dùng @PreAuthorize, SecurityContextHolder, v.v.
 *
 * @since 1.0.0
 * @author Microservice Team
 */
@Component
@Slf4j
public class JwtTokenDecoderFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // Đọc decoded user info từ headers (đã được decode ở API Gateway)
        String userId = request.getHeader("X-User-Id");
        String email = request.getHeader("X-User-Email");
        String role = request.getHeader("X-User-Role");
        String jti = request.getHeader("X-Token-Jti");

        try {
            // Nếu có user info headers, set vào SecurityContext
            if (userId != null && !userId.isBlank()) {
                // Tạo UserDetails từ headers
                UserDetailsImpl userDetails = UserDetailsImpl.builder()
                    .id(Long.parseLong(userId))
                    .username(userId)
                    .email(email)
                    .role(role)
                    .enabled(true)
                    .build();

                // Tạo authentication token và đặt vào SecurityContext
                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("[JwtTokenDecoder] Set SecurityContext - userId: {}, email: {}, role: {}", userId, email, role);
            }

            filterChain.doFilter(request, response);

        } catch (Exception e) {
            log.warn("[JwtTokenDecoder] Failed to set SecurityContext: {}", e.getMessage());
            // Nếu lỗi, vẫn tiếp tục (có thể endpoint không yêu cầu auth)
            filterChain.doFilter(request, response);
        }
    }
}





