package com.flashsale.searchservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.commonlib.event.payload.ProductApprovedPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.PRODUCT_APPROVED, groupId = "search-service-group")
    public void onProductApproved(String message) {
        try {
            ProductApprovedPayload payload = objectMapper.readValue(message, ProductApprovedPayload.class);
            log.info("Indexing approved product: {}", payload.getProductId());
            // TODO: Index to Elasticsearch
        } catch (Exception e) {
            log.error("Failed to process product.approved event: {}", e.getMessage(), e);
        }
    }

    public void search(String query, int page, int size) {
        log.info("Searching: {}", query);
        // TODO: Query Elasticsearch
    }
}

