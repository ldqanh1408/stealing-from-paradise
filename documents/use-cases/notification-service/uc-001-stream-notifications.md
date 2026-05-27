# UC-NOTIF-001: Stream Real-Time Notifications

> **Service**: notification-service (Port 8092)
> **Use Case ID**: UC-NOTIF-001
> **Priority**: HIGH
> **Source**: 02_API_notification_service.md

---

## Brief

User opens the application and establishes an SSE connection to receive real-time notifications pushed from the server.

---

## Actors

| Actor | Role |
|-------|------|
| Buyer | Receives order, payment, refund, flash sale notifications |
| Seller | Receives order, product approval/rejection, flash sale item notifications |
| Admin | Receives system alerts, product review notifications |
| System | Kafka consumers + Redis Pub/Sub + SSE handler |

---

## Preconditions

| # | Condition |
|---|-----------|
| 1 | User is authenticated with valid JWT |
| 2 | User's browser supports EventSource API |
| 3 | Redis Pub/Sub is operational |

---

## Main Flow

| Step | Actor | Action |
|------|-------|--------|
| 1 | User | Opens application (page load) |
| 2 | Client | Sends GET /notifications/stream with Authorization header |
| 3 | Server | Validates JWT, extracts user_id |
| 4 | Server | Creates SSE connection, returns `text/event-stream` |
| 5 | Server | Subscribes to Redis Pub/Sub channel `user:{user_id}` |
| 6 | System | Kafka event arrives -> creates MG_NOTIFICATIONS -> publishes to Redis |
| 7 | Server | Receives Redis message, formats as SSE `data:` event |
| 8 | Client | Renders notification in UI (toast/badge) |
| 9 | [Loop] | Steps 6-8 repeat for each new notification |

---

## Alternate Flows

| Flow | Condition | Action |
|------|-----------|--------|
| A1 | Client disconnects (network issue) | Client auto-reconnects with `Last-Event-ID` header |
| A2 | `Last-Event-ID` present | Server replays missed events from Redis buffer (60s) |
| A3 | No events in buffer | Server sends heartbeat comment `: heartbeat\n\n` every 30s |
| A4 | JWT expires mid-stream | Server closes connection; client re-authenticates and reconnects |

---

## Postconditions

| # | Condition |
|---|-----------|
| 1 | User has active SSE connection receiving real-time notifications |
| 2 | Missed notifications replayable via `Last-Event-ID` |

---

## Exceptions

| Code | Condition | Response |
|------|-----------|----------|
| 401 | JWT invalid or missing | HTTP 401 |
| 503 | Redis Pub/Sub unavailable | SSE stream opens but no events delivered; server logs error |

---

## Cross-References

| Ref ID | Target |
|--------|--------|
| FR-NOTIF-001 | SSE stream requirement |
| BR-NOTIF-001-02 | SSE delivery rules |
| ENTITY-NOTIF-001 | MG_NOTIFICATIONS |
