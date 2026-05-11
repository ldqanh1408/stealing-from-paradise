package com.flashsale.chatservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.chatservice.domain.model.PendingConfirmation;
import com.flashsale.chatservice.domain.repository.PendingConfirmationRepository;
import com.flashsale.commonlib.event.KafkaTopics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Level 3 Tool: System actions that require human confirmation.
 * Creates a PendingConfirmation in MongoDB with 5-min TTL.
 * The SSE stream pauses until the user confirms or rejects via POST /api/ai/confirm.
 */
@Component
@Slf4j
public class SystemActionTool {

    private final PendingConfirmationRepository pendingConfirmationRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public SystemActionTool(PendingConfirmationRepository pendingConfirmationRepository,
                            KafkaTemplate<String, String> kafkaTemplate,
                            ObjectMapper objectMapper) {
        this.pendingConfirmationRepository = pendingConfirmationRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Tool(description = "Perform an irreversible system action that requires user confirmation. " +
            "Use for actions like canceling orders or requesting refunds. " +
            "actionType can be CANCEL_ORDER or REQUEST_REFUND.")
    public String performSystemAction(
            @ToolParam(description = "Type of action: CANCEL_ORDER or REQUEST_REFUND") String actionType,
            @ToolParam(description = "The order ID to act upon") String orderId,
            @ToolParam(description = "Reason for the action") String reason) {

        log.info("[SystemActionTool] Level-3 action requested: type={}, orderId={}", actionType, orderId);

        String confirmId = UUID.randomUUID().toString();
        String sessionId = ToolContext.getSessionId();
        Long userId = ToolContext.getUserId();
        String summary = buildSummary(actionType, orderId, reason);

        String toolArguments;
        try {
            toolArguments = objectMapper.writeValueAsString(Map.of(
                    "actionType", actionType,
                    "orderId", orderId,
                    "reason", reason
            ));
        } catch (JsonProcessingException e) {
            toolArguments = "{\"actionType\":\"" + actionType + "\",\"orderId\":\"" + orderId + "\"}";
        }

        PendingConfirmation confirmation = PendingConfirmation.builder()
                .id(confirmId)
                .sessionId(sessionId)
                .userId(userId)
                .toolName("performSystemAction")
                .toolArguments(toolArguments)
                .summary(summary)
                .status("PENDING")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        try {
            pendingConfirmationRepository.save(confirmation).block();
            log.info("[SystemActionTool] PendingConfirmation created: id={}", confirmId);
        } catch (Exception e) {
            log.error("[SystemActionTool] Failed to save PendingConfirmation", e);
            return "{\"error\": \"Failed to create confirmation request\"}";
        }

        // Mark Level 3 as pending so ChatService stops streaming
        ToolContext.setLevel3Pending(true);

        // Add events for SSE
        ToolContext.addEvent(new ToolContext.ToolEvent("tool_start", "performSystemAction", null));

        String confirmationData;
        try {
            confirmationData = objectMapper.writeValueAsString(Map.of(
                    "confirmId", confirmId,
                    "actionType", actionType,
                    "orderId", orderId,
                    "summary", summary
            ));
        } catch (JsonProcessingException e) {
            confirmationData = "{\"confirmId\":\"" + confirmId + "\",\"summary\":\"" + summary + "\"}";
        }

        ToolContext.addEvent(new ToolContext.ToolEvent("confirmation_required", "performSystemAction", confirmationData));
        ToolContext.addEvent(new ToolContext.ToolEvent("tool_done", "performSystemAction",
                "{\"status\":\"pending_confirmation\",\"confirmId\":\"" + confirmId + "\"}"));

        // Publish Kafka event
        try {
            String kafkaPayload = objectMapper.writeValueAsString(Map.of(
                    "eventType", KafkaTopics.AI_CHAT_TOOL_CALL_EXECUTED,
                    "sessionId", sessionId,
                    "userId", userId,
                    "toolName", "performSystemAction",
                    "actionType", actionType,
                    "orderId", orderId,
                    "confirmId", confirmId,
                    "status", "PENDING_CONFIRMATION",
                    "timestamp", System.currentTimeMillis()
            ));
            kafkaTemplate.send(KafkaTopics.AI_CHAT_TOOL_CALL_EXECUTED, kafkaPayload);
        } catch (Exception e) {
            log.warn("[SystemActionTool] Failed to publish Kafka event", e);
        }

        return "{\"confirmId\":\"" + confirmId + "\",\"summary\":\"" + summary + "\",\"status\":\"pending_confirmation\"}";
    }

    private String buildSummary(String actionType, String orderId, String reason) {
        String actionLabel = switch (actionType) {
            case "CANCEL_ORDER" -> "Hủy đơn hàng";
            case "REQUEST_REFUND" -> "Yêu cầu hoàn tiền";
            default -> "Thực hiện thao tác";
        };
        return actionLabel + " #" + orderId + (reason != null && !reason.isBlank() ? " - " + reason : "");
    }
}
