package com.flashsale.productdomain;

import com.flashsale.commonlib.config.DevDataProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
@EnableConfigurationProperties(DevDataProperties.class)
public class ProductDomainApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductDomainApplication.class, args);
    }

}
