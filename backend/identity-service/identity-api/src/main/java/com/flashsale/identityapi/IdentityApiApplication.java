package com.flashsale.identityapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
public class IdentityApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityApiApplication.class, args);
    }

}
