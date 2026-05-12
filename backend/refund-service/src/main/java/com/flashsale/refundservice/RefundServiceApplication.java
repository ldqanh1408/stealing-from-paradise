package com.flashsale.refundservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.flashsale"})
@EnableDiscoveryClient
@EnableScheduling
public class RefundServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(RefundServiceApplication.class, args);
    }
}
