# AI Chat Service — Kafka Events

**Service**: ai-chat-service (Port 8093)
**Version**: v5.5
**Last Updated**: 2026-05-05

---

## Overview

AI Chat Service produces Kafka events primarily for audit logging, session tracking, and notification integration. It does not consume events from other services.

---

## Event Producer

### Topic: `ai.chat.message_received`

Fired every time a user sends a message in the AI Chat widget (success or failure).

**Partition Key**: `sessionId` — ensures all messages in a session are ordered.

**Retention**: 30 days

```json
{
  "event_id": "evt_YYYYMMDD_NNN",
  "event_type": "ai.chat.message_received",
  "timestamp": "ISO 8601",
  "correlation_id": "uuid",
  "source_service": "ai-chat-service",
  "version": 1,
  "data": {
    "sessionId": "uuid",
    "userId": "uuid",
    "messageId": "uuid",
    "role": "USER | ASSISTANT",
    "hasToolCall": true,
    "toolName": "getOrderDetail | searchProducts | cancelOrder | ...",
    "tokensUsed": 312,
    "latencyMs": 1420
  }
}
```

**Consumer Actions (Notification Service)**:
- Log message to MongoDB `mg_notifications` for audit
- Optionally push SSE update if user is online

---

### Topic: `ai.session.created`

Fired when a new chat session is created.

**Partition Key**: `sessionId`

```json
{
  "event_type": "ai.session.created",
  "timestamp": "ISO 8601",
  "data": {
    "sessionId": "uuid",
    "userId": "uuid",
    "contextPage": "home | product | order | cart"
  }
}
```

---

### Topic: `ai.session.closed`

Fired when a chat session is closed by the user or auto-expired.

**Partition Key**: `sessionId`

```json
{
  "event_type": "ai.session.closed",
  "timestamp": "ISO 8601",
  "data": {
    "sessionId": "uuid",
    "userId": "uuid",
    "reason": "USER_CLOSED | EXPIRED | IDLE_TIMEOUT"
  }
}
```

---

### Topic: `ai.confirmation.requested`

Fired when AI requests user confirmation for a Mức 3 action.

**Partition Key**: `sessionId`

```json
{
  "event_type": "ai.confirmation.requested",
  "timestamp": "ISO 8601",
  "data": {
    "sessionId": "uuid",
    "userId": "uuid",
    "confirmId": "uuid",
    "actionType": "CANCEL_ORDER | UPDATE_PROFILE | DELETE_ACCOUNT | CUSTOM",
    "riskLevel": 3
  }
}
```

---

### Topic: `ai.confirmation.confirmed`

Fired when user confirms a Mức 3 action.

**Partition Key**: `sessionId`

```json
{
  "event_type": "ai.confirmation.confirmed",
  "timestamp": "ISO 8601",
  "data": {
    "sessionId": "uuid",
    "userId": "uuid",
    "confirmId": "uuid",
    "actionType": "CANCEL_ORDER",
    "executionResult": {
      "success": true,
      "coreServiceResponse": "..."
    }
  }
}
```

**Consumer Actions (Notification Service)**:
- Send confirmation notification to user

---

### Topic: `ai.confirmation.rejected`

Fired when user cancels a Mức 3 action.

**Partition Key**: `sessionId`

```json
{
  "event_type": "ai.confirmation.rejected",
  "timestamp": "ISO 8601",
  "data": {
    "sessionId": "uuid",
    "userId": "uuid",
    "confirmId": "uuid",
    "actionType": "CANCEL_ORDER"
  }
}
```

---

### Topic: `ai.confirmation.expired`

Fired when a confirmation token expires (after 5 minutes TTL).

**Partition Key**: `sessionId`

```json
{
  "event_type": "ai.confirmation.expired",
  "timestamp": "ISO 8601",
  "data": {
    "sessionId": "uuid",
    "userId": "uuid",
    "confirmId": "uuid",
    "actionType": "CANCEL_ORDER"
  }
}
```

---

## Consumer

AI Chat Service does **not** consume events from other services. It operates as a standalone orchestration layer using REST calls to Core Services.

---

## Event Schema Registry

All events follow this base structure:

```json
{
  "event_id": "evt_YYYYMMDD_NNN",
  "event_type": "ai.{domain}.{action}",
  "timestamp": "ISO 8601",
  "correlation_id": "uuid",
  "source_service": "ai-chat-service",
  "version": 1,
  "data": { }
}
```

---

## Summary

| Topic | Type | Retention | Key |
|-------|------|-----------|-----|
| `ai.chat.message_received` | Event | 30 days | sessionId |
| `ai.session.created` | Event | 30 days | sessionId |
| `ai.session.closed` | Event | 30 days | sessionId |
| `ai.confirmation.requested` | Event | 90 days | sessionId |
| `ai.confirmation.confirmed` | Event | 90 days | sessionId |
| `ai.confirmation.rejected` | Event | 90 days | sessionId |
| `ai.confirmation.expired` | Event | 90 days | sessionId |

---

**Total**: 7 event topics (no request-reply topics)
