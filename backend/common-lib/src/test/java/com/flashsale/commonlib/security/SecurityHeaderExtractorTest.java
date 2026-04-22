package com.flashsale.commonlib.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityHeaderExtractorTest {

    @Test
    void extractsHeadersFromExchange() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/")
                .header(SecurityHeaderExtractor.X_ACCESS_TOKEN, "token")
                .header(SecurityHeaderExtractor.X_USER_ID, "123")
                .header(SecurityHeaderExtractor.X_USER_EMAIL, "user@example.com")
                .header(SecurityHeaderExtractor.X_USER_ROLE, "ADMIN")
                .header(SecurityHeaderExtractor.X_TOKEN_JTI, "jti-1")
                .build()
        );

        assertThat(SecurityHeaderExtractor.extractAccessToken(exchange)).isEqualTo("token");
        assertThat(SecurityHeaderExtractor.extractUserId(exchange)).isEqualTo("123");
        assertThat(SecurityHeaderExtractor.extractEmail(exchange)).isEqualTo("user@example.com");
        assertThat(SecurityHeaderExtractor.extractRole(exchange)).isEqualTo("ADMIN");
        assertThat(SecurityHeaderExtractor.extractJti(exchange)).isEqualTo("jti-1");
        assertThat(SecurityHeaderExtractor.isAuthenticated(exchange)).isTrue();
    }

    @Test
    void isAuthenticatedIsFalseWithoutUserId() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/").build()
        );

        assertThat(SecurityHeaderExtractor.isAuthenticated(exchange)).isFalse();
    }
}
