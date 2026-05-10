# MVP Analysis — Missing APIs, Events & Schema Gaps

> **Generated:** 2026-05-10
> **Source of Truth:** `database-entities.md` (2026-05-09) — KHÔNG được sửa file gốc
> **Scope:** Phân tích gap để hoàn thiện sản phẩm MVP
> **Methodology:** Tuân thủ template trong `System Analyst and Software Architect.md`

---

## 0. Tóm tắt điều hành

| Hạng mục | Số lượng | Mức độ |
|----------|----------|--------|
| API endpoints THIẾU (MUST-HAVE) | 3 | Block MVP |
| API endpoints THIẾU (SHOULD) | 4 | Hoàn thiện UX |
| API endpoints OBSOLETE phải xóa/deprecate | 0 (sau v3 re-activate 4 admin product YAMLs) | Cleanup |
| Kafka events THIẾU (MUST-HAVE) | 2 | Block MVP |
| Kafka events THIẾU (SHOULD) | 5 | Hoàn thiện flow |
| Kafka events OBSOLETE phải xóa | 3 (flash_sale.item_approved/rejected + flash_sale.item_sold renamed) | Cleanup |
| Bảng DB chưa định nghĩa schema chi tiết | 5 | Cần đề xuất |
| Entity doc lệch DB truth | 7 | Trong đó 5 catalog mâu thuẫn MongoDB↔PostgreSQL |

> **Đề xuất sửa `database-entities.md`** (theo yêu cầu chỉ ghi sang file mới): xem [`DB_SCHEMA_CHANGE_PROPOSAL.md`](./DB_SCHEMA_CHANGE_PROPOSAL.md).

---

## 1. Phạm vi MVP (Scope)

### ✅ MVP có:
- Identity: register/login/logout/refresh, profile, addresses, admin lock/unlock
- Catalog: browse public, category tree, product detail, seller CRUD, image upload, inventory
- Cart: view, add, update qty, remove, clear
- Flash Sale: list, register product (seller), buy (Redis Lua), reminders
- Order: checkout, list, detail, buyer cancel (PENDING/PAID), seller ship, buyer confirm received, return-refund (RTS)
- Payment: Stripe Connect onboarding, payment-intent, webhook, refund (admin approve/reject), seller transfer payouts
- Notification: SSE/WS stream, list, mark read
- Search: full-text, filter by category/price/attributes
- AI Chat: send, history, confirm-action

### ❌ MVP KHÔNG có (đã thống nhất theo CONTRADICTIONS.md):
- Flash sale item approval (auto-approve)
- Worker-service riêng (đã merge vào chat-service & phân tán cronjobs)

> **Đính chính 2026-05-10 v3 (re-scope)**: Hai workflow trước đây liệt kê KHÔNG-có nay được **đưa lại MVP** theo yêu cầu user:
> - **Seller cancel order** — chỉ cho phép ở trạng thái `PAID` (trước khi `SHIPPING`). Sau SHIPPING phải dùng RTS.
> - **Admin product approve/reject** — kèm mở rộng schema PRODUCT (xem `DB_SCHEMA_CHANGE_PROPOSAL.md` §P3-11).

---

## 2. API Gap Analysis

> **Đính chính (2026-05-10 v2):** Sau khi đọc trực tiếp các YAML, một số endpoint mà audit ban đầu báo "thiếu" thực ra đã có (gộp trong YAML khác): `GET /products/{productId}` đã có trong `api-get-products.yaml`; `PUT/DELETE /cart/items/{itemId}` đã có trong `api-post-cart-items.yaml`; `PUT /notifications/read-all` đã có trong `api-put-notifications-read.yaml`. Bảng dưới là danh sách CHÍNH XÁC.

### 2.1 MUST-HAVE — 3 endpoints thực sự còn thiếu

| # | Method + Path | Service | UC liên quan | Lý do MUST |
|---|--------------|---------|--------------|-----------|
| 1 | `GET /categories/{categoryId}` | product-service | UC-PRODUCT-002 | Xem chi tiết + cây danh mục con (currently chỉ có `GET /categories` flat list) |
| 2 | `POST /inventory/{skuCode}/reserve` | product-service | UC-PRODUCT-007 | Order-service gọi để reserve stock 15 phút khi checkout (hiện có GET, restock, adjust nhưng không có reserve trực tiếp) |
| 3 | `GET /sellers/me/orders/{orderId}` | order-service | UC-ORDER-007 | Seller xem chi tiết đơn (`GET /orders/{id}` dùng cho buyer; cần endpoint riêng cho seller scope) |

