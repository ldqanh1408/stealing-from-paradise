package com.flashsale.flashsaleservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.flashsaleservice.domain.model.FlashSaleItem;
import com.flashsale.flashsaleservice.domain.repository.FlashSaleItemRepository;
import com.flashsale.flashsaleservice.dto.request.ApproveItemRequest;
import com.flashsale.flashsaleservice.dto.request.CreateFlashSaleItemRequest;
import com.flashsale.flashsaleservice.dto.request.RejectItemRequest;
import com.flashsale.flashsaleservice.dto.response.FlashSaleItemResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlashSaleItemService {

    private final FlashSaleItemRepository itemRepo;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final FlashSaleItemMapper itemMapper;

    public Mono<FlashSaleItemResponse> createFlashSaleItem(Long sessionId, Long sellerId, CreateFlashSaleItemRequest req) {
        FlashSaleItem item = FlashSaleItem.builder()
                .sessionId(sessionId)
                .sellerId(sellerId)
                .skuCode(req.getSkuCode())
                .flashPrice(req.getFlashPrice())
                .flashStock(req.getFlashStock())
                .limitPerUser(req.getLimitPerUser() != null ? req.getLimitPerUser() : 1)
                .soldQty(0)
                .status("APPROVED")
                .build();
        return itemRepo.save(item)
                .doOnSuccess(saved -> {
                    publishItemRegisteredEvent(saved);
                    publishFlashSaleItemEvent(KafkaTopics.FLASH_SALE_ITEM_APPROVED, saved,
                            Map.of("note", "Auto-approved at registration"));
                })
                .map(itemMapper::toItemResponse);
    }

    private void publishItemRegisteredEvent(FlashSaleItem item) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("event_id", "evt_" + System.currentTimeMillis() + "_" + item.getId());
            event.put("event_type", KafkaTopics.FLASH_SALE_ITEM_REGISTERED);
            event.put("fs_item_id", item.getId());
            event.put("session_id", item.getSessionId());
            event.put("sku_code", item.getSkuCode());
            event.put("seller_id", item.getSellerId());
            event.put("flash_price", item.getFlashPrice());
            event.put("flash_stock", item.getFlashStock());
            event.put("status", item.getStatus());
            event.put("timestamp", Instant.now().toString());
            kafkaTemplate.send(KafkaTopics.FLASH_SALE_ITEM_REGISTERED,
                    String.valueOf(item.getId()), objectMapper.writeValueAsString(event));
            log.info("Published flash_sale.item_registered: fsItemId={}, sessionId={}, sellerId={}",
                    item.getId(), item.getSessionId(), item.getSellerId());
        } catch (Exception e) {
            log.error("Failed to publish flash_sale.item_registered: {}", e.getMessage(), e);
        }
    }

    private void publishFlashSaleItemEvent(String topic, FlashSaleItem item, Map<String, Object> extraFields) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("event_id", "evt_" + System.currentTimeMillis() + "_" + item.getId());
            event.put("event_type", topic);
            event.put("fs_item_id", item.getId());
            event.put("session_id", item.getSessionId());
            event.put("sku_code", item.getSkuCode());
            event.put("seller_id", item.getSellerId());
            event.put("flash_price", item.getFlashPrice());
            event.put("flash_stock", item.getFlashStock());
            event.put("status", item.getStatus());
            if (extraFields != null) {
                event.putAll(extraFields);
            }
            event.put("timestamp", Instant.now().toString());
            kafkaTemplate.send(topic, String.valueOf(item.getId()), objectMapper.writeValueAsString(event));
            log.info("Published {}: fsItemId={}, sessionId={}, sellerId={}",
                    topic, item.getId(), item.getSessionId(), item.getSellerId());
        } catch (Exception e) {
            log.error("Failed to publish {}: {}", topic, e.getMessage(), e);
        }
    }

    public Mono<FlashSaleItemResponse> approveItem(Long sessionId, Long itemId, ApproveItemRequest req) {
        return itemRepo.findById(itemId)
                .switchIfEmpty(Mono.error(new AppException(ErrorCode.NOT_FOUND, "Khong tim thay Flash Sale item")))
                .flatMap(item -> {
                    if (!sessionId.equals(item.getSessionId())) {
                        return Mono.error(new AppException(ErrorCode.NOT_FOUND, "Flash Sale item khong thuoc session nay"));
                    }
                    item.setStatus("APPROVED");
                    return itemRepo.save(item);
                })
                .doOnSuccess(saved -> publishFlashSaleItemEvent(KafkaTopics.FLASH_SALE_ITEM_APPROVED, saved,
                        Map.of("note", req != null && req.getNote() != null ? req.getNote() : "")))
                .map(itemMapper::toItemResponse);
    }

    public Mono<FlashSaleItemResponse> rejectItem(Long sessionId, Long itemId, RejectItemRequest req) {
        return itemRepo.findById(itemId)
                .switchIfEmpty(Mono.error(new AppException(ErrorCode.NOT_FOUND, "Khong tim thay Flash Sale item")))
                .flatMap(item -> {
                    if (!sessionId.equals(item.getSessionId())) {
                        return Mono.error(new AppException(ErrorCode.NOT_FOUND, "Flash Sale item khong thuoc session nay"));
                    }
                    item.setStatus("REJECTED");
                    return itemRepo.save(item);
                })
                .doOnSuccess(saved -> publishFlashSaleItemEvent(KafkaTopics.FLASH_SALE_ITEM_REJECTED, saved,
                        Map.of("reject_reason", req != null && req.getRejectReason() != null ? req.getRejectReason() : "")))
                .map(itemMapper::toItemResponse);
    }
}
