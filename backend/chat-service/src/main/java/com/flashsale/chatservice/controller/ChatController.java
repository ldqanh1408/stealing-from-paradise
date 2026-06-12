package com.flashsale.chatservice.controller;

import com.flashsale.chatservice.domain.model.ChatMessage;
import com.flashsale.chatservice.domain.model.ChatSession;
import com.flashsale.chatservice.dto.request.ChatRequest;
import com.flashsale.chatservice.dto.request.ConfirmRequest;
import com.flashsale.chatservice.service.ChatService;
import com.flashsale.commonlib.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatService chatService;

    // ────────────────────────────────────────────────────────────────────────
    //  POST /api/ai/chat — SSE streaming chat (main endpoint)
    // ────────────────────────────────────────────────────────────────────────

    @PostMapping(value = "/ai/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(
            ServerWebExchange exchange,
            @RequestBody ChatRequest request) {

        Long userId = extractUserId(exchange);
        String accessToken = extractAccessToken(exchange);
        String userEmail = extractHeader(exchange, "X-User-Email");
        String userRole = extractHeader(exchange, "X-User-Role");

        if (userId == null) {
            return Flux.just(errorEvent("Missing X-User-Id header"));
        }
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            return Flux.just(errorEvent("Message is required"));
        }

        log.info("[ChatController] Chat request: userId={}, sessionId={}, message={}",
                userId, request.getSessionId(), request.getMessage().substring(0,
                        Math.min(50, request.getMessage().length())));

        return chatService.streamChat(request.getSessionId(), request.getMessage(), userId, userEmail, userRole, accessToken);
    }

    // ────────────────────────────────────────────────────────────────────────
    //  GET /api/ai/chat/history — cursor-paginated message history
    // ────────────────────────────────────────────────────────────────────────

    @GetMapping("/ai/chat/history")
    public Mono<ResponseEntity<ApiResponse<List<ChatMessage>>>> history(
            ServerWebExchange exchange,
            @RequestParam(required = false) String sessionId,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) String before) {

        Long userId = extractUserId(exchange);
        String accessToken = extractAccessToken(exchange);
        String userEmail = extractHeader(exchange, "X-User-Email");
        String userRole = extractHeader(exchange, "X-User-Role");
        if (userId == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("AUTH_001", "Missing X-User-Id header")));
        }
        if (sessionId == null || sessionId.isBlank()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(ApiResponse.error("VAL_001", "sessionId is required")));
        }

        if (pageSize > 100) pageSize = 100;
        if (pageSize < 1) pageSize = 50;

        return chatService.getHistory(sessionId, pageSize, before)
                .collectList()
                .map(messages -> ResponseEntity.ok(ApiResponse.success(messages)));
    }

    // ────────────────────────────────────────────────────────────────────────
    //  POST /api/ai/sessions — create new session
    // ────────────────────────────────────────────────────────────────────────

    @PostMapping("/ai/sessions")
    public Mono<ResponseEntity<ApiResponse<ChatSession>>> createSession(ServerWebExchange exchange) {
        Long userId = extractUserId(exchange);
        if (userId == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("AUTH_001", "Missing X-User-Id header")));
        }

        return chatService.createSession(userId)
                .map(session -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(ApiResponse.success(session, "Session created")));
    }

    @GetMapping("/ai/sessions")
    public Mono<ResponseEntity<ApiResponse<List<ChatSession>>>> listSessions(ServerWebExchange exchange) {
        Long userId = extractUserId(exchange);
        if (userId == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("AUTH_001", "Missing X-User-Id header")));
        }

        return chatService.getActiveSessions(userId)
                .collectList()
                .map(sessions -> ResponseEntity.ok(ApiResponse.success(sessions)));
    }

    // ────────────────────────────────────────────────────────────────────────
    //  DELETE /api/ai/sessions/{sessionId} — close session
    // ────────────────────────────────────────────────────────────────────────

    @DeleteMapping("/ai/sessions/{sessionId}")
    public Mono<ResponseEntity<ApiResponse<Void>>> closeSession(
            ServerWebExchange exchange,
            @PathVariable String sessionId) {

        Long userId = extractUserId(exchange);
        if (userId == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("AUTH_001", "Missing X-User-Id header")));
        }

        return chatService.closeSession(sessionId, userId)
                .then(Mono.just(ResponseEntity.ok(
                        ApiResponse.<Void>builder()
                                .success(true)
                                .message("Session closed")
                                .timestamp(System.currentTimeMillis())
                                .build())))
                .onErrorResume(ResponseStatusException.class, ex ->
                        Mono.just(ResponseEntity.status(ex.getStatusCode())
                                .body(ApiResponse.error("RES_001", ex.getReason()))));
    }

    // ────────────────────────────────────────────────────────────────────────
    //  POST /api/ai/confirm — confirm/reject Level-3 action
    // ────────────────────────────────────────────────────────────────────────

    @PostMapping("/ai/confirm")
    public Mono<ResponseEntity<ApiResponse<ChatMessage>>> confirm(
            ServerWebExchange exchange,
            @RequestBody ConfirmRequest request) {

        Long userId = extractUserId(exchange);
        String accessToken = extractAccessToken(exchange);
        String userEmail = extractHeader(exchange, "X-User-Email");
        String userRole = extractHeader(exchange, "X-User-Role");

        if (userId == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("AUTH_001", "Missing X-User-Id header")));
        }
        if (request.getConfirmId() == null || request.getConfirmId().isBlank()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(ApiResponse.error("VAL_001", "confirmId is required")));
        }

        log.info("[ChatController] Confirm action: confirmId={}, confirmed={}, userId={}",
                request.getConfirmId(), request.isConfirmed(), userId);

        return chatService.confirmAction(request.getConfirmId(), request.isConfirmed(), userId, userEmail, userRole, accessToken)
                .map(msg -> ResponseEntity.ok(ApiResponse.success(msg,
                        request.isConfirmed() ? "Action confirmed" : "Action rejected")));
    }

    // ────────────────────────────────────────────────────────────────────────
    //  GET /api/ai/suggest — context-aware suggestions
    // ────────────────────────────────────────────────────────────────────────

    @GetMapping("/ai/suggest")
    public Mono<ResponseEntity<ApiResponse<List<String>>>> suggest(ServerWebExchange exchange) {
        // Public endpoint (optional JWT) — userId used for personalization if available
        return chatService.getSuggestions()
                .map(suggestions -> ResponseEntity.ok(ApiResponse.success(suggestions)));
    }

    // ────────────────────────────────────────────────────────────────────────
    //  Helper methods
    // ────────────────────────────────────────────────────────────────────────

    private Long extractUserId(ServerWebExchange exchange) {
        String userIdStr = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        if (userIdStr == null || userIdStr.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(userIdStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String extractAccessToken(ServerWebExchange exchange) {
        return exchange.getRequest().getHeaders().getFirst("X-Access-Token");
    }

    private String extractHeader(ServerWebExchange exchange, String headerName) {
        return exchange.getRequest().getHeaders().getFirst(headerName);
    }

    private ServerSentEvent<String> errorEvent(String message) {
        return ServerSentEvent.<String>builder()
                .event("error")
                .data("{\"error\":\"" + message + "\"}")
                .build();
    }
}
