package com.flashsale.productdomain.config;

import com.flashsale.commonlib.filter.JwtTokenDecoderFilter;
import org.springframework.context.annotation.Import;

/**
 * Imports JwtTokenDecoderFilter from common-lib so that it gets registered
 * as a Servlet Filter in product-service's filter chain.
 *
 * JwtTokenDecoderFilter reads the X-User-Id, X-User-Role, etc. headers
 * that API Gateway sets after decoding the JWT, and populates the
 * Spring SecurityContext so that @PreAuthorize annotations work correctly.
 *
 * Without this import, the filter lives in common-lib but is never
 * discovered because Spring Boot only scans com.flashsale.productdomain.**
 */
@Import(JwtTokenDecoderFilter.class)
public class SecurityFilterConfig {
}
