# Gap Analysis: Tài liệu vs Code thực tế

**Ngày phân tích:** 2026-06-07  
**Phương pháp:** So sánh trực tiếp giữa documents/ và source code Java trong backend/  
**Các file đã đọc:** KAFKA_CATALOG.md, KAFKA_EVENTS.md (mỗi service), CRONJOBS.md, tất cả Controllers, tất cả Kafka Consumers/Producers

---

## Tóm tắt nhanh

| Hạng mục | Tổng trong tài liệu | Đã implement | Chưa implement |
|----------|---------------------|--------------|----------------|
| Cronjobs | 15 jobs (1 MongoDB TTL, không phải job thật) | 1 (JOB-23) | **13** |
| Kafka Producers | ~30 topics được produce | ~26 | **4** (toàn bộ flashsale events) |
| Kafka Consumers | ~25 listeners | ~24 (1 stub không logic) | **1 missing + 4 stubs** |
| API Endpoints | ~80+ endpoints | ~79 | **1 confirmed missing** |
| REST discrepancy | — | — | 3 (HTTP method khác) |

---

## 1. Cronjobs chưa implement

Nguồn xác nhận: `documents/operations/CRONJOBS.md` + audit `@Scheduled` trong toàn bộ Java code.  
**Chỉ có JOB-23 (`PayoutScheduler`) có `@Scheduled` thực sự trong code.**

### flashsale-service (:8085)

| Job ID | Cron | Mô tả |
|--------|------|-------|
| JOB-01 | `*/1 * * * *` | Session lifecycle tự động: UPCOMING→ACTIVE→ENDED |
| JOB-08 | `0 3 * * *` | Dọn flash sale data soft-delete >30 ngày |
| JOB-21 | `0 4 * * *` | Stock reconciliation sau flash sale kết thúc |

> **JOB-01 là critical:** Không có job này, sessions sẽ mãi ở UPCOMING — flash sale **không bao giờ bắt đầu/kết thúc tự động**.

### product-service (:8090)

| Job ID | Cron | Mô tả |
|--------|------|-------|
| JOB-07 | `0 */2 * * *` | Xóa stale cart >24h không hoạt động |
| JOB-10 | `0 3 * * 0` | Hard delete product soft-delete >90 ngày |
| JOB-16 | `0 2 * * *` | Auto-hide rejected products >30 ngày |

### order-service (:8083)

| Job ID | Cron | Mô tả |
|--------|------|-------|
| JOB-13 | `*/10 * * * *` | Auto-cancel PENDING orders >30 phút |
| JOB-22 | `0 */6 * * *` | Auto-deliver SHIPPING orders >7 ngày không cập nhật |

> **JOB-13 là critical:** Không có job này, đơn hàng PENDING quá giờ sẽ tồn tại mãi mãi. Luồng cancel do timeout hiện được trigger bởi `stock.reservation.expired` (ReservationCleanupScheduler đã implement), nhưng các đơn không có reservation sẽ không được auto-cancel.

### payment-service (:8082)

| Job ID | Cron | Mô tả | Ghi chú |
|--------|------|-------|---------|
| JOB-04 | `*/5 * * * *` | Outbox event publisher | deferred — outbox pattern chưa implement |
| JOB-05 | `0 2 * * *` | Cleanup outbox events >7 ngày | deferred |
| JOB-06 | `0 2 * * *` | Cleanup failed events >30 ngày | deferred |
| JOB-12 | `0 3 * * *` | ShedLock stale lock cleanup | post-MVP |
| JOB-15 | `0 */1 * * *` | Nullify expired Stripe onboarding URLs >24h | post-MVP |

---

## 2. Kafka Producers chưa implement

### flashsale-service — KHÔNG produce bất kỳ domain event nào

**Theo tài liệu**, flashsale-service phải produce 4 events:

| Topic | Trigger | Consumers | Trạng thái |
|-------|---------|-----------|-----------|
| `flash_sale.session_started` | JOB-01 khi `start_time` đến | Product Service, Notification Service | ❌ KHÔNG produce |
| `flash_sale.session_ended` | JOB-01 khi `end_time` đến | Product Service, Notification Service | ❌ KHÔNG produce |
| `flash_sale.item_registered` | Seller đăng ký sản phẩm vào session | Notification Service | ❌ KHÔNG produce |
| `flash_sale.session_created` | Admin tạo session | Audit log | ❌ KHÔNG produce |

