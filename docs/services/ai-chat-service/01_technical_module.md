# AI Chat Support — Tài liệu kỹ thuật module

> Stack: Spring AI · PageIndex · Elasticsearch · Kafka · Redis · PostgreSQL

---

## 1. Kiến trúc tổng thể

Module gồm hai phần hoàn toàn độc lập, không chia sẻ infrastructure:

| Module | Mục đích | Stack |
|--------|----------|-------|
| **AI Chat Support** | User nhắn tin, AI tra cứu và trả lời | Spring AI, PageIndex, Redis, Core APIs |
| **Product Search** | Thanh search bar, autocomplete, SERP | Elasticsearch, Kafka, Redis cache |

> **Nguyên tắc vàng:** Không sửa hệ thống cũ (Core Services). Chỉ xây lớp AI bên ngoài bằng AI Orchestrator.

### 1.1 Phân tầng AI Orchestrator

```
Frontend / Chat UI
        ↓  (JWT + message)
API Gateway
        ↓
AI Orchestrator (Spring AI)     ←→  PageIndex (vector search)
        ↓  (JWT delegation)
Core Services (Order, Product, Account...)
```

| Layer | Vai trò |
|-------|---------|
| Layer 1 — Frontend | Gửi message + JWT, render SSE stream, hiển thị Product Card / Order Card |
| Layer 2 — AI Orchestrator | Xác thực JWT, rate limit, quản lý ChatClient, điều phối Tool calls |
| Layer 3 — PageIndex | Vector search cho sản phẩm và chức năng hệ thống |
| Layer 4 — Core Services | Hệ thống cũ, AI gọi qua API có sẵn, không sửa đổi |
| Layer 5 — Security | JWT validation, Rate limiting, Human-in-the-loop (Mức 3) |

---

## 2. Phân loại rủi ro Tool

| Mức | Loại action | Yêu cầu | Ví dụ Tool |
|-----|-------------|---------|------------|
| **Mức 1** | Đọc thông tin chung | Không cần auth đặc biệt | `searchProducts`, `searchFaq` |
| **Mức 2** | Đọc dữ liệu cá nhân | JWT hợp lệ bắt buộc | `getOrderDetail`, `getUserProfile` |
| **Mức 3** | Thay đổi / xóa dữ liệu | JWT + Human confirmation | `cancelOrder`, `deleteAccount` |

---

## 3. PageIndex — Pipeline xử lý 1 tỷ sản phẩm

PageIndex dùng ANN (Approximate Nearest Neighbor) — không scan toàn bộ catalog, chỉ mất ~50ms.

```
1 tỷ sản phẩm
      ↓  ANN vector search (~50ms)
Top-100 candidates
      ↓  Business filter (in_stock, active, đúng category)
Top-20 results
      ↓  AI rerank + intent match
3–5 sản phẩm hiển thị trong chat
```

### 3.1 Số lượng trả về theo intent

| Intent | Ví dụ query | PageIndex top-K | Hiển thị | Batch "Xem thêm" |
|--------|-------------|-----------------|----------|------------------|
| Vague | "Tìm áo cho tôi" | 50 | 3 (sau clarify) | 3 |
| Specific | "Áo thun trắng M < 200k" | 20 | 3–5 | 5 |
| Compare | "A vs B cái nào tốt" | 10 | 2–3 | Không có |

> **Cache buffer:** Lưu 20 SP vào Redis TTL 10 phút. "Xem thêm" → pop từ Redis, không gọi lại PageIndex.

---

## 4. Database Schema

### 4.1 Tổng quan các bảng

| Bảng | Giải quyết vấn đề | Ghi chú |
|------|-------------------|---------|
| `chat_sessions` | Vòng đời một cuộc trò chuyện | Scope của tất cả bảng còn lại |
| `chat_messages` | Lịch sử hội thoại đầy đủ | Gồm cả TOOL_CALL và TOOL_RESULT |
| `pending_confirmations` | Human-in-the-loop Mức 3 | Tồn tại ở cả DB lẫn Redis |
| `tool_call_logs` | Audit trail bất biến | Chỉ INSERT, không UPDATE/DELETE |
| `outbox_events` | Fallback khi Kafka fail | Cron job retry mỗi 30 giây |

