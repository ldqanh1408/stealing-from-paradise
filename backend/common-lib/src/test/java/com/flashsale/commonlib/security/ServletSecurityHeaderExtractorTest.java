package com.flashsale.commonlib.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ServletSecurityHeaderExtractorTest {

    @Test
    void extractsHeadersFromRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ServletSecurityHeaderExtractor.X_ACCESS_TOKEN, "token");
        request.addHeader(ServletSecurityHeaderExtractor.X_USER_ID, "123");
        request.addHeader(ServletSecurityHeaderExtractor.X_USER_EMAIL, "user@example.com");
        request.addHeader(ServletSecurityHeaderExtractor.X_USER_ROLE, "ADMIN");
        request.addHeader(ServletSecurityHeaderExtractor.X_TOKEN_JTI, "jti-1");

        assertThat(ServletSecurityHeaderExtractor.extractAccessToken(request)).isEqualTo("token");
        assertThat(ServletSecurityHeaderExtractor.extractUserId(request)).isEqualTo("123");
        assertThat(ServletSecurityHeaderExtractor.extractEmail(request)).isEqualTo("user@example.com");
        assertThat(ServletSecurityHeaderExtractor.extractRole(request)).isEqualTo("ADMIN");
        assertThat(ServletSecurityHeaderExtractor.extractJti(request)).isEqualTo("jti-1");
        assertThat(ServletSecurityHeaderExtractor.isAuthenticated(request)).isTrue();
    }

    @Test
    void isAuthenticatedIsFalseWithoutUserId() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThat(ServletSecurityHeaderExtractor.isAuthenticated(request)).isFalse();
    }
}
