package com.flashsale.identitydomain.config;

import com.flashsale.commonlib.filter.JwtTokenDecoderFilter;
import org.springframework.context.annotation.Import;

/**
 * Security Filter Configuration for Identity Service
 *
 * Imports JwtTokenDecoderFilter from common-lib so that it gets registered
 * as a Servlet Filter in identity-service's filter chain.
 *
 * JwtTokenDecoderFilter reads the X-User-Id, X-User-Role, etc. headers
 * that API Gateway sets after decoding the JWT, and populates the
 * Spring SecurityContext so that @PreAuthorize annotations work correctly.
 *
 * Without this import, the filter lives in common-lib but is never
 * discovered because Spring Boot only scans com.flashsale.identitydomain.**
 *
 * Currently identity-service only uses @PreAuthorize("hasRole('ADMIN')") on AdminController,
 * but adding this filter ensures the security context is populated for any future
 * authenticated endpoints.
 */
@Import(JwtTokenDecoderFilter.class)
public class SecurityFilterConfig {
}