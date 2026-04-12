package com.flashsale.cartservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.cartservice.domain.repository.CartRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final CartRepository cartRepository;

    public void addItemToCart(Long userId, String skuCode, int quantity) {
        log.info("Adding item to cart - userId: {}, skuCode: {}, quantity: {}", userId, skuCode, quantity);
        // TODO: Implement
    }

    public void removeItemFromCart(Long userId, String skuCode) {
        log.info("Removing item from cart - userId: {}, skuCode: {}", userId, skuCode);
        // TODO: Implement
    }

    public void clearCart(Long userId) {
        log.info("Clearing cart for user: {}", userId);
        // TODO: Implement
    }

    @KafkaListener(topics = KafkaTopics.INVENTORY_ADJUSTED, groupId = "cart-service-group")
    public void onInventoryAdjusted(String message) {
        log.info("Inventory adjusted: {}", message);
        // TODO: Update cart item availability
    }
}

