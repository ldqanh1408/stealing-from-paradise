package com.flashsale.promotiondomain;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class PromotionDomainApplication {

    public static void main(String[] args) {
        SpringApplication.run(PromotionDomainApplication.class, args);
    }

}