### 2.2 SHOULD-HAVE — 4 endpoints hoàn thiện UX

| # | Method + Path | Service | Ghi chú |
|---|--------------|---------|---------|
| 4 | `DELETE /notifications/{id}` | notification-service | Xóa 1 notification (hiện chỉ có mark-read) |
| 5 | `GET /orders/{orderId}/timeline` | order-service | Lịch sử trạng thái đơn (cần bảng `order_status_history` — xem P1-07 trong DB_SCHEMA_CHANGE_PROPOSAL) |
| 6 | `DELETE /cart` | product-service | Clear toàn bộ cart |
| 7 | `DELETE /seller/products/{productId}/images/{imageId}` | product-service | Xóa ảnh sản phẩm |

### 2.3 OBSOLETE — phải xóa/deprecate

> **Đính chính 2026-05-10 v3**: 4 YAML admin product trước đây liệt kê OBSOLETE đã được **re-activated** trong MVP (xem §1 và `DB_SCHEMA_CHANGE_PROPOSAL.md` §P3-11). Bảng dưới giữ lại để theo dõi lịch sử thay đổi.

| File YAML | Trạng thái | Ghi chú |
|-----------|-----------|---------|
| `api-post-admin-products-approve.yaml` | ✅ ACTIVE (re-activated) | v5.5.0 — P3-11 APPROVED & applied 2026-05-10 |
| `api-post-admin-products-reject.yaml` | ✅ ACTIVE (re-activated) | v5.5.0 — `reason` ≥10 chars |
| `api-get-admin-products-pending.yaml` | ✅ ACTIVE (re-activated) | v5.5.0 |
| `api-put-products-lifecycle.yaml` | ✅ ACTIVE (full lifecycle) | v5.5.0 — `submitForReview` + publish/unpublish |

→ **Hành động hiện tại**: Đã gỡ `# DEPRECATED` headers và bump version → `5.5.0`. Đợi P3-11 duyệt để cập nhật `database-entities.md`.

---

## 3. Kafka Event Gap Analysis

### 3.1 MUST-HAVE — 2 event chặn MVP

| Event | Producer | Consumer | Trigger | Payload (key fields) |
|-------|----------|----------|---------|----------------------|
| `stock.reservation.expired` | product-service (JOB-13) | order-service, notification-service | TTL 15 phút hết hạn | `reservation_id`, `variant_id`, `quantity`, `session_id`, `expired_at` |
| `order.payment_timeout` | order-service (JOB-22) | order-service self-consume → auto-cancel; notification-service | 10 phút PENDING_PAYMENT chưa có `payment.success` | `parent_order_id`, `order_ids[]`, `timeout_reason`, `auto_cancelled_at` |

### 3.2 SHOULD-HAVE — 5 event hoàn thiện flow

| Event | Producer | Consumer | Mục đích |
|-------|----------|----------|----------|
| `stock.reservation.confirmed` | product-service | (audit only) | Quan sát saga checkout |
| `stock.reservation.released` | product-service | (audit only) | Khi `payment.failed`/buyer cancel |
| `seller.transfer.eligible` | payment-service (JOB-23 PayoutScheduler) | notification-service | Hết 30 ngày return window → đủ điều kiện payout |
| `seller.transfer.paid_out` | payment-service (Stripe payout webhook) | notification-service | Stripe payout thành công |
| `seller.transfer.failed` | payment-service | notification-service, audit | Retry exhausted |

### 3.3 OBSOLETE — events đã rà soát lại

> **Đính chính 2026-05-10 v3**: 4 sự kiện trước đây bị OBSOLETE giờ đã được **re-activated** theo phạm vi MVP mới.

