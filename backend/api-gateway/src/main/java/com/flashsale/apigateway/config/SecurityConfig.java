package com.flashsale.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

/**
 * API Gateway Security Configuration
 *
 * This config adds CORS support so browser preflight OPTIONS requests pass through.
 * Spring Cloud Gateway's globalcors config (in application.yml) handles the CorsWebFilter.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(org.springframework.security.config.web.server.ServerHttpSecurity http) {
        http
            .csrf(csrf -> csrf.disable())
            .headers(headers -> headers.disable())
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers(HttpMethod.OPTIONS).permitAll()
                .anyExchange().permitAll()
            )
            .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
            .cors(cors -> {});
        return http.build();
    }
}


