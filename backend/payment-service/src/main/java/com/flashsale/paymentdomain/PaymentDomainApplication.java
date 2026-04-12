package com.flashsale.paymentdomain;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class PaymentDomainApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentDomainApplication.class, args);
    }

}
