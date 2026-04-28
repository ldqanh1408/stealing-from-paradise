package com.flashsale.productdomain.config;

import com.flashsale.commonlib.filter.JwtTokenDecoderFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Product Service Security Configuration
 *
 * JwtTokenDecoderFilter decodes X-User-* headers (from gateway) into SecurityContext
 * before @PreAuthorize checks run. Registered INSIDE SecurityFilterChain so the
 * context survives SecurityContextHolderFilter (STATELESS).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtTokenDecoderFilter jwtTokenDecoderFilter;

    public SecurityConfig(JwtTokenDecoderFilter jwtTokenDecoderFilter) {
        this.jwtTokenDecoderFilter = jwtTokenDecoderFilter;
    }

    @Bean
    public FilterRegistrationBean<JwtTokenDecoderFilter> jwtTokenDecoderFilterRegistration(
            JwtTokenDecoderFilter filter) {
        FilterRegistrationBean<JwtTokenDecoderFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .headers(headers -> headers
                .frameOptions(frame -> frame.deny())
                .contentTypeOptions(contentType -> {})
                .xssProtection(xss -> xss.disable())
                .referrerPolicy(referrer -> referrer
                    .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                )
            )
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .anonymous(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .addFilterBefore(jwtTokenDecoderFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}