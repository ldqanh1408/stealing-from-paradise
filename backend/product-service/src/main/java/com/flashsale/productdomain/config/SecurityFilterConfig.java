package com.flashsale.productdomain.config;

import com.flashsale.commonlib.filter.JwtTokenDecoderFilter;
import org.springframework.context.annotation.Import;

/**
 * Imports JwtTokenDecoderFilter from common-lib so it is registered
 * as a Servlet Filter in the product-service filter chain.
 */
@Import(JwtTokenDecoderFilter.class)
public class SecurityFilterConfig {
}