**Code thực tế:** FlashSaleService chỉ send 2 topics:
- `order.checkout_submitted` (khi buyer mua flash sale) ✅
- `order.address.request` (request-reply lấy địa chỉ) ✅

**Còn có stub consumer không có ý nghĩa:**
```java
// FlashSaleService.java line 92
@KafkaListener(topics = KafkaTopics.FLASH_SALE_SESSION_STARTED, ...)
public void onSessionStarted(String sessionId) {
    log.info("Flash sale session started: {}", sessionId);  // chỉ log, không làm gì
}
```
Service này consume event của chính mình nhưng chưa bao giờ emit nó.

**Hậu quả dây chuyền:** Vì `flash_sale.session_started` không bao giờ được emit:
1. `product-service` không bao giờ áp dụng flash price cho variant → `flash_sale.price_sync` không bao giờ được emit
2. `search-service` không bao giờ cập nhật giá flash trong Elasticsearch
3. `notification-service` không bao giờ thông báo user về flash sale bắt đầu/kết thúc

---

## 3. Kafka Consumers chưa implement / là Stub

### notification-service — thiếu `flash_sale.item_registered`

**Theo tài liệu:** notification-service nên consume `flash_sale.item_registered` để thông báo seller khi sản phẩm được đăng ký vào flash sale session.

**Code thực tế:** `FlashSaleEventConsumer.java` chỉ có:
```java
@KafkaListener(topics = KafkaTopics.FLASH_SALE_SESSION_STARTED, ...)
@KafkaListener(topics = KafkaTopics.FLASH_SALE_SESSION_ENDED, ...)
```
Không có listener cho `flash_sale.item_registered`. ❌

---

### payment-service — stub listener undocumented

**Không có trong tài liệu** nhưng tồn tại trong code:
```java
// PaymentService.java line 670
@KafkaListener(topics = KafkaTopics.PAYMENT_SUCCESS, groupId = "payment-service-group")
public void onPaymentSuccess(String message) {
    log.info("Payment success event received: {}", message);  // chỉ log
}
```
Payment-service tự consume event của chính mình nhưng không làm gì. Dead stub code.

---

### identity-service — 4 consumers là stub (post-MVP)

File: `IdentityEventConsumer.java`

Tất cả 4 listeners chỉ ghi log, không có business logic:

| Topic consumed | Logic theo tài liệu | Trạng thái trong code |
|----------------|--------------------|-----------------------|
| `order.delivered` | Unlock seller posting capability | `log.info(...)` only |
| `order.cancelled` | Audit log | `log.info(...)` only |
| `refund.admin_approved` | Log refund approval | `log.info(...)` only |
| `refund.rejected` | Notify buyer | `log.info(...)` only |

Comment trong code: `// Post-MVP: unlock seller posting capability`

---

## 4. API Endpoints chưa implement / sai

### product-service — thiếu public product listing

| Method | Path | Mô tả | Contract |
|--------|------|-------|---------|
| `GET` | `/v1/products` | Public product listing có phân trang + filters (category_id, seller_id, min_price, max_price, page, size) | [`api-get-products.yaml`](api-contracts/product-service/api-get-products.yaml) |

`ProductController` chỉ có `GET /products/{productId}` (single product). Không có paginated list.

---

### notification-service — HTTP method discrepancy

| Endpoint theo tài liệu | Endpoint trong code | Vấn đề |
|------------------------|---------------------|--------|
| `GET /v1/notifications/history` | `GET /v1/notifications` | Path khác |
| `PUT /v1/notifications/{id}/read` | `PATCH /v1/notifications/{notifId}/read` | PUT → PATCH |
| `PUT /v1/notifications/read-all` | `PATCH /v1/notifications/read-all` | PUT → PATCH |

---

## 5. Những thứ ĐÃ implement đầy đủ

### Kafka Producers ✅

