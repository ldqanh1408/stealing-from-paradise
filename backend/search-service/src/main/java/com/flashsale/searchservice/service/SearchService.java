package com.flashsale.searchservice.service;

import com.flashsale.commonlib.event.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import com.flashsale.commonlib.event.payload.ProductApprovedPayload;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    @KafkaListener(topics = KafkaTopics.PRODUCT_APPROVED, groupId = "search-service-group")
    public void onProductApproved(ProductApprovedPayload payload) {
        log.info("Indexing approved product: {}", payload.getProductId());
        // TODO: Index to Elasticsearch
    }

    public void search(String query, int page, int size) {
        log.info("Searching: {}", query);
        // TODO: Query Elasticsearch
    }
}

