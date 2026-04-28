# 💳 Payment Service API (+ Refund Management)

**Service Name**: Payment Service + Refund Management (Consolidated)  
**Port**: `:8085`  
**Base URL**: `/api/v1`  
**Status**: v5.3 RTS

**Mô tả**: Stripe Connect · Destination Charges · Transfer API · Refund Management · Webhooks

---

## 📡 Kafka Integration

### Produces (Event Publisher)
- `payment.success` → Order Service (Payment succeeded)
- `payment.failed` → Order, Notification Services (Payment failed)
- `refund.requested` → Notification Service (Refund requested)
- `refund.admin_approved` → Notification Service (Refund approved)
- `refund.rejected` → Notification Service (Refund rejected)
- `refund.stripe_auto` → Order, Loyalty Services (Stripe chargeback)

### Consumes (Event Subscriber)
- `order.created` ← Order Service (Receive order for payment processing)

---

## 💰 Stripe Onboarding (Seller)

### POST /stripe/onboarding/start
**Bắt đầu onboarding Stripe (Seller)**

**Quyền truy cập**: JWT Required (SELLER)  
**Tags**: Stripe Connect

**Mô tả**: Gọi Stripe API `accountLinks.create` để tạo onboarding URL. URL hợp lệ trong 24 giờ, sau đó tự null bởi JOB-15.

**Request Body**: (không có body)

**Response 201**:
```json
{
  "onboarding_url": "https://connect.stripe.com/setup/e/acct_xxx/...",
  "expires_at": "2025-10-02T10:00:00Z"
}
```

**Error Responses**:
| Status | Mô tả |
|--------|-------|
| 409 | Seller đã có Stripe account (details_submitted = true) |

---

### GET /stripe/onboarding/status
**Kiểm tra trạng thái Stripe account**

**Quyền truy cập**: JWT Required (SELLER)

**Response 200**:
```json
{
  "stripe_account_id": "acct_1OxABC",
  "details_submitted": true,
  "charges_enabled": true,
  "payouts_enabled": true,
  "onboarding_status": "COMPLETE",
  "onboarding_url": null
}
```

**Ghi chú**:
- PENDING = chưa bắt đầu
- IN_PROGRESS = đang KYC
- COMPLETE = đã xong
- SUSPENDED = bị Stripe đình chỉ

---

### POST /stripe/onboarding/refresh-link
**Tạo lại onboarding link (hết hạn)**

**Quyền truy cập**: JWT Required (SELLER)  
**Tags**: Stripe Connect

**Request Body**: (không có body)

**Response 200**:
```json
{
  "onboarding_url": "https://connect.stripe.com/setup/e/acct_xxx/new-link",
  "expires_at": "2025-10-03T10:00:00Z"
}
```

---

## 💳 Payment Endpoints

### GET /payments/parent-order/{parentOrderId}
**Thông tin giao dịch thanh toán**

**Quyền truy cập**: JWT Required (BUYER \| ADMIN)

**Response 200**:
```json
{
  "transaction_id": 301,
  "parent_order_id": 55,
  "amount": 1330000,
  "method": "STRIPE",
  "status": "SUCCESS",
  "stripe_pi_id": "pi_3PxABC...",
  "application_fee": 66500,
  "trans_ref": "TXN-20251001-301",
  "paid_at": "2025-10-01T10:05:00Z",
  "remaining_seconds": null
}
```

**Ghi chú**: `remaining_seconds` chỉ có giá trị khi status = PENDING

---

### POST /stripe/webhooks
**Nhận Stripe Webhook events**

**Quyền truy cập**: Webhook (không cần JWT)

**Events xử lý**:
| Event | Xử lý |
|-------|-------|
| payment_intent.succeeded | TRANSACTIONS → SUCCESS, produce payment.success |
| payment_intent.payment_failed | TRANSACTIONS → FAILED, produce payment.failed |
| charge.refunded | REFUNDS → SUCCESS |
| account.updated | Sync SELLER_STRIPE_ACCOUNTS |
| transfer.created | Ghi stripe_transfer_id |

---

## ↩️ Refund Management APIs

