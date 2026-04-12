package com.flashsale.productdomain.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@Configuration
@EnableMongoAuditing     // ← kích hoạt @CreatedDate, @LastModifiedDate
public class MongoConfig {
}

