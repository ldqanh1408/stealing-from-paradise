package com.flashsale.identitydomain;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import com.flashsale.commonlib.config.DevDataProperties;

@SpringBootApplication(scanBasePackages = {"com.flashsale"})
@EnableDiscoveryClient
@EnableConfigurationProperties(DevDataProperties.class)
public class IdentityDomainApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityDomainApplication.class, args);
    }

}
