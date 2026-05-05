# AI Chat Service — Database Tables

> Stack: PostgreSQL · Redis  
> Cập nhật: 2026-05-03

---

## Enum Types

```sql
CREATE TYPE session_status   AS ENUM ('ACTIVE', 'CLOSED', 'EXPIRED');
CREATE TYPE message_role     AS ENUM ('USER', 'ASSISTANT', 'TOOL_CALL', 'TOOL_RESULT');
CREATE TYPE confirm_status   AS ENUM ('PENDING', 'CONFIRMED', 'REJECTED', 'EXPIRED');
CREATE TYPE confirm_action   AS ENUM ('CANCEL_ORDER', 'UPDATE_PROFILE', 'DELETE_ACCOUNT', 'CUSTOM');
CREATE TYPE tool_call_status AS ENUM ('SUCCESS', 'FAILED', 'BLOCKED', 'TIMEOUT');
CREATE TYPE outbox_status    AS ENUM ('PENDING', 'PROCESSING', 'DONE', 'FAILED');
```

---

## CHAT_SESSIONS
Vòng đời một cuộc trò chuyện AI

| Cột | Kiểu | Ghi chú |
|-----|------|--------|
| `id` | UUID | Primary Key, gen_random_uuid() |
| `user_id` | VARCHAR(36) | NOT NULL, chủ sở hữu session |
| `status` | session_status | ACTIVE (default) \| CLOSED \| EXPIRED |
| `context_summary` | TEXT | Tóm tắt nén khi history > 50 messages |
| `created_at` | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() |
| `updated_at` | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() |
| `closed_at` | TIMESTAMPTZ | Thời điểm đóng |

**Indexes:**
- `idx_sessions_user` ON (user_id)
- `idx_sessions_status` ON (status, updated_at)

---

## CHAT_MESSAGES
Lịch sử hội thoại đầy đủ

| Cột | Kiểu | Ghi chú |
|-----|------|--------|
| `id` | UUID | Primary Key, gen_random_uuid() |
| `session_id` | UUID | FK → CHAT_SESSIONS.id, NOT NULL |
| `role` | message_role | USER \| ASSISTANT \| TOOL_CALL \| TOOL_RESULT |
| `content` | TEXT | NOT NULL (JSON string với TOOL_CALL/TOOL_RESULT) |
| `tool_name` | VARCHAR(100) | Chỉ có giá trị khi role = TOOL_CALL/TOOL_RESULT |
| `sequence_no` | INT | NOT NULL, thứ tự tuyệt đối trong session |
| `tokens_used` | INT | Chỉ có với ASSISTANT messages |
| `created_at` | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() |

**Ràng buộc:** UNIQUE (session_id, sequence_no)

**Indexes:**
- `idx_messages_session` ON (session_id, sequence_no)

---

## PENDING_CONFIRMATIONS
Human-in-the-loop cho action Mức 3

| Cột | Kiểu | Ghi chú |
|-----|------|--------|
| `id` | UUID | Primary Key, chính là confirm token |
| `session_id` | UUID | FK → CHAT_SESSIONS.id, NOT NULL |
| `message_id` | UUID | FK → CHAT_MESSAGES.id, NOT NULL |
| `user_id` | VARCHAR(36) | NOT NULL |
| `action_type` | confirm_action | CANCEL_ORDER \| UPDATE_PROFILE \| DELETE_ACCOUNT \| CUSTOM |
| `payload` | JSONB | NOT NULL, dữ liệu để thực thi sau khi confirmed |
| `status` | confirm_status | PENDING (default) \| CONFIRMED \| REJECTED \| EXPIRED |
| `expires_at` | TIMESTAMPTZ | NOT NULL (now + 5 phút) |
| `created_at` | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() |
| `resolved_at` | TIMESTAMPTZ | Thời điểm xử lý |

**Indexes:**
- `idx_confirm_status` ON (status, expires_at)

> Tồn tại ở cả DB (audit trail) và Redis (fast lookup với TTL 5 phút).

---

## TOOL_CALL_LOGS
Audit trail bất biến — Partition by month

| Cột | Kiểu | Ghi chú |
|-----|------|--------|
| `id` | UUID | Primary Key, gen_random_uuid() |
| `session_id` | UUID | FK → CHAT_SESSIONS.id |
| `message_id` | UUID | FK → CHAT_MESSAGES.id |
| `user_id` | VARCHAR(36) | NOT NULL |
| `tool_name` | VARCHAR(100) | NOT NULL |
| `input_params` | JSONB | NOT NULL |
| `output` | JSONB | Kết quả trả về |
| `status` | tool_call_status | SUCCESS \| FAILED \| BLOCKED \| TIMEOUT |
| `duration_ms` | INT | NOT NULL |
| `risk_level` | SMALLINT | NOT NULL (1 / 2 / 3) |
| `created_at` | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() |

**Partition:** BY RANGE (created_at) — tạo partition mới mỗi tháng.

**Indexes:**
- `idx_tool_logs_user` ON (user_id, created_at)
- `idx_tool_logs_name` ON (tool_name, created_at)

---

## OUTBOX_EVENTS (AI Chat)
Event Outbox Pattern cho Kafka fallback

| Cột | Kiểu | Ghi chú |
|-----|------|--------|
| `id` | UUID | Primary Key, gen_random_uuid() |
| `event_type` | VARCHAR(100) | NOT NULL |
| `payload` | JSONB | NOT NULL |
| `status` | outbox_status | PENDING (default) \| PROCESSING \| DONE \| FAILED |
| `retry_count` | SMALLINT | DEFAULT 0 |
| `error_message` | TEXT | Lỗi nếu có |
| `created_at` | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() |
| `processed_at` | TIMESTAMPTZ | Thời điểm xử lý |

**Partial Index:** `WHERE status = 'PENDING'` — chỉ index các row chưa xử lý.

Query pattern (FOR UPDATE SKIP LOCKED):
```sql
SELECT * FROM outbox_events
WHERE status = 'PENDING' AND retry_count < 3
ORDER BY created_at ASC
LIMIT 50
FOR UPDATE SKIP LOCKED;
```

---

## Redis Keys

| Key | TTL | Mục đích |
|-----|-----|----------|
| `rate:{userId}` | 60s | Rate limit counter (20 req/phút) |
| `tool:rate:{userId}` | 60s | Rate limit riêng cho Tool calls (10/phút) |
| `ctx:{sessionId}` | 30 phút | Cache 20 messages gần nhất, tránh query DB mỗi request |
| `pending:{confirmId}` | 5 phút | Fast lookup khi user bấm confirm |
| `buf:{sessionId}` | 10 phút | Buffer 20 SP từ PageIndex cho "Xem thêm" |
| `tool:cache:{hash}` | 60s | Cache kết quả Tool đọc (Mức 1) |

---

## Entity Relationship

```
chat_sessions 1──N chat_messages
chat_sessions 1──N pending_confirmations
chat_sessions 1──N tool_call_logs
chat_messages 1──N pending_confirmations
```
