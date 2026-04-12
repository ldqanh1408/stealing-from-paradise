package com.flashsale.productdomain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.commonlib.event.payload.ProductApprovedPayload;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    @KafkaListener(topics = KafkaTopics.PRODUCT_APPROVED, groupId = "product-service-group")
    public void onProductApproved(ProductApprovedPayload payload) {
        log.info("Product approved: {}", payload.getProductId());
        // TODO: Implement business logic
    }

    public void createProduct(String sellerId, String name) {
        log.info("Creating product for seller: {}", sellerId);
        // TODO: Implement
    }
}