### 4.2 DDL

```sql
-- Enum types
CREATE TYPE session_status   AS ENUM ('ACTIVE', 'CLOSED', 'EXPIRED');
CREATE TYPE message_role     AS ENUM ('USER', 'ASSISTANT', 'TOOL_CALL', 'TOOL_RESULT');
CREATE TYPE confirm_status   AS ENUM ('PENDING', 'CONFIRMED', 'REJECTED', 'EXPIRED');
CREATE TYPE confirm_action   AS ENUM ('CANCEL_ORDER', 'UPDATE_PROFILE', 'DELETE_ACCOUNT', 'CUSTOM');
CREATE TYPE tool_call_status AS ENUM ('SUCCESS', 'FAILED', 'BLOCKED', 'TIMEOUT');
CREATE TYPE outbox_status    AS ENUM ('PENDING', 'PROCESSING', 'DONE', 'FAILED');

-- chat_sessions
CREATE TABLE chat_sessions (
    id               UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          VARCHAR(36)     NOT NULL,
    status           session_status  NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    closed_at        TIMESTAMPTZ,
    context_summary  TEXT            -- Tóm tắt nén khi history > 50 messages
);
CREATE INDEX idx_sessions_user   ON chat_sessions (user_id);
CREATE INDEX idx_sessions_status ON chat_sessions (status, updated_at);

-- chat_messages
CREATE TABLE chat_messages (
    id           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id   UUID          NOT NULL REFERENCES chat_sessions(id),
    role         message_role  NOT NULL,
    content      TEXT          NOT NULL,  -- JSON string với TOOL_CALL/TOOL_RESULT
    tool_name    VARCHAR(100),            -- chỉ có giá trị khi role = TOOL_CALL/TOOL_RESULT
    sequence_no  INT           NOT NULL,  -- thứ tự tuyệt đối trong session
    tokens_used  INT,                     -- chỉ có với ASSISTANT messages
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    UNIQUE (session_id, sequence_no)
);
CREATE INDEX idx_messages_session ON chat_messages (session_id, sequence_no);

-- pending_confirmations
CREATE TABLE pending_confirmations (
    id           UUID            PRIMARY KEY DEFAULT gen_random_uuid(), -- chính là confirm token
    session_id   UUID            NOT NULL REFERENCES chat_sessions(id),
    message_id   UUID            NOT NULL REFERENCES chat_messages(id),
    user_id      VARCHAR(36)     NOT NULL,
    action_type  confirm_action  NOT NULL,
    payload      JSONB           NOT NULL,  -- dữ liệu để thực thi sau khi confirmed
    status       confirm_status  NOT NULL DEFAULT 'PENDING',
    expires_at   TIMESTAMPTZ     NOT NULL,  -- now + 5 phút
    created_at   TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    resolved_at  TIMESTAMPTZ
);
CREATE INDEX idx_confirm_status ON pending_confirmations (status, expires_at);

-- tool_call_logs (append-only audit trail)
CREATE TABLE tool_call_logs (
    id            UUID              PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id    UUID              REFERENCES chat_sessions(id),
    message_id    UUID              REFERENCES chat_messages(id),
    user_id       VARCHAR(36)       NOT NULL,
    tool_name     VARCHAR(100)      NOT NULL,
    input_params  JSONB             NOT NULL,
    output        JSONB,
    status        tool_call_status  NOT NULL,
    duration_ms   INT               NOT NULL,
    risk_level    SMALLINT          NOT NULL,  -- 1 / 2 / 3
    created_at    TIMESTAMPTZ       NOT NULL DEFAULT NOW()
) PARTITION BY RANGE (created_at);  -- partition by month

CREATE INDEX idx_tool_logs_user ON tool_call_logs (user_id, created_at);
CREATE INDEX idx_tool_logs_name ON tool_call_logs (tool_name, created_at);

-- Tạo partition tháng đầu tiên (lặp lại mỗi tháng)
CREATE TABLE tool_call_logs_2026_05
    PARTITION OF tool_call_logs
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');

-- outbox_events
CREATE TABLE outbox_events (
    id               UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type       VARCHAR(100)   NOT NULL,
    payload          JSONB          NOT NULL,
    status           outbox_status  NOT NULL DEFAULT 'PENDING',
    retry_count      SMALLINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    processed_at     TIMESTAMPTZ,
    error_message    TEXT
);

-- Partial index: chỉ index PENDING rows để query nhanh
CREATE INDEX idx_outbox_pending ON outbox_events (status, created_at)
    WHERE status = 'PENDING';
```

