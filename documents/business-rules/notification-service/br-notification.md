# BR-NOTIF-001: Notification Lifecycle Business Rules

> **Service**: notification-service (Port 8092)
> **Database**: MongoDB
> **Source**: 02_API_notification_service.md, KAFKA_EVENTS.md

---

## BR-NOTIF-001-01: Notification Creation

| Condition | Action |
|-----------|--------|
| Kafka event received from any subscribed topic | Create MG_NOTIFICATIONS document with `is_read = false` |
| Event payload contains `user_id` | Map directly to `user_id` field |
| Event payload contains `title`/`body`/`type` | Store as-is in respective fields |
| Event payload has extra data | Store in `metadata` JSONB field |
| Kafka event deserialization fails | Log error, skip event, no notification created |

**Trigger Topics**: All 30+ consumer topics (identity, product, order, payment, flash_sale, stripe)

---

## BR-NOTIF-001-02: SSE Real-Time Delivery

| Condition | Action |
|-----------|--------|
| Notification created successfully | Push to Redis Pub/Sub channel `user:{user_id}` |
| User has active SSE connection | Deliver event immediately via `text/event-stream` |
| User has no active SSE connection | Event buffered in Redis Pub/Sub (60s buffer) |
| SSE connection established with `Last-Event-ID` header | Replay missed events from buffer |

---

## BR-NOTIF-001-03: Read Status Transitions

| IF | THEN |
|----|------|
| `is_read = false` AND user calls PUT /notifications/{id}/read | Set `is_read = true` |
| `is_read = false` AND user calls PUT /notifications/read-all | Set `is_read = true` for ALL user's unread notifications |
| `is_read = true` AND user calls PUT /notifications/{id}/read | Idempotent: no change, return 200 |
| `is_read = true` AND user calls PUT /notifications/read-all | No documents updated, `updated_count = 0` |

---

## BR-NOTIF-001-04: TTL Expiry

| Condition | Action |
|-----------|--------|
| `created_at` + 90 days < NOW() | MongoDB TTL index auto-deletes document |
| User queries history beyond 90 days | Results truncated (data no longer exists) |
| Archived/deleted notification accessed by ID | Return 404 |

---

## BR-NOTIF-001-05: Authorization

| Condition | Action |
|-----------|--------|
| JWT absent or invalid | Return 401 |
| JWT valid, user requests own notifications | Authorized |
| JWT valid, user attempts another user's notifications | Return 403 |
| JWT valid, admin role | Authorized for any user's notifications |

---

## BR-NOTIF-001-06: Pagination

| Param | Default | Max | Rule |
|-------|---------|-----|------|
| `page` | 0 | N/A | 0-based page index |
| `size` | 20 | 100 | If size > 100, clamp to 100 |
| `is_read` filter | null (all) | N/A | true = read only, false = unread only |

---

## BR-NOTIF-001-07: Priority Handling

| Priority | SSE Delivery | UI Treatment |
|----------|-------------|--------------|
| URGENT | Immediate, no batching | Push notification + badge |
| HIGH | Immediate | Badge increment |
| NORMAL | Immediate | Badge increment |
| LOW | Immediate, may be batched in UI | Badge increment |

---

## Cross-References

| Ref ID | Target |
|--------|--------|
| UC-NOTIF-001 | Stream notifications (SSE) |
| UC-NOTIF-002 | View history |
| UC-NOTIF-003 | Mark read |
| FR-NOTIF-001 | SSE delivery |
| FR-NOTIF-002 | Paginated history |
| FR-NOTIF-003 | Read management |
| ST-NOTIF-001 | State diagram |
