package com.flashsale.commonlib.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * Common Security Configuration for MVC Services
 * 
 * ✅ Automatically disables security headers
 * ✅ Disable CSRF, Form Login, HTTP Basic
 * ✅ Stateless session management
 * 
 * Usage: Add @EnableWebSecurity to service config
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({DispatcherServlet.class, SecurityFilterChain.class})
@EnableWebSecurity
@Slf4j
public class MvcSecurityConfig {

    /**
     * Security filter chain for MVC services
     * - CSRF disabled
     * - Headers disabled (removes all security headers)
     * - Stateless session
     */
    @Bean
    @ConditionalOnMissingBean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        log.info("🔐 [MVC] Configuring security filter chain with disabled headers");
        
        http
            .csrf(AbstractHttpConfigurer::disable)
            .headers(AbstractHttpConfigurer::disable)  // ✨ Remove all security headers
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable);

        return http.build();
    }
}