### 4.3 Ghi chú thiết kế quan trọng

**chat_messages — 4 records cho 1 lượt hỏi đáp:**

Khi AI gọi Tool, thứ tự các record trong một lượt:

```
#1  role=USER        → "Đơn hàng ORD-2024-00892 đâu?"
#2  role=TOOL_CALL   → {"name":"getOrderDetail","args":{...}}
#3  role=TOOL_RESULT → {"status":"SHIPPED","eta":"2026-05-05"}
#4  role=ASSISTANT   → "Đơn hàng đang được giao, dự kiến 05/05..."
```

`sequence_no` là bắt buộc — timestamp không đủ tin cậy (nhiều record tạo cùng millisecond).

**pending_confirmations — tại sao tồn tại ở cả DB lẫn Redis:**

- **Redis** `pending:{confirmId}` TTL 5 phút → fast lookup khi user bấm nút (< 5ms)
- **DB** record đầy đủ → audit trail lâu dài, cron job expire PENDING quá hạn

**tool_call_logs — partition bắt buộc:**

Bảng này chỉ INSERT và tăng trưởng rất nhanh. Sau 6 tháng với traffic thực tế có thể lên hàng chục triệu records. Không partition thì query sẽ full-table-scan.

**outbox_events — query với FOR UPDATE SKIP LOCKED:**

```sql
-- Nhiều instance app chạy song song, mỗi instance chỉ lấy row chưa bị lock
SELECT * FROM outbox_events
WHERE status = 'PENDING' AND retry_count < 3
ORDER BY created_at ASC
LIMIT 50
FOR UPDATE SKIP LOCKED;
```

### 4.4 Redis keys

| Key | TTL | Mục đích |
|-----|-----|----------|
| `rate:{userId}` | 60s | Rate limit counter (20 req/phút) |
| `tool:rate:{userId}` | 60s | Rate limit riêng cho Tool calls (10/phút) |
| `ctx:{sessionId}` | 30 phút | Cache 20 messages gần nhất, tránh query DB mỗi request |
| `pending:{confirmId}` | 5 phút | Fast lookup khi user bấm confirm |
| `buf:{sessionId}` | 10 phút | Buffer 20 SP từ PageIndex cho "Xem thêm" |
| `tool:cache:{hash}` | 60s | Cache kết quả Tool đọc (Mức 1) |

---

## 5. Kafka — Realtime Sync DB → Elasticsearch

### 5.1 Cấu hình

| Thông số | Giá trị |
|----------|---------|
| Topic | `product.changes` |
| Partition key | `productId` — đảm bảo thứ tự event cho từng sản phẩm |
| Consumer group | `es-indexer` |
| Publish timing | Sau khi DB transaction commit (`TransactionSynchronization.afterCommit`) |
| Fallback | Ghi `outbox_events` khi Kafka fail, cron retry mỗi 30s |

### 5.2 Event types

| Type | ES action | Khi nào |
|------|-----------|---------|
| `CREATED` | Full index document | Sản phẩm mới tạo |
| `UPDATED` | Full reindex | Tên, mô tả thay đổi |
| `DELETED` | Update `is_active = false` | Soft delete |
| `STOCK_CHANGED` | Partial update: `in_stock` | Tồn kho thay đổi |
| `PRICE_CHANGED` | Partial update: `price` | Giá thay đổi |

---

## 6. Elasticsearch — Product Search

### 6.1 Chiến lược phân trang

**Quyết định: from/size + track_total_hits = 10,000.**

User bấm trang → lazy load tại thời điển đó, data mới nhất. Chấp nhận lệch nhẹ giữa các trang khi data realtime.

| Tham số | Giá trị | Lý do |
|---------|---------|-------|
| `max_result_window` | 10,000 | Hard limit của ES, không nâng lên |
| Page size | 40 sản phẩm/trang | Tối đa 250 trang |
| `track_total_hits` | 10,000 | Đếm đến 10k rồi dừng, hiển thị "10,000+ sản phẩm" |
| Tiebreaker | `sort_id: asc` | Bắt buộc với mọi sort option để đảm bảo thứ tự ổn định |

