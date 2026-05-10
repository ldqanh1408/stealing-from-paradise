# Đề xuất sửa `database-entities.md` (Đã duyệt một phần)

> **Generated:** 2026-05-10
> **Reviewed:** 2026-05-10 by Le Dac Quoc Anh
> **Status:** ✅ APPLIED — các mục được duyệt đã áp dụng vào `database-entities.md`

## Tổng hợp quyết định

| ID | Quyết định | Ghi chú |
|----|------------|---------|
| P0-01 | ✅ Approve A | PostgreSQL là chân lý; cần sync 7 entity-*.md + CONTRADICTIONS.md |
| P0-02 | ❌ Reject | Không thêm `outbox_events` schema |
| P0-03 | ❌ Reject | Không thêm `failed_events` schema |
| P0-04 | ❌ Reject | Không thêm `shedlock` schema |
| P0-05 | ✏️ Modified | Giữ `pending_confirmations` + `tool_call_logs`; **bỏ** `outbox_events_ai` |
| P1-06 | ✏️ Modified | Chỉ thêm cột `currency`; bỏ `total_shipping_fee` / `total_platform_fee` / `total_discount_amount` |
| P1-07 | ❌ Reject | Không cần `order_status_history` cho MVP — Axon event store + các cột timestamp hiện tại đủ dùng |
| P1-08 | ✅ Approve | Thêm `failure_reason`, `failure_code`, `stripe_payout_id`; làm rõ status enum |
| P2-09 | ✅ Approve | Thêm `read_at` vào `mg_notifications` |
| P2-10 | ⏸️ Pending | User chưa quyết định — chờ xác nhận |
| P3-11 | ✅ Approve | 2026-05-10 user duyệt — applied vào `database-entities.md` + `entity-product.md`. Status enum mở rộng (7 values), thêm `reject_reason`, `reviewed_at`, `reviewed_by`, `reject_count`, partial index `idx_products_status_pending`. |

Mỗi đề xuất bên dưới có: **Vấn đề → Đề xuất → Tác động → Quyết định cần** (☐ approve / ☐ reject).

---

## P0-01 — Mâu thuẫn MongoDB ↔ PostgreSQL cho Catalog/Cart

**Vấn đề**:
- `database-entities.md` mục 3 (line 102–104) viết rõ: *"Tất cả bảng catalog chuyển sang **PostgreSQL** (không dùng MongoDB). Các bảng cũ `MG_*` được thay thế hoàn toàn."*
- Nhưng `CONTRADICTIONS.md` mục 1.3 lại viết: *"Old docs were CORRECT (MongoDB). New docs have been updated to reflect MongoDB reality."*
- 7 file `documents/data-models/product-service/entity-*.md` đang ghi MongoDB (`mg_products`, `mg_carts`, ObjectId…).

**Đề xuất** (chọn 1):

- **Phương án A (giữ nguyên DB truth)**: Coi `database-entities.md` là chân lý → PostgreSQL. Sửa 7 entity doc + cập nhật `CONTRADICTIONS.md` để loại bỏ ghi chú lỗi.
- **Phương án B (rollback DB truth về MongoDB)**: Thay nội dung mục 3, 4 trong `database-entities.md` từ PostgreSQL về `MG_*` collections + ObjectId. Phương án này phá vỡ thiết kế Search index hiện tại (đang dùng `keyword UUID` cho `sku_id`, `product_id`).

**Tác động**:
- Saga checkout gọi `order.stock_check.request` đang dùng `variant_id UUID` (xem `order_items.variant_id UUID`) → **Phương án A bắt buộc** để consistent với `orders.parent_orders` PostgreSQL.
- Search index `skus` dùng `keyword` cho IDs → tương thích cả hai nhưng `is_active boolean` map tốt hơn với SQL boolean.

**Đề xuất chính thức**: ✅ Phương án A — giữ PostgreSQL.

**Quyết định**: ☐ Approve A   ☐ Approve B   ☐ Reject

---

