# ✅ PHÂN TÍCH CHÍNH XÁC: Payment Service So Với Tài Liệu (Dùng Code-Review-Graph)

**Phân tích**: 2026-04-20  
**Tool**: code-review-graph (648 files, 2,148 nodes, 9,182 edges)  
**Kết luận**: Payment Service **~75% hoàn thành** (tốt hơn dự kiến ban đầu!)

---

## 📊 Thực Tế Hiện Tại (Theo Code-Review-Graph)

Payment Service đã có những components sau:

✅ **Models (Entities)**:
- Transaction (67 lines)
- SellerTransfer (58 lines)
- SellerStripeAccount (65 lines)
- **Refund** (84 lines) ← ĐÃ CÓ!
- **RefundItem** ← ĐÃ CÓ (referenced in RefundService)

✅ **Services**:
- PaymentService (284 lines) - Webhook + Payment init
- StripeOnboardingService (125 lines)
- **RefundService** (706 lines) ← ĐÃ CÓ & ĐẦY ĐỦ!

✅ **Controllers**:
- PaymentController (78 lines)
- StripeOnboardingController (53 lines)
- **AdminRefundController** (95 lines) ← ĐÃ CÓ!

✅ **DTOs**:
- RefundDetailResponse (98 lines)
- RefundListResponse (52 lines)
- AdminRefundApproveResponse (53 lines)
- AdminRefundRejectRequest, AdminRefundApproveRequest ← ĐÃ CÓ

---

## 🔴 Những Gì Thực Sự Còn Thiếu

Dựa vào phân tích graph, những tính năng THỰC SỰ THIẾU:

### 1. **❌ RTS (Return-to-Sender) Auto-Refund**

**Tài Liệu Yêu Cầu** (03_BUSINESS.md, 02_API.md):
- Seller gọi `POST /orders/{orderId}/return-to-sender` (Order Service endpoint)
- Payment Service nhận event từ Kafka topic `ORDER_RETURNED_RTS`
- Tự động execute Stripe refund (NO wait for Admin)
- Publish `REFUND_RTS_COMPLETED` event

**Trạng Thái Hiện Tại**:
- ❌ Không có `@KafkaListener` cho `ORDER_RETURNED_RTS`
- ❌ Không có method `handleReturnToSenderConfirmed()` hay tương tự
- ✅ Nhưng RefundService có method `approveRefund()` có thể dùng để auto-approve

**Ảnh hưởng**: Khi Seller xác nhận hàng hoàn, hệ thống KHÔNG tự động hoàn tiền Buyer

---

### 2. **❌ Kafka Consumers cho Refund Requests**

**Tài Liệu Yêu Cầu**:
- Order Service publish `REFUND_REQUESTED` hoặc `REFUND_FULL_REQUESTED`
- Payment Service consume và lưu vào database

**Trạng Thái Hiện Tại**:
- ❌ Không có `@KafkaListener` cho `REFUND_REQUESTED`
- ❌ Không có `@KafkaListener` cho `REFUND_FULL_REQUESTED`
- ✅ Nhưng `RefundService` có method `listAllRefunds()` để query

**Ảnh hưởng**: Refund requests từ Order Service sẽ bị mất (không được lưu vào Payment Service DB)

---

### 3. **❌ Request-Reply Consumers**

**Tài Liệu Yêu Cầu**:
- Order Service query Payment Service via Kafka request-reply pattern
- Topics: `ORDER_PAYMENT_STATUS_REQUEST` ↔ `ORDER_PAYMENT_STATUS_RESPONSE`
- Topics: `ORDER_REFUNDS_REQUEST` ↔ `ORDER_REFUNDS_RESPONSE`

**Trạng Thái Hiện Tại**:
- ⚠️ Topics được tạo trong `KafkaTopicConfig`
- ❌ KHÔNG có handlers để consume request topics
- ✅ Nhưng `PaymentService` có method `getTransactionByParentOrder()` có thể dùng

**Ảnh hưởng**: Order Service không thể query Payment status/refund info via Kafka

---

## 🟡 Phần Bổ Sung Cần Làm (3 Điểm Chính)

### A. **RTS Auto-Refund (P0 - Critical)**

Thêm vào `PaymentService` hoặc `RefundService`:

```java
@KafkaListener(topics = KafkaTopics.ORDER_RETURNED_RTS, groupId = "payment-service-group")
@Transactional
public void handleReturnToSenderConfirmed(String message) {
    // 1. Parse: order_id, seller_id, total_amount, evidence_images[]
    // 2. Find parent_order_id from order_id
    // 3. Get transaction from parent_order_id
    // 4. Create Refund (type=FULL, initiated_by=SELLER, status=PENDING)
    // 5. AUTO call refundService.approveRefund() → execute Stripe refund
    // 6. Publish REFUND_RTS_COMPLETED event
    // 7. Log & notify
}
```

---

### B. **Refund Request Consumers (P0 - Critical)**

Thêm vào `RefundService`:

