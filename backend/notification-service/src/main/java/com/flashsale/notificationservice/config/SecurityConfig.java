package com.flashsale.notificationservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;

/**
 * Notification Service Security Configuration
 *
 * Uses WebFluxSecurityConfig from common-lib which:
 * - Disables CSRF
 * - Disables all security headers
 * - Permits all exchanges
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {
    // SecurityWebFilterChain bean provided by WebFluxSecurityConfig from common-lib
}

