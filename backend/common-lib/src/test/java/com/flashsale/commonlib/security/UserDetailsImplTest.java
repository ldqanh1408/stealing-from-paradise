package com.flashsale.commonlib.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import static org.assertj.core.api.Assertions.assertThat;

class UserDetailsImplTest {

    @Test
    void authoritiesAreUppercaseRolePrefixed() {
        UserDetailsImpl userDetails = UserDetailsImpl.builder()
            .role("admin")
            .enabled(true)
            .build();

        assertThat(userDetails.getAuthorities())
            .extracting(GrantedAuthority::getAuthority)
            .containsExactly("ROLE_ADMIN");
    }

    @Test
    void emptyRoleReturnsNoAuthorities() {
        UserDetailsImpl userDetails = UserDetailsImpl.builder()
            .role("")
            .enabled(true)
            .build();

        assertThat(userDetails.getAuthorities()).isEmpty();
    }

    @Test
    void accountLockStateFollowsEnabledFlag() {
        UserDetailsImpl userDetails = UserDetailsImpl.builder()
            .enabled(false)
            .build();

        assertThat(userDetails.isAccountNonLocked()).isFalse();
        assertThat(userDetails.isEnabled()).isFalse();
    }
}
