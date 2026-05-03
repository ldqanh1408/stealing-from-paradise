# Payment Service — API Reference

> Base path: `/api/v1` → Gateway routes to `payment-service:8084`
>
> External: Stripe Connect, VNPAY

---

## Stripe Onboarding (Seller)

### POST /stripe/onboarding/start
**Bắt đầu onboarding Stripe**

**Quyền truy cập**: JWT Required (SELLER)

**Request Body:** (no body)

**Response 201:**
```json
{
  "success": true,
  "data": {
    "onboarding_url": "https://connect.stripe.com/setup/e/acct_xxx/...",
    "expires_at": "2025-10-02T10:00:00Z"
  }
}
```

---

### GET /stripe/onboarding/status
**Kiểm tra trạng thái Stripe account**

**Quyền truy cập**: JWT Required (SELLER)

**Response 200:**
```json
{
  "success": true,
  "data": {
    "stripe_account_id": "acct_xxx",
    "account_status": "ACTIVE",
    "charges_enabled": true,
    "payouts_enabled": false,
    "requirements": {
      "currently_due": ["individual.verification.document"],
      "eventually_due": ["company.tax_id"],
      "disabled_reason": "requirements.past_due"
    }
  }
}
```

---

### POST /stripe/onboarding/refresh-link
**Tạo lại onboarding link (khi link cũ hết hạn)**

**Quyền truy cập**: JWT Required (SELLER)

**Request Body:** (no body)

**Response 201:**
```json
{
  "success": true,
  "data": {
    "onboarding_url": "https://connect.stripe.com/setup/e/acct_yyy/...",
    "expires_at": "2025-10-03T10:00:00Z"
  }
}
```

---

## Payments

### GET /payments/parent-order/{parentOrderId}
**Trạng thái thanh toán của đơn cha**

**Quyền truy cập**: JWT Required (BUYER | ADMIN)

**Response 200:**
```json
{
  "success": true,
  "data": {
    "parent_order_id": 100,
    "final_amount": 530000,
    "paid_amount": 0,
    "status": "PENDING",
    "method": "STRIPE",
    "transactions": [
      {
        "transaction_id": 200,
        "amount": 530000,
        "method": "STRIPE",
        "trans_ref": "pi_3Px...",
        "status": "SUCCESS",
        "created_at": "2025-11-01T08:00:00Z"
      }
    ]
  }
}
```

---

## Webhooks

### POST /stripe/webhooks
**Stripe Webhook endpoint**

**Quyền truy cập**: Public (xác thực bằng Stripe signature)

**Events xử lý:**
- `checkout.session.completed` — Thanh toán thành công
- `account.updated` — Stripe Connect account thay đổi
- `charge.refunded` — Stripe refund hoàn tất

**Response 200:**
```json
{
  "received": true
}
```

---

## Seller Transfers

### GET /seller/payments/transfers
**Lịch sử chuyển tiền (Seller)**

**Quyền truy cập**: JWT Required (SELLER)

**Query Params:**

| Param | Type | Mô tả |
|-------|------|-------|
| status | string | PENDING | SUCCESS | FAILED | REVERSED |
| from_date | date | |
| to_date | date | |
| page | integer | Default 0 |
| size | integer | Default 20 |

---

### GET /seller/payments/balance
**Số dư khả dụng (Seller)**

**Quyền truy cập**: JWT Required (SELLER)

**Response 200:**
```json
{
  "success": true,
  "data": {
    "seller_id": 10,
    "pending_balance": 1500000,
    "available_balance": 5000000,
    "total_earned": 15000000
  }
}
```

---

## Admin — Refund Management

### GET /admin/refunds
**Tất cả yêu cầu hoàn tiền**

**Quyền truy cập**: JWT Required (ADMIN)

**Query Params:**

| Param | Type | Mô tả |
|-------|------|-------|
| status | string | PENDING | SUCCESS | FAILED | REJECTED |
| type | string | FULL | PARTIAL |
| seller_id | long | Filter |
| group_ref | uuid | Filter |
| from_date | date | |
| to_date | date | |
| page | integer | Default 0 |
| size | integer | Default 20 |

---

### POST /admin/refunds/{refundId}/approve
**Duyệt hoàn tiền thủ công**

**Quyền truy cập**: JWT Required (ADMIN)

**Request Body:**
```json
{
  "admin_note": "string",          // (Required)
  "adjust_amount": "decimal",      // Override số tiền
  "caused_by": "string",           // SELLER | BUYER
  "tracking_number": "string"      // Mã vận đơn hoàn (v5.3)
}
```

**Response 200:**
```json
{
  "success": true,
  "data": {
    "refund_id": 88,
    "status": "SUCCESS",
    "amount": 500000,
    "tracking_number": "VC123456789",
    "reviewed_by": 1,
    "reviewed_at": "2025-12-15T10:30:00Z"
  }
}
```

**Side Effects:**
1. Stripe: `refunds.create({ payment_intent, amount })`
2. REFUNDS.status = SUCCESS
3. Publish `refund.admin_approved` → Kafka
4. Seller trust_score -= 5 (nếu caused_by = SELLER)
5. Push notification đến Buyer

---

### POST /admin/refunds/{refundId}/reject
**Từ chối yêu cầu hoàn tiền**

**Quyền truy cập**: JWT Required (ADMIN)

**Request Body:**
```json
{
  "reject_reason": "string",       // (Required)
  "fraud_evidence": "boolean"      // true = trừ điểm Buyer
}
```

**Response 200:** Refund rejected, push notification
