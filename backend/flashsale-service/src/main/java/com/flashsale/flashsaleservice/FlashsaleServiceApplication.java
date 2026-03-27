package com.flashsale.flashsaleservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class FlashsaleServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlashsaleServiceApplication.class, args);
    }

}
