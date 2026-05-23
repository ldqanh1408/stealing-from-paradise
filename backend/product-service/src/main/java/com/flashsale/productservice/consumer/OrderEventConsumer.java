package com.flashsale.productservice.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.productservice.entity.StockReservation;
import com.flashsale.productservice.repository.StockReservationRepository;
import com.flashsale.productservice.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final InventoryService inventoryService;
    private final StockReservationRepository reservationRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "${kafka.topics.order-created:order.created}", groupId = "product-service-group")
    public void onOrderCreated(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode payload = objectMapper.readTree(record.value());
            String orderId = payload.has("orderId") ? payload.get("orderId").asText() : null;
            String sessionId = payload.has("sessionId") ? payload.get("sessionId").asText() : null;

            log.info("Received order.created event: orderId={}, sessionId={}", orderId, sessionId);

            if (sessionId != null) {
                List<StockReservation> pendingReservations = reservationRepository
                        .findBySessionIdAndStatus(sessionId, com.flashsale.productservice.entity.ReservationStatus.PENDING);

                for (StockReservation reservation : pendingReservations) {
                    try {
                        inventoryService.confirmReservation(reservation.getId());
                        log.info("Confirmed reservation: reservationId={}, variantId={}, quantity={}",
                                reservation.getId(), reservation.getVariantId(), reservation.getQuantity());
                    } catch (Exception e) {
                        log.error("Failed to confirm reservation: {}", reservation.getId(), e);
                    }
                }

                log.info("Order created processing complete: orderId={}, sessionId={}, confirmedCount={}",
                        orderId, sessionId, pendingReservations.size());
            }

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing order.created event: {}", record.value(), e);
            ack.acknowledge();
        }
    }

    @KafkaListener(topics = "${kafka.topics.order-cancelled:order.cancelled}", groupId = "product-service-group")
    public void onOrderCancelled(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode payload = objectMapper.readTree(record.value());
            String orderId = payload.has("orderId") ? payload.get("orderId").asText() : null;
            String sessionId = payload.has("sessionId") ? payload.get("sessionId").asText() : null;

            log.info("Received order.cancelled event: orderId={}, sessionId={}", orderId, sessionId);

            if (sessionId != null) {
                List<StockReservation> pendingReservations = reservationRepository
                        .findBySessionIdAndStatus(sessionId, com.flashsale.productservice.entity.ReservationStatus.PENDING);

                for (StockReservation reservation : pendingReservations) {
                    try {
                        inventoryService.releaseReservation(reservation.getId());
                        log.info("Released reservation: reservationId={}, variantId={}, quantity={}",
                                reservation.getId(), reservation.getVariantId(), reservation.getQuantity());
                    } catch (Exception e) {
                        log.error("Failed to release reservation: {}", reservation.getId(), e);
                    }
                }

                log.info("Order cancelled processing complete: orderId={}, sessionId={}, releasedCount={}",
                        orderId, sessionId, pendingReservations.size());
            }

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing order.cancelled event: {}", record.value(), e);
            ack.acknowledge();
        }
    }

    @KafkaListener(topics = "${kafka.topics.order-returned:order.returned}", groupId = "product-service-group")
    public void onOrderReturned(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode payload = objectMapper.readTree(record.value());
            String orderId = payload.has("orderId") ? payload.get("orderId").asText() : null;
            String sessionId = payload.has("sessionId") ? payload.get("sessionId").asText() : null;

            log.info("Received order.returned event: orderId={}, sessionId={}", orderId, sessionId);

            if (payload.has("items") && payload.get("items").isArray()) {
                for (JsonNode item : payload.get("items")) {
                    String variantIdStr = item.has("variantId") ? item.get("variantId").asText() : null;
                    int quantity = item.has("quantity") ? item.get("quantity").asInt() : 0;

                    if (variantIdStr != null && quantity > 0) {
                        try {
                            UUID variantId = UUID.fromString(variantIdStr);
                            inventoryService.restoreStockOnReturn(variantId, quantity);
                            log.info("Restored stock on return: variantId={}, quantity={}", variantId, quantity);
                        } catch (Exception e) {
                            log.error("Failed to restore stock for variantId={}: {}", variantIdStr, e.getMessage());
                        }
                    }
                }
            }

            log.info("Order returned processing complete: orderId={}", orderId);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing order.returned event: {}", record.value(), e);
            ack.acknowledge();
        }
    }
}