### POST /orders/parent/{parentOrderId}/refund
**Full Refund toàn bộ đơn cha**

**Quyền truy cập**: JWT Required (BUYER)  
**Tags**: Stripe Refund API | Kafka → refund.full_requested

**Điều kiện**:
- order.status == "PAID" (chưa ship)
- transaction.status == "SUCCESS" (đã thanh toán)
- refunds_pending == 0 (không có refund PENDING)
- Nếu BẤT KỲ sub-order nào SHIPPING/DELIVERED → Reject 422

**Request Body**:
```json
{
  "reason": "string",           // Lý do hủy đơn (Required)
  "evidence_images": ["string"] // Mảng URL ảnh bằng chứng (Optional)
}
```

**Response 201**: Full refund created

---

### GET /orders/parent/{parentOrderId}/refund
**Trạng thái Full Refund của đơn cha**

**Quyền truy cập**: JWT Required (BUYER \| ADMIN)

**Response 200**: Full refund status

---

### POST /orders/{orderId}/refunds
**Partial Refund — 1 sub-order (1 seller)**

**Quyền truy cập**: JWT Required (BUYER)  
**Tags**: Stripe Refund API | Kafka → refund.requested

**Điều kiện**:
- order.status IN ["PAID", "SHIPPING", "DELIVERED", "PARTIALLY_REFUNDED"]
- qty_to_refund > 0
- qty_to_refund ≤ (item.quantity - item.refunded_quantity)
- Nếu status = DELIVERED: NOW() - order.updated_at ≤ 7 ngày

**Request Body**:
```json
{
  "reason": "string",                // Lý do hoàn chung (Required)
  "items": [                         // Danh sách items cần hoàn (Required)
    {
      "order_item_id": "long",       // ID của ORDER_ITEM (Required)
      "quantity": "integer",         // Số lượng cần hoàn (Required)
      "item_reason": "string"        // Lý do hoàn riêng cho item này (Optional)
    }
  ],
  "evidence_images": ["string"]      // Ảnh bằng chứng (MinIO URLs) (Optional)
}
```

**Response 201**: Yêu cầu tạo thành công

---

### POST /orders/parent/{parentOrderId}/refunds/partial
**Partial Refund — nhiều sub-orders / sellers**

**Quyền truy cập**: JWT Required (BUYER)  
**Tags**: Kafka → refund.requested (per seller)

**Mô tả**: System tự động nhóm items theo sub-order, tạo REFUNDS riêng cho mỗi seller, liên kết bằng `group_ref`.

**Request Body**:
```json
{
  "reason": "string",          // Lý do hoàn chung (Required)
  "items": [                   // Items từ nhiều sub-orders/sellers (Required)
    {
      "order_item_id": "long",  // ID ORDER_ITEM (Required)
      "quantity": "integer",    // Số lượng (Required)
      "item_reason": "string"   // Lý do riêng (Optional)
    }
  ],
  "evidence_images": ["string"] // Ảnh bằng chứng (Optional)
}
```

**Response 201**: All refunds created with group_ref

---

### GET /orders/{orderId}/refunds
**Lịch sử hoàn tiền của 1 sub-order**

**Quyền truy cập**: JWT Required (BUYER \| SELLER - owner \| ADMIN)

**Response 200**: List of refunds

---

### GET /orders/{orderId}/refunds/{refundId}
**Chi tiết 1 yêu cầu hoàn tiền**

**Quyền truy cập**: JWT Required (BUYER \| ADMIN)

**Response 200**: Full refund detail: REFUNDS + REFUND_ITEMS + admin_note

---

### GET /orders/refunds
**Tất cả yêu cầu hoàn tiền của Buyer**

**Quyền truy cập**: JWT Required (BUYER)

**Query Params**:
| Param | Type | Mô tả |
|-------|------|-------|
| status | string | PENDING \| SUCCESS \| FAILED \| REJECTED |
| type | string | FULL \| PARTIAL |
| from_date / to_date | date | ISO 8601 |
| page, size | integer | Phân trang |

**Response 200**: List of refunds

---

