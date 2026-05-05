# 💳 Payment Service API

**Port**: `:8082`  
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
  "status": "SUCCESS",
  "application_fee_amount": 60000,
  "trans_ref": "TXN-20251001-301",
  "pay_at": "2026-10-01T10:05:00Z",
  "sellers": [
    {
      "seller_id": 5,
      "order_id": 100,
      "amount": 700000,
      "fee": 35000,
      "net_amount": 665000,
      "stripe_transfer_id": "tr_3PxABC2K98765432",
      "transfer_status": "SUCCESS"
    },
    {
      "seller_id": 9,
      "order_id": 101,
      "amount": 500000,
      "fee": 25000,
      "net_amount": 475000,
      "stripe_transfer_id": "tr_3PxABC2K98765433",
      "transfer_status": "SUCCESS"
    }
  ]
}
```

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

---

### GET /payments/parent-order/{parentOrderId}/client-secret
**Lấy Stripe client secret để thanh toán**

**Quyền truy cập**: JWT Required (BUYER)

**Response 200**:
```json
{
  "client_secret": "pi_3PxABC2K1234567_secret_abc123..."
}
```

---

### GET /payments/by-intent/{stripePaymentIntentId}
**Tra cứu giao dịch theo Stripe Payment Intent ID**

**Quyền truy cập**: JWT Required (BUYER \| ADMIN)

**Response 200**: Thông tin TRANSACTIONS theo `stripePaymentIntentId`.

---

## Seller Payments

### GET /seller/payments/earnings
**Xem thu nhập của Seller**

**Quyền truy cập**: JWT Required (SELLER)

**Response 200**: Tổng quan thu nhập, lịch sử transfer, số dư.

---

### GET /seller/payments/stripe-dashboard
**Lấy link Stripe Dashboard**

**Quyền truy cập**: JWT Required (SELLER)

**Response 200**:
```json
{
  "dashboard_url": "https://dashboard.stripe.com/express/..."
}
```

---

## Seller Transfers

### GET /seller/payments/transfers
**Lịch sử chuyển tiền (Seller)**

**Quyền truy cập**: JWT Required (SELLER)

**Query Params**:
| Param | Type | Mô tả |
|-------|------|-------|
| status | string | PENDING \| SUCCESS \| FAILED \| REVERSED |
| from_date | date | ISO 8601 |
| to_date | date | ISO 8601 |
| page | integer | Default 0 |
| size | integer | Default 20 |

---

### GET /seller/payments/balance
**Số dư khả dụng (Seller)**

**Quyền truy cập**: JWT Required (SELLER)

**Response 200**:
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

## Admin Refund Management

> Các endpoint này nằm trong Payment Service, yêu cầu quyền ADMIN.

### GET /admin/refunds
**Danh sách tất cả refund**

**Quyền truy cập**: JWT Required (ADMIN)

**Query Params**: status, from_date, to_date, page, size

---

### GET /admin/refunds/{refundId}
**Chi tiết một refund**

**Quyền truy cập**: JWT Required (ADMIN)

---

### POST /admin/refunds/{refundId}/approve
**Admin duyệt refund**

**Quyền truy cập**: JWT Required (ADMIN)

**Request Body**:
```json
{
  "tracking_number": "VT123456789",
  "note": "Đã xác nhận, hoàn tiền"
}
```

---

### POST /admin/refunds/{refundId}/reject
**Admin từ chối refund**

**Quyền truy cập**: JWT Required (ADMIN)

**Request Body**:
```json
{
  "reason": "Không đủ điều kiện hoàn tiền"
}
```

---

## 📊 Summary

| Endpoint | Method | Auth |
|----------|--------|------|
| /stripe/onboarding/start | POST | JWT (SELLER) |
| /stripe/onboarding/status | GET | JWT (SELLER) |
| /stripe/onboarding/refresh-link | POST | JWT (SELLER) |
| /payments/parent-order/{id} | GET | JWT (BUYER\|ADMIN) |
| /payments/parent-order/{id}/client-secret | GET | JWT (BUYER) |
| /payments/by-intent/{id} | GET | JWT (BUYER\|ADMIN) |
| /stripe/webhooks | POST | Stripe signature |
| /seller/payments/earnings | GET | JWT (SELLER) |
| /seller/payments/stripe-dashboard | GET | JWT (SELLER) |
| /seller/payments/transfers | GET | JWT (SELLER) |
| /seller/payments/balance | GET | JWT (SELLER) |
| /admin/refunds | GET | JWT (ADMIN) |
| /admin/refunds/{id} | GET | JWT (ADMIN) |
| /admin/refunds/{id}/approve | POST | JWT (ADMIN) |
| /admin/refunds/{id}/reject | POST | JWT (ADMIN) |

**Kafka Topics published by Payment Service**:
- `payment.success` — Thanh toán thành công
- `payment.failed` — Thanh toán thất bại
- `stripe.account_suspended` — Stripe Express bị đình chỉ

---

**Phiên bản:** v5.4  
**Cập nhật:** 2026-04-30
