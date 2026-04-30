package com.flashsale.identitydomain.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.identitydomain.domain.model.Address;
import com.flashsale.identitydomain.domain.repository.AddressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Handles Kafka request-reply for address lookups from order-service.
 * Listens on order.address.request, replies on order.address.response.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AddressKafkaConsumer {

    private final AddressRepository addressRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = KafkaTopics.ORDER_ADDRESS_REQUEST,
            groupId = "identity-service-address-reply"
    )
    public void onAddressRequest(String message) {
        try {
            Map<String, Object> request = objectMapper.readValue(message, new TypeReference<>() {});
            Object correlationIdObj = request.get("correlation_id");
            Object addressIdObj = request.get("address_id");
            Object userIdObj = request.get("user_id");

            if (correlationIdObj == null || addressIdObj == null || userIdObj == null) {
                log.warn("Invalid address request: missing required fields");
                return;
            }

            String correlationId = correlationIdObj.toString();
            Long addressId = ((Number) addressIdObj).longValue();
            Long userId = ((Number) userIdObj).longValue();

            Optional<Address> addressOpt = addressRepository.findByIdAndUserId(addressId, userId);

            Map<String, Object> response = new HashMap<>();
            response.put("correlation_id", correlationId);

            if (addressOpt.isPresent()) {
                Address address = addressOpt.get();
                response.put("addressId", address.getId());
                response.put("userId", address.getUserId());
                response.put("fullAddress", address.getFullAddress());
                response.put("provinceId", address.getProvinceId());
                response.put("districtId", address.getDistrictId());
                response.put("error", false);
                log.debug("Address response sent: correlationId={}, addressId={}", correlationId, addressId);
            } else {
                response.put("error", true);
                log.debug("Address not found: addressId={}, userId={}", addressId, userId);
            }

            kafkaTemplate.send(KafkaTopics.ORDER_ADDRESS_RESPONSE, correlationId, response);

        } catch (Exception e) {
            log.error("Failed to process address request: {}", e.getMessage(), e);
        }
    }
}