| Service | Topics produced |
|---------|----------------|
| product-service | `product.activated`, `product.deactivated`, `product.updated`, `product.deleted`, `product.pending_review`, `product.approved`, `product.rejected`, `variant.price_updated`, `variant.stock_updated`, `category.updated`, `flash_sale.price_sync`, `search.index_data.response`, `order.checkout_submitted`, `stock.reservation.expired` |
| order-service (Axon saga) | `order.created`, `order.shipped`, `order.delivered`, `order.cancelled`, `seller.order_cancelled`, `order.returned`, `order.paid`, `order.payment_failed`, `order.payment_timeout`, `order.auto_cancelled`, `refund.requested`, `refund.full_requested`, `payment.requested` |
| payment-service | `payment.success`, `payment.failed`, `seller.transfer.eligible`, `seller.transfer.paid_out`, `seller.transfer.failed` |
| refund-service | `refund.created`, `refund.admin_approved`, `refund.rejected`, `refund.rts_completed`, `refund.stripe_auto`, request-reply responses |
| flashsale-service | `order.checkout_submitted` (flash buy), `order.address.request` |
| search-service | `search.index_data.request` |

### Kafka Consumers ✅

| Service | Topics consumed |
|---------|----------------|
| product-service | `order.paid`, `order.payment_failed`, `order.cancelled`, `order.returned`, `flash_sale.session_started`, `flash_sale.session_ended`, `search.index_data.request` |
| order-service | `payment.success`, `payment.failed`, `refund.admin_approved`, `refund.rts_completed`, `stock.reservation.expired`, `order.checkout_submitted` |
| payment-service | `payment.requested`, `order.delivered`, `order.cancelled`, `order.auto_cancelled` |
| refund-service | `refund.requested`, `refund.full_requested`, `order.returned`, `refund.stripe_auto`, request-reply (`order.refunds.request`, `order.refund_presigned_url.request`, `order.payment_status.request`) |
| search-service | `product.activated`, `product.deactivated`, `product.updated`, `product.deleted`, `category.updated`, `variant.price_updated`, `variant.stock_updated`, `flash_sale.price_sync`, `search.index_data.response` |
| notification-service | `product.pending_review`, `product.approved`, `product.rejected`, `order.created`, `order.shipped`, `order.delivered`, `order.cancelled`, `order.returned`, `seller.order_cancelled`, `order.payment_timeout`, `payment.success`, `payment.failed`, `refund.requested`, `refund.admin_approved`, `refund.rejected`, `seller.transfer.eligible`, `seller.transfer.paid_out`, `seller.transfer.failed`, `stock.reservation.expired`, `ai_chat.message_sent`, `ai_chat.tool_call_executed`, `ai_chat.confirmation_resolved` |
| identity-service | `order.address.request` (request-reply ✅), `order.delivered`, `order.cancelled`, `refund.admin_approved`, `refund.rejected` (stub ⚠️) |
| flashsale-service | `order.address.response` (request-reply) |

### APIs ✅ (tất cả services có đầy đủ trừ mục §4)

identity-service, product-service (CRUD + cart + inventory + admin review), order-service (checkout + lifecycle + refunds), payment-service (stripe + onboarding + earnings), refund-service (admin review), notification-service (SSE + history), search-service (search + reindex), flashsale-service (CRUD + buy + reminders), ai-chat-service.

---

## 6. Ưu tiên xử lý

### Phải làm trước production

1. **flashsale-service: emit `flash_sale.session_started` / `flash_sale.session_ended`** — implement JOB-01 hoặc mechanism tương đương; không có thì toàn bộ flash sale pricing chain bị broken
2. **flashsale-service: emit `flash_sale.item_registered`** — khi seller đăng ký sản phẩm
3. **JOB-13: auto-cancel PENDING orders >30 phút** — tránh đơn hàng zombie
4. **`GET /v1/products`** — public product listing

### Nên làm sớm (ảnh hưởng UX)

5. **notification-service: add `flash_sale.item_registered` consumer**
6. **JOB-22: auto-deliver stale SHIPPING orders**
7. **JOB-07: stale cart cleanup**
8. **notification-service: đồng bộ HTTP method** (PUT vs PATCH)
9. **identity-service: implement logic thực cho `order.delivered`** (unlock seller posting)

### Deferred post-MVP

10. **Outbox pattern** (JOB-04/05/06) — hiện tại payment-service publish Kafka trực tiếp
11. **JOB-08, JOB-10, JOB-16, JOB-21** — data cleanup jobs
12. **JOB-12, JOB-15** — maintenance jobs
13. **payment-service: xóa stub `onPaymentSuccess` listener** (dead code)

---

*Phân tích dựa trên code tại branch `main`, commit `0175bc9`.*