### 6.2 Index Mapping (cấu trúc quan trọng)

```json
{
  "mappings": {
    "properties": {
      "name":       { "type": "text", "analyzer": "vi_analyzer",
                      "fields": { "suggest": { "type": "text", "analyzer": "suggest_analyzer" },
                                  "keyword": { "type": "keyword" } } },
      "price":      { "type": "double" },
      "brand_id":   { "type": "keyword" },
      "color":      { "type": "keyword" },
      "size":       { "type": "keyword" },
      "in_stock":   { "type": "boolean" },
      "created_at": { "type": "date" },
      "sold_count": { "type": "integer" },
      "sort_id":    { "type": "long" }
    }
  }
}
```

> **Rule:** Field dùng cho filter/sort/agg phải là `keyword` hoặc `number`. `text` chỉ dùng cho full-text search.

### 6.3 Tiếng Việt

| Vấn đề | Giải pháp |
|--------|-----------|
| Gõ không dấu: "ao thun" | `asciifolding` filter với `preserve_original: true` |
| Sai chính tả: "ao thunn" | `fuzziness: AUTO` trong query |
| Từ đồng nghĩa | Synonym filter với file `synonyms/vi_product.txt` |
| Plugin khuyến nghị | `elasticsearch-plugin install analysis-icu` |

---

## 7. Spring AI — Cấu hình chính

### 7.1 application.yml

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o
          temperature: 0.1    # thấp = ít ảo giác
          max-tokens: 2048

pageindex:
  api-key: ${PAGEINDEX_API_KEY}
  index-id:
    product: ${PAGEINDEX_PRODUCT_INDEX}
    feature: ${PAGEINDEX_FEATURE_INDEX}
```

### 7.2 System Prompt template

```
Bạn là [TÊN BOT], trợ lý ảo của [TÊN CÔNG TY].

## Quy tắc cốt lõi
1. KHÔNG bao giờ bịa thông tin. Nếu không biết → "Em chưa có thông tin về vấn đề này".
2. LUÔN dùng Tools để tra cứu thay vì trả lời từ kiến thức chung.
3. KHÔNG thực hiện hành động thay đổi dữ liệu khi chưa được xác nhận.
4. Nếu câu hỏi ngoài phạm vi → từ chối lịch sự.

## Phong cách
- Xưng "em", gọi khách là "anh/chị"
- Ngắn gọn, thân thiện, chuyên nghiệp

## Context người dùng
- userId: {userId} | Tên: {userName} | Thời gian: {currentTime}
```

### 7.3 Tool Definition checklist

Mô tả Tool phải đủ rõ để AI biết KHI NÀO dùng và KHÔNG dùng khi nào:

| Yếu tố | Bắt buộc? |
|--------|-----------|
| Khi nào dùng | Bắt buộc |
| Khi nào KHÔNG dùng | Bắt buộc |
| Ví dụ trigger phrase | Khuyến nghị |
| Cảnh báo xác nhận | Bắt buộc với Mức 3 |
| Định dạng tham số | Khuyến nghị |

---

## 8. Cấu trúc project Spring AI

```
com.yourcompany.ai
├── config/
│   ├── SpringAiConfig.java          # ChatClient bean, model config
│   ├── PageIndexConfig.java         # PageIndex client bean
│   └── SecurityConfig.java          # JWT filter, rate limit
├── controller/
│   ├── ChatController.java          # POST /api/ai/chat (SSE)
│   ├── SessionController.java       # POST/DELETE /api/ai/sessions
│   └── ConfirmController.java       # POST /api/ai/confirm
├── service/
│   ├── ChatService.java             # Orchestration logic
│   ├── PageIndexService.java        # Vector search wrapper
│   └── ConfirmationService.java     # Human-in-the-loop
├── tools/
│   ├── ProductSearchTool.java       # @Tool risk_level=1
│   ├── OrderQueryTool.java          # @Tool risk_level=2
│   └── SystemActionTool.java        # @Tool risk_level=3
└── model/
    ├── ChatRequest.java
    └── ChatResponse.java
```
