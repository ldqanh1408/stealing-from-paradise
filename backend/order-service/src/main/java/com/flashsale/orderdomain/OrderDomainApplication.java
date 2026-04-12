package com.flashsale.orderdomain;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class OrderDomainApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderDomainApplication.class, args);
    }

}
