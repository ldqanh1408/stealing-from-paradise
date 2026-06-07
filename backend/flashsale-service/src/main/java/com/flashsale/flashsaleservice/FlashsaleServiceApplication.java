package com.flashsale.flashsaleservice;

import com.flashsale.commonlib.config.DevDataProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
@EnableConfigurationProperties(DevDataProperties.class)
public class FlashsaleServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlashsaleServiceApplication.class, args);
    }

}
