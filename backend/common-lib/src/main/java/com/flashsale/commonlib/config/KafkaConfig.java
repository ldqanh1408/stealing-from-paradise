package com.flashsale.commonlib.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;

@Configuration
@EnableKafka
public class KafkaConfig {
    // Kafka configuration handled via application.yml
    // Producers and consumers are wired via @KafkaListener annotations
}

