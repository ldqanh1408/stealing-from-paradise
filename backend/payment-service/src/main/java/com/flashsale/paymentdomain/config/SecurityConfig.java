package com.flashsale.paymentdomain.config;

import com.flashsale.commonlib.filter.JwtTokenDecoderFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Payment Service Security Configuration
 *
 * JwtTokenDecoderFilter is added into the SecurityFilterChain via addFilterBefore,
 * BEFORE AuthorizationFilter (which evaluates @PreAuthorize). This ensures the
 * SecurityContext is populated from gateway X-User-* headers before role checks run.
 *
 * The @Import variant is NOT used here because @Import alone registers the filter
 * in the servlet filter chain at HIGHEST_PRECEDENCE+10, which is AFTER
 * AuthorizationFilter (Integer.MIN_VALUE) — causing @PreAuthorize to see no auth.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtTokenDecoderFilter jwtTokenDecoderFilter;

    public SecurityConfig(JwtTokenDecoderFilter jwtTokenDecoderFilter) {
        this.jwtTokenDecoderFilter = jwtTokenDecoderFilter;
    }

    /**
     * Disable Spring Boot's auto-registration of JwtTokenDecoderFilter as a top-level
     * servlet filter. Otherwise it runs BEFORE FilterChainProxy, sets the SecurityContext,
     * but then SecurityContextHolderFilter (inside the chain, with STATELESS) wipes it.
     * The in-chain re-registration via addFilterBefore would then be skipped by
     * OncePerRequestFilter's already-filtered guard, leaving auth as null.
     *
     * With this disabled, the filter only runs INSIDE the SecurityFilterChain, after
     * SecurityContextHolderFilter, so the context survives until @PreAuthorize.
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
            .headers(AbstractHttpConfigurer::disable)
            .anonymous(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .addFilterBefore(jwtTokenDecoderFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
