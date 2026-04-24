package com.flashsale.devdata;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Dev-only standalone runner that can be used to reset and reseed ALL
 * dev databases (payment, order, product) from a single command.
 *
 * Usage:
 *   cd backend/dev-data-runner
 *   mvn spring-boot:run -Dspring-boot.run.profiles=dev
 *
 * Or set environment:
 *   SPRING_PROFILES_ACTIVE=dev
 *
 * For selective reset, set dev-data.reset-{service}=true.
 */
@SpringBootApplication
@Slf4j
public class DevDataRunner implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(DevDataRunner.class, args);
    }

    @Override
    public void run(String... args) {
        log.info("=================================================");
        log.info("  DevDataRunner — Dev Environment Data Seeder");
        log.info("  Services: payment, order, product");
        log.info("=================================================");
        log.info("");
        log.info("To seed data, enable dev profile on each service:");
        log.info("  SPRING_PROFILES_ACTIVE=dev");
        log.info("");
        log.info("To reset (wipe + reseed), set dev-data.reset=true");
        log.info("  in each service's application-dev.yml");
        log.info("");
        log.info("Or run individual services with:");
        log.info("  payment-service: SPRING_PROFILES_ACTIVE=dev");
        log.info("  order-service:   SPRING_PROFILES_ACTIVE=dev");
        log.info("  product-service: SPRING_PROFILES_ACTIVE=dev");
        log.info("");
    }
}
