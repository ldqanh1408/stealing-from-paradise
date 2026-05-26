package com.flashsale.searchservice.config;

import com.flashsale.commonlib.filter.JwtTokenDecoderFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
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
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.GET, "/api/v1/search/products").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/search/products/suggest").permitAll()
                    .requestMatchers("/actuator/**").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/search/reindex").authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/v1/search/reindex/status").authenticated()
                    .anyRequest().permitAll()
            )
            .addFilterBefore(jwtTokenDecoderFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
