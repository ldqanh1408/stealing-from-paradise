package com.flashsale.productservice.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
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

    /**
     * Nhận order.cancelled → release stock reservations.
     */
    @KafkaListener(topics = "${kafka.topics.order-cancelled:order.cancelled}", groupId = "product-service-group")
    public void onOrderCancelled(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode payload = objectMapper.readTree(record.value());
            String sessionId = payload.has("sessionId") ? payload.get("sessionId").asText()
                    : (payload.has("session_id") ? payload.get("session_id").asText() : null);

            log.info("Received order.cancelled event: sessionId={}", sessionId);
            processRelease(sessionId);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing order.cancelled event: {}", record.value(), e);
            ack.acknowledge();
        }
    }

    /**
     * Nhận order.paid → confirm stock reservations.
     * Đây là thời điểm thanh toán thành công — stock reservation được xác nhận vĩnh viễn.
     */
    @KafkaListener(topics = "${kafka.topics.order-paid:order.paid}", groupId = "product-service-group")
    public void onOrderPaid(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode payload = objectMapper.readTree(record.value());
            String sessionId = payload.has("sessionId") ? payload.get("sessionId").asText()
                    : (payload.has("session_id") ? payload.get("session_id").asText() : null);

            log.info("Received order.paid event: sessionId={}", sessionId);
            processConfirm(sessionId);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing order.paid event: {}", record.value(), e);
            ack.acknowledge();
        }
    }

    /**
     * Nhận order.payment_failed → release stock reservations.
     * Thanh toán thất bại → stock được giải phóng cho người khác mua.
     */
    @KafkaListener(topics = "${kafka.topics.order-payment-failed:order.payment_failed}", groupId = "product-service-group")
    public void onOrderPaymentFailed(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode payload = objectMapper.readTree(record.value());
            String sessionId = payload.has("sessionId") ? payload.get("sessionId").asText()
                    : (payload.has("session_id") ? payload.get("session_id").asText() : null);

            log.info("Received order.payment_failed event: sessionId={}", sessionId);
            processRelease(sessionId);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing order.payment_failed event: {}", record.value(), e);
            ack.acknowledge();
        }
    }

    /**
     * Nhận order.returned → restore stock.
     */
    @KafkaListener(topics = "${kafka.topics.order-returned:order.returned}", groupId = "product-service-group")
    public void onOrderReturned(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode payload = objectMapper.readTree(record.value());
            String sessionId = payload.has("sessionId") ? payload.get("sessionId").asText()
                    : (payload.has("session_id") ? payload.get("session_id").asText() : null);

            log.info("Received order.returned event: sessionId={}", sessionId);

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

            log.info("Order returned processing complete: sessionId={}", sessionId);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing order.returned event: {}", record.value(), e);
            ack.acknowledge();
        }
    }

    private void processConfirm(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            log.warn("order.paid event has no sessionId — cannot confirm reservations");
            return;
        }

        List<StockReservation> pending = reservationRepository
                .findBySessionIdAndStatus(sessionId,
                        com.flashsale.productservice.entity.ReservationStatus.PENDING);

        for (StockReservation reservation : pending) {
            try {
                inventoryService.confirmReservation(reservation.getId());
                log.info("Confirmed reservation: reservationId={}, variantId={}, quantity={}",
                        reservation.getId(), reservation.getVariantId(), reservation.getQuantity());
            } catch (Exception e) {
                log.error("Failed to confirm reservation: {}", reservation.getId(), e);
            }
        }

        log.info("order.paid processing complete: sessionId={}, confirmedCount={}", sessionId, pending.size());
    }

    private void processRelease(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            log.warn("Event has no sessionId — cannot release reservations");
            return;
        }

        List<StockReservation> pending = reservationRepository
                .findBySessionIdAndStatus(sessionId,
                        com.flashsale.productservice.entity.ReservationStatus.PENDING);

        for (StockReservation reservation : pending) {
            try {
                inventoryService.releaseReservation(reservation.getId());
                log.info("Released reservation: reservationId={}, variantId={}, quantity={}",
                        reservation.getId(), reservation.getVariantId(), reservation.getQuantity());
            } catch (Exception e) {
                log.error("Failed to release reservation: {}", reservation.getId(), e);
            }
        }

        log.info("Stock release processing complete: sessionId={}, releasedCount={}", sessionId, pending.size());
    }
}
