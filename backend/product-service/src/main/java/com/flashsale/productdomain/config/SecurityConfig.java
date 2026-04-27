package com.flashsale.productdomain.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

/**
 * Product Service Security Configuration
 *
 * Uses MvcSecurityConfig from common-lib which:
 * - Disables CSRF
 * - Disables security headers
 * - Stateless session management
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    // SecurityFilterChain bean provided by common-lib MvcSecurityConfig
}
