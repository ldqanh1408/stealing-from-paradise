package com.flashsale.productdomain;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class ProductDomainApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductDomainApplication.class, args);
    }

}
