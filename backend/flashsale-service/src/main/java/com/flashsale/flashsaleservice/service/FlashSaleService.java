package com.flashsale.flashsaleservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import com.flashsale.commonlib.event.KafkaTopics;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlashSaleService {

    @KafkaListener(topics = KafkaTopics.FLASH_SALE_SESSION_STARTED, groupId = "flashsale-service-group")
    public void onSessionStarted(String sessionId) {
        log.info("Flash sale session started: {}", sessionId);
        // TODO: Implement
    }

    public void buyFlashSaleItem(String sessionId, String skuId, String buyerId, int quantity) {
        log.info("Buying flash sale item: sessionId={}, skuId={}, buyerId={}", sessionId, skuId, buyerId);
        // TODO: Implement - DECR Redis stock + publish Kafka
    }
}

