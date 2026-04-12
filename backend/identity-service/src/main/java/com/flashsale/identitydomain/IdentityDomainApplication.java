package com.flashsale.identitydomain;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = {"com.flashsale"})
@EnableDiscoveryClient
public class IdentityDomainApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityDomainApplication.class, args);
    }

}