| Event | Trạng thái | Ghi chú |
|-------|-----------|---------|
| `product.pending_review` | ✅ ACTIVE (re-activated) | Trigger khi seller submit product (UC-PRODUCT-012) |
| `product.approved` | ✅ ACTIVE (re-activated) | Trigger khi admin approve (UC-PRODUCT-014) |
| `product.rejected` | ✅ ACTIVE (re-activated) | Trigger khi admin reject (UC-PRODUCT-015) |
| `seller.order_cancelled` | ✅ ACTIVE (re-activated) | Trigger khi seller cancel ở `PAID` (UC-ORDER-008) |
| `flash_sale.item_approved` / `flash_sale.item_rejected` | ❌ OBSOLETE | Auto-approve trong MVP — không có review workflow |
| `flash_sale.item_sold` | 🔄 RENAMED | Đã đổi tên thành `flash_sale.item_purchased` |

→ Cập nhật đã thực hiện ở `KAFKA_CATALOG.md` + `messaging/product-service/KAFKA_EVENTS.md` + `messaging/order-service/KAFKA_EVENTS.md`.

---

## 4. Data-Model Gap Analysis

### 4.1 Entity doc lệch DB truth

| Entity file | Vấn đề | Hành động |
|-------------|--------|-----------|
| `product-service/entity-category.md` | Ghi MongoDB `mg_categories` — DB truth là PostgreSQL UUID | Cần sửa entity doc |
| `product-service/entity-product.md` | MongoDB → PostgreSQL | Sửa entity doc |
| `product-service/entity-product-variant.md` | MongoDB → PostgreSQL | Sửa entity doc |
| `product-service/entity-product-image.md` | MongoDB → PostgreSQL | Sửa entity doc |
| `product-service/entity-stock-reservation.md` | MongoDB → PostgreSQL | Sửa entity doc |
| `product-service/entity-cart.md` | MongoDB → PostgreSQL | Sửa entity doc |
| `product-service/entity-cart-item.md` | MongoDB → PostgreSQL | Sửa entity doc |

**Quyết định cần xác nhận**: `database-entities.md` mục 3 viết rõ "Tất cả bảng catalog **chuyển sang PostgreSQL**". Nhưng `CONTRADICTIONS.md` mục 1.3 ghi "Old docs were CORRECT (MongoDB)" — mâu thuẫn nội bộ.

→ **Đề xuất**: bám `database-entities.md` (2026-05-09) làm chân lý → sửa 7 entity doc + cập nhật `CONTRADICTIONS.md` mục 1.3.

### 4.2 Bảng được nhắc nhưng thiếu schema chi tiết

`database-entities.md` chỉ ghi *"giữ nguyên"* cho:

- `outbox_events`
- `failed_events`
- `shedlock`
- `pending_confirmations` (ai-chat)
- `tool_call_logs` (ai-chat)
- `outbox_events_ai` (ai-chat)

→ Đề xuất bổ sung schema cụ thể vào `DB_SCHEMA_CHANGE_PROPOSAL.md` để tao xem xét.

---

## 5. Action Plan ưu tiên

| Priority | Hạng mục | Owner | Output |
|----------|----------|-------|--------|
| P0 | Bổ sung 7 API YAML MUST-HAVE | SA | `documents/api-contracts/{service}/api-*.yaml` |
| P0 | Bổ sung 2 Kafka event MUST-HAVE | SA | Cập nhật KAFKA_CATALOG + service KAFKA_EVENTS |
| P0 | Đề xuất sửa `database-entities.md` | SA | `DB_SCHEMA_CHANGE_PROPOSAL.md` (chờ user duyệt) |
| P1 | Cleanup obsolete API/event (DEPRECATED markers) | SA | Sửa file YAML/MD hiện có |
| P1 | Bổ sung 5 SHOULD event (seller transfer + reservation observability) | SA | KAFKA docs |
| P2 | Sửa 7 entity catalog MongoDB→PostgreSQL | SA | Chờ user xác nhận chân lý |
| P2 | Bổ sung 4 SHOULD API | SA | YAML files |

---

## 6. Cross-references

- Template: [`System Analyst and Software Architect.md`](./System Analyst and Software Architect.md)
- Source of truth: [`database-entities.md`](./database-entities.md)
- Mâu thuẫn đã ghi: [`CONTRADICTIONS.md`](./CONTRADICTIONS.md)
- Đề xuất sửa DB: [`DB_SCHEMA_CHANGE_PROPOSAL.md`](./DB_SCHEMA_CHANGE_PROPOSAL.md)
- API catalog: [`operations/API_URLS.md`](./operations/API_URLS.md)
- Kafka catalog: [`messaging/KAFKA_CATALOG.md`](./messaging/KAFKA_CATALOG.md)
