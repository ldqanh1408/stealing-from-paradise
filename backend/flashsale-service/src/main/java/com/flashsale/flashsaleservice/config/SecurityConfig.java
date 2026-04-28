package com.flashsale.flashsaleservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;

/**
 * Flash Sale Service Security Configuration
 *
 * Uses WebFluxSecurityConfig from common-lib which:
 * - Disables CSRF (stateless JWT)
 * - Security headers enabled (X-Frame-Options, X-Content-Type-Options, HSTS, Referrer-Policy)
 * - Permits all exchanges (authorization via @PreAuthorize)
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {
    // SecurityWebFilterChain bean provided by WebFluxSecurityConfig from common-lib
}

