package com.flashsale.commonlib.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.reactive.DispatcherHandler;

/**
 * Common Security Configuration for WebFlux Services
 *
 * ✅ Automatically disables security headers
 * ✅ Disable CSRF, HTTP Basic
 * ✅ Stateless reactive setup
 *
 * Usage: Add @EnableWebFluxSecurity to service config
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass(DispatcherHandler.class)
@EnableWebFluxSecurity
@Slf4j
public class WebFluxSecurityConfig {

    /**
     * Security web filter chain for WebFlux services
     * - CSRF disabled
     * - Headers disabled (removes all security headers)
     * - Reactive stateless session
     */
    @Bean
    @ConditionalOnMissingBean(name = "springSecurityFilterChain")
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        log.info("🚀 [WebFlux] Configuring security web filter chain with disabled headers");

        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .headers(headers -> headers.disable())  // ✨ Remove all security headers
            .authorizeExchange(exchange ->
                exchange.anyExchange().permitAll()
            );

        return http.build();
    }
}