## P0-02 — Bảng `outbox_events` chưa có schema

**Vấn đề**: Mục 9 chỉ ghi *"Giữ nguyên"* nhưng không có file schema cũ nào trong source-of-truth.

**Đề xuất bổ sung**:

```sql
CREATE TABLE outbox_events (
  id              BIGSERIAL    PRIMARY KEY,
  aggregate_type  VARCHAR(50)  NOT NULL,        -- 'order','payment','product'…
  aggregate_id    VARCHAR(100) NOT NULL,         -- entity ID (string-form for UUID/BIGINT)
  event_type      VARCHAR(100) NOT NULL,         -- 'order.created'
  payload         JSONB        NOT NULL,
  status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING | PROCESSED | FAILED
  retry_count     INT          NOT NULL DEFAULT 0,
  next_retry_at   TIMESTAMP,
  last_error      TEXT,
  created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
  processed_at    TIMESTAMP
);
CREATE INDEX idx_outbox_status_retry ON outbox_events(status, next_retry_at);
CREATE INDEX idx_outbox_aggregate    ON outbox_events(aggregate_type, aggregate_id);
```

**Quyết định**: ☐ Approve   ☐ Reject   ☐ Modify

---

## P0-03 — Bảng `failed_events` (DLQ) chưa có schema

**Đề xuất**:

```sql
CREATE TABLE failed_events (
  id              BIGSERIAL    PRIMARY KEY,
  topic           VARCHAR(100) NOT NULL,
  partition       INT,
  offset_value    BIGINT,
  event_id        VARCHAR(100),                  -- evt_id từ envelope
  payload         JSONB        NOT NULL,
  error_message   TEXT         NOT NULL,
  error_class     VARCHAR(255),
  retry_count     INT          NOT NULL DEFAULT 0,
  status          VARCHAR(20)  NOT NULL DEFAULT 'DEAD',     -- DEAD | RETRYING | RESOLVED
  consumer_group  VARCHAR(100),
  created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
  resolved_at     TIMESTAMP
);
CREATE INDEX idx_failed_status     ON failed_events(status);
CREATE INDEX idx_failed_created_at ON failed_events(created_at);
```

**Retention** (xem CRONJOBS): RESOLVED >30 ngày + DEAD >90 ngày → cleanup.

**Quyết định**: ☐ Approve   ☐ Reject   ☐ Modify

---

## P0-04 — Bảng `shedlock` chưa có schema

**Đề xuất** (chuẩn ShedLock 5.x cho PostgreSQL):

```sql
CREATE TABLE shedlock (
  name        VARCHAR(64)  PRIMARY KEY,
  lock_until  TIMESTAMP    NOT NULL,
  locked_at   TIMESTAMP    NOT NULL,
  locked_by   VARCHAR(255) NOT NULL
);
```

> CONTRADICTIONS.md mục #35 ghi: ShedLock claim trong docs nhưng code chỉ có 1 `@Scheduled` (PayoutScheduler) chưa có `@SchedulerLock`. Khi nào áp dụng ShedLock thì cần tạo bảng này.

**Quyết định**: ☐ Approve   ☐ Reject   ☐ Modify

---

## P0-05 — AI Chat: bảng `pending_confirmations`, `tool_call_logs`, `outbox_events_ai`

**Vấn đề**: Mục 11 chỉ ghi *"giữ nguyên toàn bộ bảng chat_sessions, chat_messages, pending_confirmations, tool_call_logs, outbox_events_ai như thiết kế cũ, không thay đổi"* — không có schema thực tế.

**Đề xuất**:

