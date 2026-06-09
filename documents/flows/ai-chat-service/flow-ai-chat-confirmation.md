# Business Flow: AI Chat Streaming and Human Confirmation
Scope: `chat-service`

### Use Case Coverage

| Use case | Status against current code | Evidence | Notes |
|----------|-----------------------------|----------|-------|
| UC-AICHAT-001: Start New Chat Session | Implemented | `ChatController.createSession` line 90, `ChatService.createSession` line 260 | Session creation persists the session, publishes `ai.session.created`, and active sessions are listed by `GET /api/ai/sessions`. |
| UC-AICHAT-002: Send Message | Implemented | `ChatController.chat` line 35, `ChatService.streamChat` line 77, `ChatService.publishMessageSent` line 533 | Streams tool events, deltas, done, and error events via SSE. The service publishes both legacy `ai_chat.message_sent` and documented `ai.chat.message_received` events. |
| UC-AICHAT-003: Confirm or Reject Pending Action | Implemented | `ChatController.confirm` line 134, `ChatService.confirmAction` line 297, `SystemActionTool.performSystemAction` line 42 | Pending confirmations are persisted in MongoDB with TTL. Resolution publishes the legacy resolved event plus `ai.confirmation.confirmed` or `ai.confirmation.rejected`. |

### Sequence Diagram

```mermaid
sequenceDiagram
    actor User
    participant Chat as Chat Service
    participant LLM as Chat Client
    participant Tools as Tool Layer
    participant DB as MongoDB
    participant Kafka as Kafka
    participant Core as Core Services

    User->>Chat: POST /api/ai/sessions
    Chat->>DB: Insert chat session
    Chat->>Kafka: ai.session.created
    Chat-->>User: ChatSession

    User->>Chat: POST /api/ai/chat (SSE)
    Chat->>DB: Save user message
    Chat->>LLM: Generate response with tools
    LLM->>Tools: Invoke product/order/system tools as needed
    alt Normal response
        Chat-->>User: SSE tool_start/tool_done/delta/done
        Chat->>DB: Save assistant message
        Chat->>Kafka: ai_chat.message_sent and ai.chat.message_received
    else Level 3 action requires confirmation
        Tools->>DB: Insert pending confirmation
        Tools->>Kafka: ai_chat.tool_call_executed
        Chat-->>User: SSE confirmation_required
    end

    User->>Chat: POST /api/ai/confirm
    alt Confirmed
        Chat->>Core: Execute confirmed action through WebClient/tool logic
        Chat->>DB: Mark confirmation CONFIRMED
        Chat->>Kafka: ai_chat.confirmation_resolved and ai.confirmation.confirmed
        Chat-->>User: Result message
    else Rejected
        Chat->>DB: Mark confirmation REJECTED
        Chat->>Kafka: ai_chat.confirmation_resolved and ai.confirmation.rejected
        Chat-->>User: Rejection message
    end
```

### Implementation Notes

| Concern | Current behavior |
|---------|------------------|
| Pending confirmation storage | MongoDB `pending_confirmations` with TTL is the authoritative store; no Redis `pending:{confirmId}` key is used. |
| Event compatibility | The service keeps legacy `ai_chat.*` topics and also publishes the documented `ai.*` aliases. |
| Rate limiting | `RateLimiter` is in-memory, so limits reset on service restart and are not cluster-wide. |
| Confirmed action execution | New Level 3 tools must add explicit handling in `ChatService.executeConfirmedAction`. |
