package com.flashsale.commonlib.filter;

import com.flashsale.commonlib.security.UserDetailsImpl;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.server.WebFilterChain;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenDecoderWebFilterTest {

    private final JwtTokenDecoderWebFilter filter = new JwtTokenDecoderWebFilter();

    @Test
    void setsSecurityContextWhenHeadersPresent() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/")
                .header("X-User-Id", "123")
                .header("X-User-Email", "user@example.com")
                .header("X-User-Role", "ADMIN")
                .header("X-Token-Jti", "jti-1")
                .build()
        );

        AtomicReference<Authentication> authenticationRef = new AtomicReference<>();
        WebFilterChain chain = serverWebExchange -> ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .doOnNext(authenticationRef::set)
            .then();

        filter.filter(exchange, chain).block();

        Authentication authentication = authenticationRef.get();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isInstanceOf(UserDetailsImpl.class);
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        assertThat(userDetails.getId()).isEqualTo(123L);
        assertThat(userDetails.getEmail()).isEqualTo("user@example.com");
        assertThat(userDetails.getRole()).isEqualTo("ADMIN");
    }

    @Test
    void doesNotSetContextWhenHeadersMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/").build()
        );

        AtomicReference<Authentication> authenticationRef = new AtomicReference<>();
        WebFilterChain chain = serverWebExchange -> ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .doOnNext(authenticationRef::set)
            .then();

        filter.filter(exchange, chain).block();

        assertThat(authenticationRef.get()).isNull();
    }

    @Test
    void continuesWithoutContextWhenUserIdInvalid() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/")
                .header("X-User-Id", "not-a-number")
                .build()
        );

        AtomicReference<Authentication> authenticationRef = new AtomicReference<>();
        WebFilterChain chain = serverWebExchange -> ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .doOnNext(authenticationRef::set)
            .then();

        filter.filter(exchange, chain).block();

        assertThat(authenticationRef.get()).isNull();
    }
}