```sql
CREATE TABLE pending_confirmations (
  id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id      UUID         NOT NULL,                   -- FK chat_sessions.id
  message_id      UUID         NOT NULL,                   -- FK chat_messages.id
  user_id         BIGINT       NOT NULL,
  tool_name       VARCHAR(100) NOT NULL,                   -- e.g. cancel_order
  tool_arguments  JSONB        NOT NULL,
  level           VARCHAR(20)  NOT NULL,                   -- LEVEL_1 | LEVEL_2 | LEVEL_3
  status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING', -- PENDING | CONFIRMED | REJECTED | EXPIRED
  expires_at      TIMESTAMP    NOT NULL,                   -- e.g. now()+5min
  resolved_at     TIMESTAMP,
  created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_pending_session ON pending_confirmations(session_id);
CREATE INDEX idx_pending_status  ON pending_confirmations(status, expires_at);

CREATE TABLE tool_call_logs (
  id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id      UUID         NOT NULL,
  message_id      UUID,
  tool_name       VARCHAR(100) NOT NULL,
  arguments       JSONB        NOT NULL,
  result          JSONB,
  status          VARCHAR(20)  NOT NULL,                   -- SUCCESS | FAILED | REJECTED
  error_message   TEXT,
  duration_ms     INT,
  created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_tool_call_session ON tool_call_logs(session_id);
CREATE INDEX idx_tool_call_status  ON tool_call_logs(status, created_at);

-- outbox_events_ai: same schema as outbox_events but scoped to chat-service DB
CREATE TABLE outbox_events_ai ( ... same as outbox_events ... );
```

**Quyết định**: ☐ Approve   ☐ Reject   ☐ Modify

---

## P1-06 — `parent_orders` không có cột `total_seller_fee` / `currency`

**Vấn đề**: `parent_orders` chỉ có `total_amt`, `final_amt`. Hiện tại không lưu commission tổng / discount tổng / shipping fee.

**Đề xuất**: Thêm các cột sau (tùy chọn, có thể defer):

```sql
ALTER TABLE parent_orders
  ADD COLUMN currency               VARCHAR(3)    NOT NULL DEFAULT 'VND',
  ADD COLUMN total_shipping_fee     DECIMAL(18,2) NOT NULL DEFAULT 0,
  ADD COLUMN total_platform_fee     DECIMAL(18,2) NOT NULL DEFAULT 0,
  ADD COLUMN total_discount_amount  DECIMAL(18,2) NOT NULL DEFAULT 0;
```

**Tác động**: Cần migration cho dữ liệu hiện có (`UPDATE … SET currency='VND'`).

**Quyết định**: ☐ Approve   ☐ Reject   ☐ Defer post-MVP

---

## P1-07 — `orders.status_history` (timeline)

**Vấn đề**: Để hiện thực `GET /orders/{id}/timeline` cần lưu lịch sử state-transition.

**Đề xuất** (bảng mới):

```sql
CREATE TABLE order_status_history (
  id          BIGSERIAL    PRIMARY KEY,
  order_id    BIGINT       NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  from_status VARCHAR(50),
  to_status   VARCHAR(50)  NOT NULL,
  reason      TEXT,
  actor_type  VARCHAR(20)  NOT NULL,                 -- BUYER | SELLER | SYSTEM | ADMIN
  actor_id    BIGINT,
  created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_order_status_history_order ON order_status_history(order_id, created_at);
```

**Tác động**: Trigger/listener: mỗi khi `orders.status` UPDATE → INSERT vào history.

**Quyết định**: ☐ Approve   ☐ Reject   ☐ Defer post-MVP

---

## P1-08 — `seller_transfers.payout_status` chi tiết

**Vấn đề**: Hiện tại `status` chỉ generic. Để track Stripe payout cần thêm trạng thái cụ thể.

**Đề xuất**:

- Status enum đề xuất: `ELIGIBLE` → `IN_TRANSIT` → `PAID` / `FAILED` / `RETRYING`.
- Thêm cột:

```sql
ALTER TABLE seller_transfers
  ADD COLUMN failure_reason       TEXT,
  ADD COLUMN failure_code         VARCHAR(50),
  ADD COLUMN stripe_payout_id     VARCHAR(100);  -- separate from stripe_transfer_id
```

**Quyết định**: ☐ Approve   ☐ Reject   ☐ Defer

---

## P2-09 — `notifications` thêm cột `read_at`