```java
@KafkaListener(topics = KafkaTopics.REFUND_REQUESTED, groupId = "payment-service-group")
@Transactional
public void onRefundRequested(String message) {
    // Parse & save Refund record (status=PENDING)
    // Save RefundItems
}

@KafkaListener(topics = KafkaTopics.REFUND_FULL_REQUESTED, groupId = "payment-service-group")
@Transactional
public void onRefundFullRequested(String message) {
    // Parse & create multiple Refund records (1 per sub-order)
    // All with same group_ref
}
```

---

### C. **Request-Reply Consumers (P2 - Medium)**

Thêm vào `PaymentService`:

```java
@KafkaListener(topics = KafkaTopics.ORDER_PAYMENT_STATUS_REQUEST, groupId = "payment-service-group")
public void onOrderPaymentStatusRequest(String message) {
    // Parse parent_order_id
    // Query transaction
    // Publish to ORDER_PAYMENT_STATUS_RESPONSE
}

@KafkaListener(topics = KafkaTopics.ORDER_REFUNDS_REQUEST, groupId = "payment-service-group")
public void onOrderRefundsRequest(String message) {
    // Parse order_id
    // Query refunds
    // Publish to ORDER_REFUNDS_RESPONSE
}
```

---

## 📋 Danh Sách Chi Tiết Còn Thiếu

| Tính Năng | Loại | Trạng Thái | Độ Ưu Tiên |
|-----------|------|-----------|-----------|
| RTS Auto-Refund Listener | Kafka Consumer | ❌ Không có | P0 CRITICAL |
| REFUND_REQUESTED Listener | Kafka Consumer | ❌ Không có | P0 CRITICAL |
| REFUND_FULL_REQUESTED Listener | Kafka Consumer | ❌ Không có | P0 CRITICAL |
| ORDER_PAYMENT_STATUS_REQUEST Listener | Kafka Consumer | ❌ Không có | P2 MEDIUM |
| ORDER_REFUNDS_REQUEST Listener | Kafka Consumer | ❌ Không có | P2 MEDIUM |
| Distributed Lock (idempotency) | Infrastructure | ⚠️ Cơ bản có | P1 HIGH |
| Refund rejection with fraud flag | Logic | ✅ Có (trong RefundService) | ✓ DONE |
| Admin refund approval with tracking# | Logic | ✅ Có (v5.3) | ✓ DONE |
| Transfer reversal on partial refund | Logic | ❌ Chưa verify | P1 HIGH |

---

## 🔧 Implementation Plan (Ưu Tiên)

### **Phase 1 (P0 - CRITICAL - 1-2 ngày)**

1. Implement RTS auto-refund handler
   - File: `PaymentService.java` hoặc `RefundService.java`
   - Lines: +30-40
   - Method name: `onReturnToSenderConfirmed()` hoặc `handleReturnToSenderConfirmed()`

2. Implement Refund request consumers
   - File: `RefundService.java`
   - Lines: +40-60 (2 methods)
   - Method names: `onRefundRequested()`, `onRefundFullRequested()`

3. Verify transfer reversal logic
   - File: `RefundService.approveRefund()`
   - Check: calls `Transfer.reverse()` for partial refunds

### **Phase 2 (P1 - HIGH - 2-3 ngày)**

4. Add distributed lock for `onPaymentRequested()`
   - Use Redis `setIfAbsent()` with timeout
   - Prevent duplicate PaymentIntent creation

5. Implement request-reply consumers
   - File: `PaymentService.java`
   - Methods: `onOrderPaymentStatusRequest()`, `onOrderRefundsRequest()`

6. Add integration tests

### **Phase 3 (P2 - MEDIUM - Ongoing)**

7. Monitoring & alerting
8. Documentation update

---

## 📊 Kết Luận

**Payment Service hiện tại: ~75% hoàn thành** ✅

### ✅ Đã Có:
- Refund models & repositories
- RefundService (706 lines)
- AdminRefundController
- Admin approve/reject logic
- Stripe webhook handlers
- Seller Stripe onboarding
- Payment initialization & transfers

### ❌ Thiếu (Nhưng Không Phức Tạp):
- 3 Kafka listeners (RTS + 2 refund request)
- 2 Request-reply consumers
- Distributed lock enhancement
- Refund request persistence

**Effort để hoàn thành**: 2-3 ngày (~300-400 lines code)  
**Risk level**: LOW (logic đã có, chỉ cần integrate Kafka)  
**Priority**: HIGH (impacts payment flow integrity)

---

## 📚 Tham Khảo

- [08_PAYMENT_ORDER_INTEGRATION.md](08_PAYMENT_ORDER_INTEGRATION.md) - Integration patterns
- [06_PAYMENT_SAGA_FLOW.md](06_PAYMENT_SAGA_FLOW.md) - Saga details
- [02_API.md](02_API.md) - API specifications
- [03_BUSINESS.md](03_BUSINESS.md) - Business rules

