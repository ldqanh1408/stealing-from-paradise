package com.flashsale.commonlib.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Centralized dev-data configuration properties.
 * Bind via: @EnableConfigurationProperties(DevDataProperties.class)
 *
 * Usage in application-dev.yml:
 *   dev-data:
 *     enabled: true
 *     reset: true
 */
@Data
@Component
@ConfigurationProperties(prefix = "dev-data")
public class DevDataProperties {
    private boolean enabled = false;
    private boolean reset = false;
}