**Vấn đề**: Chỉ có `is_read BOOLEAN` — không biết khi nào đã đọc.

**Đề xuất**:

```javascript
// MongoDB mg_notifications
{
  read_at: ISODate | null   // set khi PUT /notifications/{id}/read
}
```

**Quyết định**: ☐ Approve   ☐ Reject

---

## P2-10 — Index cho `users.email`, `users.phone`

**Vấn đề**: Đã có UNIQUE nhưng chưa nói rõ có index riêng cho lookup login (`email OR username OR phone`). UNIQUE đã ngụ ý index.

**Đề xuất**: bổ sung dòng ghi chú trong tài liệu:

```sql
-- Tự động có UNIQUE INDEX:
-- idx_users_username UNIQUE, idx_users_email UNIQUE, idx_users_phone UNIQUE
```

**Quyết định**: ☐ Approve (chỉ là ghi chú, không thay đổi schema)

---

## P3-11 — Mở rộng `products` cho Admin Review Workflow

**Bối cảnh**: User yêu cầu (2026-05-10 v3) đưa lại workflow **Admin Product Approve/Reject** vào MVP và đã chọn phương án **"Mở rộng đầy đủ"** — DB schema phải hỗ trợ states `pending / approved / rejected` cùng với metadata reviewer.

**Vấn đề**: `database-entities.md` mục 3 hiện ghi `product.status` enum chỉ có `active / out_of_stock / inactive`. Không có cột reviewer/reject_reason. Nếu giữ nguyên, không thể implement workflow seller submit → admin approve/reject.

**Đề xuất schema delta**:

```sql
-- 1. Mở rộng enum status (PostgreSQL TEXT/CHECK constraint hoặc enum type)
ALTER TABLE products
  ADD CONSTRAINT chk_products_status
  CHECK (status IN ('draft', 'pending', 'approved', 'rejected', 'active', 'out_of_stock', 'inactive'));

-- 2. Thêm 4 cột reviewer metadata + counter (đều NULLABLE/DEFAULT — chỉ set khi admin act)
ALTER TABLE products
  ADD COLUMN reject_reason  TEXT          NULL,                       -- lý do admin reject (≥10 chars khi set)
  ADD COLUMN reviewed_at    TIMESTAMP     NULL,                       -- thời điểm approve/reject gần nhất
  ADD COLUMN reviewed_by    BIGINT        NULL REFERENCES users(id),  -- admin user_id (FK soft, không CASCADE)
  ADD COLUMN reject_count   INT           NOT NULL DEFAULT 0;          -- 3-strike limit (BR-PRODUCT-009.8)

-- 3. Index hỗ trợ admin queue listing (FIFO oldest pending first)
CREATE INDEX idx_products_status_pending ON products(status, created_at)
  WHERE status = 'pending';
```

> **Approved 2026-05-10**: Bổ sung `reject_count` để hỗ trợ 3-strike resubmit limit (BR-PRODUCT-009.8). Approve action reset `reject_count = 0` để tha lỗi sau khi pass.

**Lifecycle mới (7 trạng thái)**:

```
draft ──submit──▶ pending ──approve──▶ approved ──publish──▶ active
                     │                                          │
                     └──reject──▶ rejected ──resubmit──▶ draft  │
                                                                ▼
                                                       out_of_stock ↔ inactive
```

| Trạng thái | Ý nghĩa | Có thể publish (active) | Hiển thị shop |
|-----------|---------|------------------------|---------------|
| draft | Seller đang soạn, chưa submit | Không | Không |
| pending | Đã submit, chờ admin duyệt | Không | Không |
| approved | Admin đã duyệt nhưng chưa publish | Sẵn sàng | Không |
| rejected | Admin từ chối với lý do | Không (phải sửa + resubmit) | Không |
| active | Đã publish, hiển thị + bán được | — | Có |
| out_of_stock | Hết hàng (auto từ inventory) | — | Có (badge "Hết hàng") |
| inactive | Seller đã unpublish | — | Không |

