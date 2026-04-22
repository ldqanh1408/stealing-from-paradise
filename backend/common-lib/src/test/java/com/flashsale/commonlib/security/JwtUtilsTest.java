package com.flashsale.commonlib.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilsTest {

    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "secretKey", "test-secret-key-for-jwt-utils-123456");
        ReflectionTestUtils.setField(jwtUtils, "accessTokenExpiration", 3600L);
        ReflectionTestUtils.setField(jwtUtils, "refreshTokenExpiration", 7200L);
    }

    @Test
    void generateAccessTokenIncludesExpectedClaims() {
        String token = jwtUtils.generateAccessToken("123", "user@example.com", "admin");

        Claims claims = jwtUtils.parseToken(token);

        assertThat(claims.getSubject()).isEqualTo("123");
        assertThat(claims.get("email", String.class)).isEqualTo("user@example.com");
        assertThat(claims.get("role", String.class)).isEqualTo("admin");
        assertThat(claims.get("type", String.class)).isEqualTo("access");
        assertThat(claims.getId()).isNotBlank();
        assertThat(jwtUtils.isAccessToken(token)).isTrue();
        assertThat(jwtUtils.isRefreshToken(token)).isFalse();
        assertThat(jwtUtils.extractUserId(token)).isEqualTo("123");
        assertThat(jwtUtils.extractRole(token)).isEqualTo("admin");
        assertThat(jwtUtils.extractEmail(token)).isEqualTo("user@example.com");
        assertThat(jwtUtils.extractTokenType(token)).isEqualTo("access");
        assertThat(jwtUtils.extractJti(token)).isNotBlank();
    }

    @Test
    void generateRefreshTokenIncludesExpectedClaims() {
        String token = jwtUtils.generateRefreshToken("456");

        Claims claims = jwtUtils.parseToken(token);

        assertThat(claims.getSubject()).isEqualTo("456");
        assertThat(claims.get("type", String.class)).isEqualTo("refresh");
        assertThat(jwtUtils.isRefreshToken(token)).isTrue();
        assertThat(jwtUtils.isAccessToken(token)).isFalse();
        assertThat(jwtUtils.extractRole(token)).isNull();
        assertThat(jwtUtils.extractEmail(token)).isNull();
    }

    @Test
    void isTokenValidReturnsFalseForExpiredTokens() {
        ReflectionTestUtils.setField(jwtUtils, "accessTokenExpiration", -1L);

        String token = jwtUtils.generateAccessToken("123", "user@example.com", "admin");

        assertThat(jwtUtils.isTokenValid(token)).isFalse();
    }

    @Test
    void extractMethodsReturnNullForInvalidTokens() {
        String invalidToken = "not-a-jwt";

        assertThat(jwtUtils.extractUserId(invalidToken)).isNull();
        assertThat(jwtUtils.extractRole(invalidToken)).isNull();
        assertThat(jwtUtils.extractEmail(invalidToken)).isNull();
        assertThat(jwtUtils.extractJti(invalidToken)).isNull();
        assertThat(jwtUtils.extractTokenType(invalidToken)).isNull();
    }
}
