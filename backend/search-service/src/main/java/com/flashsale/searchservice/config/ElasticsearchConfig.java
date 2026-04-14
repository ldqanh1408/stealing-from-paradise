package com.flashsale.searchservice.config;

import com.flashsale.searchservice.domain.model.SearchProduct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;

/**
 * Elasticsearch Index Configuration
 * Tạo index và mapping cho products khi service khởi động
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class ElasticsearchConfig {

    @Bean
    public CommandLineRunner initializeElasticsearchIndex(ElasticsearchOperations elasticsearchOperations) {
        return args -> {
            try {
                log.info("🚀 Initializing Elasticsearch index configuration...");
                IndexOperations indexOperations = elasticsearchOperations.indexOps(SearchProduct.class);

                // Delete existing index nếu có (optional, uncomment để reset)
                // if (indexOperations.exists()) {
                //     indexOperations.delete();
                //     log.info("Deleted existing 'products' index");
                // }

                // Create index nếu không tồn tại
                if (!indexOperations.exists()) {
                    log.info("Creating 'products' Elasticsearch index...");
                    boolean created = indexOperations.create();
                    if (created) {
                        log.info("✅ 'products' index created successfully");

                        // Put mapping
                        boolean mapped = indexOperations.putMapping();
                        if (mapped) {
                            log.info("✅ Mapping applied successfully");
                        }
                    }
                } else {
                    log.info("✅ 'products' index already exists - skipping creation");
                }
            } catch (Exception e) {
                log.warn("⚠️ Warning initializing Elasticsearch index (service may not be running yet)", e.getMessage());
                // Don't throw - allow service to start even if Elasticsearch unavailable
            }
        };
    }
}
