package com.flashsale.paymentdomain.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Payment Service Security Configuration
 *
 * Two filter chains via securityMatcher:
 * - /api/v1/stripe/webhooks → permitAll (authenticated by Stripe-Signature header in controller)
 * - /error                 → permitAll (Spring error dispatch always uses GET)
 * - all other endpoints     → require authenticated user (JWT via X-User-Id headers from Gateway)
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String WEBHOOK_PATH = "/api/v1/stripe/webhooks";
    private static final String ERROR_PATH = "/error";

    @Bean
    public SecurityFilterChain webhookSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(WEBHOOK_PATH)
            .csrf(AbstractHttpConfigurer::disable)
            .headers(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(authz -> authz
                .anyRequest().permitAll()
            );
        return http.build();
    }

    @Bean
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .headers(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(authz -> authz
                // Spring error dispatch (dispatched as GET /error)
                .requestMatchers(ERROR_PATH).permitAll()
                .anyRequest().authenticated()
            );
        return http.build();
    }
}