### GET /orders/{orderId}/refunds/presigned-url
**Lấy MinIO Pre-signed URL để upload ảnh bằng chứng hoàn tiền**

**Quyền truy cập**: JWT Required (BUYER - owner)

**Mô tả**: Trả về Pre-signed PUT URL từ MinIO với TTL **15 phút**.

**Query Params**:
| Param | Type | Required | Mô tả |
|-------|------|----------|-------|
| file_name | string | ✓ | Tên file gốc (vd: evidence.jpg) |
| content_type | string | ✓ | MIME type: image/jpeg \| image/png \| image/webp |

**Response 200**:
```json
{
  "presigned_url": "https://minio.internal/refund-evidence/orders/100/uuid-abc.jpg?X-Amz-Signature=...",
  "object_url": "https://cdn.marketplace.vn/refund-evidence/orders/100/uuid-abc.jpg",
  "expires_in": 900
}
```

---

## 🛡️ Admin Refund Management

### GET /admin/refunds
**Tất cả yêu cầu hoàn tiền (Admin)**

**Quyền truy cập**: JWT Required (ADMIN)

**Query Params**:
| Param | Type | Mô tả |
|-------|------|-------|
| status | string | PENDING \| SUCCESS \| FAILED \| REJECTED |
| type | string | FULL \| PARTIAL |
| seller_id | long | Lọc theo seller bị ảnh hưởng |
| group_ref | uuid | Lọc theo nhóm Full Refund |
| from_date / to_date | date | Khoảng thời gian |
| page, size | integer | Phân trang |

---

### POST /admin/refunds/{refundId}/approve
**Duyệt hoàn tiền thủ công**

**Quyền truy cập**: JWT Required (ADMIN)  
**Tags**: Stripe Refund API | Kafka → refund.admin_approved

**Khi nào dùng**:
1. Tự động xử lý thất bại (FAILED) — Admin retry thủ công
2. Tranh chấp Buyer vs Seller — Admin phán quyết
3. Stripe webhook miss — Admin force approve

**Request Body**:
```json
{
  "admin_note": "string",        // Lý do Admin can thiệp (Required)
  "adjust_amount": "decimal",    // Override số tiền hoàn (Optional)
  "caused_by": "string",         // SELLER | BUYER (Optional)
  "tracking_number": "string"    // Mã vận đơn hoàn (Optional - v5.3 NEW)
}
```

**Mô tả trường mới (v5.3)**:
- `tracking_number`: Mã vận đơn hoàn hàng. Lưu vào `REFUND_ITEMS` để theo dõi hàng phản lại.

**Response 200**: Stripe refund created, REFUNDS.status = SUCCESS

---

### POST /admin/refunds/{refundId}/reject
**Từ chối yêu cầu hoàn tiền**

**Quyền truy cập**: JWT Required (ADMIN)  
**Tags**: Kafka → refund.rejected

**Request Body**:
```json
{
  "reject_reason": "string",      // Lý do từ chối (Required)
  "fraud_evidence": "boolean"     // true = trừ điểm Buyer (Optional)
}
```

**Response 200**: REFUNDS.status = REJECTED

---

## 📊 Summary

| Metric | Value |
|--------|-------|
| **Total Endpoints** | 13 |
| **Stripe Onboarding** | 3 |
| **Payment Endpoints** | 2 |
| **Refund Endpoints** | 8 |
| **Kafka Topics Produced** | 6 |
| **Kafka Topics Consumed** | 1 |

---

## 🔗 Integration Points

| Service | Topic | Direction | Mô tả |
|---------|-------|-----------|-------|
| **Order Service** | order.created | ← | Receive order for payment |
| **Order Service** | payment.success | → | Payment succeeded |
| **Order Service** | payment.failed | → | Payment failed |
| **Notification Service** | refund.requested | → | Refund requested |
| **Notification Service** | refund.admin_approved | → | Refund approved |
| **Notification Service** | refund.rejected | → | Refund rejected |
| **Loyalty Service** | refund.stripe_auto | → | Chargeback refunds |

---

**Last Updated**: 2026-04-28  
**Version**: v5.3 RTS

