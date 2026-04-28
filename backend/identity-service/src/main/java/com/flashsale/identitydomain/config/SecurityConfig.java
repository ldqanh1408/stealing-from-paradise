package com.flashsale.identitydomain.config;

import com.flashsale.commonlib.filter.JwtTokenDecoderFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import com.flashsale.identitydomain.service.CustomUserDetailsService;

/**
 * Security Configuration for Identity Service
 *
 * JwtTokenDecoderFilter runs BEFORE FilterChainProxy at @Order(HIGHEST_PRECEDENCE+10).
 * This causes it to set SecurityContext BEFORE FilterChainProxy, but then
 * SecurityContextHolderFilter (inside the chain, STATELESS) wipes it — leaving null.
 *
 * Fix: disable the top-level servlet filter registration and add the filter
 * INSIDE the SecurityFilterChain via addFilterBefore, after SecurityContextHolderFilter.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService customUserDetailsService;
    private final JwtTokenDecoderFilter jwtTokenDecoderFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder authenticationManagerBuilder =
                http.getSharedObject(AuthenticationManagerBuilder.class);
        authenticationManagerBuilder
                .userDetailsService(customUserDetailsService)
                .passwordEncoder(passwordEncoder());
        return authenticationManagerBuilder.build();
    }

    /**
     * Disable top-level servlet filter registration so the filter only runs
     * inside the SecurityFilterChain where STATELESS won't wipe it immediately.
     */
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

