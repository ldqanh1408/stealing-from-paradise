package com.flashsale.flashsaleservice.consumer;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.flashsaleservice.service.FlashSaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderAddressResponseConsumer {

    private final FlashSaleService flashSaleService;

    @KafkaListener(topics = KafkaTopics.ORDER_ADDRESS_RESPONSE, groupId = "flashsale-service-address-group")
    public void onMessage(String message) {
        flashSaleService.onAddressResponse(message);
    }
}
