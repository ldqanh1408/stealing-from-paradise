package com.flashsale.orderdomain.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

/**
 * Order Service Security Configuration
 * 
 * Uses MvcSecurityConfig from common-lib which:
 * - Disables CSRF
 * - Disables all security headers
 * - Stateless session management
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    // SecurityFilterChain bean provided by MvcSecurityConfig from common-lib
}