**Tác động**:

| Hạng mục | Ảnh hưởng |
|----------|----------|
| `entity-product.md` | Cập nhật cột status comment + thêm 3 cột mới |
| `state-product.md` | Thay diagram 3-state → 7-state |
| `br-catalog.md` | Thêm BR-PRODUCT-008 (admin review workflow); update BR-PRODUCT-003 |
| 4 admin product YAML | Bỏ `# DEPRECATED`, đảm bảo response trả `reviewed_at`, `reviewed_by`, `reject_reason` |
| `KAFKA_EVENTS.md` (product) | Thêm 3 event: `product.pending_review`, `product.approved`, `product.rejected` |
| `KAFKA_CATALOG.md` | Re-add 3 events + cập nhật count |
| Search service | Re-index khi `approved → active` (consumer mới cho `product.approved` hoặc dựa vào `product.activated`) |
| Notification service | 3 template mới (NOTIF-PRODUCT-APPROVED/REJECTED/PENDING-REVIEW) |
| 4 use case mới | UC-PRODUCT-009..012 |
| `traceability-matrix.md` (product) | Thêm 4 dòng UC + map BR + Kafka |

**Migration plan**:
- Bước 1: Apply ALTER TABLE (3 cột mới, NULLABLE → backfill `NULL`).
- Bước 2: Backfill — sản phẩm hiện tại (`status IN active/out_of_stock/inactive`) coi như đã được approved trước khi workflow tồn tại; có thể optional set `reviewed_at = created_at`, `reviewed_by = NULL` (system grandfather).
- Bước 3: Update enum constraint sau khi backfill xong.
- Không có downtime (toàn bộ cột NULLABLE, enum mở rộng forward-compatible).

**Rủi ro / Trade-off**:
- Thêm bottleneck: mọi product mới phải qua admin queue → cần SLA review (đề xuất ≤24h, KPI riêng).
- Resubmit loop: `rejected → draft → pending` có thể tạo ping-pong; cần giới hạn (đề xuất tối đa 3 lần reject; lần 4 lock seller).
- Search re-indexing: khi `active → inactive` cũng cần xoá khỏi ES; flow này đã có sẵn từ `product.deactivated`.

**Quyết định**: ☐ Approve   ☐ Reject   ☐ Modify (vd: bỏ `reviewed_by`, gộp `reject_reason` vào `metadata` JSONB, hoặc chỉ approve enum mà không thêm cột riêng)

> **Lưu ý**: Phase 3 (B2..B17 trong plan re-activation) sẽ chỉ tiến hành SAU KHI mục P3-11 này được duyệt. Phase 1 (Seller Cancel) đã hoàn thành và không phụ thuộc P3-11.

---

## Tổng kết

| ID | Hạng mục | Loại | Ưu tiên |
|----|----------|------|---------|
| P0-01 | MongoDB↔PostgreSQL contradiction | Sửa logic | Block MVP |
| P0-02 | `outbox_events` schema | Bổ sung | Block MVP |
| P0-03 | `failed_events` schema | Bổ sung | Block MVP |
| P0-04 | `shedlock` schema | Bổ sung | Block MVP |
| P0-05 | AI chat 3 bảng | Bổ sung | Block MVP |
| P1-06 | `parent_orders` cột mới | Sửa schema | Hoàn thiện |
| P1-07 | `order_status_history` bảng mới | Bổ sung | Hoàn thiện |
| P1-08 | `seller_transfers` cột Stripe payout | Sửa schema | Hoàn thiện |
| P2-09 | `notifications.read_at` | Bổ sung | UX |
| P2-10 | Ghi chú index `users` | Tài liệu | Tài liệu |
| P3-11 | `products` admin review (status enum 7 values + reject_reason + reviewed_at + reviewed_by + reject_count + partial index `idx_products_status_pending`) | Sửa schema | Block re-activation Phase 3 |

→ Sau khi user duyệt, sẽ áp dụng vào `database-entities.md` (1 commit duy nhất, kèm changelog).
