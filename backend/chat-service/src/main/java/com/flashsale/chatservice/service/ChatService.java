package com.flashsale.chatservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.chatservice.domain.model.ChatMessage;
import com.flashsale.chatservice.domain.model.ChatSession;
import com.flashsale.chatservice.domain.model.PendingConfirmation;
import com.flashsale.chatservice.domain.repository.ChatMessageRepository;
import com.flashsale.chatservice.domain.repository.ChatSessionRepository;
import com.flashsale.chatservice.domain.repository.PendingConfirmationRepository;
import com.flashsale.commonlib.event.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private static final String SYSTEM_PROMPT = """
            You are FlashBot, a helpful Vietnamese shopping assistant for the FlashSale e-commerce platform.
            You help users find products, check their orders, and perform actions on their behalf.

            Guidelines:
            - Always respond in Vietnamese (tiếng Việt)
            - Be friendly, concise, and helpful
            - Use available tools to look up real product and order information
            - For sensitive actions (canceling orders, requesting refunds), always use the system action tool
              which will ask the user for confirmation before proceeding
            - When showing products, highlight key information: name, price, and availability
            - When showing orders, include status, items, and tracking information
            - If you don't know something, be honest and suggest how the user can find out
            """;

    private static final int MAX_HISTORY_MESSAGES = 20;

    private final RateLimiter rateLimiter = new RateLimiter();

    private final ChatModel chatModel;
    private final ChatMessageRepository messageRepo;
    private final ChatSessionRepository sessionRepo;
    private final PendingConfirmationRepository confirmationRepo;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    // Tool beans for Spring AI tool calling
    private final ProductSearchTool productSearchTool;
    private final OrderQueryTool orderQueryTool;
    private final SystemActionTool systemActionTool;

    // ────────────────────────────────────────────────────────────────────────
    //  Stream Chat (SSE) — main endpoint
    // ────────────────────────────────────────────────────────────────────────

    public Flux<ServerSentEvent<String>> streamChat(String sessionId, String message, Long userId, String accessToken) {
        return Flux.defer(() -> {
            if (!rateLimiter.tryAcquireChat(userId)) {
                return Flux.just(errorEvent("Rate limit exceeded. Tối đa 20 tin nhắn mỗi phút."));
            }

            ToolContext.setAccessToken(accessToken);
            ToolContext.setUserId(userId);

            return getOrCreateSession(sessionId, userId)
                    .flatMapMany(session -> {
                        String sid = session.getId();
                        ToolContext.setSessionId(sid);
                        updateSessionActivity(sid).subscribe();

                        return saveUserMessage(sid, message, userId)
                                .flatMapMany(seqNo -> processConversation(sid, message, userId));
                    })
                    .doFinally(signal -> ToolContext.clear());
        });
    }

    // ────────────────────────────────────────────────────────────────────────
    //  Conversation processing (two-phase)
    // ────────────────────────────────────────────────────────────────────────

    private Flux<ServerSentEvent<String>> processConversation(String sessionId, String message, Long userId) {
        return loadRecentHistory(sessionId)
                .flatMapMany(history -> {
                    List<Message> llmMessages = buildInitialMessages(history, message);
                    return executeLlmWithTools(sessionId, userId, llmMessages);
                });
    }

    private List<Message> buildInitialMessages(List<ChatMessage> history, String userMessage) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        for (ChatMessage dbMsg : history) {
            messages.add(dbMessageToLlm(dbMsg));
        }
        messages.add(new UserMessage(userMessage));
        return messages;
    }

    private Message dbMessageToLlm(ChatMessage dbMsg) {
        return switch (dbMsg.getRole()) {
            case "USER" -> new UserMessage(dbMsg.getContent());
            case "ASSISTANT" -> new AssistantMessage(dbMsg.getContent());
            case "TOOL_CALL" -> new AssistantMessage(
                    "[ToolCall: " + dbMsg.getToolName() + "] " + dbMsg.getContent());
            case "TOOL_RESULT" -> new UserMessage(
                    "[ToolResult: " + dbMsg.getToolName() + "] " + dbMsg.getContent());
            default -> new UserMessage(dbMsg.getContent());
        };
    }

    /**
     * Phase 1: Non-streaming LLM call with tools.
     * Spring AI handles tool execution internally.
     * Tool events are collected via ThreadLocal ToolContext.
     * Phase 2: Emit collected tool events + stream final text as delta events.
     */
    private Flux<ServerSentEvent<String>> executeLlmWithTools(
            String sessionId, Long userId, List<Message> messages) {

        ChatClient chatClient = ChatClient.create(chatModel);

        try {
            ChatResponse response = chatClient.prompt()
                    .messages(messages)
                    .tools(productSearchTool, orderQueryTool, systemActionTool)
                    .call()
                    .chatResponse();

            String finalText = response.getResult().getOutput().getContent();

            // Save assistant message
            return nextSequenceNo(sessionId)
                    .flatMapMany(seqNo -> {
                        ChatMessage assistantMsg = ChatMessage.builder()
                                .sessionId(sessionId)
                                .role("ASSISTANT")
                                .content(finalText)
                                .sequenceNo(seqNo)
                                .createdAt(LocalDateTime.now())
                                .build();
                        return messageRepo.save(assistantMsg);
                    })
                    .flatMapMany(saved -> {
                        List<ToolContext.ToolEvent> toolEvents = ToolContext.getEvents();
                        Flux<ServerSentEvent<String>> toolEventFlux = Flux.fromIterable(toolEvents)
                                .map(this::mapToolEventToSse);

                        Flux<ServerSentEvent<String>> textFlux;
                        if (ToolContext.isLevel3Pending()) {
                            textFlux = Flux.empty();
                        } else {
                            textFlux = streamTextAsDeltas(finalText);
                        }

                        publishMessageSent(sessionId, userId).subscribe();

                        return Flux.concat(
                                toolEventFlux,
                                textFlux,
                                Flux.just(doneEvent())
                        );
                    });
        } catch (Exception e) {
            log.error("[ChatService] LLM call failed for session {}", sessionId, e);
            return Flux.just(errorEvent("AI service error: " + e.getMessage()));
        }
    }

    private ServerSentEvent<String> mapToolEventToSse(ToolContext.ToolEvent event) {
        return switch (event.eventType()) {
            case "tool_start" -> ServerSentEvent.<String>builder()
                    .event("tool_start")
                    .data("{\"tool\":\"" + event.toolName() + "\"}")
                    .build();
            case "tool_done" -> ServerSentEvent.<String>builder()
                    .event("tool_done")
                    .data(event.data() != null ? event.data() : "{\"tool\":\"" + event.toolName() + "\"}")
                    .build();
            case "confirmation_required" -> ServerSentEvent.<String>builder()
                    .event("confirmation_required")
                    .data(event.data())
                    .build();
            case "products" -> ServerSentEvent.<String>builder()
                    .event("products")
                    .data(event.data())
                    .build();
            case "order" -> ServerSentEvent.<String>builder()
                    .event("order")
                    .data(event.data())
                    .build();
            default -> ServerSentEvent.<String>builder()
                    .event(event.eventType())
                    .data(event.data() != null ? event.data() : "")
                    .build();
        };
    }

    private Flux<ServerSentEvent<String>> streamTextAsDeltas(String text) {
        if (text == null || text.isBlank()) {
            return Flux.empty();
        }
        // Split on word boundaries for pseudo-streaming effect
        String[] chunks = text.split("(?<=\\s)");
        return Flux.fromArray(chunks)
                .filter(s -> !s.isEmpty())
                .map(chunk -> ServerSentEvent.<String>builder()
                        .event("delta")
                        .data(chunk)
                        .build());
    }

    private ServerSentEvent<String> doneEvent() {
        return ServerSentEvent.<String>builder()
                .event("done")
                .data("[DONE]")
                .build();
    }

    private ServerSentEvent<String> errorEvent(String message) {
        return ServerSentEvent.<String>builder()
                .event("error")
                .data("{\"error\":\"" + message + "\"}")
                .build();
    }

    // ────────────────────────────────────────────────────────────────────────
    //  Session management
    // ────────────────────────────────────────────────────────────────────────

    public Mono<ChatSession> createSession(Long userId) {
        ChatSession session = ChatSession.builder()
                .userId(userId)
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return sessionRepo.save(session)
                .doOnSuccess(s -> log.info("[ChatService] Session created: id={}, userId={}", s.getId(), userId));
    }

    public Mono<Void> closeSession(String sessionId, Long userId) {
        return sessionRepo.findByIdAndUserId(sessionId, userId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found")))
                .flatMap(session -> {
                    session.setStatus("CLOSED");
                    session.setClosedAt(LocalDateTime.now());
                    session.setUpdatedAt(LocalDateTime.now());
                    return sessionRepo.save(session);
                })
                .doOnSuccess(s -> log.info("[ChatService] Session closed: id={}", sessionId))
                .then();
    }

    private Mono<ChatSession> getOrCreateSession(String sessionId, Long userId) {
        if (sessionId != null && !sessionId.isBlank()) {
            return sessionRepo.findByIdAndUserId(sessionId, userId)
                    .switchIfEmpty(createSession(userId));
        }
        return createSession(userId);
    }

    private Mono<Void> updateSessionActivity(String sessionId) {
        return sessionRepo.findById(sessionId)
                .flatMap(session -> {
                    session.setUpdatedAt(LocalDateTime.now());
                    return sessionRepo.save(session);
                })
                .then();
    }

    // ────────────────────────────────────────────────────────────────────────
    //  Confirm / Reject Level-3 action
    // ────────────────────────────────────────────────────────────────────────

    public Mono<ChatMessage> confirmAction(String confirmId, boolean confirmed, Long userId) {
        return confirmationRepo.findByIdAndUserId(confirmId, userId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Confirmation not found or expired")))
                .flatMap(confirmation -> {
                    if (!"PENDING".equals(confirmation.getStatus())) {
                        return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                                "Confirmation already resolved"));
                    }

                    if (confirmed) {
                        return executeConfirmedAction(confirmation);
                    } else {
                        return rejectAction(confirmation);
                    }
                });
    }

    private Mono<ChatMessage> executeConfirmedAction(PendingConfirmation confirmation) {
        String actionType = extractActionType(confirmation.getToolArguments());
        String orderId = extractOrderId(confirmation.getToolArguments());
        String confirmId = confirmation.getId();

        log.info("[ChatService] Executing confirmed action: type={}, orderId={}", actionType, orderId);

        String actionUrl = switch (actionType) {
            case "CANCEL_ORDER" -> "/v1/orders/" + orderId + "/cancel";
            case "REQUEST_REFUND" -> "/v1/orders/parent/" + orderId + "/refund";
            default -> null;
        };

        if (actionUrl == null) {
            return rejectWithError(confirmation, "Unknown action type: " + actionType);
        }

        String accessToken = ToolContext.getAccessToken();

        return webClientBuilder.build()
                .post()
                .uri("http://order-service" + actionUrl)
                .header("X-Access-Token", accessToken != null ? accessToken : "")
                .retrieve()
                .bodyToMono(String.class)
                .onErrorReturn("{\"error\": \"Action execution failed\"}")
                .flatMap(result -> {
                    confirmation.setStatus("CONFIRMED");
                    confirmation.setConfirmedAt(LocalDateTime.now());
                    confirmation.setUpdatedAt(LocalDateTime.now());

                    return confirmationRepo.save(confirmation)
                            .then(saveToolResultMessage(confirmation.getSessionId(),
                                    confirmation.getToolName(), result, confirmation.getUserId()))
                            .then(publishConfirmationResolved(confirmation.getSessionId(),
                                    confirmation.getUserId(), confirmId, "CONFIRMED"))
                            .then(generateConfirmationResponse(confirmation.getSessionId(),
                                    confirmation.getUserId(), actionType, orderId, result, true));
                });
    }

    private Mono<ChatMessage> rejectAction(PendingConfirmation confirmation) {
        confirmation.setStatus("REJECTED");
        confirmation.setUpdatedAt(LocalDateTime.now());

        return confirmationRepo.save(confirmation)
                .then(saveToolResultMessage(confirmation.getSessionId(),
                        confirmation.getToolName(),
                        "{\"status\":\"rejected\",\"message\":\"User rejected the action\"}",
                        confirmation.getUserId()))
                .then(publishConfirmationResolved(confirmation.getSessionId(),
                        confirmation.getUserId(), confirmation.getId(), "REJECTED"))
                .then(generateConfirmationResponse(confirmation.getSessionId(),
                        confirmation.getUserId(),
                        extractActionType(confirmation.getToolArguments()),
                        extractOrderId(confirmation.getToolArguments()),
                        "User rejected the action", false));
    }

    private Mono<ChatMessage> generateConfirmationResponse(String sessionId, Long userId,
                                                            String actionType, String orderId,
                                                            String result, boolean confirmed) {
        String actionLabel = "CANCEL_ORDER".equals(actionType) ? "hủy đơn hàng" : "yêu cầu hoàn tiền";
        String statusText = confirmed ? "đã được thực hiện thành công" : "đã bị từ chối";

        String prompt = "Người dùng đã " + (confirmed ? "xác nhận" : "từ chối")
                + " thao tác " + actionLabel + " cho đơn hàng #" + orderId + ". "
                + "Kết quả: " + result + ". "
                + "Hãy trả lời người dùng bằng tiếng Việt, thông báo kết quả một cách thân thiện.";

        ChatClient chatClient = ChatClient.create(chatModel);

        try {
            ChatResponse response = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(prompt)
                    .call()
                    .chatResponse();

            String content = response.getResult().getOutput().getContent();

            return nextSequenceNo(sessionId)
                    .flatMap(seqNo -> {
                        ChatMessage msg = ChatMessage.builder()
                                .sessionId(sessionId)
                                .role("ASSISTANT")
                                .content(content)
                                .sequenceNo(seqNo)
                                .createdAt(LocalDateTime.now())
                                .build();
                        return messageRepo.save(msg);
                    });
        } catch (Exception e) {
            log.error("[ChatService] Failed to generate confirmation response", e);
            ChatMessage fallback = ChatMessage.builder()
                    .sessionId(sessionId)
                    .role("ASSISTANT")
                    .content(confirmed
                            ? "Thao tác " + actionLabel + " #" + orderId + " " + statusText + "."
                            : "Thao tác " + actionLabel + " #" + orderId + " " + statusText + ".")
                    .sequenceNo(1)
                    .createdAt(LocalDateTime.now())
                    .build();
            return messageRepo.save(fallback);
        }
    }

    private Mono<Void> rejectWithError(PendingConfirmation confirmation, String error) {
        confirmation.setStatus("REJECTED");
        confirmation.setUpdatedAt(LocalDateTime.now());
        return confirmationRepo.save(confirmation).then();
    }

    // ────────────────────────────────────────────────────────────────────────
    //  Message history (cursor pagination)
    // ────────────────────────────────────────────────────────────────────────

    public Flux<ChatMessage> getHistory(String sessionId, int pageSize, String before) {
        int fetchSize = Math.min(pageSize + 5, 100);
        return messageRepo.findBySessionIdOrderBySequenceNoDesc(sessionId,
                        PageRequest.of(0, fetchSize))
                .filter(msg -> {
                    if (before == null || before.isBlank()) return true;
                    try {
                        return msg.getSequenceNo() < Integer.parseInt(before);
                    } catch (NumberFormatException e) {
                        return true;
                    }
                })
                .take(pageSize);
    }

    // ────────────────────────────────────────────────────────────────────────
    //  Suggestions
    // ────────────────────────────────────────────────────────────────────────

    public Mono<List<String>> getSuggestions() {
        return Mono.just(List.of(
                "Tìm cho tôi sản phẩm bán chạy nhất",
                "Đơn hàng gần đây của tôi thế nào?",
                "Có flash sale nào đang diễn ra không?",
                "Làm sao để theo dõi đơn hàng?",
                "Tôi muốn tìm điện thoại dưới 10 triệu",
                "Phí vận chuyển được tính thế nào?",
                "Chính sách đổi trả ra sao?"
        ));
    }

    // ────────────────────────────────────────────────────────────────────────
    //  Internal helpers
    // ────────────────────────────────────────────────────────────────────────

    private Mono<Integer> nextSequenceNo(String sessionId) {
        return messageRepo.countBySessionId(sessionId)
                .map(count -> count.intValue() + 1);
    }

    private Mono<ChatMessage> saveUserMessage(String sessionId, String content, Long userId) {
        return nextSequenceNo(sessionId)
                .flatMap(seqNo -> {
                    ChatMessage msg = ChatMessage.builder()
                            .sessionId(sessionId)
                            .role("USER")
                            .content(content)
                            .sequenceNo(seqNo)
                            .createdAt(LocalDateTime.now())
                            .build();
                    return messageRepo.save(msg);
                })
                .doOnSuccess(m -> log.debug("[ChatService] Saved USER message: session={}, seq={}",
                        sessionId, m.getSequenceNo()));
    }

    private Mono<ChatMessage> saveToolResultMessage(String sessionId, String toolName, String result, Long userId) {
        return nextSequenceNo(sessionId)
                .flatMap(seqNo -> {
                    ChatMessage msg = ChatMessage.builder()
                            .sessionId(sessionId)
                            .role("TOOL_RESULT")
                            .content(result)
                            .toolName(toolName)
                            .sequenceNo(seqNo)
                            .createdAt(LocalDateTime.now())
                            .build();
                    return messageRepo.save(msg);
                });
    }

    private Mono<List<ChatMessage>> loadRecentHistory(String sessionId) {
        return messageRepo.findBySessionIdOrderBySequenceNoAsc(sessionId)
                .take(MAX_HISTORY_MESSAGES)
                .collectList();
    }

    private String extractActionType(String toolArguments) {
        try {
            return objectMapper.readTree(toolArguments).get("actionType").asText();
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    private String extractOrderId(String toolArguments) {
        try {
            return objectMapper.readTree(toolArguments).get("orderId").asText();
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    private Mono<Void> publishMessageSent(String sessionId, Long userId) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "eventType", KafkaTopics.AI_CHAT_MESSAGE_SENT,
                    "sessionId", sessionId,
                    "userId", userId,
                    "timestamp", System.currentTimeMillis()
            ));
            return Mono.fromRunnable(() ->
                    kafkaTemplate.send(KafkaTopics.AI_CHAT_MESSAGE_SENT, payload));
        } catch (JsonProcessingException e) {
            log.warn("[ChatService] Failed to serialize Kafka payload", e);
            return Mono.empty();
        }
    }

    private Mono<Void> publishConfirmationResolved(String sessionId, Long userId, String confirmId, String status) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "eventType", KafkaTopics.AI_CHAT_CONFIRMATION_RESOLVED,
                    "sessionId", sessionId,
                    "userId", userId,
                    "confirmId", confirmId,
                    "status", status,
                    "timestamp", System.currentTimeMillis()
            ));
            return Mono.fromRunnable(() ->
                    kafkaTemplate.send(KafkaTopics.AI_CHAT_CONFIRMATION_RESOLVED, payload));
        } catch (JsonProcessingException e) {
            log.warn("[ChatService] Failed to serialize Kafka payload", e);
            return Mono.empty();
        }
    }
}
