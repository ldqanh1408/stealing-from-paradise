# 💳 Payment Service API

**Port**: `:8085`  
**Mô tả**: Stripe Connect · Destination Charges · Transfer API · Webhooks  
**Base URL**: `/api/v1`

---

## 📚 Mục Lục

1. [Stripe Onboarding (Seller)](#stripe-onboarding-seller)
2. [Payment Queries](#payment-queries)
3. [Stripe Webhooks](#stripe-webhooks)

---

## Stripe Onboarding (Seller)

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
  "stripe_account_id": "acct_1OxABC123456789",
  "account_status": "ACTIVE",
  "details_submitted": true,
  "charges_enabled": true,
  "payouts_enabled": true,
  "onboarding_status": "COMPLETE",
  "onboarding_url": null
}
```

**Onboarding Status Values**:
| Status | Mô tả |
|--------|-------|
| PENDING | Chưa bắt đầu |
| IN_PROGRESS | Đang KYC |
| COMPLETE | Đã hoàn thành |
| SUSPENDED | Bị Stripe đình chỉ |

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

## Payment Queries

### GET /payments/parent-order/{parentOrderId}
**Thông tin giao dịch thanh toán**

**Quyền truy cập**: JWT Required (BUYER \| ADMIN)

**Response 200**:
```json
{
  "transaction_id": 301,
  "parent_order_id": 55,
  "amount": 1200000,
  "method": "STRIPE",
  "status": "SUCCESS",
  "stripe_pi_id": "pi_3PxABC2K1234567...",
  "application_fee": 60000,
  "application_fee_percentage": 5.0,
  "trans_ref": "TXN-20251001-301",
  "paid_at": "2026-10-01T10:05:00Z",
  "remaining_seconds": null,
  "sellers": [
    {
      "seller_id": 5,
      "seller_name": "Shop Nike VN",
      "order_id": 100,
      "amount": 700000,
      "fee": 35000,
      "net_amount": 665000,
      "stripe_transfer_id": "tr_3PxABC2K98765432",
      "transfer_status": "SUCCEEDED"
    },
    {
      "seller_id": 9,
      "seller_name": "Shop Adidas VN",
      "order_id": 101,
      "amount": 500000,
      "fee": 25000,
      "net_amount": 475000,
      "stripe_transfer_id": "tr_3PxABC2K98765433",
      "transfer_status": "SUCCEEDED"
    }
  ]
}
```

> `remaining_seconds` chỉ có giá trị khi status = PENDING

---

## Stripe Webhooks

### POST /stripe/webhooks
**Nhận Stripe Webhook events**

**Mô tả**: Endpoint nhận webhook từ Stripe. Xác thực bằng `Stripe-Signature` header.

**Events xử lý**:
| Event | Xử lý |
|-------|-------|
| payment_intent.succeeded | TRANSACTIONS → SUCCESS |
| payment_intent.payment_failed | TRANSACTIONS → FAILED |
| charge.refunded | REFUNDS → SUCCESS |
| account.updated | Sync SELLER_STRIPE_ACCOUNTS |
| transfer.created | Ghi stripe_transfer_id |

---

## 📊 Summary

| Endpoint | Method | Auth |
|----------|--------|------|
| /stripe/onboarding/start | POST | JWT (SELLER) |
| /stripe/onboarding/status | GET | JWT (SELLER) |
| /stripe/onboarding/refresh-link | POST | JWT (SELLER) |
| /payments/parent-order/{id} | GET | JWT (BUYER\|ADMIN) |
| /stripe/webhooks | POST | Stripe signature |

**Kafka Topics published by Payment Service**:
- `payment.success` — Thanh toán thành công
- `payment.failed` — Thanh toán thất bại
- `stripe.account_suspended` — Stripe Express bị đình chỉ

---

**Phiên bản:** v5.3 RTS Unified  
**Cập nhật:** 2026-04-15
