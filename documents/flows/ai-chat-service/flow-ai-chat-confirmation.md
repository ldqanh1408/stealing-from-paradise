# Business Flow: AI Chat Streaming and Human Confirmation
Scope: `chat-service`

### Use Case Coverage

| Use case | Status against current code | Evidence | Notes |
|----------|-----------------------------|----------|-------|
| UC-AICHAT-001: Start New Chat Session | Implemented | `ChatController.createSession` line 90, `ChatService.createSession` line 252 | Session close and history endpoints also exist. |
| UC-AICHAT-002: Send Message | Implemented | `ChatController.chat` line 35, `ChatService.streamChat` line 77 | Streams tool events, deltas, done, and error events via SSE. |
| UC-AICHAT-003: Confirm or Reject Pending Action | Implemented | `ChatController.confirm` line 134, `ChatService.confirmAction` line 297, `SystemActionTool.performSystemAction` line 42 | Current confirmed action path supports the coded action types in `ChatService.executeConfirmedAction`. |

### Sequence Diagram

```mermaid
sequenceDiagram
    actor User
    participant Chat as Chat Service
    participant LLM as Chat Client
    participant Tools as Tool Layer
    participant DB as PostgreSQL
    participant Kafka as Kafka
    participant Core as Core Services

    User->>Chat: POST /api/ai/sessions
    Chat->>DB: Insert CHAT_SESSIONS
    Chat-->>User: ChatSession

    User->>Chat: POST /api/ai/chat (SSE)
    Chat->>DB: Save user message
    Chat->>LLM: Generate response with tools
    LLM->>Tools: Invoke product/order/system tools as needed
    alt Normal response
        Chat-->>User: SSE tool_start/tool_done/delta/done
        Chat->>DB: Save assistant message
        Chat->>Kafka: ai_chat.message_sent
    else Level 3 action requires confirmation
        Tools->>DB: Insert PENDING_CONFIRMATIONS
        Tools->>Kafka: ai_chat.tool_call_executed
        Chat-->>User: SSE confirmation_required
    end

    User->>Chat: POST /api/ai/confirm
    alt Confirmed
        Chat->>Core: Execute confirmed action through WebClient/tool logic
        Chat->>DB: Mark confirmation CONFIRMED
        Chat->>Kafka: ai_chat.confirmation_resolved
        Chat-->>User: Result message
    else Rejected
        Chat->>DB: Mark confirmation REJECTED
        Chat->>Kafka: ai_chat.confirmation_resolved
        Chat-->>User: Rejection message
    end
```

### Implementation Gaps

| Gap | Impact |
|-----|--------|
| `RateLimiter` is in-memory and has a TODO for Redis. | Rate limits reset on service restart and are not cluster-safe yet. |
| Confirmed actions depend on the hard-coded action handling in `ChatService.executeConfirmedAction`. | New Level 3 tools must add explicit execution logic before the flow is truly supported. |
