package com.flashsale.productservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

import lombok.Getter;
import lombok.Setter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/categories/**").permitAll()
                .requestMatchers("/products/**").permitAll()
                .anyRequest().authenticated()
            );
        return http.build();
    }

    @Bean
    @ConfigurationProperties(prefix = "reservation")
    public ReservationProperties reservationProperties() {
        return new ReservationProperties();
    }

    @Getter
    @Setter
    public static class ReservationProperties {
        private Cleanup cleanup = new Cleanup();
        private int ttlMinutes = 15;

        @Getter
        @Setter
        public static class Cleanup {
            private long intervalMs = 180000;
        }
    }
}
